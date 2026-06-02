package com.example.namastays.trek.util

/**
 * Simple 2D Kalman filter for GPS smoothing
 * Predicts position between fixes and corrects when new fix arrives
 * Makes movement feel continuous like Google Maps
 */
class KalmanFilter {
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1f  // negative = uninitialised

    companion object {
        // How much we trust GPS accuracy (lower = trust GPS more)
        private const val MIN_ACCURACY = 1f
    }

    fun process(
        newLat: Double,
        newLng: Double,
        accuracy: Float,
        timestampMs: Long
    ): Pair<Double, Double> {
        val accuracyClamped = accuracy.coerceAtLeast(MIN_ACCURACY)

        if (variance < 0) {
            // First fix — initialise
            lat = newLat
            lng = newLng
            variance = accuracyClamped * accuracyClamped
            return Pair(lat, lng)
        }

        // Kalman gain
        val gain = variance / (variance + accuracyClamped * accuracyClamped)

        // Update position
        lat += gain * (newLat - lat)
        lng += gain * (newLng - lng)

        // Update variance
        variance = (1 - gain) * variance

        return Pair(lat, lng)
    }

    fun reset() {
        variance = -1f
    }
}