package com.example.namastays.utilities

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.namastays.NamastaysApp
import com.example.namastays.R
import com.example.namastays.dto.TrekState
import com.example.namastays.screens.AltitudeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Foreground service that keeps [TrekEngine] alive and updates the
 * persistent notification with live altitude + zone + battery-saver status.
 *
 * Lifecycle:
 *   startForegroundService() → onCreate() → engine.start()
 *   stopService()            → onDestroy() → engine.stop()
 *
 * Notification updates:
 *   We observe only the fields that appear in the notification
 *   (altitude, altitudeZone, inBatterySaver) via distinctUntilChanged
 *   so we don't spam NotificationManager on every GPS fix.
 *   Updates are throttled to at most once per [NOTIFICATION_UPDATE_INTERVAL_MS].
 */
class TrekTrackingService : Service() {

    companion object {
        private const val TAG                          = "TrekTrackingService"
        private const val CHANNEL_ID                   = "trek_tracking"
        private const val NOTIFICATION_ID              = 1001
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 5_000L   // 5 s
    }

    private val engine by lazy { (application as NamastaysApp).trekEngine }

    private var serviceScope: CoroutineScope? = null
    private var lastNotificationUpdateMs = 0L

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Post initial notification immediately so startForeground() doesn't ANR
        startForeground(NOTIFICATION_ID, buildNotification(TrekState()))
        Log.d(TAG, "onCreate")

        try {
            engine.start()
            Log.d(TAG, "engine started")
        } catch (e: Exception) {
            Log.e(TAG, "engine start failed: ${e.message}")
            stopSelf()
            return
        }

        // Observe state changes and update the notification
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        serviceScope!!.launch {
            engine.state
                .map { s ->
                    // Only re-notify when these three values change
                    Triple(s.altitude.toInt(), s.altitudeZone, s.inBatterySaver)
                }
                .distinctUntilChanged()
                .collect { (_, _, _) ->
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdateMs >= NOTIFICATION_UPDATE_INTERVAL_MS) {
                        lastNotificationUpdateMs = now
                        updateNotification(engine.state.value)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY: if the system kills the service, restart it without
        // the original intent — engine will re-open a new session on restart.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope?.cancel()
        serviceScope = null
        engine.stop()
        Log.d(TAG, "onDestroy — engine stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trek Tracking",
                // LOW = no sound, no pop-up; still shows in shade
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description          = "Live altitude and trek stats"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun updateNotification(state: TrekState) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TrekState): Notification {
        val altText = if (state.altitude > 0.0)
            "${state.altitude.toInt()} m  ·  ${state.altitudeZone.label}"
        else
            "Acquiring GPS fix…"

        val subText = buildString {
            if (state.distanceKm > 0.0) append("%.1f km".format(state.distanceKm))
            if (state.inBatterySaver) {
                if (isNotEmpty()) append("  ·  ")
                append("Battery saver")
            }
        }.ifEmpty { "Trek Mode active" }

        // Tapping the notification opens the app to Trek Mode
        val tapIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val tapPending = PendingIntent.getActivity(this, 0, tapIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(altText)
            .setContentText(subText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)          // no sound/vibration on update
            .setContentIntent(tapPending)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }
}