package com.example.namastays.data


import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.util.UUID

object BleManager {

    private const val TAG = "BLE_SOS"

    // Unique identifier for Namastays SOS packets
    // Any device scanning for this UUID knows it's a Namastays SOS
    private val SOS_SERVICE_UUID = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isAdvertising = false
    private var isScanning = false

    // ── Advertising (Sending SOS) ─────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startAdvertising(
        context: Context,
        latitude: Double,
        longitude: Double,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (!BlePermissionHelper.canAdvertise(context)) {
            onResult(false, "BLE advertising not available")
            return
        }

        if (isAdvertising) {
            onResult(false, "Already advertising")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            onResult(false, "Bluetooth is not enabled")
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser

        if (advertiser == null) {
            onResult(false, "BLE advertising not supported on this device")
            return
        }

        // Build the payload
        val payload = buildSosPayload(context, latitude, longitude)

        // Advertise settings — high power, low latency for SOS
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false) // We don't need connections, just broadcast
            .setTimeout(0) // Advertise indefinitely until we stop
            .build()

        // Build the advertisement data with our SOS payload
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // Remove device name from ad packet — too large
            .addServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .addServiceData(ParcelUuid(SOS_SERVICE_UUID), payload)
            .build()

        // Put device name in scan response instead — separate 31 byte budget
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        advertiser?.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)

        isAdvertising = true
        Log.d(TAG, "BLE advertising started with payload size: ${payload.size} bytes")
        onResult(true, "📡 BLE SOS broadcast started")
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "BLE advertising stopped")
        }
    }

    // ── Payload Builder ───────────────────────────────────────────────────────

    private fun buildSosPayload(
        context: Context,
        latitude: Double,
        longitude: Double
    ): ByteArray {
        // Packet structure:
        // [2 bytes: app ID] [1 byte: type] [4 bytes: lat] [4 bytes: lon] [8 bytes: timestamp]
        // Total: 19 bytes — well within 31 byte BLE limit

        val buffer = ByteBuffer.allocate(19)

        // App identifier — "NS" for Namastays
        buffer.put('N'.code.toByte())
        buffer.put('S'.code.toByte())

        // Packet type — 0x01 = SOS
        buffer.put(0x01)

        // Latitude and longitude as floats (4 bytes each)
        buffer.putFloat(latitude.toFloat())
        buffer.putFloat(longitude.toFloat())

        // Timestamp (8 bytes)
        buffer.putLong(System.currentTimeMillis())

        return buffer.array()
    }

    // ── Advertise Callback ────────────────────────────────────────────────────

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val reason = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED     -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE      -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR      -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else                                 -> "Unknown error $errorCode"
            }
            Log.e(TAG, "Advertising failed: $reason")
        }
    }

    fun isCurrentlyAdvertising() = isAdvertising

    // ── Scanning (Receiving SOS) ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun startScanning(
        context: Context,
        onSosReceived: (SosAlert) -> Unit
    ) {
        if (!BlePermissionHelper.canScan(context)) {
            Log.e(TAG, "Cannot scan — missing permissions")
            return
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning")
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth not enabled")
            return
        }

        scanner = adapter.bluetoothLeScanner

        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }

        // Only scan for Namastays SOS packets using our UUID filter
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SOS_SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(
            listOf(filter),
            settings,
            buildScanCallback(context, onSosReceived)
        )

        isScanning = true
        Log.d(TAG, "BLE scanning started")
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning) {
            scanner?.stopScan(buildScanCallback(null) {})
            isScanning = false
            Log.d(TAG, "BLE scanning stopped")
        }
    }

    @SuppressLint("MissingPermission")
    private fun buildScanCallback(
        context: Context?,
        onSosReceived: (SosAlert) -> Unit
    ): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceData = result.scanRecord
                    ?.getServiceData(ParcelUuid(SOS_SERVICE_UUID))
                    ?: return

                // Validate it's a Namastays packet
                if (serviceData.size < 19) return
                if (serviceData[0] != 'N'.code.toByte()) return
                if (serviceData[1] != 'S'.code.toByte()) return
                if (serviceData[2] != 0x01.toByte()) return

                // Parse the payload
                val buffer = ByteBuffer.wrap(serviceData)
                buffer.position(3) // Skip app ID and type bytes

                val lat = buffer.float
                val lon = buffer.float
                val timestamp = buffer.long

                val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    result.scanRecord?.deviceName ?: "Unknown Device"
                } else {
                    @SuppressLint("MissingPermission")
                    result.device.name ?: "Unknown Device"
                }

                val rssi = result.rssi
                val estimatedDistance = estimateDistance(rssi)

                val alert = SosAlert(
                    deviceName = deviceName,
                    latitude = lat.toDouble(),
                    longitude = lon.toDouble(),
                    timestamp = timestamp,
                    estimatedDistanceMeters = estimatedDistance,
                    rssi = rssi
                )

                Log.d(TAG, "SOS received from $deviceName at ~${estimatedDistance}m")

                context?.let {
                    SosAlertReceiver.handleAlert(it, alert, onSosReceived)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                isScanning = false
                Log.e(TAG, "Scan failed with error: $errorCode")
            }
        }
    }

    // Estimate distance from RSSI value
    // Not perfectly accurate but good enough for relative proximity
    private fun estimateDistance(rssi: Int): Int {
        val txPower = -59 // Typical BLE TX power at 1 meter
        if (rssi == 0) return -1
        val ratio = rssi.toDouble() / txPower.toDouble()
        return if (ratio < 1.0) {
            (Math.pow(ratio, 10.0) * 100).toInt()
        } else {
            ((0.89976) * Math.pow(ratio, 7.7095) + 0.111).toInt() * 100
        }
    }

    fun isCurrentlyScanning() = isScanning
}