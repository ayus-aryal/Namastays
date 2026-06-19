package com.example.namastays.trek.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ─── Domain types ─────────────────────────────────────────────────────────────

data class TrekLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LocationQuality {
    ACQUIRING,   // waiting for good fix
    EXCELLENT,   // accuracy < 10 m
    GOOD,        // accuracy 10–30 m
    POOR,        // accuracy 30–100 m
    UNUSABLE     // accuracy > 100 m
}

fun TrekLocation.quality(): LocationQuality = when {
    accuracy <= 0f   -> LocationQuality.ACQUIRING
    accuracy < 10f   -> LocationQuality.EXCELLENT
    accuracy < 30f   -> LocationQuality.GOOD
    accuracy < 100f  -> LocationQuality.POOR
    else             -> LocationQuality.UNUSABLE
}

// 15 km/h = 4.17 m/s — above this we assume the user is in a vehicle.
fun TrekLocation.isVehicleSpeed(): Boolean = speed > 4.17f

// ─── LocationTracker ──────────────────────────────────────────────────────────

object LocationTracker {

    // Positions to average for jitter smoothing in the legacy trackLocation() flow.
    private const val SMOOTHING_WINDOW = 3

    // Minimum accuracy before we trust a fix in the legacy flow.
    private const val MIN_ACCURACY_METERS = 50f

    // FIX: raised from 27.8 m/s (100 km/h) to 55 m/s (200 km/h).
    // 27.8 m/s is a normal motorway speed — not a GPS jump. True chipset
    // position jumps are typically > 100 m/s. Raising the threshold means
    // passengers in fast vehicles no longer have their fixes silently dropped,
    // which caused the dot to freeze then teleport on the next valid fix.
    private const val GPS_JUMP_SPEED_MS = 55f  // ~200 km/h

    // ── Legacy flow (kept for any callers outside the nav path) ───────────────

    @SuppressLint("MissingPermission")
    fun trackLocation(context: Context): Flow<TrekLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(5000L)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                if (location.hasSpeed() && location.speed > GPS_JUMP_SPEED_MS) {
                    Log.w("LocationTracker", "Ignoring GPS jump: ${location.speed} m/s")
                    return
                }
                trySend(location.toTrekLocation())
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Log.d("LocationTracker", "trackLocation: started")
        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d("LocationTracker", "trackLocation: stopped")
        }
    }
        .scan(emptyList<TrekLocation>()) { window, location ->
            (window + location).takeLast(SMOOTHING_WINDOW)
        }
        .filter { window ->
            window.isNotEmpty() && window.last().accuracy < MIN_ACCURACY_METERS
        }
        .map { window -> smoothLocations(window) }

    // ── Smooth flow with Kalman filter (used by trackLocationAdaptive) ────────

    @SuppressLint("MissingPermission")
    fun trackLocationSmooth(context: Context): Flow<TrekLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val kalman = KalmanFilter()

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(2000L)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // FIX: use raised threshold — see GPS_JUMP_SPEED_MS comment above.
                if (location.hasSpeed() && location.speed > GPS_JUMP_SPEED_MS) return

                val (smoothLat, smoothLng) = kalman.process(
                    newLat      = location.latitude,
                    newLng      = location.longitude,
                    accuracy    = location.accuracy,
                    timestampMs = location.time
                )

                trySend(
                    TrekLocation(
                        latitude  = smoothLat,
                        longitude = smoothLng,
                        accuracy  = location.accuracy,
                        speed     = if (location.hasSpeed()) location.speed else 0f,
                        bearing   = if (location.hasBearing()) location.bearing else 0f,
                        timestamp = location.time
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            client.removeLocationUpdates(callback)
            kalman.reset()
        }
    }

    // ── Adaptive throttle ─────────────────────────────────────────────────────
    // FIX: lastEmitTime was a field on the object — shared across ALL collectors,
    // so a second collector (e.g. after a quick nav restart) would be starved for
    // up to 10 s because the first collector's timestamp was still fresh.
    // Now it is a local var inside the filter lambda — each collector is independent.

    @SuppressLint("MissingPermission")
    fun trackLocationAdaptive(context: Context): Flow<TrekLocation> {
        // Local emit time — one per collector, not shared across instances.
        var lastEmitTime = 0L
        return trackLocationSmooth(context)
            .filter { location ->
                val now = System.currentTimeMillis()
                val intervalMs = when {
                    location.speed > 2f   -> 2_000L   // fast walking / jogging
                    location.speed > 0.3f -> 4_000L   // normal walking
                    else                  -> 10_000L  // stationary
                }
                if (now - lastEmitTime >= intervalMs) {
                    lastEmitTime = now
                    true
                } else false
            }
    }

    // ── Smoothing helper ──────────────────────────────────────────────────────
    // FIX: was two separate passes (sumOf latitude, sumOf longitude).
    // Now a single fold — halves the iterations over the (small) window.

    private fun smoothLocations(locations: List<TrekLocation>): TrekLocation {
        if (locations.size == 1) return locations.first()

        data class Acc(val latSum: Double, val lngSum: Double, val accSum: Double)

        val (latSum, lngSum, accSum) = locations.fold(Acc(0.0, 0.0, 0.0)) { acc, loc ->
            Acc(
                latSum = acc.latSum + loc.latitude,
                lngSum = acc.lngSum + loc.longitude,
                accSum = acc.accSum + loc.accuracy
            )
        }
        val n = locations.size.toDouble()
        val latest = locations.last()
        return TrekLocation(
            latitude  = latSum / n,
            longitude = lngSum / n,
            accuracy  = (accSum / n).toFloat(),
            speed     = latest.speed,
            bearing   = latest.bearing,
            timestamp = latest.timestamp
        )
    }

    // ── Dead reckoning ────────────────────────────────────────────────────────
    // Interpolates position between GPS fixes for smooth dot movement.
    // The ViewModel guards against stale timestamps (elapsed > 5 000 ms)
    // before calling this.

    fun deadReckon(lastLocation: TrekLocation, elapsedMs: Long): TrekLocation {
        if (lastLocation.speed < 0.3f) return lastLocation   // stationary

        val distanceM  = lastLocation.speed * (elapsedMs / 1000.0)
        val bearingRad = Math.toRadians(lastLocation.bearing.toDouble())
        val earthR     = 6_371_000.0

        val lat1 = Math.toRadians(lastLocation.latitude)
        val lng1 = Math.toRadians(lastLocation.longitude)

        val lat2 = asin(
            sin(lat1) * cos(distanceM / earthR) +
                    cos(lat1) * sin(distanceM / earthR) * cos(bearingRad)
        )
        val lng2 = lng1 + atan2(
            sin(bearingRad) * sin(distanceM / earthR) * cos(lat1),
            cos(distanceM / earthR) - sin(lat1) * sin(lat2)
        )

        return lastLocation.copy(
            latitude  = Math.toDegrees(lat2),
            longitude = Math.toDegrees(lng2)
        )
    }

    // ── Distance ──────────────────────────────────────────────────────────────

    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    // ── Internal extension ────────────────────────────────────────────────────

    private fun android.location.Location.toTrekLocation() = TrekLocation(
        latitude  = latitude,
        longitude = longitude,
        accuracy  = accuracy,
        speed     = if (hasSpeed()) speed else 0f,
        bearing   = if (hasBearing()) bearing else 0f,
        timestamp = time
    )
}