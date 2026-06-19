package com.example.namastays.utilities

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.TrekElevationPoint
import com.example.namastays.data.TrekSession
import com.example.namastays.dto.TrekState
import com.example.namastays.screens.AltitudeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Central engine for Trek Mode. Owned as a singleton by NamastaysApp.
 *
 * Responsibilities
 * ────────────────
 * 1. Drive [GpsDataSource] + [BarometerSource] + [KalmanFilter] (unchanged).
 * 2. ACCURACY GATING — every GPS fix is rejected if accuracy > [ACCURACY_THRESHOLD_M].
 * 3. SPEED — 5-point median over recent accurate fixes, zeroed below noise floor.
 *    Raw location.speed on Android is instantaneous and meaningless below ~1 km/h.
 * 4. DISTANCE — Haversine only when accuracy ≤ threshold AND displacement ≥ [MIN_DISPLACEMENT_M].
 *    Eliminates the phantom distance that accumulates while standing still.
 * 5. ELEVATION — Kalman-filtered fused altitude; gain/loss gated to ≥ [MIN_ELEVATION_DELTA_M].
 * 6. BATTERY SAVER — after [BATTERY_SAVER_DEBOUNCE_MS] below [BATTERY_SAVER_SPEED_KMH],
 *    switches GPS to slow polling. Hysteretic exit to prevent thrashing.
 * 7. SESSION LIFECYCLE — opens a TrekSession row on start(), closes it with final
 *    stats on stop(). Orphaned sessions from crashes are closed on next start().
 * 8. ELEVATION POINTS — writes one DB row per [ELEVATION_RECORD_INTERVAL_MS] for
 *    the sparkline, only when accuracy is within threshold.
 * 9. ASCENT RATE — sliding 30-minute window of (altitude, time) pairs → m/hr.
 *
 * ── Resource lifecycle note (fixed) ─────────────────────────────────────────
 * Previously, `engineScope` (a CoroutineScope wrapping Dispatchers.IO) was
 * only cancelled inside `invokeOnCompletion` of the session-close launch —
 * meaning if stop() was called twice in quick succession, the app was killed
 * mid-close, or start()/stop()/start() happened rapidly, the old scope's Job
 * (and anything it was holding, e.g. an in-flight Room transaction) could
 * become unreachable without ever being explicitly cancelled. The GC would
 * eventually finalize whatever Closeable was underneath, surfacing as a
 * generic "resource failed to call close" warning completely disconnected
 * from this file — which is exactly the bug pattern that was hard to trace.
 *
 * Fixed by: cancelling engineScope synchronously and immediately in stop(),
 * and doing the final session-close write on a short-lived scope that is
 * guaranteed to either complete or hit a hard timeout — it no longer depends
 * on the engine's main scope surviving long enough to finish.
 */
class TrekEngine(private val context: Context) {

    companion object {
        private const val TAG = "TrekEngine"

        // ── Accuracy ─────────────────────────────────────────────────────────
        const val ACCURACY_THRESHOLD_M     = 20f
        const val MIN_DISPLACEMENT_M       = 5.0
        const val MIN_ELEVATION_DELTA_M    = 2.0

        // ── Speed ─────────────────────────────────────────────────────────────
        const val SPEED_WINDOW_SIZE        = 5
        const val MIN_MEANINGFUL_SPEED_KMH = 0.8

        // ── Battery saver ────────────────────────────────────────────────────
        const val BATTERY_SAVER_DEBOUNCE_MS     = 30_000L
        const val BATTERY_SAVER_SPEED_KMH       = 0.5
        const val BATTERY_SAVER_EXIT_SPEED_KMH  = 1.2
        const val BATTERY_SAVER_EXIT_DEBOUNCE_MS = 20_000L

        // ── Elevation recording ───────────────────────────────────────────────
        const val ELEVATION_RECORD_INTERVAL_MS  = 60_000L
        const val ELEVATION_PURGE_AGE_MS        = 30L * 24 * 60 * 60 * 1_000

        // ── Ascent rate ───────────────────────────────────────────────────────
        const val ASCENT_RATE_WINDOW_MS = 30 * 60 * 1_000L

        /** Hard cap on how long the final session-close write may run before
         *  we give up waiting and clear state anyway. Prevents a slow/stuck
         *  DB write from holding the engine in a "stopping" limbo forever. */
        const val SESSION_CLOSE_TIMEOUT_MS = 5_000L
    }

    // ── Existing sub-systems (unchanged) ─────────────────────────────────────
    private val gps      = GpsDataSource(context)
    private val barometer = BarometerSource(context)
    private val kalman   = KalmanFilter()

    // ── DB access ─────────────────────────────────────────────────────────────
    private val db            by lazy { SafetyDatabase.getInstance(context) }
    private val sessionDao    by lazy { db.trekSessionDao() }
    private val elevationDao  by lazy { db.trekElevationPointDao() }

    // ── Coroutine scopes ────────────────────────────────────────────────────
    // engineScope: lives only while a trek session is actively running.
    // Cancelled synchronously and immediately in stop() — no longer waits on
    // a completion callback to decide when cleanup happens.
    private var engineScope: CoroutineScope? = null

    // closeScope: a SEPARATE, short-lived scope used only for the final
    // session-close write. It is independent of engineScope's lifetime by
    // design, so cancelling engineScope in stop() can never interrupt the
    // write that's supposed to happen because of stop(). It is itself
    // cancelled once the close write finishes (or times out), so it never
    // outlives its single use.
    private var closeScope: CoroutineScope? = null

    // ── State ─────────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(TrekState())
    val state: StateFlow<TrekState> = _state

    // ── GPS accumulators ──────────────────────────────────────────────────────
    @Volatile private var lastAcceptedLocation : Location? = null
    @Volatile private var lastAltitude         : Double?  = null
    @Volatile private var totalGain            = 0.0
    @Volatile private var totalLoss            = 0.0
    @Volatile private var totalDistanceM       = 0.0
    @Volatile private var maxAltM              = 0.0
    @Volatile private var latestBaro           : Double?  = null

    // ── Speed median window ───────────────────────────────────────────────────
    private val speedWindow = ArrayDeque<Float>(SPEED_WINDOW_SIZE)

    // ── Battery saver state ───────────────────────────────────────────────────
    @Volatile private var inBatterySaver       = false
    @Volatile private var lowSpeedSinceMs      = 0L
    @Volatile private var highSpeedSinceMs     = 0L

    // ── Session ───────────────────────────────────────────────────────────────
    @Volatile private var currentSessionId     : Long? = null
    @Volatile private var sessionStartMs       = 0L

    // ── Elevation recording throttle ──────────────────────────────────────────
    @Volatile private var lastElevationRecordMs = 0L

    // ── Ascent rate: circular buffer of (timestampMs, altitudeM) ─────────────
    private val ascentWindow = ArrayDeque<Pair<Long, Double>>()

    // ── Engine lifecycle flag ─────────────────────────────────────────────────
    @Volatile private var started = false

    // ── Guards against start()/stop()/start() races (see class doc) ──────────
    @Volatile private var stopping = false

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun start() {
        if (started) {
            Log.d(TAG, "already started — ignoring")
            return
        }
        if (stopping) {
            Log.w(TAG, "start() called while previous stop() still finishing — proceeding anyway, but a previous session's close-write may race with this one")
        }

        started = true
        Log.d(TAG, "starting")

        engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        engineScope!!.launch {
            sessionDao.closeOrphanedSessions(System.currentTimeMillis())
            elevationDao.purgeOlderThan(System.currentTimeMillis() - ELEVATION_PURGE_AGE_MS)
            val sessionId = sessionDao.insert(
                TrekSession(startMs = System.currentTimeMillis())
            )
            currentSessionId = sessionId
            sessionStartMs   = System.currentTimeMillis()
            Log.d(TAG, "session opened: id=$sessionId")

            _state.value = _state.value.copy(currentSessionId = sessionId)
        }

        barometer.start { pressureAlt -> latestBaro = pressureAlt }
        gps.start { location -> onLocation(location) }
    }

    fun stop() {
        if (!started) return
        gps.stop()
        barometer.stop()
        started = false

        // 1. Cancel engineScope IMMEDIATELY and SYNCHRONOUSLY. Any in-flight
        //    elevation-point inserts launched on it are abandoned — that's
        //    fine, they're periodic telemetry, not critical data. This is
        //    the key fix: engineScope's lifetime no longer depends on a
        //    completion callback that might never run.
        val scopeToCancel = engineScope
        engineScope = null
        scopeToCancel?.cancel()

        // 2. Do the final session-close write on its own short-lived scope,
        //    independent of the one we just cancelled. withTimeoutOrNull
        //    guarantees this either finishes the write or gives up after
        //    SESSION_CLOSE_TIMEOUT_MS — either way, closeScope below gets
        //    cancelled and released, so nothing lingers.
        val sessionId = currentSessionId
        if (sessionId == null) {
            // Nothing to close — clean up synchronously, no leftover scope.
            resetAccumulators()
            _state.value = TrekState()
            return
        }

        stopping = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        closeScope = scope

        scope.launch {
            try {
                withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MS) {
                    val nowMs      = System.currentTimeMillis()
                    val elapsedS   = (nowMs - sessionStartMs) / 1_000.0
                    val avgSpeedKmh = if (elapsedS > 0) (totalDistanceM / elapsedS) * 3.6 else 0.0

                    sessionDao.close(
                        id          = sessionId,
                        endMs       = nowMs,
                        distanceM   = totalDistanceM,
                        gainM       = totalGain,
                        lossM       = totalLoss,
                        maxAltM     = maxAltM,
                        avgSpeedKmh = avgSpeedKmh
                    )
                    Log.d(TAG, "session closed: id=$sessionId dist=${totalDistanceM}m")
                } ?: Log.w(TAG, "session close write timed out after ${SESSION_CLOSE_TIMEOUT_MS}ms — session row may remain unclosed until next start()'s orphan cleanup")
            } finally {
                // Runs whether the write succeeded, timed out, or threw —
                // guarantees this scope is always released, never leaked.
                resetAccumulators()
                _state.value = TrekState()
                stopping = false
                closeScope?.cancel()
                closeScope = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GPS callback — all the real work happens here
    // ─────────────────────────────────────────────────────────────────────────

    private fun onLocation(location: Location) {

        if (location.accuracy > ACCURACY_THRESHOLD_M) {
            Log.v(TAG, "Fix rejected: accuracy=${location.accuracy}m")
            return
        }

        val now = System.currentTimeMillis()

        val gpsAlt   = location.altitude
        val baroAlt  = latestBaro
        val fusedRaw = when {
            baroAlt != null -> (gpsAlt * 0.3) + (baroAlt * 0.7)
            gpsAlt  != 0.0  -> gpsAlt
            else            -> 0.0
        }
        val altitude = kalman.update(fusedRaw)

        val last = lastAcceptedLocation
        if (last != null) {
            val dist = last.distanceTo(location).toDouble()
            if (dist >= MIN_DISPLACEMENT_M) {
                totalDistanceM += dist
                lastAcceptedLocation = location
            }
        } else {
            lastAcceptedLocation = location
        }

        val prevAlt = lastAltitude
        if (prevAlt != null) {
            val delta = altitude - prevAlt
            if (delta >  MIN_ELEVATION_DELTA_M) totalGain += delta
            if (delta < -MIN_ELEVATION_DELTA_M) totalLoss += abs(delta)
        }
        lastAltitude = altitude
        if (altitude > maxAltM) maxAltM = altitude

        val rawSpeedMs = if (location.hasSpeed() && location.speed >= 0f)
            location.speed else 0f
        speedWindow.addLast(rawSpeedMs)
        if (speedWindow.size > SPEED_WINDOW_SIZE) speedWindow.removeFirst()
        val medianSpeedKmh = medianOf(speedWindow) * 3.6
        val speedKmh = if (medianSpeedKmh < MIN_MEANINGFUL_SPEED_KMH) 0.0
        else medianSpeedKmh

        ascentWindow.addLast(Pair(now, altitude))
        val cutoff = now - ASCENT_RATE_WINDOW_MS
        while (ascentWindow.isNotEmpty() && ascentWindow.first().first < cutoff) {
            ascentWindow.removeFirst()
        }
        val ascentRateM = if (ascentWindow.size >= 2) {
            val oldest   = ascentWindow.first()
            val newest   = ascentWindow.last()
            val deltaAlt = newest.second - oldest.second
            val deltaHr  = (newest.first - oldest.first) / 3_600_000.0
            if (deltaHr > 0 && deltaAlt > 0) deltaAlt / deltaHr else 0.0
        } else 0.0

        if (!inBatterySaver) {
            if (speedKmh < BATTERY_SAVER_SPEED_KMH) {
                if (lowSpeedSinceMs == 0L) lowSpeedSinceMs = now
                if (now - lowSpeedSinceMs >= BATTERY_SAVER_DEBOUNCE_MS) {
                    inBatterySaver  = true
                    lowSpeedSinceMs = 0L
                    gps.setBatterySaver(true)
                    Log.i(TAG, "Battery saver ON")
                }
            } else {
                lowSpeedSinceMs = 0L
            }
        } else {
            if (speedKmh >= BATTERY_SAVER_EXIT_SPEED_KMH) {
                if (highSpeedSinceMs == 0L) highSpeedSinceMs = now
                if (now - highSpeedSinceMs >= BATTERY_SAVER_EXIT_DEBOUNCE_MS) {
                    inBatterySaver   = false
                    highSpeedSinceMs = 0L
                    gps.setBatterySaver(false)
                    Log.i(TAG, "Battery saver OFF")
                }
            } else {
                highSpeedSinceMs = 0L
            }
        }

        // ── 8. Write elevation point to DB (throttled) ────────────────────────
        // Guarded with a local snapshot of engineScope, not the property
        // directly — if stop() races with this exact instant and nulls out
        // engineScope between the check and the launch, we'd otherwise NPE
        // on the !!-style access the original code relied on implicitly.
        val sessionId = currentSessionId
        val scope = engineScope
        if (sessionId != null && scope != null && now - lastElevationRecordMs >= ELEVATION_RECORD_INTERVAL_MS) {
            lastElevationRecordMs = now
            scope.launch {
                elevationDao.insert(
                    TrekElevationPoint(
                        sessionId   = sessionId,
                        timestampMs = now,
                        altitudeM   = altitude,
                        accuracyM   = location.accuracy
                    )
                )
            }
        }

        _state.value = TrekState(
            altitude             = altitude,
            latitude             = location.latitude,
            longitude            = location.longitude,
            accuracy             = location.accuracy,
            speedKmh             = speedKmh,
            distanceKm           = totalDistanceM / 1_000.0,
            gainMeters           = totalGain,
            lossMeters           = totalLoss,
            altitudeZone         = zone(altitude),
            ascentRateM          = ascentRateM,
            currentSessionId     = currentSessionId,
            barometerAvailable   = baroAlt != null,
            inBatterySaver       = inBatterySaver
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun medianOf(window: ArrayDeque<Float>): Float {
        if (window.isEmpty()) return 0f
        val sorted = window.sorted()
        val mid    = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f
        else sorted[mid]
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
    }
}