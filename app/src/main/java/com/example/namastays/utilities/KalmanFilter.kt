package com.example.namastays.utilities

/**
 * 1-D Kalman filter used to smooth fused (barometer + GPS) altitude
 * readings into a single stable altitude estimate.
 *
 * FIX KAL-1 (audit): update() previously took only the raw measurement and
 * used a hardcoded measurement-noise constant (r = 4.0) for every reading,
 * regardless of how accurate that particular fix actually was. TrekEngine
 * already has location.accuracy available on every GPS callback (it's the
 * same value used to gate fixes via ACCURACY_THRESHOLD_M) but never passed
 * it into the filter — so a fix reported at 5m accuracy and one reported
 * at 19m accuracy (both pass the <20m gate) were trusted identically.
 * update() now accepts an optional measurementNoise parameter, scaled by
 * the caller from the GPS fix's reported accuracy, so noisier fixes are
 * smoothed more aggressively and high-confidence fixes are trusted more.
 * Defaults to the original constant if no accuracy is supplied, so this is
 * backward compatible with any other caller.
 *
 * FIX KAL-2 (audit): reset() added. Previously, KalmanFilter's internal
 * state (x, p) was never cleared between sessions — TrekEngine owns a
 * single KalmanFilter instance for the lifetime of the app (TrekEngine
 * itself is an Application-scoped singleton), so starting a new session
 * shortly after stopping a previous one (e.g. after driving to a different
 * trailhead at a very different elevation) fed the new session's first few
 * readings through state left over from the old session, producing a
 * transient inaccurate altitude at the start of every session except the
 * very first one of the app's lifetime. TrekEngine.start() now calls
 * reset() before starting sensors.
 */
class KalmanFilter {

    private var x = 0.0
    private var p = 1.0
    private var initialized = false

    /**
     * @param measurement the new altitude reading to incorporate.
     * @param measurementNoise lower = trust this measurement more.
     *   Callers should scale this from the source's reported accuracy
     *   (e.g. GPS location.accuracy in meters) rather than using a single
     *   fixed value for every reading. Defaults to the filter's original
     *   constant for backward compatibility.
     */
    fun update(measurement: Double, measurementNoise: Double = DEFAULT_MEASUREMENT_NOISE): Double {
        if (!initialized) {
            x = measurement
            initialized = true
            return x
        }

        val q = PROCESS_NOISE
        val r = measurementNoise.coerceAtLeast(MIN_MEASUREMENT_NOISE)

        p += q

        val k = p / (p + r)

        x += k * (measurement - x)
        p *= (1 - k)

        return x
    }

    /**
     * FIX KAL-2 — clears filter state. Must be called at the start of every
     * new session so the previous session's last estimate doesn't bleed
     * into the next one's first few readings.
     */
    fun reset() {
        x = 0.0
        p = 1.0
        initialized = false
    }

    private companion object {
        const val PROCESS_NOISE = 0.1
        const val DEFAULT_MEASUREMENT_NOISE = 4.0
        // Floor to avoid a near-zero accuracy value collapsing k toward 1.0
        // and making the filter trust a single fix almost completely.
        const val MIN_MEASUREMENT_NOISE = 0.5
    }
}