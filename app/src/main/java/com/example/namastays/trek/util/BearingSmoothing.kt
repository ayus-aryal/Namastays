package com.example.namastays.trek.util

import kotlin.math.*

/**
 * Smooths GPS bearing using a circular moving average
 * Raw GPS bearing is very noisy — jumps 20-30 degrees
 * between fixes even when walking straight
 * This makes map rotation feel smooth like Google Maps
 */
class BearingSmoother(private val windowSize: Int = 5) {

    private val bearings = ArrayDeque<Float>(windowSize)

    fun addBearing(bearing: Float): Float {
        if (bearings.size >= windowSize) {
            bearings.removeFirst()
        }
        bearings.addLast(bearing)
        return getSmoothed()
    }

    /**
     * Circular mean — handles 359° → 1° wrapping correctly
     * Normal average would give 180° which is wrong
     */
    private fun getSmoothed(): Float {
        if (bearings.isEmpty()) return 0f

        val sinSum = bearings.sumOf { sin(Math.toRadians(it.toDouble())) }
        val cosSum = bearings.sumOf { cos(Math.toRadians(it.toDouble())) }

        val avg = Math.toDegrees(atan2(sinSum, cosSum)).toFloat()
        return (avg + 360f) % 360f
    }

    fun reset() {
        bearings.clear()
    }
}

/**
 * Calculates look-ahead camera offset
 * Moves the camera target AHEAD of the user so more
 * trail is visible in the direction of travel
 *
 * Like Google Maps — you're always in lower third
 * with more road/trail visible ahead
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
        // How many meters are visible at this zoom level
        // (approximate — varies by latitude but good enough)
        val metersPerScreen = getMetersPerScreen(zoom, position.latitude)
        val offsetMeters = metersPerScreen * offsetFraction

        // Project ahead in bearing direction
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

    /**
     * Approximate meters visible on screen at a given zoom
     * Based on standard web mercator tile sizing
     */
    private fun getMetersPerScreen(zoom: Double, latitude: Double): Double {
        val metersPerPixel = 156543.03392 *
                cos(Math.toRadians(latitude)) /
                2.0.pow(zoom)
        // Assume ~800px screen height
        return metersPerPixel * 800
    }
}