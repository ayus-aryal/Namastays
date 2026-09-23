package com.example.namastays.trek.util

import kotlin.math.*

// NOTE: BearingSmoother (a windowed circular-mean smoother) previously lived
// in this file but was dead code — TrekMapViewModel's zero-allocation
// FloatArray ring buffer (see bearingBufAverage()) replaced it entirely, and
// nothing else referenced this class. Removed to avoid confusing future
// maintainers into thinking two competing smoothing strategies are both live.

/**
 * Calculates look-ahead camera offset.
 * Moves the camera target AHEAD of the user so more trail is visible in the
 * direction of travel — like Google Maps, where you sit in the lower third
 * of the screen with more road/trail visible ahead.
 */
object LookAheadCamera {

    /**
     * @param position Current user position
     * @param bearing  Smoothed direction of travel
     * @param zoom     Current zoom level
     * @param offsetFraction How far ahead to offset (0.3 = 30% of visible area)
     * @return Adjusted camera target with look-ahead applied
     */
    fun calculateTarget(
        position: TrekLocation,
        bearing: Float,
        zoom: Double,
        offsetFraction: Double = 0.35
    ): Pair<Double, Double> {
        // How many meters are visible at this zoom level (approximate — varies
        // by latitude but good enough for camera framing purposes).
        val metersPerScreen = getMetersPerScreen(zoom, position.latitude)
        val offsetMeters = metersPerScreen * offsetFraction

        // Project ahead in the bearing direction using the standard great-circle
        // destination-point formula.
        val bearingRad = Math.toRadians(bearing.toDouble())
        val earthRadius = 6371000.0

        val lat1 = Math.toRadians(position.latitude)
        val lng1 = Math.toRadians(position.longitude)

        val lat2 = asin(
            sin(lat1) * cos(offsetMeters / earthRadius) +
                    cos(lat1) * sin(offsetMeters / earthRadius) * cos(bearingRad)
        )

        val lng2 = lng1 + atan2(
            sin(bearingRad) * sin(offsetMeters / earthRadius) * cos(lat1),
            cos(offsetMeters / earthRadius) - sin(lat1) * sin(lat2)
        )

        return Pair(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /** Approximate meters visible on screen at a given zoom, based on standard web-mercator tile sizing. */
    private fun getMetersPerScreen(zoom: Double, latitude: Double): Double {
        val metersPerPixel = 156543.03392 *
                cos(Math.toRadians(latitude)) /
                2.0.pow(zoom)
        // Assume ~800px screen height.
        return metersPerPixel * 800
    }
}