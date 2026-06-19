package com.example.namastays.trek.util

/**
 * Manages zoom level based on movement speed
 * Like Google Maps — zooms in when stopped, out when moving fast
 *
 * Hysteresis prevents zoom flickering:
 * must maintain new speed for HYSTERESIS_MS before zoom changes
 */
class SpeedAdaptiveZoom {

    private val HYSTERESIS_MS = 2000L  // 2 seconds at new speed before zoom changes

    private var currentZoom        = DEFAULT_ZOOM
    private var targetZoom         = DEFAULT_ZOOM
    private var lastSpeedChangeMs  = 0L
    private var lastSpeedCategory  = SpeedCategory.WALKING

    companion object {
        const val DEFAULT_ZOOM = 16.0

        // Zoom levels per speed category
        const val ZOOM_STOPPED  = 17.5
        const val ZOOM_WALKING  = 16.0
        const val ZOOM_RUNNING  = 15.0
        const val ZOOM_FAST     = 14.0

        // Speed thresholds in m/s
        const val SPEED_STOPPED  = 0.3f   // < 0.3 m/s = stopped
        const val SPEED_WALKING  = 2.0f   // 0.3-2.0 = walking
        const val SPEED_RUNNING  = 4.0f   // 2.0-4.0 = running/fast hiking
        // > 4.0 = very fast
    }

    enum class SpeedCategory {
        STOPPED,
        WALKING,
        RUNNING,
        FAST
    }

    /**
     * Call this on every location update
     * Returns the zoom level to use — may be same as before
     * if hysteresis hasn't elapsed
     */
    fun getZoom(speedMs: Float, currentTimeMs: Long): Double {
        val newCategory = categorize(speedMs)

        if (newCategory != lastSpeedCategory) {
            // Speed category changed — start hysteresis timer
            if (lastSpeedChangeMs == 0L) {
                lastSpeedChangeMs = currentTimeMs
            }

            // Only change zoom after hysteresis period
            val elapsed = currentTimeMs - lastSpeedChangeMs
            if (elapsed >= HYSTERESIS_MS) {
                lastSpeedCategory = newCategory
                targetZoom = zoomForCategory(newCategory)
                lastSpeedChangeMs = 0L
            }
        } else {
            // Same category — reset timer
            lastSpeedChangeMs = 0L
        }

        return targetZoom
    }

    /**
     * Smooth zoom transition
     * Instead of jumping to target zoom, ease toward it
     * at a rate of 0.3 zoom levels per call (~0.3/sec at 1Hz)
     */
    fun getSmoothedZoom(speedMs: Float, currentTimeMs: Long): Double {
        val target = getZoom(speedMs, currentTimeMs)

        // Ease toward target — feels like Google Maps zoom
        val diff = target - currentZoom
        if (kotlin.math.abs(diff) > 0.05) {
            currentZoom += diff * 0.15  // 15% per update = smooth ease
        } else {
            currentZoom = target
        }

        return currentZoom
    }

    private fun categorize(speedMs: Float): SpeedCategory {
        return when {
            speedMs < SPEED_STOPPED -> SpeedCategory.STOPPED
            speedMs < SPEED_WALKING -> SpeedCategory.WALKING
            speedMs < SPEED_RUNNING -> SpeedCategory.RUNNING
            else                    -> SpeedCategory.FAST
        }
    }

    private fun zoomForCategory(category: SpeedCategory): Double {
        return when (category) {
            SpeedCategory.STOPPED -> ZOOM_STOPPED
            SpeedCategory.WALKING -> ZOOM_WALKING
            SpeedCategory.RUNNING -> ZOOM_RUNNING
            SpeedCategory.FAST    -> ZOOM_FAST
        }
    }

    fun reset() {
        currentZoom       = DEFAULT_ZOOM
        targetZoom        = DEFAULT_ZOOM
        lastSpeedChangeMs = 0L
        lastSpeedCategory = SpeedCategory.WALKING
    }

    fun getCurrentZoom() = currentZoom
}