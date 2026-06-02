package com.example.namastays.trek.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

data class TrekLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LocationQuality {
    ACQUIRING,  // waiting for good fix
    EXCELLENT,  // accuracy < 10m
    GOOD,       // accuracy 10-30m
    POOR,       // accuracy 30-100m
    UNUSABLE    // accuracy > 100m
}

fun TrekLocation.quality(): LocationQuality {
    return when {
        accuracy <= 0f  -> LocationQuality.ACQUIRING
        accuracy < 10f  -> LocationQuality.EXCELLENT
        accuracy < 30f  -> LocationQuality.GOOD
        accuracy < 100f -> LocationQuality.POOR
        else            -> LocationQuality.UNUSABLE
    }
}

fun TrekLocation.isVehicleSpeed(): Boolean {
    return speed > 4.17f  // 15 km/h
}

object LocationTracker {

    // How many positions to average for smoothing
    private const val SMOOTHING_WINDOW = 3

    // Minimum accuracy before we trust the fix
    private const val MIN_ACCURACY_METERS = 50f

    @SuppressLint("MissingPermission")
    fun trackLocation(context: Context): Flow<TrekLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)

        // Start with high frequency
        var currentInterval = 3000L
        var lastSpeed = 0f

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            currentInterval
        )
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(5000L)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                if (location.hasSpeed() && location.speed > 27.8f) {
                    Log.w("LocationTracker", "Ignoring GPS jump")
                    return
                }

                lastSpeed = if (location.hasSpeed()) location.speed else 0f

                trySend(
                    TrekLocation(
                        latitude  = location.latitude,
                        longitude = location.longitude,
                        accuracy  = location.accuracy,
                        speed     = lastSpeed,
                        bearing   = if (location.hasBearing()) location.bearing else 0f
                    )
                )
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        Log.d("LocationTracker", "Started location updates")

        awaitClose {
            client.removeLocationUpdates(callback)
            Log.d("LocationTracker", "Stopped location updates")
        }
    }
        .scan(emptyList<TrekLocation>()) { window, location ->
            (window + location).takeLast(SMOOTHING_WINDOW)
        }
        .filter { window ->
            window.isNotEmpty() && window.last().accuracy < MIN_ACCURACY_METERS
        }
        .map { window ->
            smoothLocations(window)
        }

    /**
     * Averages a list of locations to reduce GPS jitter
     */
    private fun smoothLocations(locations: List<TrekLocation>): TrekLocation {
        if (locations.size == 1) return locations.first()

        val avgLat = locations.sumOf { it.latitude } / locations.size
        val avgLng = locations.sumOf { it.longitude } / locations.size
        val avgAccuracy = locations.sumOf { it.accuracy.toDouble() }.toFloat() / locations.size
        val latest = locations.last()

        return TrekLocation(
            latitude  = avgLat,
            longitude = avgLng,
            accuracy  = avgAccuracy,
            speed     = latest.speed,
            bearing   = latest.bearing,
            timestamp = latest.timestamp
        )
    }

    fun distanceBetween(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    /**
     * Wraps trackLocation with adaptive frequency
     * Stationary (< 0.3 m/s) → emit every 30s (saves battery)
     * Walking (0.3 - 2 m/s)  → emit every 5s
     * Moving fast (> 2 m/s)  → emit every 3s
     *
     * We don't restart the GPS request (expensive)
     * Instead we throttle emissions from the flow
     */
    @SuppressLint("MissingPermission")
    fun trackLocationSmooth(context: Context): Flow<TrekLocation> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val kalman = KalmanFilter()

        // Request 1-second updates — same as Google Maps
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L  // 1 second
        )
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(2000L)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return

                // Filter impossible speeds
                if (location.hasSpeed() && location.speed > 27.8f) return

                // Apply Kalman filter
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

    // Keep adaptive for battery saving when stationary
    @SuppressLint("MissingPermission")
    fun trackLocationAdaptive(context: Context): Flow<TrekLocation> =
        trackLocationSmooth(context)
            .filter { location ->
                // Only throttle when stationary to save battery
                location.speed > 0.3f || System.currentTimeMillis() % 10000 < 1000
            }


    /**
     * Estimates current position based on last known position,
     * speed and bearing — used between GPS fixes
     * Makes dot move smoothly without waiting for next fix
     */
    fun deadReckon(
        lastLocation: TrekLocation,
        elapsedMs: Long
    ): TrekLocation {
        if (lastLocation.speed < 0.3f) return lastLocation // stationary

        val distanceM = lastLocation.speed * (elapsedMs / 1000.0)
        val bearingRad = Math.toRadians(lastLocation.bearing.toDouble())

        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(lastLocation.latitude)
        val lng1 = Math.toRadians(lastLocation.longitude)

        val lat2 = Math.asin(
            Math.sin(lat1) * Math.cos(distanceM / earthRadius) +
                    Math.cos(lat1) * Math.sin(distanceM / earthRadius) * Math.cos(bearingRad)
        )

        val lng2 = lng1 + Math.atan2(
            Math.sin(bearingRad) * Math.sin(distanceM / earthRadius) * Math.cos(lat1),
            Math.cos(distanceM / earthRadius) - Math.sin(lat1) * Math.sin(lat2)
        )

        return lastLocation.copy(
            latitude  = Math.toDegrees(lat2),
            longitude = Math.toDegrees(lng2)
        )
    }
}