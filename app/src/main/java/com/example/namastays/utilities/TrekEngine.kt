package com.example.namastays.utilities

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.TrekElevationPoint
import com.example.namastays.data.TrekSession
import com.example.namastays.dto.GpsSignalState
import com.example.namastays.dto.TrekState
import com.example.namastays.screens.AltitudeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

class TrekEngine(private val context: Context) {

    companion object {
        private const val TAG = "TrekEngine"

        const val ACCURACY_THRESHOLD_M          = 20f
        const val MIN_DISPLACEMENT_M            = 5.0
        const val MIN_ELEVATION_DELTA_M         = 2.0
        const val SPEED_WINDOW_SIZE             = 5
        const val MIN_MEANINGFUL_SPEED_KMH      = 0.8
        const val BATTERY_SAVER_DEBOUNCE_MS     = 30_000L
        const val BATTERY_SAVER_SPEED_KMH       = 0.5
        const val BATTERY_SAVER_EXIT_SPEED_KMH  = 1.2
        const val BATTERY_SAVER_EXIT_DEBOUNCE_MS = 20_000L
        const val ELEVATION_RECORD_INTERVAL_MS  = 60_000L
        const val ELEVATION_PURGE_AGE_MS        = 30L * 24 * 60 * 60 * 1_000
        const val ASCENT_RATE_WINDOW_MS         = 30 * 60 * 1_000L
        const val SESSION_CLOSE_TIMEOUT_MS      = 5_000L
        const val GPS_DEGRADED_THRESHOLD        = 5

        // ── NEW: altitude calibration ──────────────────────────────────────────
        // Number of GPS altitude fixes to collect and average before seeding
        // the altitude Kalman filter. A single GPS altitude reading has poor
        // vertical accuracy (typically 2-3x worse than horizontal); averaging
        // several fixes before trusting any of them as the starting altitude
        // is standard practice in Garmin/Suunto-class devices' "auto-calibrate
        // at start" behavior. During this window, fused altitude falls back to
        // barometer-only (see onLocation) rather than emitting an unseeded/
        // unreliable value.
        const val ALTITUDE_CALIBRATION_SAMPLE_COUNT = 8

        // ── NEW: barometer drift correction ──────────────────────────────────
        // Barometric altitude is accurate *relative to itself* but drifts as
        // ambient pressure changes with weather over the course of a multi-
        // hour trek — a storm front rolling in can look like 10-20m of
        // "altitude change" with zero actual elevation change. Real devices
        // periodically nudge the baro-derived estimate back toward a
        // (multi-fix-averaged, not single-fix) GPS altitude on a slow
        // timescale — correcting long-term drift without letting any single
        // noisy GPS altitude fix corrupt the fused value on a per-reading
        // basis, the way naive per-fix blending would.
        const val DRIFT_CORRECTION_INTERVAL_MS  = 12 * 60 * 1_000L   // 12 min
        const val DRIFT_CORRECTION_WINDOW_SIZE  = 20                // GPS fixes averaged per correction
        // Deliberately large — passed as measurementNoise so the correction
        // pulls gently (small Kalman gain) rather than snapping to the GPS
        // average outright.
        const val DRIFT_CORRECTION_NOISE        = 50.0

        // ── NEW: speed outlier rejection ───────────────────────────────────────
        // Matches LocationTracker.isVehicleSpeed()'s threshold (4.17 m/s =
        // 15 km/h) for consistency across the app: above this, a foot-trekking
        // GPS speed reading is treated as sensor noise (common near buildings/
        // dense tree cover), not real movement, and is excluded from the
        // median window entirely rather than being clamped to a value that
        // would still skew the median.
        const val MAX_PLAUSIBLE_SPEED_KMH       = 15.0
    }

    private val gps       = GpsDataSource(context)
    private val barometer = BarometerSource(context)

    // Existing 1-D altitude filter — unchanged class, now seeded via
    // calibration (see start()/onLocation()) instead of taking its first
    // raw fused reading as gospel.
    private val kalman    = KalmanFilter()

    // ── NEW: position smoothing ─────────────────────────────────────────────
    // Same 2-D Kalman filter already proven in LocationTracker for map/nav
    // position smoothing, now also used here. Distance was previously
    // accumulated from raw location deltas — a GPS fix jittering just under
    // MIN_DISPLACEMENT_M in one direction and back can silently overcount
    // distance on slow/stationary sections. Smoothing position before the
    // displacement gate closes that gap using the same filter the app
    // already trusts elsewhere, rather than a second bespoke scheme.
    private val positionKalman = com.example.namastays.trek.util.KalmanFilter()

    private val db           by lazy { SafetyDatabase.getInstance(context) }
    private val sessionDao   by lazy { db.trekSessionDao() }
    private val elevationDao by lazy { db.trekElevationPointDao() }

    private val engineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private var engineScope: CoroutineScope? = null
    private val startStopMutex = Mutex()
    private var sessionOpenDeferred: CompletableDeferred<Unit>? = null

    val _state = MutableStateFlow(TrekState())
    val state: StateFlow<TrekState> = _state

    private val _fatalError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val fatalError: SharedFlow<String> = _fatalError.asSharedFlow()

    // ── Accumulators — only accessed on engineDispatcher ──────────────────────
    private var lastAcceptedLocation  : Location? = null
    private var lastAltitude          : Double?   = null
    private var totalGain             = 0.0
    private var totalLoss             = 0.0
    private var totalDistanceM        = 0.0
    private var maxAltM               = 0.0
    private var latestBaro            : Double?   = null
    private val speedWindow           = ArrayDeque<Float>(SPEED_WINDOW_SIZE)
    private var inBatterySaver        = false
    private var lowSpeedSinceMs       = 0L
    private var highSpeedSinceMs      = 0L
    private var currentSessionId      : Long?     = null
    private var sessionStartMs        = 0L
    private var lastElevationRecordMs = 0L
    private val ascentWindow          = ArrayDeque<Pair<Long, Double>>()
    private var consecutiveRejections = 0

    // ── NEW: altitude calibration state ─────────────────────────────────────
    // Raw (unfused) GPS altitude readings collected before the filter is
    // seeded. Cleared on every start()/resetAccumulators().
    private val calibrationSamples = ArrayList<Double>(ALTITUDE_CALIBRATION_SAMPLE_COUNT)
    private var altitudeCalibrated = false

    // ── NEW: drift correction state ─────────────────────────────────────────
    // Rolling window of recent raw GPS altitude fixes, used to compute the
    // periodic drift-correction anchor. Separate from calibrationSamples —
    // that one seeds the filter once; this one keeps a live rolling average
    // for the lifetime of the session.
    private val driftWindow = ArrayDeque<Double>(DRIFT_CORRECTION_WINDOW_SIZE)
    private var lastDriftCorrectionMs = 0L

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        CoroutineScope(SupervisorJob() + engineDispatcher).launch {
            startStopMutex.withLock {
                if (engineScope != null) {
                    Log.d(TAG, "already started — ignoring")
                    return@withLock
                }

                val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
                engineScope = scope

                val deferred = CompletableDeferred<Unit>()
                sessionOpenDeferred = deferred
                var openedSessionId: Long? = null

                try {
                    sessionDao.closeOrphanedSessions(System.currentTimeMillis())
                    elevationDao.purgeOlderThan(System.currentTimeMillis() - ELEVATION_PURGE_AGE_MS)
                    val sessionId = sessionDao.insert(TrekSession(startMs = System.currentTimeMillis()))
                    openedSessionId  = sessionId
                    currentSessionId = sessionId
                    sessionStartMs   = System.currentTimeMillis()
                    Log.d(TAG, "session opened: id=$sessionId")
                    _state.value = _state.value.copy(currentSessionId = sessionId)
                    deferred.complete(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open session: ${e.message}", e)
                    deferred.completeExceptionally(e)
                    engineScope?.cancel()
                    engineScope = null
                    return@withLock
                }

                // FIX KAL-2: reset filter state so a new session doesn't inherit
                // smoothed altitude from the previous one.
                kalman.reset()
                // NEW: reset position filter alongside altitude filter, and
                // clear calibration/drift bookkeeping for the new session.
                positionKalman.reset()
                calibrationSamples.clear()
                altitudeCalibrated = false
                driftWindow.clear()
                lastDriftCorrectionMs = 0L

                try {
                    barometer.start { pressureAlt ->
                        scope.launch { latestBaro = pressureAlt }
                    }
                    gps.start { location ->
                        scope.launch { onLocation(location) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start sensors: ${e.message}", e)
                    val sessionId = openedSessionId
                    if (sessionId != null) {
                        runCatching {
                            withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MS) {
                                sessionDao.close(id = sessionId, endMs = System.currentTimeMillis(), distanceM = 0.0, gainM = 0.0, lossM = 0.0, maxAltM = 0.0, avgSpeedKmh = 0.0)
                            }
                        }
                    }
                    resetAccumulators()
                    scope.cancel()
                    engineScope = null
                    _state.value = TrekState()
                    _fatalError.tryEmit("Couldn't start trek sensors: ${e.message ?: e::class.simpleName}")
                    return@withLock
                }
            }
        }
    }

    fun stop() {
        CoroutineScope(SupervisorJob() + engineDispatcher).launch {
            startStopMutex.withLock {
                val scope = engineScope ?: run {
                    Log.d(TAG, "stop() called but engine not started")
                    return@withLock
                }

                gps.stop()
                barometer.stop()

                try {
                    sessionOpenDeferred?.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Session never opened cleanly; skipping close write")
                    scope.cancel()
                    engineScope = null
                    resetAccumulators()
                    _state.value = TrekState()
                    return@withLock
                }

                scope.cancel()
                engineScope = null

                val sessionId = currentSessionId
                if (sessionId == null) {
                    resetAccumulators()
                    _state.value = TrekState()
                    return@withLock
                }

                val distSnapshot   = totalDistanceM
                val gainSnapshot   = totalGain
                val lossSnapshot   = totalLoss
                val maxAltSnapshot = maxAltM
                val startSnapshot  = sessionStartMs

                resetAccumulators()
                _state.value = TrekState()

                val nowMs       = System.currentTimeMillis()
                val elapsedS    = (nowMs - startSnapshot) / 1_000.0
                val avgSpeedKmh = if (elapsedS > 0) (distSnapshot / elapsedS) * 3.6 else 0.0
                withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MS) {
                    sessionDao.close(id = sessionId, endMs = nowMs, distanceM = distSnapshot, gainM = gainSnapshot, lossM = lossSnapshot, maxAltM = maxAltSnapshot, avgSpeedKmh = avgSpeedKmh)
                    Log.d(TAG, "session closed: id=$sessionId dist=${distSnapshot}m")
                } ?: Log.w(TAG, "session close write timed out after ${SESSION_CLOSE_TIMEOUT_MS}ms")
            }
        }
    }

    // ── GPS processing — runs exclusively on engineDispatcher ─────────────────

    private fun onLocation(location: Location) {

        if (location.accuracy > ACCURACY_THRESHOLD_M) {
            consecutiveRejections++
            Log.v(TAG, "Fix rejected: accuracy=${location.accuracy}m (rejection #$consecutiveRejections)")

            if (consecutiveRejections >= GPS_DEGRADED_THRESHOLD &&
                _state.value.gpsSignalState !is GpsSignalState.DEGRADED
            ) {
                _state.value = _state.value.copy(
                    gpsSignalState = GpsSignalState.DEGRADED(consecutiveRejections)
                )
            }
            return
        }

        if (_state.value.gpsSignalState !is GpsSignalState.OK) {
            _state.value = _state.value.copy(gpsSignalState = GpsSignalState.OK)
        }
        consecutiveRejections = 0

        val now = System.currentTimeMillis()

        val gpsAlt  = location.altitude
        val baroAlt = latestBaro
        val hasAltitudeSource = baroAlt != null || gpsAlt != 0.0

        // ── NEW: altitude calibration phase ─────────────────────────────────
        // Collect raw GPS altitude fixes (only when GPS itself reports a
        // real altitude — some fixes report 0.0/no altitude data) before
        // seeding the Kalman filter. During this window, emit barometer-only
        // (or last-known) altitude rather than an unseeded fused estimate —
        // see altitude computation below.
        if (!altitudeCalibrated && gpsAlt != 0.0) {
            calibrationSamples.add(gpsAlt)
            if (calibrationSamples.size >= ALTITUDE_CALIBRATION_SAMPLE_COUNT) {
                val seedAltitude = calibrationSamples.average()
                // First update() call after reset() always sets x = measurement
                // directly (see KalmanFilter.update — !initialized branch), so
                // this seeds the filter's starting altitude from the averaged
                // fixes rather than a single noisy one.
                kalman.update(seedAltitude)
                altitudeCalibrated = true
                Log.d(TAG, "Altitude calibrated: seed=$seedAltitude from ${calibrationSamples.size} fixes")
            }
        }

        val altitude = when {
            !hasAltitudeSource -> lastAltitude ?: 0.0
            !altitudeCalibrated && baroAlt != null ->
                // Mid-calibration, but barometer is available — use raw
                // barometer reading directly rather than an unseeded fused
                // value. Not run through the Kalman filter yet since the
                // filter hasn't been seeded (would take this single reading
                // as its permanent starting state — see update()'s
                // !initialized branch — prematurely, before averaging).
                baroAlt
            !altitudeCalibrated ->
                // No barometer and still calibrating — nothing trustworthy
                // to show yet.
                lastAltitude ?: 0.0
            else -> {
                val fusedRaw = if (baroAlt != null) (gpsAlt * 0.3) + (baroAlt * 0.7) else gpsAlt
                kalman.update(fusedRaw, measurementNoise = location.accuracy.toDouble())
            }
        }

        // ── NEW: barometer drift correction ─────────────────────────────────
        // Maintain a rolling window of raw GPS altitude fixes, and every
        // DRIFT_CORRECTION_INTERVAL_MS, nudge the filter gently toward their
        // average using a deliberately large measurementNoise (small Kalman
        // gain) — corrects slow baro drift from weather changes without
        // letting a single noisy fix snap the estimate.
        if (altitudeCalibrated && gpsAlt != 0.0) {
            driftWindow.addLast(gpsAlt)
            if (driftWindow.size > DRIFT_CORRECTION_WINDOW_SIZE) driftWindow.removeFirst()

            if (lastDriftCorrectionMs == 0L) lastDriftCorrectionMs = now
            if (driftWindow.size >= DRIFT_CORRECTION_WINDOW_SIZE &&
                now - lastDriftCorrectionMs >= DRIFT_CORRECTION_INTERVAL_MS
            ) {
                val gpsAltAvg = driftWindow.average()
                kalman.update(gpsAltAvg, measurementNoise = DRIFT_CORRECTION_NOISE)
                lastDriftCorrectionMs = now
                Log.d(TAG, "Drift correction applied: gpsAltAvg=$gpsAltAvg")
            }
        }

        // ── NEW: position smoothing ──────────────────────────────────────────
        // Smooth lat/lng before using them for displacement/distance, closing
        // the raw-jitter overcounting gap described in the class KDoc. Speed/
        // bearing/accuracy are carried over from the raw fix unchanged — only
        // position itself is smoothed.
        val (smoothLat, smoothLng) = positionKalman.process(
            newLat      = location.latitude,
            newLng      = location.longitude,
            accuracy    = location.accuracy,
            timestampMs = now
        )
        val smoothedLocation = Location(location.provider).apply {
            latitude  = smoothLat
            longitude = smoothLng
            accuracy  = location.accuracy
            speed     = location.speed
            bearing   = location.bearing
            time      = location.time
        }

        val last = lastAcceptedLocation
        if (last != null) {
            val dist = last.distanceTo(smoothedLocation).toDouble()
            if (dist >= MIN_DISPLACEMENT_M) {
                totalDistanceM      += dist
                lastAcceptedLocation = smoothedLocation
            }
        } else {
            lastAcceptedLocation = smoothedLocation
        }

        val prevAlt = lastAltitude
        if (prevAlt != null) {
            val delta = altitude - prevAlt
            if (delta >  MIN_ELEVATION_DELTA_M) totalGain += delta
            if (delta < -MIN_ELEVATION_DELTA_M) totalLoss += abs(delta)
        }
        lastAltitude = altitude
        if (altitude > maxAltM) maxAltM = altitude

        // ── NEW: speed outlier rejection ─────────────────────────────────────
        // Reject implausible instantaneous speeds for foot trekking (sensor
        // noise from GPS multipath near buildings/tree cover) BEFORE they
        // enter the median window, rather than letting a spike sit in the
        // window and skew the median if a second spike lands nearby. This
        // sample is skipped entirely (median computed from remaining window
        // entries) rather than clamped, since a clamped-but-still-present
        // outlier would still bias the window toward it.
        val rawSpeedMs   = if (location.hasSpeed() && location.speed >= 0f) location.speed else 0f
        val rawSpeedKmh  = rawSpeedMs * 3.6
        if (rawSpeedKmh <= MAX_PLAUSIBLE_SPEED_KMH) {
            speedWindow.addLast(rawSpeedMs)
            if (speedWindow.size > SPEED_WINDOW_SIZE) speedWindow.removeFirst()
        } else {
            Log.v(TAG, "Speed outlier rejected: ${rawSpeedKmh}km/h")
        }
        val medianSpeedKmh = medianOf(speedWindow) * 3.6
        val speedKmh = if (medianSpeedKmh < MIN_MEANINGFUL_SPEED_KMH) 0.0 else medianSpeedKmh

        ascentWindow.addLast(Pair(now, altitude))
        val cutoff = now - ASCENT_RATE_WINDOW_MS
        while (ascentWindow.isNotEmpty() && ascentWindow.first().first < cutoff) ascentWindow.removeFirst()
        val ascentRateM = if (ascentWindow.size >= 2) {
            val oldest  = ascentWindow.first()
            val newest  = ascentWindow.last()
            val deltaAlt = newest.second - oldest.second
            val deltaHr  = (newest.first - oldest.first) / 3_600_000.0
            if (deltaHr > 0 && deltaAlt > 0) deltaAlt / deltaHr else 0.0
        } else 0.0

        if (!inBatterySaver) {
            if (speedKmh < BATTERY_SAVER_SPEED_KMH) {
                if (lowSpeedSinceMs == 0L) lowSpeedSinceMs = now
                if (now - lowSpeedSinceMs >= BATTERY_SAVER_DEBOUNCE_MS) {
                    inBatterySaver  = true; lowSpeedSinceMs = 0L
                    gps.setBatterySaver(true); barometer.setBatterySaver(true)
                    Log.i(TAG, "Battery saver ON")
                }
            } else { lowSpeedSinceMs = 0L }
        } else {
            if (speedKmh >= BATTERY_SAVER_EXIT_SPEED_KMH) {
                if (highSpeedSinceMs == 0L) highSpeedSinceMs = now
                if (now - highSpeedSinceMs >= BATTERY_SAVER_EXIT_DEBOUNCE_MS) {
                    inBatterySaver   = false; highSpeedSinceMs = 0L
                    gps.setBatterySaver(false); barometer.setBatterySaver(false)
                    Log.i(TAG, "Battery saver OFF")
                }
            } else { highSpeedSinceMs = 0L }
        }

        val sessionId = currentSessionId
        if (sessionId != null && now - lastElevationRecordMs >= ELEVATION_RECORD_INTERVAL_MS) {
            lastElevationRecordMs = now
            engineScope?.launch {
                runCatching {
                    elevationDao.insert(TrekElevationPoint(sessionId = sessionId, timestampMs = now, altitudeM = altitude, accuracyM = location.accuracy))
                }.onFailure { e -> Log.e(TAG, "Failed to insert elevation point: ${e.message}", e) }
            }
        }

        _state.value = TrekState(
            altitude           = altitude,
            latitude           = location.latitude,
            longitude          = location.longitude,
            accuracy           = location.accuracy,
            speedKmh           = speedKmh,
            distanceKm         = totalDistanceM / 1_000.0,
            gainMeters         = totalGain,
            lossMeters         = totalLoss,
            altitudeZone       = zone(altitude),
            ascentRateM        = ascentRateM,
            currentSessionId   = currentSessionId,
            barometerAvailable = baroAlt != null,
            inBatterySaver     = inBatterySaver,
            gpsSignalState     = _state.value.gpsSignalState
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun medianOf(window: ArrayDeque<Float>): Float {
        if (window.isEmpty()) return 0f
        val sorted = window.sorted()
        val mid    = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
    }

    private fun zone(alt: Double) = when {
        alt < 2_500 -> AltitudeZone.NORMAL
        alt < 3_500 -> AltitudeZone.ACCLIMATIZATION
        alt < 5_000 -> AltitudeZone.HIGH_RISK
        else        -> AltitudeZone.EXTREME
    }

    private fun resetAccumulators() {
        lastAcceptedLocation  = null
        lastAltitude          = null
        totalGain             = 0.0
        totalLoss             = 0.0
        totalDistanceM        = 0.0
        maxAltM               = 0.0
        latestBaro            = null
        speedWindow.clear()
        ascentWindow.clear()
        inBatterySaver        = false
        lowSpeedSinceMs       = 0L
        highSpeedSinceMs      = 0L
        currentSessionId      = null
        sessionStartMs        = 0L
        lastElevationRecordMs = 0L
        sessionOpenDeferred   = null
        consecutiveRejections = 0
        // NEW
        calibrationSamples.clear()
        altitudeCalibrated    = false
        driftWindow.clear()
        lastDriftCorrectionMs = 0L
    }
}