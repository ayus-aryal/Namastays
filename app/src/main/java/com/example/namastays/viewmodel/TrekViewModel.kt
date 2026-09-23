package com.example.namastays.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.NamastaysApp
import com.example.namastays.data.AnchorPoint
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.SleepAltitudeRecord
import com.example.namastays.data.TrekElevationPoint
import com.example.namastays.data.TrekRepository
import com.example.namastays.data.TrekSession
import com.example.namastays.dto.GpsSignalState
import com.example.namastays.dto.TrekState
import com.example.namastays.utilities.TrekTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * ViewModel for [TrekModeScreen] and [TrekSessionDetailScreen].
 *
 * ── Source of truth for Trek Mode active/inactive ───────────────────────────
 *  [isTracking] is derived from [trekState.currentSessionId], which is set
 *  by TrekEngine.start() when the session row is successfully opened in Room
 *  and cleared by TrekEngine.stop() when the session is closed.
 *
 *  This means:
 *   • UI never needs rememberSaveable for the toggle — it always reflects
 *     whether a session is genuinely open in the DB.
 *   • After process death + service still running: on relaunch the engine
 *     state already has currentSessionId != null → toggle shows ON correctly.
 *   • After service crash with no session: currentSessionId is null → OFF.
 *   • No Activity-manager polling, no deprecated API usage.
 *
 * ── Session ownership ────────────────────────────────────────────────────────
 *  This ViewModel never opens or closes sessions directly. It delegates
 *  entirely to [startTrekMode] / [stopTrekMode], which start/stop the
 *  foreground service. The service drives TrekEngine, which owns the
 *  Room session lifecycle.
 *
 * ── Threading contract ───────────────────────────────────────────────────────
 *  All StateFlows are safe to collect on any dispatcher.
 *  Every suspend function touching Room is guarded with
 *  withContext(Dispatchers.IO) so callers on Dispatchers.Main never block.
 */




/**
 * State for TrekSessionDetailScreen's data load.
 *
 * Exposed as a StateFlow on TrekViewModel so the detail screen's data
 * survives configuration changes — no re-query on rotation.
 *
 * FIX UI-1/UI-2/UI-7 (audit): previously this data lived in plain
 * `remember {}` inside TrekSessionDetailScreen, with no error handling and
 * sequential (not parallel) DB reads.
 */
sealed class DetailScreenState {
    /** Initial state before any load has been requested. */
    object Idle : DetailScreenState()

    /** Load is in flight. */
    object Loading : DetailScreenState()

    /**
     * Load succeeded.
     * @param elevationPoints down-sampled to 5-min buckets (or raw if < 3
     *   sampled points exist — see TrekRepository.elevationPoints).
     * @param prevSleepAlt the sleep altitude recorded for the night before
     *   the session, or null if no record exists.
     */
    data class Success(
        val elevationPoints : List<TrekElevationPoint>,
        val prevSleepAlt    : Double?
    ) : DetailScreenState()

    /** Load failed — message is safe to show directly in the UI. */
    data class Error(val message: String) : DetailScreenState()
}


/**
 * Result of an [TrekViewModel.anchorCurrentLocation] call, so the screen can
 * decide what toast/snackbar to show without re-deriving GPS state itself.
 *
 * NoFix exists separately from Saved(degraded) because a location with truly
 * no fix yet (lat/lng still 0.0, or currentSessionId null) shouldn't silently
 * save garbage coordinates — that's different from "we have a fix, but it's
 * a poor one."
 */
sealed class AnchorSaveResult {
    /** Saved successfully with a good-quality fix. */
    object Saved : AnchorSaveResult()

    /** Saved, but GPS signal was degraded at the moment of saving. */
    data class SavedWithLowAccuracy(val accuracyMeters: Float) : AnchorSaveResult()

    /** Not saved — no usable location yet (Trek Mode just started, no fix). */
    object NoFixAvailable : AnchorSaveResult()
}



class TrekViewModel(application: Application) : AndroidViewModel(application) {

    private val app    = application as NamastaysApp
    private val engine = app.trekEngine

    private val anchorRepo = app.anchorPointRepository


    private val db   = SafetyDatabase.getInstance(application)
    private val repo = TrekRepository(
        sessionDao   = db.trekSessionDao(),
        elevationDao = db.trekElevationPointDao(),
        sleepDao     = db.sleepAltitudeDao()
    )

    // ── Live sensor state ─────────────────────────────────────────────────────

    /**
     * Raw engine state. Always up to date while Trek Mode is active.
     * currentSessionId != null ⟺ a session is open and engine is running.
     */
    val trekState: StateFlow<TrekState> = engine.state

    // ── Trek Mode active/inactive ─────────────────────────────────────────────

    /**
     * True when TrekEngine has an open session (currentSessionId != null).
     *
     * This is the canonical source of truth for the toggle switch in the UI.
     * It survives:
     *   • Recomposition               — StateFlow, always current
     *   • Configuration change        — ViewModel survives, StateFlow is live
     *   • Process death + re-launch   — engine re-reads state from the running
     *                                   service's StateFlow on the first collect
     *   • Service killed by system    — engine.stop() fires, clears sessionId,
     *                                   isTracking becomes false automatically
     *
     * SharingStarted.Eagerly: we want this to be up to date the instant the
     * ViewModel is created, not only when a subscriber appears. This ensures
     * the toggle syncs correctly even before the screen first composes.
     */
    val isTracking: StateFlow<Boolean> = engine.state
        .map { it.currentSessionId != null }
        .distinctUntilChanged()
        .stateIn(
            scope         = viewModelScope,
            started       = SharingStarted.Eagerly,
            initialValue  = engine.state.value.currentSessionId != null
        )



    // ── Backtrack anchor point ──────────────────────────────────────────────────

    /**
     * Current anchor point (null if none set, or if the stored one has
     * expired — see AnchorPointRepository.EXPIRY_MS). Exposed here so
     * TrekModeScreen can show "Anchor Point Set • Tap to Update" vs the
     * default button label without duplicating the expiry check.
     *
     * WhileSubscribed(5_000) matches allSessions/allSleepRecords below —
     * this is read-mostly UI state, not something that needs Eagerly.
     */
    val anchorPoint: StateFlow<AnchorPoint?> =
        anchorRepo.anchorPoint
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Saves (or overwrites) the anchor using the current live trek location.
     *
     * Returns [AnchorSaveResult] rather than a plain Boolean so the caller
     * (TrekModeScreen) can show the right message: a silent save, a
     * "low accuracy" warning, or a "no fix yet" message, without needing to
     * separately inspect trekState/gpsSignalState itself.
     *
     * Does NOT block the save on poor accuracy, see AnchorPointRepository
     * KDoc. Blocking would defeat the point of the feature: marking a spot
     * *right now* is often most needed exactly when signal is weakest.
     *
     * Safe to call from Dispatchers.Main.
     */
    fun anchorCurrentLocation(onResult: (AnchorSaveResult) -> Unit = {}) {
        val current = trekState.value

        // No session open, or no fix received yet (lat/lng still default 0.0
        // — see TrekEngine's own "sentinel-altitude" comment for the same
        // pattern applied to altitude). Refuse rather than save (0.0, 0.0).
        val hasFix = current.currentSessionId != null &&
                (current.latitude != 0.0 || current.longitude != 0.0)

        if (!hasFix) {
            onResult(AnchorSaveResult.NoFixAvailable)
            return
        }

        val isDegraded = current.gpsSignalState is GpsSignalState.DEGRADED

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                anchorRepo.saveAnchor(
                    latitude       = current.latitude,
                    longitude      = current.longitude,
                    accuracyMeters = current.accuracy
                )
            }
            onResult(
                if (isDegraded) AnchorSaveResult.SavedWithLowAccuracy(current.accuracy)
                else AnchorSaveResult.Saved
            )
        }
    }

    // ── Session logbook ───────────────────────────────────────────────────────

    /** All closed sessions, newest first. Emits an empty list when none exist. */
    val allSessions: StateFlow<List<TrekSession>> =
        repo.allSessions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Sleep altitude history ────────────────────────────────────────────────

    /** All sleep altitude records. */
    val allSleepRecords: StateFlow<List<SleepAltitudeRecord>> =
        repo.allSleepRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Detail screen navigation ──────────────────────────────────────────────

    /**
     * Session currently open on the detail screen, or null (list visible).
     * Driven by [selectSession] instead of NavController arguments —
     * avoids serialising TrekSession through nav args.
     */
    private val _selectedSession = MutableStateFlow<TrekSession?>(null)
    val selectedSession: StateFlow<TrekSession?> = _selectedSession.asStateFlow()

    fun selectSession(session: TrekSession?) {
        _selectedSession.value = session
    }

    /**
     * Deletes [sessionId] and its elevation points (CASCADE), then clears
     * selectedSession if it was the deleted one.
     */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteSession(sessionId) }
            if (_selectedSession.value?.id == sessionId) {
                _selectedSession.value = null
            }
        }
    }

    // ── Trek Mode control ─────────────────────────────────────────────────────

    /**
     * Start Trek Mode: launches the foreground service.
     * The service calls engine.start(), which opens a Room session and
     * sets currentSessionId — isTracking becomes true automatically.
     *
     * Callers must have already verified:
     *   • ACCESS_FINE_LOCATION (and ACCESS_COARSE_LOCATION) granted
     *   • GPS provider enabled
     *
     * This function is a no-op if the service is already running
     * (engine.start() is internally idempotent via startStopMutex).
     */
    fun startTrekMode() {
        val ctx     = getApplication<Application>()
        val intent  = Intent(ctx, TrekTrackingService::class.java)
        ContextCompat.startForegroundService(ctx, intent)
    }

    /**
     * Stop Trek Mode: stops the foreground service.
     * Service.onDestroy() calls engine.stop(), which closes the Room
     * session and clears currentSessionId — isTracking becomes false.
     */
    fun stopTrekMode() {
        val ctx = getApplication<Application>()
        ctx.stopService(Intent(ctx, TrekTrackingService::class.java))
    }

    // ── Sleep altitude ────────────────────────────────────────────────────────

    /**
     * True if a sleep altitude record already exists for today.
     * Safe to call from Dispatchers.Main.
     */
    suspend fun todayRecordExists(): Boolean =
        withContext(Dispatchers.IO) { repo.sleepRecordForToday() != null }

    /**
     * Persist [altitude] as today's sleep altitude record.
     * Safe to call from Dispatchers.Main.
     */
    suspend fun saveSleepAltitude(altitude: Double) =
        withContext(Dispatchers.IO) { repo.saveSleepAltitude(altitude) }

    // ── Detail screen data ────────────────────────────────────────────────────

    /**
     * Elevation points for [sessionId], down-sampled to 5-min buckets.
     * Falls back to raw points if fewer than 3 sampled points exist.
     * Returns empty list on any DB error — UI must handle this gracefully.
     * Safe to call from Dispatchers.Main.
     */
    suspend fun elevationPoints(sessionId: Long): List<TrekElevationPoint> =
        withContext(Dispatchers.IO) {
            runCatching { repo.elevationPoints(sessionId) }.getOrElse { emptyList() }
        }

    /**
     * Sleep altitude record for the night *before* [sessionStartMs].
     * Used on the detail screen to show delta vs last sleep altitude.
     * Returns null if no record for that date.
     * Safe to call from Dispatchers.Main.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sleepAltitudeBeforeSession(sessionStartMs: Long): SleepAltitudeRecord? =
        withContext(Dispatchers.IO) {
            val date = Instant
                .ofEpochMilli(sessionStartMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .minusDays(1)
                .toString()
            runCatching { repo.sleepRecordForDate(date) }.getOrNull()
        }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is cancelled automatically by AndroidViewModel.
        // No extra cleanup needed — engine lifecycle is owned by the service,
        // not this ViewModel, so we must NOT call engine.stop() here.
    }



    // FIX UI-1/UI-2/UI-7 — replaces the screen-level LaunchedEffect.
    private val _detailState = MutableStateFlow<DetailScreenState>(DetailScreenState.Idle)
    val detailState: StateFlow<DetailScreenState> = _detailState.asStateFlow()

    /**
     * Loads elevation points and the previous night's sleep altitude for
     * [session] concurrently (FIX UI-7), with full error handling (FIX UI-2).
     *
     * Results are stored in [detailState], which is a StateFlow and therefore
     * survives configuration changes — calling this again after rotation is a
     * no-op if the current state is already Success for the same session
     * (callers should guard on session.id equality if needed, or just call it
     * and let the Loading→Success transition be instant from the Room cache).
     *
     * Must be called from a scope that can use [Build.VERSION_CODES.O] APIs
     * (java.time) — the caller (TrekSessionDetailScreen) is already annotated.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadSessionDetail(session: TrekSession) {
        viewModelScope.launch {
            _detailState.value = DetailScreenState.Loading
            try {
                // FIX UI-7 — both reads are independent; run concurrently.
                coroutineScope {
                    val elevationDeferred = async { elevationPoints(session.id) }
                    val sleepDeferred     = async { sleepAltitudeBeforeSession(session.startMs) }
                    _detailState.value = DetailScreenState.Success(
                        elevationPoints = elevationDeferred.await(),
                        prevSleepAlt    = sleepDeferred.await()?.altitudeMeters
                    )
                }
            } catch (e: Exception) {
                // FIX UI-2 — was uncaught, now surfaces as a typed error state.
                Log.e("TrekViewModel", "Failed to load session detail: ${e.message}", e)
                _detailState.value = DetailScreenState.Error(
                    e.message?.ifBlank { null } ?: "Couldn't load session data"
                )
            }
        }
    }

    /**
     * Resets [detailState] to Idle. Call this when the detail screen is closed
     * so the stale Success state from the previous session doesn't briefly flash
     * when a different session is opened next.
     */
    fun clearSessionDetail() {
        _detailState.value = DetailScreenState.Idle
    }
}