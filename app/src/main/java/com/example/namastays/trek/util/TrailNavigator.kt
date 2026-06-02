package com.example.namastays.trek.util

import android.util.Log
import org.maplibre.geojson.Point

data class NavigationState(
    val currentLocation: TrekLocation,
    val nearestPointIndex: Int,         // index in GPX points list
    val distanceToTrail: Float,         // meters from trail
    val distanceCovered: Float,         // meters walked so far
    val distanceRemaining: Float,       // meters to destination
    val progressPercent: Float,         // 0-100
    val currentElevation: Int,          // estimated from nearest GPX point
    val status: NavigationStatus,
    val warningMessage: String? = null,
    val eta: String = "",
    val bearingToTrail: Float = 0f
)

enum class NavigationStatus {
    ACQUIRING,

    ON_TRAIL,           // within 100m of trail
    OFF_TRAIL_WARNING,  // 100-500m from trail
    OFF_TRAIL_CRITICAL, // >500m from trail
    WRONG_DIRECTION,    // moving away from destination
    WRONG_LOCATION,     // too far from trailhead to start
    IN_VEHICLE,         // moving too fast
    POOR_GPS,           // accuracy too low
    COMPLETED           // reached destination
}

object TrailNavigator {

    private const val OFF_TRAIL_WARNING_METERS  = 100f
    private const val OFF_TRAIL_CRITICAL_METERS = 500f
    private const val COMPLETION_RADIUS_METERS  = 50f
    private const val FAR_FROM_TRAILHEAD_METERS = 2000f  // 2km

    /**
     * Main function — call this every time location updates
     * Returns full NavigationState with all warnings
     */
    fun calculateState(
        location: TrekLocation,
        gpxPoints: List<Point>,
        previousState: NavigationState?
    ): NavigationState {

        if (gpxPoints.isEmpty()) {
            return NavigationState(
                currentLocation    = location,
                nearestPointIndex  = 0,
                distanceToTrail    = 0f,
                distanceCovered    = 0f,
                distanceRemaining  = 0f,
                progressPercent    = 0f,
                currentElevation   = 0,
                status             = NavigationStatus.ON_TRAIL
            )
        }

        // Check vehicle speed first
        if (location.isVehicleSpeed()) {
            return NavigationState(
                currentLocation    = location,
                nearestPointIndex  = previousState?.nearestPointIndex ?: 0,
                distanceToTrail    = previousState?.distanceToTrail ?: 0f,
                distanceCovered    = previousState?.distanceCovered ?: 0f,
                distanceRemaining  = previousState?.distanceRemaining ?: 0f,
                progressPercent    = previousState?.progressPercent ?: 0f,
                currentElevation   = previousState?.currentElevation ?: 0,
                status             = NavigationStatus.IN_VEHICLE,
                warningMessage     = "You appear to be in a vehicle. Navigation paused."
            )
        }

        // Check GPS quality
        if (location.quality() == LocationQuality.UNUSABLE) {
            return NavigationState(
                currentLocation    = location,
                nearestPointIndex  = previousState?.nearestPointIndex ?: 0,
                distanceToTrail    = previousState?.distanceToTrail ?: 0f,
                distanceCovered    = previousState?.distanceCovered ?: 0f,
                distanceRemaining  = previousState?.distanceRemaining ?: 0f,
                progressPercent    = previousState?.progressPercent ?: 0f,
                currentElevation   = previousState?.currentElevation ?: 0,
                status             = NavigationStatus.POOR_GPS,
                warningMessage     = "GPS signal too weak. Move to open ground."
            )
        }

        // Find nearest point on trail
        val nearestIndex = findNearestPointIndex(location, gpxPoints)
        val nearestPoint = gpxPoints[nearestIndex]
        val distanceToTrail = LocationTracker.distanceBetween(
            location.latitude, location.longitude,
            nearestPoint.latitude(), nearestPoint.longitude()
        )

        // Check if too far from trailhead at start
        if (previousState == null) {
            val trailhead = gpxPoints.first()
            val distanceFromTrailhead = LocationTracker.distanceBetween(
                location.latitude, location.longitude,
                trailhead.latitude(), trailhead.longitude()
            )
            if (distanceFromTrailhead > FAR_FROM_TRAILHEAD_METERS) {
                val km = "%.0f".format(distanceFromTrailhead / 1000)
                return NavigationState(
                    currentLocation    = location,
                    nearestPointIndex  = nearestIndex,
                    distanceToTrail    = distanceToTrail,
                    distanceCovered    = 0f,
                    distanceRemaining  = calculateTotalDistance(gpxPoints),
                    progressPercent    = 0f,
                    currentElevation   = 0,
                    status             = NavigationStatus.WRONG_LOCATION,
                    warningMessage = "You are ${km}km from the trailhead at Nayapul " +
                            "(straight-line distance). Travel to Nayapul to begin the trek."
                )
            }
        }

        // Calculate distances
        val distanceCovered = calculateDistanceCovered(gpxPoints, nearestIndex)
        val totalDistance   = calculateTotalDistance(gpxPoints)
        val distanceRemaining = totalDistance - distanceCovered
        val progressPercent = (distanceCovered / totalDistance * 100).coerceIn(0f, 100f)

        // Check completion
        val destination = gpxPoints.last()
        val distanceToEnd = LocationTracker.distanceBetween(
            location.latitude, location.longitude,
            destination.latitude(), destination.longitude()
        )
        if (distanceToEnd < COMPLETION_RADIUS_METERS && progressPercent >10f) {
            return NavigationState(
                currentLocation    = location,
                nearestPointIndex  = nearestIndex,
                distanceToTrail    = distanceToTrail,
                distanceCovered    = distanceCovered,
                distanceRemaining  = 0f,
                progressPercent    = 100f,
                currentElevation   = 0,
                status             = NavigationStatus.COMPLETED,
                warningMessage     = "You have completed the trek!",
                eta                = "",
                bearingToTrail     = 0f
            )
        }

        // Check wrong direction
        // Only flag after user has moved at least 50m from start
        // and nearest point index is consistently decreasing
        val isWrongDirection = if (previousState != null &&
            distanceCovered > 50f &&
            location.speed > 0.5f) {
            val prevIndex = previousState.nearestPointIndex
            nearestIndex < prevIndex - 10
        } else false

        // Determine status
        val status = when {
            distanceToTrail > OFF_TRAIL_CRITICAL_METERS -> NavigationStatus.OFF_TRAIL_CRITICAL
            distanceToTrail > OFF_TRAIL_WARNING_METERS  -> NavigationStatus.OFF_TRAIL_WARNING
            isWrongDirection                            -> NavigationStatus.WRONG_DIRECTION
            else                                        -> NavigationStatus.ON_TRAIL
        }

        val warningMessage = when (status) {
            NavigationStatus.OFF_TRAIL_CRITICAL ->
                "You are ${distanceToTrail.toInt()}m off trail. Return to the route."
            NavigationStatus.OFF_TRAIL_WARNING  ->
                "You are ${distanceToTrail.toInt()}m from the trail."
            NavigationStatus.WRONG_DIRECTION    ->
                "You are heading away from your destination."
            else -> null
        }

        val eta = estimateEta(distanceRemaining, location.speed)
        val bearing = if (distanceToTrail > OFF_TRAIL_WARNING_METERS) {
            bearingToTrail(location, gpxPoints, nearestIndex)
        } else 0f

        return NavigationState(
            currentLocation    = location,
            nearestPointIndex  = nearestIndex,
            distanceToTrail    = distanceToTrail,
            distanceCovered    = distanceCovered,
            distanceRemaining  = distanceRemaining,
            progressPercent    = progressPercent,
            currentElevation   = 0,
            status             = status,
            warningMessage     = warningMessage,
            eta                = eta,
            bearingToTrail     = bearing
        )
    }

    /**
     * Finds the index of the nearest GPX point to current location
     * Uses a simple linear scan — good enough for trek-sized routes
     */
    private fun findNearestPointIndex(
        location: TrekLocation,
        points: List<Point>
    ): Int {
        var minDistance = Float.MAX_VALUE
        var nearestIndex = 0

        points.forEachIndexed { index, point ->
            val distance = LocationTracker.distanceBetween(
                location.latitude, location.longitude,
                point.latitude(), point.longitude()
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = index
            }
        }

        return nearestIndex
    }

    /**
     * Calculates total trail distance by summing
     * distances between consecutive GPX points
     */
    fun calculateTotalDistance(points: List<Point>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += LocationTracker.distanceBetween(
                points[i - 1].latitude(), points[i - 1].longitude(),
                points[i].latitude(), points[i].longitude()
            )
        }
        return total
    }

    /**
     * Calculates how far along the trail the user has come
     * by summing distances up to the nearest point index
     */
    private fun calculateDistanceCovered(
        points: List<Point>,
        upToIndex: Int
    ): Float {
        var total = 0f
        for (i in 1..upToIndex.coerceAtMost(points.size - 1)) {
            total += LocationTracker.distanceBetween(
                points[i - 1].latitude(), points[i - 1].longitude(),
                points[i].latitude(), points[i].longitude()
            )
        }
        return total
    }

    /**
     * Estimates time remaining based on:
     * - Current speed if moving
     * - Average trekking speed (3.5 km/h) if stationary
     */
    fun estimateEta(
        distanceRemainingMeters: Float,
        currentSpeedMs: Float
    ): String {
        val speedMs = if (currentSpeedMs > 0.5f) currentSpeedMs
        else 0.972f  // 3.5 km/h in m/s (average trek speed)

        val secondsRemaining = distanceRemainingMeters / speedMs
        val hoursRemaining = secondsRemaining / 3600
        val minutesRemaining = (secondsRemaining % 3600) / 60

        return when {
            hoursRemaining >= 1 ->
                "${hoursRemaining.toInt()}h ${minutesRemaining.toInt()}m"
            minutesRemaining >= 1 ->
                "${minutesRemaining.toInt()} min"
            else -> "< 1 min"
        }
    }

    /**
     * Returns bearing from current location to nearest trail point
     * Used to show arrow pointing back to trail when off route
     */
    fun bearingToTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): Float {
        if (gpxPoints.isEmpty()) return 0f
        val nearest = gpxPoints[nearestPointIndex]

        val lat1 = Math.toRadians(location.latitude)
        val lat2 = Math.toRadians(nearest.latitude())
        val dLng = Math.toRadians(nearest.longitude() - location.longitude)

        val y = Math.sin(dLng) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) -
                Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng)

        return ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360).toFloat()
    }

    fun snapToTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestPointIndex: Int,
        snapRadiusMeters: Float = 30f
    ): TrekLocation {
        if (gpxPoints.isEmpty()) return location

        val nearest = gpxPoints[nearestPointIndex]
        val distToTrail = FloatArray(1)
        android.location.Location.distanceBetween(
            location.latitude, location.longitude,
            nearest.latitude(), nearest.longitude(),
            distToTrail
        )

        // Only snap if within radius — outside it means genuinely off-trail
        return if (distToTrail[0] <= snapRadiusMeters) {
            location.copy(
                latitude  = nearest.latitude(),
                longitude = nearest.longitude()
            )
        } else {
            location  // off-trail, show raw GPS
        }
    }
}