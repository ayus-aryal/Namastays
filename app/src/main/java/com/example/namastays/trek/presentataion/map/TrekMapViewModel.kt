package com.example.namastays.trek.presentation.map

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.namastays.api.ApiClient
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.domain.TrekNavigationSession
import com.example.namastays.trek.domain.Waypoint
import com.example.namastays.trek.util.ElevationPoint
import com.example.namastays.trek.util.GpxParser
import com.example.namastays.trek.util.LocationRepository
import com.example.namastays.trek.util.LocationSnapshotStore
import com.example.namastays.trek.util.LocationTracker
import com.example.namastays.trek.util.NavigationState
import com.example.namastays.trek.util.NavigationStatus
import com.example.namastays.trek.util.SpeedAdaptiveZoom
import com.example.namastays.trek.util.TrailNavigator
import com.example.namastays.trek.util.TrekLocation
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.trek.util.WaypointParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.geojson.Point

// ─── UI state ─────────────────────────────────────────────────────────────────

/**
 * Single source of truth for everything the Trek Map screen renders.
 *
 * [waypoints] is owned here rather than as local Compose state in the screen —
 * previously it was parsed inside the composable's onMapReady callback into a
 * `remember { mutableStateOf(...) }`, which reset to empty on process death /
 * configuration change until re-parsed, and created a second, inconsistent
 * state-ownership pattern alongside every other ViewModel-driven field.
 */
data class TrekMapUiState(
    val tilesExist: Boolean = false,
    val isDownloaded: Boolean = false,
    val routeLoaded: Boolean = false,

    val trek: TrekItem? = null,
    val elevationPoints: List<ElevationPoint> = emptyList(),
    val gpxPoints: List<Point> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),

    val isNavigating: Boolean = false,
    val isAcquiringGps: Boolean = false,
    val navigationState: NavigationState? = null,
    val cameraFollowMode: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val navigationHeading: Float = 0f,

    val sensorBearing: Float = 0f,
    val lastKnownLocation: TrekLocation? = null,
    val customMarkers: List<CustomMarker> = emptyList(),

    val showWrongLocationDialog: Boolean = false,
    val wrongLocationMessage: String = "",
    val showCompletedDialog: Boolean = false,
    val showResumeDialog: Boolean = false,
    val savedNavigationSession: TrekNavigationSession? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class TrekMapViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val trekId: String = checkNotNull(savedStateHandle["trekId"])

    private val db          = TrekDatabase.getInstance(application)
    private val markerDao   = db.markerDao()
    private val sessionDao  = db.navigationSessionDao()
    private val downloadDao = db.downloadedTrekDao()
    private val repository  = TrekRepository(
        api           = ApiClient.trekApi,
        cacheDao      = db.trekCacheDao(),
        itineraryDao  = db.trekItineraryDao(),
        highlightDao  = db.trekHighlightDao(),
        downloadedDao = db.downloadedTrekDao()
    )
    private val locationRepository = LocationRepository(application)

    val speedAdaptiveZoom = SpeedAdaptiveZoom()
    val snapshotStore     = LocationSnapshotStore()

    private val _uiState = MutableStateFlow(TrekMapUiState())
    val uiState: StateFlow<TrekMapUiState> = _uiState.asStateFlow()

    /**
     * Total trail length in meters, cached once after the route loads and
     * reused by TrailNavigator on every GPS fix. Recomputing this by summing
     * all N GPX points at ~1 Hz would be O(N) per second for a value that
     * never changes for the lifetime of a navigation session.
     */
    private var totalTrailDistance = 0f

    private var previousNavState: NavigationState? = null
    private var navStartTimeMs = 0L

    /**
     * Zero-allocation circular buffer smoothing raw GPS bearing over the last
     * [BEARING_BUF_SIZE] fixes. A plain arithmetic average of raw bearings is
     * wrong across the 359°→0° wrap (averaging 359° and 1° should give 0°,
     * not 180°), so we accumulate sin/cos components — see [bearingBufAverage].
     */
    private val bearingBuf     = FloatArray(BEARING_BUF_SIZE)
    private var bearingBufHead = 0
    private var bearingBufSize = 0

    // Sensor (compass). Lifecycle is owned entirely by the composable's
    // DisposableEffect(state.isNavigating): startSensor() while browsing,
    // stopSensor() while navigating (navigation uses GPS bearing instead).
    // startNavigation()/stopNavigation() intentionally never touch the
    // sensor — that was the root cause of a prior double-registration bug.
    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null

    private var navigationJob: Job? = null
    private var timerJob: Job? = null
    private var deadReckonJob: Job? = null

    private var lastSessionSaveMs = 0L

    /**
     * Set by the composable inside its onMapReady callback; nulled by
     * [stopNavigation] and by the composable's own DisposableEffect(Unit)
     * onDispose — whichever happens first — so the ~60 Hz dead-reckoning loop
     * can never invoke a callback into a destroyed map.
     */
    var onDotUpdate: ((lat: Double, lng: Double, bearing: Float) -> Unit)? = null

    init {
        loadTrekData()
        warmUpGps()
    }

    // ─── Init ──────────────────────────────────────────────────────────────────

    private fun loadTrekData() {
        viewModelScope.launch {
            val trek = repository.getTrekById(trekId)
            _uiState.update { it.copy(trek = trek) }

            // Reconcile Room's "downloaded" flag against the actual file on
            // disk — covers the case where a download was interrupted or the
            // mbtiles file was removed out from under Room.
            val fileExists        = MBTilesLoader.isDownloaded(getApplication(), trekId)
            val roomSaysDownloaded = withContext(Dispatchers.IO) { downloadDao.isDownloaded(trekId) }
            if (roomSaysDownloaded && !fileExists) {
                withContext(Dispatchers.IO) { downloadDao.deleteByTrekId(trekId) }
                _uiState.update { it.copy(tilesExist = false, isDownloaded = false) }
            } else {
                _uiState.update { it.copy(tilesExist = fileExists, isDownloaded = fileExists) }
            }

            // Single SAX pass over the GPX file — see GpxParser.parseFull KDoc.
            val parseResult = withContext(Dispatchers.IO) {
                GpxParser.parseFull(getApplication(), trekId)
            }
            totalTrailDistance = TrailNavigator.calculateTotalDistance(parseResult.points)

            val parsedWaypoints = withContext(Dispatchers.IO) {
                WaypointParser.parse(getApplication(), trekId)
            }

            _uiState.update {
                it.copy(
                    gpxPoints       = parseResult.points,
                    elevationPoints = parseResult.elevationProfile,
                    waypoints       = parsedWaypoints
                )
            }

            // Custom markers — live Flow, kept up to date for the ViewModel's lifetime.
            launch {
                markerDao.getMarkersForTrek(trekId).collect { markers ->
                    _uiState.update { it.copy(customMarkers = markers) }
                }
            }
        }
    }

    fun onRouteLoaded() {
        _uiState.update { it.copy(routeLoaded = true) }
        checkForSavedSession()
    }

    private fun checkForSavedSession() {
        viewModelScope.launch {
            val session = withContext(Dispatchers.IO) { sessionDao.getSession(trekId) }
            if (session != null) {
                val ageHours = (System.currentTimeMillis() - session.updatedAt) / 3_600_000
                if (ageHours < 24) {
                    _uiState.update { it.copy(showResumeDialog = true, savedNavigationSession = session) }
                } else {
                    withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                }
            }
        }
    }

    // ─── Sensor (compass) ────────────────────────────────────────────────────

    fun startSensor() {
        if (sensorListener != null) return // already registered — avoid double registration
        val ctx: Context = getApplication()
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotMatrix, orientation)
                val bearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                _uiState.update { it.copy(sensorBearing = bearing) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        sensorListener = listener
    }

    fun stopSensor() {
        sensorListener?.let { sensorManager?.unregisterListener(it) }
        sensorListener = null
        sensorManager = null
    }

    // ─── GPS warm-up ───────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun warmUpGps() {
        viewModelScope.launch { locationRepository.warmUp() }
    }

    // ─── Locate me ─────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun locateMe(onLocation: (TrekLocation) -> Unit) {
        viewModelScope.launch {
            val quick = locationRepository.getLastKnown()
            if (quick != null) {
                _uiState.update { it.copy(lastKnownLocation = quick) }
                snapshotStore.update(quick)
                onLocation(quick)
            }

            val fresh = locationRepository.getCurrentLocation()
            when {
                fresh == null && quick == null -> {
                    _uiState.update {
                        it.copy(
                            wrongLocationMessage = "Unable to get location. Check GPS signal.",
                            showWrongLocationDialog = true
                        )
                    }
                }
                fresh != null -> {
                    _uiState.update { it.copy(lastKnownLocation = fresh) }
                    snapshotStore.update(fresh)
                    // Only re-animate the camera if the fresh fix meaningfully
                    // differs from the quick one, to avoid a double-jitter
                    // animation when both fixes land in effectively the same spot.
                    val shouldAnimate = quick == null || LocationTracker.distanceBetween(
                        quick.latitude, quick.longitude,
                        fresh.latitude, fresh.longitude
                    ) > 20f
                    if (shouldAnimate) onLocation(fresh)
                }
            }
        }
    }

    // ─── Navigation lifecycle ──────────────────────────────────────────────────

    fun startNavigation() {
        if (_uiState.value.isNavigating) return
        resetBearingBuffer()
        speedAdaptiveZoom.reset()
        navStartTimeMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(isNavigating = true, isAcquiringGps = true, cameraFollowMode = true, elapsedSeconds = 0L)
        }
        startTimer()
        startLocationTracking()
        startDeadReckoning()
    }

    fun stopNavigation() {
        navigationJob?.cancel()
        timerJob?.cancel()
        deadReckonJob?.cancel()
        navigationJob  = null
        timerJob       = null
        deadReckonJob  = null

        // Belt-and-braces alongside the composable's own onDispose: null the
        // dot-update lambda here too, so the dead-reckoning loop can never
        // fire into a destroyed map regardless of what triggered the stop.
        onDotUpdate = null

        previousNavState = null
        resetBearingBuffer()
        speedAdaptiveZoom.reset()

        _uiState.update {
            it.copy(
                isNavigating      = false,
                isAcquiringGps    = false,
                navigationState   = null,
                cameraFollowMode  = false,
                elapsedSeconds    = 0L,
                navigationHeading = 0f
            )
        }
    }

    fun resumeNavigation(session: TrekNavigationSession) {
        dismissResumeDialog()
        val gpxPoints = _uiState.value.gpxPoints
        if (gpxPoints.isEmpty()) {
            // Route hasn't loaded (or failed to parse) — discard the stale
            // session rather than resuming against an empty trail.
            viewModelScope.launch { withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) } }
            startNavigation()
            return
        }

        val elevationPoints = _uiState.value.elevationPoints
        val nearestPoint    = gpxPoints.getOrNull(session.nearestPointIndex)
        val distanceToTrail = nearestPoint?.let {
            LocationTracker.distanceBetween(
                session.lastLatitude, session.lastLongitude,
                it.latitude(), it.longitude()
            )
        } ?: 0f

        val total = if (totalTrailDistance > 0f)
            totalTrailDistance
        else
            TrailNavigator.calculateTotalDistance(gpxPoints)

        previousNavState = NavigationState(
            currentLocation   = TrekLocation(
                latitude  = session.lastLatitude,
                longitude = session.lastLongitude,
                accuracy  = session.lastAccuracy,
                speed     = 0f,
                bearing   = 0f
            ),
            nearestPointIndex = session.nearestPointIndex,
            distanceToTrail   = distanceToTrail,
            distanceCovered   = session.distanceCovered,
            distanceRemaining = (total - session.distanceCovered).coerceAtLeast(0f),
            progressPercent   = session.progressPercent,
            currentElevation  = TrailNavigator.elevationAtIndex(elevationPoints, session.nearestPointIndex),
            status            = NavigationStatus.ON_TRAIL
        )
        startNavigation()
    }

    fun startFresh() {
        dismissResumeDialog()
        viewModelScope.launch { withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) } }
    }

    // ─── Camera ────────────────────────────────────────────────────────────────

    fun enableCameraFollow()  { _uiState.update { it.copy(cameraFollowMode = true) } }
    fun disableCameraFollow() { _uiState.update { it.copy(cameraFollowMode = false) } }

    // ─── Dialogs ───────────────────────────────────────────────────────────────

    fun dismissWrongLocationDialog() =
        _uiState.update { it.copy(showWrongLocationDialog = false) }

    fun dismissWrongLocationAndStop() {
        dismissWrongLocationDialog()
        stopNavigation()
    }

    /**
     * One atomic state update covering dialog dismissal + state clearing, so
     * there's no intermediate frame where showCompletedDialog = false but
     * isNavigating is still true with a null navigationState (which used to
     * make NavigationBottomSheet flash with null content for a frame).
     */
    fun dismissCompletedDialog() {
        _uiState.update {
            it.copy(showCompletedDialog = false, navigationState = null, cameraFollowMode = false)
        }
        viewModelScope.launch { withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) } }
        stopNavigation()
    }

    private fun dismissResumeDialog() =
        _uiState.update { it.copy(showResumeDialog = false, savedNavigationSession = null) }

    // ─── Markers ───────────────────────────────────────────────────────────────

    fun insertMarker(marker: CustomMarker) {
        viewModelScope.launch { markerDao.insert(marker) }
    }

    fun deleteMarker(markerId: Long) {
        viewModelScope.launch { markerDao.deleteById(markerId) }
    }

    // ─── Internal — location tracking ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        navigationJob = viewModelScope.launch {
            LocationTracker.trackLocationAdaptive(getApplication())
                .catch { e -> android.util.Log.e("TrekMapVM", "Location error: ${e.message}") }
                .collect { location ->
                    _uiState.update { it.copy(isAcquiringGps = false) }

                    bearingBuf[bearingBufHead] = location.bearing
                    bearingBufHead = (bearingBufHead + 1) % bearingBuf.size
                    if (bearingBufSize < bearingBuf.size) bearingBufSize++
                    val smoothedBearing = bearingBufAverage()

                    val elapsedMs  = System.currentTimeMillis() - navStartTimeMs
                    val avgSpeedMs = previousNavState?.let { prev ->
                        if (elapsedMs > 30_000 && prev.distanceCovered > 50f)
                            prev.distanceCovered / (elapsedMs / 1000f)
                        else 0f
                    } ?: 0f

                    val currentUiState = _uiState.value
                    val state = TrailNavigator.calculateState(
                        location           = location,
                        gpxPoints          = currentUiState.gpxPoints,
                        previousState      = previousNavState,
                        elevationPoints    = currentUiState.elevationPoints,
                        trailheadName      = currentUiState.trek?.name ?: "the trailhead",
                        avgSpeedMs         = avgSpeedMs,
                        totalTrailDistance = totalTrailDistance
                    )

                    if (state.status == NavigationStatus.WRONG_LOCATION && previousNavState == null) {
                        _uiState.update {
                            it.copy(showWrongLocationDialog = true, wrongLocationMessage = state.warningMessage ?: "")
                        }
                    }

                    if (state.status == NavigationStatus.COMPLETED &&
                        previousNavState?.status != NavigationStatus.COMPLETED) {
                        _uiState.update { it.copy(showCompletedDialog = true) }
                        withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                    }

                    val newHeading = if (location.speed > 0.5f) smoothedBearing
                    else _uiState.value.navigationHeading

                    _uiState.update {
                        it.copy(
                            navigationState   = state,
                            navigationHeading = newHeading,
                            lastKnownLocation = location
                        )
                    }

                    previousNavState = state
                    throttledSaveSession(state)
                }
        }
    }

    // ─── Internal — timer ──────────────────────────────────────────────────────

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (isActive) {
                _uiState.update { it.copy(elapsedSeconds = (System.currentTimeMillis() - start) / 1000) }
                delay(1000)
            }
        }
    }

    // ─── Internal — dead reckoning ─────────────────────────────────────────────

    private fun startDeadReckoning() {
        deadReckonJob = viewModelScope.launch {
            while (isActive) {
                delay(16)
                val state = previousNavState ?: continue
                val loc   = state.currentLocation
                if (_uiState.value.isAcquiringGps) continue

                val elapsed = System.currentTimeMillis() - loc.timestamp

                // Skip stale fixes (e.g. a resumed session with an old
                // timestamp) — otherwise the dot would teleport to the old
                // position on every 16ms tick until the first fresh GPS fix arrives.
                if (elapsed > 5_000) continue

                if (loc.speed > 0.3f && elapsed in 16..3000) {
                    val estimated = LocationTracker.deadReckon(loc, elapsed)
                    onDotUpdate?.invoke(estimated.latitude, estimated.longitude, _uiState.value.navigationHeading)
                } else if (elapsed <= 16) {
                    onDotUpdate?.invoke(loc.latitude, loc.longitude, _uiState.value.navigationHeading)
                }
            }
        }
    }

    // ─── Internal — throttled session save ────────────────────────────────────

    private fun throttledSaveSession(state: NavigationState) {
        if (state.status == NavigationStatus.WRONG_LOCATION) return
        val now = System.currentTimeMillis()
        if (now - lastSessionSaveMs < 5_000) return
        lastSessionSaveMs = now
        viewModelScope.launch(Dispatchers.IO) {
            sessionDao.saveSession(
                TrekNavigationSession(
                    trekId            = trekId,
                    lastLatitude      = state.currentLocation.latitude,
                    lastLongitude     = state.currentLocation.longitude,
                    lastAccuracy      = state.currentLocation.accuracy,
                    distanceCovered   = state.distanceCovered,
                    progressPercent   = state.progressPercent,
                    nearestPointIndex = state.nearestPointIndex
                )
            )
        }
    }

    // ─── Bearing ring buffer ───────────────────────────────────────────────────

    private fun resetBearingBuffer() {
        bearingBuf.fill(0f)
        bearingBufHead = 0
        bearingBufSize = 0
    }

    /**
     * Circular mean of the last [bearingBufSize] bearings via sin/cos
     * accumulation. A plain arithmetic average is wrong across the 359°→0°
     * wraparound; accumulating components and recovering the angle with
     * atan2 handles the wrap correctly.
     *
     * CRASH FIX: the previous version used an inclusive range
     * `0..bearingBufSize`, which reads one index past the valid range once
     * the buffer fills — e.g. `bearingBuf[5]` on a size-5 array — throwing
     * ArrayIndexOutOfBoundsException a few seconds into every navigation
     * session. Changed to the exclusive range `0 until bearingBufSize`.
     * Note this averages by *occupancy count*, not temporal order — that's
     * fine, since a mean doesn't depend on the order of its inputs.
     */
    private fun bearingBufAverage(): Float {
        if (bearingBufSize == 0) return 0f
        var sinSum = 0.0
        var cosSum = 0.0
        for (i in 0 until bearingBufSize) {
            val rad = Math.toRadians(bearingBuf[i].toDouble())
            sinSum += Math.sin(rad)
            cosSum += Math.cos(rad)
        }
        return Math.toDegrees(Math.atan2(sinSum, cosSum)).toFloat()
            .let { if (it < 0f) it + 360f else it }
    }

    // ─── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        stopSensor()
        onDotUpdate = null
        navigationJob?.cancel()
        timerJob?.cancel()
        deadReckonJob?.cancel()
    }

    private companion object {
        const val BEARING_BUF_SIZE = 5
    }
}