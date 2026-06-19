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
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.domain.TrekNavigationSession
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

data class TrekMapUiState(
    val tilesExist: Boolean = false,
    val isDownloaded: Boolean = false,
    val routeLoaded: Boolean = false,

    val trek: TrekItem? = null,
    val elevationPoints: List<ElevationPoint> = emptyList(),
    val gpxPoints: List<Point> = emptyList(),

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
        cacheDao      = db.trekCacheDao(),
        itineraryDao  = db.trekItineraryDao(),
        highlightDao  = db.trekHighlightDao(),
        downloadedDao = db.downloadedTrekDao()
    )
    private val locationRepository = LocationRepository(application)

    // FIX: BearingSmoother removed — bearing smoothing is now done entirely by
    // the zero-allocation FloatArray ring buffer in startLocationTracking().
    // BearingSmoother was still instantiated and exposed but never called.
    val speedAdaptiveZoom = SpeedAdaptiveZoom()
    val snapshotStore     = LocationSnapshotStore()

    private val _uiState = MutableStateFlow(TrekMapUiState())
    val uiState: StateFlow<TrekMapUiState> = _uiState.asStateFlow()

    // FIX: total trail distance cached after route loads — passed into
    // TrailNavigator.calculateState() so it never re-sums all N GPX points
    // at 1 Hz during navigation (was O(N) per GPS fix for a constant value).
    private var totalTrailDistance = 0f

    private var previousNavState: NavigationState? = null
    private var navStartTimeMs = 0L

    // Zero-allocation bearing ring buffer
    private val bearingBuf     = FloatArray(5)
    private var bearingBufHead = 0
    private var bearingBufSize = 0

    // Sensor
    private var sensorManager: SensorManager? = null
    private var sensorListener: SensorEventListener? = null

    // Jobs
    private var navigationJob: Job? = null
    private var timerJob: Job? = null
    private var deadReckonJob: Job? = null

    private var lastSessionSaveMs = 0L

    // Set by the composable inside onMapReady; nulled by stopNavigation() and
    // the composable's DisposableEffect(Unit) onDispose — whichever fires first.
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

            // Tile / download status
            val fileExists        = MBTilesLoader.isDownloaded(getApplication(), trekId)
            val roomSaysDownloaded = withContext(Dispatchers.IO) { downloadDao.isDownloaded(trekId) }
            if (roomSaysDownloaded && !fileExists) {
                withContext(Dispatchers.IO) { downloadDao.deleteByTrekId(trekId) }
                _uiState.update { it.copy(tilesExist = false, isDownloaded = false) }
            } else {
                _uiState.update { it.copy(tilesExist = fileExists, isDownloaded = fileExists) }
            }

            // FIX: parse GPX file ONCE via parseFull() instead of calling
            // parseToGeoJson / parseElevationProfile / getBounds / parseToPoints
            // separately (was 4 full file reads + 4 SAX parse passes on startup).
            val parseResult = withContext(Dispatchers.IO) {
                GpxParser.parseFull(getApplication(), trekId)
            }

            // Cache total distance — used by TrailNavigator on every GPS fix.
            totalTrailDistance = TrailNavigator.calculateTotalDistance(parseResult.points)

            _uiState.update {
                it.copy(
                    gpxPoints       = parseResult.points,
                    elevationPoints = parseResult.elevationProfile
                )
            }

            // Custom markers — live Flow
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

    // ─── Sensor ────────────────────────────────────────────────────────────────
    // The DisposableEffect in TrekMapScreen is the SINGLE owner of the sensor
    // lifecycle. startNavigation() / stopNavigation() do NOT touch the sensor.

    fun startSensor() {
        if (sensorListener != null) return
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
    // FIX: only re-animates camera on the fresh fix if it differs from the
    // stale one by > 20 m, preventing a double-jitter animation.

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

        // Null the dot-update lambda so the dead-reckoning coroutine cannot
        // fire into a destroyed map, regardless of what triggered the stop.
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

    // FIX: guard against empty gpxPoints (parse failure or route not yet loaded).
    fun resumeNavigation(session: TrekNavigationSession) {
        dismissResumeDialog()
        val gpxPoints = _uiState.value.gpxPoints
        if (gpxPoints.isEmpty()) {
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

        // FIX: use cached totalTrailDistance instead of recomputing.
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

    // FIX: single _uiState.update to avoid intermediate state where
    // showCompletedDialog = false but isNavigating = true and navigationState = null,
    // which caused NavigationBottomSheet to briefly flash with null state.
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

                    // Zero-allocation ring buffer bearing average
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
                        // FIX: pass cached total so TrailNavigator never re-sums all points.
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

                // FIX: skip if timestamp is stale (resumed session with old timestamp).
                // Without this the dot would teleport to the old position on every
                // 16 ms tick until the first fresh GPS fix arrived.
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

    private fun bearingBufAverage(): Float {
        if (bearingBufSize == 0) return 0f
        var sinSum = 0.0
        var cosSum = 0.0
        for (i in 0..bearingBufSize) {
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
}