package com.example.namastays

import android.app.Application
import android.util.Log
import com.example.namastays.utilities.TrekEngine
import org.maplibre.android.MapLibre

class NamastaysApp : Application() {

    lateinit var trekEngine: TrekEngine
        private set

    override fun onCreate() {
        super.onCreate()

        MapLibre.getInstance(this)  // ← add this line


        trekEngine = TrekEngine(applicationContext)
        Log.d("APP", "TrekEngine initialized")

    }
}