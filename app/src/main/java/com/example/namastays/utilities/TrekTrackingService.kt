package com.example.namastays.utilities

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.namastays.NamastaysApp
import com.example.namastays.R

class TrekTrackingService : Service() {

    private val engine by lazy {
        (application as NamastaysApp).trekEngine
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundNotification()
        try {
            engine.start()
            Log.d("SERVICE", "engine started")
        } catch (e: Exception) {
            Log.e("SERVICE", "engine start failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        Log.d("SERVICE", "engine stopped")
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("trek", "Trek Tracking", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, "trek")
            .setContentTitle("Trek Mode Active")
            .setContentText("Tracking altitude & movement")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?) = null
}