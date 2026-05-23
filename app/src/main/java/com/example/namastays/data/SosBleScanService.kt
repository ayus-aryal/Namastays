package com.example.namastays.data

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class SosBleScanService : Service() {

    companion object {
        private const val TAG = "BLE_SCAN_SERVICE"
        private const val CHANNEL_ID = "sos_scan_service_channel"
        private const val NOTIFICATION_ID = 8001
        private const val SCAN_DURATION_MS = 3_000L
        private const val SCAN_INTERVAL_MS = 60_000L
        private const val PREF_NAME = "ble_prefs"
        private const val PREF_KEY_ENABLED = "ble_scanning_enabled"

        const val ACTION_START = "com.example.namastays.START_BLE_SCAN"
        const val ACTION_STOP  = "com.example.namastays.STOP_BLE_SCAN"

        var isRunning = false
            private set

        fun isUserEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_KEY_ENABLED, false)
        }

        fun setUserEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_ENABLED, enabled)
                .apply()
        }

        fun start(context: Context) {
            setUserEnabled(context, true)
            val intent = Intent(context, SosBleScanService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            setUserEnabled(context, false)
            val intent = Intent(context, SosBleScanService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun toggle(context: Context) {
            if (isRunning) stop(context) else start(context)
        }
    }

    private var bleScanner: BluetoothLeScanner? = null
    private var scanJob: Job? = null
    private var isScanning = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Scan callback ─────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Log.d(TAG, "Batch: ${results.size} result(s)")
            results.forEach { handleResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED                  -> "Already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED              -> "Feature unsupported"
                SCAN_FAILED_INTERNAL_ERROR                   -> "Internal error"
                else                                         -> "Unknown error $errorCode"
            }
            Log.e(TAG, "Scan failed: $reason")
            isScanning = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!isRunning) {
                    startForegroundWithNotification()
                    startScanLoop()
                    isRunning = true
                    Log.d(TAG, "BLE scan service started")
                } else {
                    Log.d(TAG, "Already running — ignoring start")
                }
            }
            ACTION_STOP -> {
                stopScanLoop()
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.d(TAG, "BLE scan service stopped")
            }
        }
        return START_STICKY
    }

    // ── Scan loop — 3s scan every 60s ────────────────────────────────────────
    private fun startScanLoop() {
        scanJob = serviceScope.launch {
            while (isActive) {
                startSingleScan()
                delay(SCAN_DURATION_MS)
                stopSingleScan()
                Log.d(TAG, "Scan window done — next in ${SCAN_INTERVAL_MS / 1000}s")
                delay(SCAN_INTERVAL_MS - SCAN_DURATION_MS)
            }
        }
    }

    private fun stopScanLoop() {
        scanJob?.cancel()
        scanJob = null
        stopSingleScan()
    }

    @SuppressLint("MissingPermission")
    private fun startSingleScan() {
        if (isScanning) return

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.w(TAG, "Bluetooth not enabled — skipping scan cycle")
            return
        }

        bleScanner = adapter.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner unavailable — skipping scan cycle")
            return
        }

        val sosUuid = java.util.UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(sosUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            .setReportDelay(0L)
            .build()

        try {
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            isScanning = true
            Log.d(TAG, "Scan window started")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopSingleScan() {
        if (!isScanning) return
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            Log.d(TAG, "Scan window ended")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    // ── Packet handler ────────────────────────────────────────────────────────
    private fun handleResult(result: ScanResult) {
        val sosUuid = java.util.UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(sosUuid)) ?: return

        Log.d(TAG, "Packet received — size: ${serviceData.size} bytes")
        Log.d(TAG, "Raw bytes (first 5): ${serviceData.take(5).map { it.toInt() and 0xFF }}")

        if (serviceData.size < 19) {
            Log.w(TAG, "Too small: ${serviceData.size} bytes")
            return
        }
        if (serviceData[0] != 'N'.code.toByte() ||
            serviceData[1] != 'S'.code.toByte() ||
            serviceData[2] != 0x01.toByte()
        ) {
            Log.w(TAG, "Byte validation failed")
            return
        }

        val buffer = java.nio.ByteBuffer.wrap(serviceData)
        buffer.position(3)

        val lat = buffer.float
        val lon = buffer.float
        val timestamp = buffer.long
        val deviceName = result.scanRecord?.deviceName ?: "Unknown Device"
        val estimatedDistance = estimateDistance(result.rssi)

        Log.d(TAG, "SOS from '$deviceName' lat=$lat lon=$lon ~${estimatedDistance}m")

        val alert = SosAlert(
            deviceName = deviceName,
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
            timestamp = timestamp,
            estimatedDistanceMeters = estimatedDistance,
            rssi = result.rssi
        )

        SosAlertReceiver.handleAlert(this, alert) { }
    }

    private fun estimateDistance(rssi: Int): Int {
        return when {
            rssi >= -60 -> 5
            rssi >= -70 -> 15
            rssi >= -80 -> 40
            rssi >= -90 -> 70
            else        -> 100
        }
    }

    // ── Minimal foreground notification — no status updates ──────────────────
    private fun startForegroundWithNotification() {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Namastays Safety Active")
            .setContentText("BLE SOS monitoring enabled")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Background Scanner",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps BLE scanning active for nearby SOS detection"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopScanLoop()
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }
}