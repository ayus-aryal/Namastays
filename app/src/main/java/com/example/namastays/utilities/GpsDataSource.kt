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
 *    interval = 3 s, minInterval = 1 s, no displacement filter,
 *    PRIORITY_HIGH_ACCURACY
 *
 *  BATTERY SAVER:
 *    interval = 15 s, minInterval = 5 s, minDisplacement = 10 m,
 *    PRIORITY_BALANCED_POWER_ACCURACY
 *    Switched in by TrekEngine when speed < 0.5 km/h for 30 s.
 *    Switched out when speed > 1.2 km/h for 20 s.
 *
 * All accuracy gating is done in TrekEngine, not here — this class only
 * controls delivery frequency and (as of this fix) request priority.
 *
 * FIX BAT-1 (audit): saverRequest() previously still used
 * PRIORITY_HIGH_ACCURACY, only changing interval/displacement. On most
 * devices, PRIORITY_HIGH_ACCURACY keeps the GNSS chip engaged for fix
 * quality regardless of how infrequently callbacks are requested — the
 * real power saving from FusedLocationProviderClient comes substantially
 * from *priority*, not just polling interval. Battery saver mode now also
 * drops to PRIORITY_BALANCED_POWER_ACCURACY, which lets the provider use
 * lower-power positioning sources (e.g. network/Wi-Fi-assisted) instead of
 * keeping GNSS fully engaged, while the user is confirmed stationary by
 * TrekEngine's own speed-based trigger.
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

    @SuppressLint("MissingPermission")
    fun start(onLocation: (android.location.Location) -> Unit) {
        callback = buildCallback(onLocation)
        requestUpdates(activeRequest(), "start (active mode)")
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
        requestUpdates(request, "battery saver ${if (enable) "ON" else "OFF"}", cb)
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        Log.d(TAG, "stopped")
    }

    // FIX (audit, GPS-1): requestLocationUpdates() returns a Task<Void>
    // that was previously fire-and-forget — a registration failure (e.g.
    // device location settings don't actually satisfy the request, which
    // is a distinct failure from a missing runtime permission) was
    // invisible anywhere in the stack. Now logged explicitly; if this
    // needs to surface to the UI in the future (e.g. a "couldn't start
    // GPS" message), this is the single place to add that signal.
    @SuppressLint("MissingPermission")
    private fun requestUpdates(
        request: LocationRequest,
        logLabel: String,
        cb: LocationCallback? = callback
    ) {
        val callback = cb ?: return

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnSuccessListener {
                Log.d(TAG, "$logLabel — registered")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "$logLabel — registration failed: ${e.message}", e)
            }
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

    // FIX BAT-1 — PRIORITY_BALANCED_POWER_ACCURACY instead of
    // PRIORITY_HIGH_ACCURACY, in addition to the existing interval/
    // displacement relaxation.
    private fun saverRequest() = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_SAVER_MS
    ).apply {
        setMinUpdateIntervalMillis(MIN_INTERVAL_SAVER_MS)
        setMinUpdateDistanceMeters(DISPLACEMENT_SAVER_M)
        setWaitForAccurateLocation(false)
    }.build()
}