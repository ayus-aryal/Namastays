package com.example.namastays.data


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── SOS Alert data class ──────────────────────────────────────────────────────
data class SosAlert(
    val deviceName: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val estimatedDistanceMeters: Int,
    val rssi: Int
) {
    fun getFormattedTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getMapsUrl(): String {
        return "https://maps.google.com/?q=$latitude,$longitude"
    }

    fun getDistanceText(): String {
        return if (estimatedDistanceMeters < 0) "Unknown distance"
        else "Within ~$estimatedDistanceMeters meters"
    }
}

// ── Alert handler ─────────────────────────────────────────────────────────────
object SosAlertReceiver {

    private const val CHANNEL_ID = "sos_alert_channel"
    private const val NOTIFICATION_ID = 9001

    // Debounce — don't spam notifications from same device
    private val recentAlerts = mutableMapOf<String, Long>()
    private const val DEBOUNCE_MS = 30_000L // 30 seconds

    fun handleAlert(
        context: Context,
        alert: SosAlert,
        onSosReceived: (SosAlert) -> Unit
    ) {
        // Check debounce
        val lastAlert = recentAlerts[alert.deviceName] ?: 0L
        if (System.currentTimeMillis() - lastAlert < DEBOUNCE_MS) {
            return
        }
        recentAlerts[alert.deviceName] = System.currentTimeMillis()

        // Trigger callback for in-app UI update
        onSosReceived(alert)

        // Show notification
        showSosNotification(context, alert)
    }

    private fun showSosNotification(context: Context, alert: SosAlert) {
        createNotificationChannel(context)

        // Intent to open maps when notification tapped
        val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse(alert.getMapsUrl())
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mapsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SOS Alert Nearby!")
            .setContentText("${alert.deviceName} needs help • ${alert.getDistanceText()}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        """
                        From: ${alert.deviceName}
                        Distance: ${alert.getDistanceText()}
                        Location: ${alert.latitude}, ${alert.longitude}
                        Time: ${alert.getFormattedTime()}
                        
                        Tap to open location in Maps
                        """.trimIndent()
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SOS Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency SOS alerts from nearby Namastays users"
                enableVibration(true)
                enableLights(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}