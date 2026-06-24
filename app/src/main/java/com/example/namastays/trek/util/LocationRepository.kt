package com.example.namastays.trek.util

import android.annotation.SuppressLint
import android.app.Application
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
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Changes vs original:
 *
 * FIX #7  — Accepts [Application] instead of raw [Context] to guarantee that
 *            applicationContext is always used, preventing an Activity leak if
 *            a caller accidentally passes an Activity context.
 *
 * FIX #8  — warmUp() callback variable capture semantics clarified and made
 *            safe. The Runnable captured [cb] when it was created, meaning it
 *            always held the original reference even after [cb] was nulled.
 *            Replaced with a direct reference held in [warmUpCallback] so both
 *            the success path and the timeout path remove the exact same object.
 *
 * FIX #9  — warmUp() is now guarded by [warmUpStarted] AtomicBoolean so
 *            calling it multiple times (e.g. on every onStart) registers only
 *            one LocationCallback and one cleanup timer.
 *
 * FIX #10 — getLastKnown() documents that the GMS Task has no cancel API;
 *            invokeOnCancellation is a no-op but added for parity and clarity.
 *
 * FIX #11 — getCurrentLocation() now creates a CancellationTokenSource and
 *            cancels it in invokeOnCancellation, telling FLP to stop the
 *            HIGH_ACCURACY GPS session when the calling coroutine is cancelled.
 *            Previously passing null meant the GPS session outlived the screen.
 */
class LocationRepository(application: Application) {  // FIX #7 — Application, not Context

    // FIX #7 — applicationContext guaranteed; no Activity reference held.
    private val appContext = application.applicationContext

    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val mainHandler = Handler(Looper.getMainLooper())

    // FIX #9 — ensures warmUp() registers at most one callback per process lifetime.
    private val warmUpStarted = AtomicBoolean(false)

    // FIX #8 — holds the active callback so both success and timeout paths
    // remove the same object reference (no capture-at-creation confusion).
    private var warmUpCallback: LocationCallback? = null

    // ─── Warm-up ──────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun warmUp() {
        // FIX #9 — bail out if already started; compareAndSet is atomic.
        if (!warmUpStarted.compareAndSet(false, true)) return

        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
            .setWaitForAccurateLocation(false)
            .build()

        // FIX #8 — cleanup references warmUpCallback, which is set before the
        // remove call, so it always points at the registered object.
        val cleanup = Runnable {
            warmUpCallback?.let { client.removeLocationUpdates(it) }
            warmUpCallback = null
        }

        warmUpCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                mainHandler.removeCallbacks(cleanup)
                // FIX #8 — remove using the field, not a captured local var.
                warmUpCallback?.let { client.removeLocationUpdates(it) }
                warmUpCallback = null
            }
        }

        try {
            client.requestLocationUpdates(req, warmUpCallback!!, Looper.getMainLooper())
            mainHandler.postDelayed(cleanup, 30_000L)
        } catch (_: SecurityException) {
            // Permission not granted yet — reset so warmUp() can be called
            // again after the user grants permission.
            warmUpCallback = null
            warmUpStarted.set(false)
        }
    }

    // ─── Last known (instant) ─────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun getLastKnown(): TrekLocation? =
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc?.toTrekLocation()) }
                .addOnFailureListener { cont.resume(null) }

            // FIX #10 — GMS Task for lastLocation has no cancel API; document
            // this explicitly so future maintainers don't wonder why it's absent.
            cont.invokeOnCancellation {
                // lastLocation is a single-shot cache read; no resource to release.
            }
        }

    // ─── Fresh high-accuracy fix (10 s timeout) ───────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): TrekLocation? =
        withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                // FIX #11 — CancellationTokenSource lets us tell FLP to stop
                // the GPS session if the calling coroutine is cancelled.
                val cts = CancellationTokenSource()

                val req = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                    .build()

                client.getCurrentLocation(req, cts.token)
                    .addOnSuccessListener { loc -> cont.resume(loc?.toTrekLocation()) }
                    .addOnFailureListener { cont.resume(null) }

                // FIX #11 — cancels the FLP task, releasing the GPS session.
                cont.invokeOnCancellation { cts.cancel() }
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