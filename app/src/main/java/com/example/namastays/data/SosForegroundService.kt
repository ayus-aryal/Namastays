package com.example.namastays.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.namastays.MainActivity
import com.example.namastays.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SosForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var countdownJob: Job? = null

    companion object {
        private const val TAG             = "SosForegroundService"
        private const val CHANNEL_ID      = "sos_channel"
        private const val NOTIF_ID        = 9001
        private const val COUNTDOWN_START = 5

        const val ACTION_START  = "com.example.namastays.SOS_START"
        const val ACTION_CANCEL = "com.example.namastays.SOS_CANCEL"
        const val ACTION_STOP   = "com.example.namastays.SOS_STOP"

        // Shared state — survives as long as the service process is alive
        private val _sosState = MutableStateFlow<SosState>(SosState.Idle)
        val sosState: StateFlow<SosState> = _sosState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SosForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, SosForegroundService::class.java).apply {
                    action = ACTION_CANCEL
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SosForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }

        fun resetState() {
            _sosState.value = SosState.Idle
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> handleStart()
            ACTION_CANCEL -> handleCancel()
            ACTION_STOP   -> handleStop()
        }
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        countdownJob?.cancel()
        serviceScope.cancel()
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun handleStart() {
        if (_sosState.value is SosState.Active || _sosState.value is SosState.Counting) return

        startForeground(NOTIF_ID, buildNotification("SOS countdown starting…"))
        startCountdown()
    }

    private fun handleCancel() {
        countdownJob?.cancel()
        _sosState.value = SosState.Idle
        updateNotification("SOS cancelled")
        stopSelf()
    }

    private fun handleStop() {
        countdownJob?.cancel()
        _sosState.value = SosState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Countdown ─────────────────────────────────────────────────────────────

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var remaining = COUNTDOWN_START
            _sosState.value = SosState.Counting(remaining)

            while (remaining > 0) {
                updateNotification("SOS activating in $remaining seconds… Tap to cancel")
                delay(1_000L)
                remaining--
                if (remaining > 0) _sosState.value = SosState.Counting(remaining)
            }

            // Countdown finished — fire SOS
            _sosState.value = SosState.Active(sentAt = System.currentTimeMillis())
            updateNotification("🆘 SOS Active — help is being notified")
            fireSos()
        }
    }

    // ── Fire SOS ──────────────────────────────────────────────────────────────

    private suspend fun fireSos() {
        withContext(Dispatchers.IO) {
            try {
                // SosManager handles both SMS AND BLE internally — don't call BleManager separately
                SosManager.sendSosMessages(
                    context  = applicationContext,
                    contacts = emptyList(), // pendingContacts already set via setPendingContacts()
                    onResult = { success, message ->
                        Log.d(TAG, "SOS result: success=$success, message=$message")
                        if (!success) {
                            _sosState.value = SosState.Failed(message)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "SOS fire failed: ${e.message}")
                _sosState.value = SosState.Failed(e.message ?: "Unknown error")
            }

            // ← DELETE the separate BleManager.startAdvertising() block entirely
            // SosManager.sendSosMessages already calls BleManager.startAdvertising()
            // with the correct context + coordinates from the location fetch
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Emergency",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "SOS emergency alerts"
                setShowBadge(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SosForegroundService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Emergency")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // ensure this drawable exists
            .setContentIntent(openAppIntent)
            .addAction(0, "Cancel", cancelIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(text))
    }
}