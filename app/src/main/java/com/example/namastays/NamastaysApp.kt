package com.example.namastays

import android.app.Application
import android.util.Log
import com.example.namastays.utilities.TrekEngine

class NamastaysApp : Application() {

    lateinit var trekEngine: TrekEngine
        private set

    override fun onCreate() {
        super.onCreate()

        trekEngine = TrekEngine(applicationContext)
        Log.d("APP", "TrekEngine initialized")

    }
}