package com.example.namastays.utilities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

/**
 * Wraps FusedLocationProviderClient with two polling modes:
 *
 *  ACTIVE (default):
 *    interval = 3 s, minInterval = 1 s, no displacement filter
 *
 *  BATTERY SAVER:
 *    interval = 15 s, minInterval = 5 s, minDisplacement = 10 m
 *    Switched in by TrekEngine when speed < 0.5 km/h for 30 s.
 *    Switched out when speed > 1.2 km/h for 20 s.
 *
 * All accuracy gating is done in TrekEngine, not here — this class only
 * controls delivery frequency.
 */
class GpsDataSource(private val context: Context) {

    companion object {
        private const val TAG = "GpsDataSource"

        // Active mode
        private const val INTERVAL_ACTIVE_MS   = 3_000L
        private const val MIN_INTERVAL_ACTIVE_MS = 1_000L

        // Battery saver mode
        private const val INTERVAL_SAVER_MS    = 15_000L
        private const val MIN_INTERVAL_SAVER_MS = 5_000L
        private const val DISPLACEMENT_SAVER_M  = 10f
    }

    private val client   = LocationServices.getFusedLocationProviderClient(context)
    private var callback : LocationCallback? = null
    private var handler  : ((android.location.Location) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start(onLocation: (android.location.Location) -> Unit) {
        handler  = onLocation
        callback = buildCallback(onLocation)
        client.requestLocationUpdates(activeRequest(), callback!!, Looper.getMainLooper())
        Log.d(TAG, "started (active mode)")
    }

    /**
     * Switch between active and battery-saver polling modes.
     * Re-registers the callback with the new request; no data gap because
     * FusedLocationProvider delivers the last known location immediately.
     */
    @SuppressLint("MissingPermission")
    fun setBatterySaver(enable: Boolean) {
        val cb = callback ?: return
        client.removeLocationUpdates(cb)
        val request = if (enable) saverRequest() else activeRequest()
        client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        Log.d(TAG, "battery saver ${if (enable) "ON" else "OFF"}")
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        handler  = null
        Log.d(TAG, "stopped")
    }

    private fun buildCallback(onLocation: (android.location.Location) -> Unit) =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { onLocation(it) }
            }
        }

    private fun activeRequest() = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_ACTIVE_MS
    ).apply {
        setMinUpdateIntervalMillis(MIN_INTERVAL_ACTIVE_MS)
        setWaitForAccurateLocation(false)
    }.build()

    private fun saverRequest() = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_SAVER_MS
    ).apply {
        setMinUpdateIntervalMillis(MIN_INTERVAL_SAVER_MS)
        setMinUpdateDistanceMeters(DISPLACEMENT_SAVER_M)
        setWaitForAccurateLocation(false)
    }.build()
}