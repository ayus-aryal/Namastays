package com.example.namastays.trek.util

class KalmanFilter {
    private var lat = 0.0
    private var lng = 0.0
    private var variance = -1f
    private var lastTimestampMs = 0L

    companion object {
        private const val MIN_ACCURACY = 1f
        // Meters-squared per second — how much uncertainty grows while moving.
        // 3.0 works well for walking pace; increase for faster movement.
        private const val PROCESS_NOISE = 3f
    }

    fun process(
        newLat: Double,
        newLng: Double,
        accuracy: Float,
        timestampMs: Long
    ): Pair<Double, Double> {
        val accuracyClamped = accuracy.coerceAtLeast(MIN_ACCURACY)

        if (variance < 0) {
            lat = newLat
            lng = newLng
            variance = accuracyClamped * accuracyClamped
            lastTimestampMs = timestampMs
            return Pair(lat, lng)
        }

        // Grow variance with time so the filter stays responsive
        val elapsedSec = ((timestampMs - lastTimestampMs) / 1000f).coerceIn(0f, 10f)
        variance += elapsedSec * PROCESS_NOISE
        lastTimestampMs = timestampMs

        val gain = variance / (variance + accuracyClamped * accuracyClamped)
        lat += gain * (newLat - lat)
        lng += gain * (newLng - lng)
        variance = (1 - gain) * variance

        return Pair(lat, lng)
    }

    fun reset() {
        variance = -1f
        lastTimestampMs = 0L
    }
}