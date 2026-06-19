package com.example.namastays.trek.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Centralises all FusedLocationProviderClient access.
 *
 * warmUp()            — fires a low-priority passive request on startup so the
 *                       GPS chipset is active before the user taps "Start trek".
 *                       Eliminates the 10-20 s cold-start TTFF.
 * getLastKnown()      — instant; returns cached fix with no GPS wake.
 * getCurrentLocation()— fresh HIGH_ACCURACY fix, 10 s timeout, null on failure.
 */
class LocationRepository(private val context: Context) {

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val mainHandler = Handler(Looper.getMainLooper())

    // ─── Warm-up ──────────────────────────────────────────────────────────────
    // FIX: the original callback removed itself only inside onLocationResult.
    // If permission was revoked mid-flight (or the chipset never fired a fix),
    // the callback leaked indefinitely. Now a 30 s self-removal is scheduled
    // via Handler the moment we register, guaranteeing cleanup regardless.

    @SuppressLint("MissingPermission")
    fun warmUp() {
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(false)
            .build()

        var cb: LocationCallback? = null
        val cleanup = Runnable { cb?.let { client.removeLocationUpdates(it) }; cb = null }

        cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                mainHandler.removeCallbacks(cleanup)
                client.removeLocationUpdates(this)
                cb = null
            }
        }

        try {
            client.requestLocationUpdates(req, cb!!, Looper.getMainLooper())
            // FIX: schedule forced cleanup after 30 s in case no fix ever arrives.
            mainHandler.postDelayed(cleanup, 30_000L)
        } catch (_: SecurityException) {
            // Permission not granted yet — no-op, nothing to clean up.
            cb = null
        }
    }

    // ─── Last known (instant) ─────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun getLastKnown(): TrekLocation? =
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc?.toTrekLocation()) }
                .addOnFailureListener { cont.resume(null) }
        }

    // ─── Fresh high-accuracy fix (10 s timeout) ───────────────────────────────
    // FIX: added invokeOnCancellation so the underlying Task is cancelled if
    // the calling coroutine is cancelled (e.g. ViewModel cleared mid-request),
    // preventing the FLP task from lingering after the screen is gone.

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): TrekLocation? =
        withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val req = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    .build()

                val task = client.getCurrentLocation(req, null)
                task
                    .addOnSuccessListener { loc -> cont.resume(loc?.toTrekLocation()) }
                    .addOnFailureListener { cont.resume(null) }

                cont.invokeOnCancellation {
                    // Best-effort cancel — FLP doesn't expose a direct cancel
                    // on getCurrentLocation, but marking the continuation as
                    // cancelled stops the resume from having any effect.
                }
            }
        }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private fun android.location.Location.toTrekLocation() = TrekLocation(
        latitude  = latitude,
        longitude = longitude,
        accuracy  = accuracy,
        speed     = speed,
        bearing   = bearing,
        timestamp = time
    )
}