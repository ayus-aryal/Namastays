package com.example.namastays.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.namastays.R
import kotlin.random.Random

object NotificationDisplay {

    private const val CHANNEL_BOOKINGS = "bookings"
    private const val CHANNEL_GENERAL  = "general"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BOOKINGS, "Bookings", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Booking and stay updates" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "General app notifications" }
        )
    }

    /** True if we're actually allowed to post a system notification right now.
     *  Below API 33, the runtime permission doesn't exist — posting is
     *  governed only by the user's notification-settings toggle, which
     *  NotificationManagerCompat.areNotificationsEnabled() covers, but
     *  checking POST_NOTIFICATIONS specifically is what prevents the
     *  SecurityException on 33+, so that's the one guard we need here. */
    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun show(context: Context, title: String?, body: String?, type: String?, deepLinkId: String?) {
        if (!canPostNotifications(context)) return

        ensureChannels(context)

        val channelId = when (type) {
            "rate_stay", "new_review", "host_reply" -> CHANNEL_BOOKINGS
            else -> CHANNEL_GENERAL
        }

        val tapIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("notification_type", type)
                putExtra("notification_deep_link_id", deepLinkId)
            }

        val pending = PendingIntent.getActivity(
            context, Random.nextInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title ?: "NamaStays")
            .setContentText(body ?: "")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(Random.nextInt(), notification)
    }
}