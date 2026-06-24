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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Threading model (post-fix):
 *
 * All mutable accumulator state is confined to [engineDispatcher], a
 * single-threaded coroutine dispatcher. Every GPS callback immediately
 * dispatches work onto this dispatcher instead of mutating state on the
 * FLP binder thread. This eliminates:
 *
 *   E1 — @Volatile does not protect compound read-modify-write operations.
 *   E2 — ArrayDeque mutations racing between two concurrent FLP callbacks.
 *
 * [startStopMutex] serialises start()/stop() transitions so a rapid
 * start→stop→start sequence can never open two sessions simultaneously or
 * close a session with zeroed stats:
 *
 *   E3 — stopping flag was advisory-only; start() could race close-write.
 *   E4 — session-open coroutine could be cancelled before setting currentSessionId.
 */
class TrekEngine(private val context: Context) {

    companion object {
        private const val TAG = "TrekEngine"

        const val ACCURACY_THRESHOLD_M         = 20f
        const val MIN_DISPLACEMENT_M           = 5.0
        const val MIN_ELEVATION_DELTA_M        = 2.0
        const val SPEED_WINDOW_SIZE            = 5
        const val MIN_MEANINGFUL_SPEED_KMH     = 0.8
        const val BATTERY_SAVER_DEBOUNCE_MS    = 30_000L
        const val BATTERY_SAVER_SPEED_KMH      = 0.5
        const val BATTERY_SAVER_EXIT_SPEED_KMH = 1.2
        const val BATTERY_SAVER_EXIT_DEBOUNCE_MS = 20_000L
        const val ELEVATION_RECORD_INTERVAL_MS = 60_000L
        const val ELEVATION_PURGE_AGE_MS       = 30L * 24 * 60 * 60 * 1_000
        const val ASCENT_RATE_WINDOW_MS        = 30 * 60 * 1_000L
        const val SESSION_CLOSE_TIMEOUT_MS     = 5_000L
    }

    private val gps       = GpsDataSource(context)
    private val barometer = BarometerSource(context)
    private val kalman    = KalmanFilter()

    private val db           by lazy { SafetyDatabase.getInstance(context) }
    private val sessionDao   by lazy { db.trekSessionDao() }
    private val elevationDao by lazy { db.trekElevationPointDao() }

    // FIX E1/E2 — single-threaded dispatcher; ALL accumulator reads and writes
    // happen here. No @Volatile needed for fields only touched on this dispatcher.
    private val engineDispatcher = Dispatchers.IO.limitedParallelism(1)

    // engineScope: lives while a session is active. Cancelled on stop().
    private var engineScope: CoroutineScope? = null

    // FIX E3/E4 — serialises all start/stop transitions.
    private val startStopMutex = Mutex()

    // Signals when the session-open write is complete so stop() can safely
    // read currentSessionId and accumulator values.
    private var sessionOpenDeferred: CompletableDeferred<Unit>? = null

    val _state = MutableStateFlow(TrekState())
    val state: StateFlow<TrekState> = _state

    // ── Accumulators — only accessed on engineDispatcher ──────────────────────
    private var lastAcceptedLocation  : Location? = null
    private var lastAltitude          : Double?  = null
    private var totalGain             = 0.0
    private var totalLoss             = 0.0
    private var totalDistanceM        = 0.0
    private var maxAltM               = 0.0
    private var latestBaro            : Double?  = null
    private val speedWindow           = ArrayDeque<Float>(SPEED_WINDOW_SIZE)
    private var inBatterySaver        = false
    private var lowSpeedSinceMs       = 0L
    private var highSpeedSinceMs      = 0L
    private var currentSessionId      : Long? = null
    private var sessionStartMs        = 0L
    private var lastElevationRecordMs = 0L
    private val ascentWindow          = ArrayDeque<Pair<Long, Double>>()

    // ── Public API ────────────────────────────────────────────────────────────

    fun start() {
        // FIX E3/E4 — launch in a fire-and-forget scope outside engineScope so
        // the mutex acquisition and DB write are not on the calling thread.
        CoroutineScope(SupervisorJob() + engineDispatcher).launch {
            startStopMutex.withLock {
                if (engineScope != null) {
                    Log.d(TAG, "already started — ignoring")
                    return@withLock
                }

                val scope = CoroutineScope(SupervisorJob() + engineDispatcher)
                engineScope = scope

                // FIX E4 — open the session synchronously within the lock before
                // starting GPS callbacks. currentSessionId is set before any
                // location update can read it.
                val deferred = CompletableDeferred<Unit>()
                sessionOpenDeferred = deferred

                try {
                    sessionDao.closeOrphanedSessions(System.currentTimeMillis())
                    elevationDao.purgeOlderThan(
                        System.currentTimeMillis() - ELEVATION_PURGE_AGE_MS
                    )
                    val sessionId = sessionDao.insert(
                        TrekSession(startMs = System.currentTimeMillis())
                    )
                    currentSessionId = sessionId
                    sessionStartMs   = System.currentTimeMillis()
                    Log.d(TAG, "session opened: id=$sessionId")
                    _state.value = _state.value.copy(currentSessionId = sessionId)
                    deferred.complete(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open session: ${e.message}")
                    deferred.completeExceptionally(e)
                    engineScope?.cancel()
                    engineScope = null
                    return@withLock
                }

                // Start sensors only after session row exists.
                barometer.start { pressureAlt ->
                    // FIX E2 — dispatch onto engineDispatcher, not barometer's callback thread.
                    scope.launch { latestBaro = pressureAlt }
                }
                gps.start { location ->
                    // FIX E2 — all location processing on engineDispatcher.
                    scope.launch { onLocation(location) }
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

                // FIX E4 — wait for the session-open write to finish before reading
                // currentSessionId and accumulators. If it failed, there's nothing
                // to close.
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

                // FIX E1 — read accumulators on engineDispatcher (we're already on it
                // inside startStopMutex, so these reads are safe).
                scope.cancel()
                engineScope = null

                val sessionId = currentSessionId
                if (sessionId == null) {
                    resetAccumulators()
                    _state.value = TrekState()
                    return@withLock
                }

                // Snapshot values before resetAccumulators() clears them.
                val distSnapshot  = totalDistanceM
                val gainSnapshot  = totalGain
                val lossSnapshot  = totalLoss
                val maxAltSnapshot = maxAltM
                val startSnapshot = sessionStartMs

                resetAccumulators()
                _state.value = TrekState()

                // FIX — close write on its own scope with a hard timeout so it
                // cannot block the mutex forever.
                val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                try {
                    withTimeoutOrNull(SESSION_CLOSE_TIMEOUT_MS) {
                        val nowMs       = System.currentTimeMillis()
                        val elapsedS    = (nowMs - startSnapshot) / 1_000.0
                        val avgSpeedKmh = if (elapsedS > 0) (distSnapshot / elapsedS) * 3.6 else 0.0
                        sessionDao.close(
                            id          = sessionId,
                            endMs       = nowMs,
                            distanceM   = distSnapshot,
                            gainM       = gainSnapshot,
                            lossM       = lossSnapshot,
                            maxAltM     = maxAltSnapshot,
                            avgSpeedKmh = avgSpeedKmh
                        )
                        Log.d(TAG, "session closed: id=$sessionId dist=${distSnapshot}m")
                    } ?: Log.w(TAG, "session close write timed out after ${SESSION_CLOSE_TIMEOUT_MS}ms")
                } finally {
                    closeScope.cancel()
                }
            }
        }
    }

    // ── GPS processing — runs exclusively on engineDispatcher ─────────────────

    private fun onLocation(location: Location) {
        // FIX E1/E2 — this function is only ever called via scope.launch on
        // engineDispatcher (single-threaded), so all reads/writes below are safe.

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
                totalDistanceM      += dist
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

        val rawSpeedMs = if (location.hasSpeed() && location.speed >= 0f) location.speed else 0f
        speedWindow.addLast(rawSpeedMs)
        if (speedWindow.size > SPEED_WINDOW_SIZE) speedWindow.removeFirst()
        val medianSpeedKmh = medianOf(speedWindow) * 3.6
        val speedKmh = if (medianSpeedKmh < MIN_MEANINGFUL_SPEED_KMH) 0.0 else medianSpeedKmh

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

        val sessionId = currentSessionId
        if (sessionId != null && now - lastElevationRecordMs >= ELEVATION_RECORD_INTERVAL_MS) {
            lastElevationRecordMs = now
            // FIX E2 — launch on the current (engine) scope; if scope was cancelled
            // by stop() between the null-check and here, the launch is a no-op.
            engineScope?.launch {
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
            inBatterySaver     = inBatterySaver
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // Called only from within startStopMutex on engineDispatcher — safe.
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
    }
}