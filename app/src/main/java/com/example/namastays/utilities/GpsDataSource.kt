package com.example.namastays.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

class GpsDataSource(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start(onUpdate: (Location) -> Unit) {

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onUpdate(it) }
            }
        }
        callback = cb

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).build()
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        Log.d("GPS", "started")
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        Log.d("GPS", "stopped")
    }
}