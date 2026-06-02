package com.example.namastays.trek.presentataion.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.MarkerIconType
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.domain.TrekNavigationSession
import com.example.namastays.trek.domain.Waypoint
import com.example.namastays.trek.domain.WaypointType
import com.example.namastays.trek.presentataion.map.components.AddMarkerBottomSheet
import com.example.namastays.trek.presentataion.map.components.BrowsingBottomSheet
import com.example.namastays.trek.presentataion.map.components.DownloadRequiredScreen
import com.example.namastays.trek.presentataion.map.components.EditMarkerBottomSheet
import com.example.namastays.trek.presentataion.map.components.GpsAcquiringBanner
import com.example.namastays.trek.presentataion.map.components.NavigationBottomSheet
import com.example.namastays.trek.presentataion.map.components.NavigationWarningBanner
import com.example.namastays.trek.presentataion.map.components.ResumeNavigationDialog
import com.example.namastays.trek.presentataion.map.components.TrekCompletedDialog
import com.example.namastays.trek.presentataion.map.components.TrekMapView
import com.example.namastays.trek.presentataion.map.components.WaypointBottomSheet
import com.example.namastays.trek.presentataion.map.components.WrongLocationDialog
import com.example.namastays.trek.presentataion.map.components.buildCameraUpdate
import com.example.namastays.trek.presentation.navigation.LocationPermissionHandler
import com.example.namastays.trek.util.ElevationPoint
import com.example.namastays.trek.util.GpxParser
import com.example.namastays.trek.util.LocationTracker
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.trek.util.NavigationState
import com.example.namastays.trek.util.NavigationStatus
import com.example.namastays.trek.util.TrailNavigator
import com.example.namastays.trek.util.TrekLocation
import com.example.namastays.trek.util.WaypointIcons
import com.example.namastays.trek.util.WaypointParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekMapScreen(
    trekId: String,
    navController: NavController? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { TrekDatabase.getInstance(context) }
    val markerDao = remember { db.markerDao() }
    val sessionDao = remember { db.navigationSessionDao() }
    val downloadDao = remember { db.downloadedTrekDao() }

    var tilesExist by remember { mutableStateOf(false) }
    var isTrailView by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<TrekLocation?>(null) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var savedNavigationSession by remember { mutableStateOf<TrekNavigationSession?>(null) }
    var isAcquiringGps by remember { mutableStateOf(false) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var routeLoaded by remember { mutableStateOf(false) }
    var selectedWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var showAddMarker by remember { mutableStateOf(false) }
    var pendingMarkerLat by remember { mutableStateOf(0.0) }
    var pendingMarkerLng by remember { mutableStateOf(0.0) }
    var customMarkers by remember { mutableStateOf<List<CustomMarker>>(emptyList()) }
    var selectedCustomMarker by remember { mutableStateOf<CustomMarker?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    var navigationState by remember { mutableStateOf<NavigationState?>(null) }
    var gpxPoints by remember { mutableStateOf<List<Point>>(emptyList()) }
    var showPermissionHandler by remember { mutableStateOf(false) }
    var showWrongLocationDialog by remember { mutableStateOf(false) }
    var wrongLocationMessage by remember { mutableStateOf("") }
    var showCompletedDialog by remember { mutableStateOf(false) }
    var cameraFollowMode by remember { mutableStateOf(false) }
    var previousNavState by remember { mutableStateOf<NavigationState?>(null) }
    var navigationStartTime by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var elevationPoints by remember { mutableStateOf<List<ElevationPoint>>(emptyList()) }
    var isDownloaded by remember { mutableStateOf(false) }

    // Sensor-based bearing for compass while browsing
    var sensorBearing by remember { mutableStateOf(0f) }
    // Bearing buffer for smoothing during navigation
    val recentBearings = remember { ArrayDeque<Float>(5) }

    val repository = remember { TrekRepository(db.trekCacheDao()) }
    var trek by remember { mutableStateOf<TrekItem?>(null) }

    LaunchedEffect(trekId) {
        trek = repository.getTrekById(trekId)
    }

    val trekName = trek?.name ?: trekId

    // ─── Magnetometer compass (browse mode only) ────────────────────────────

        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    sensorBearing = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }


    // ─── Verify file exists on disk ─────────────────────────────────────────
    LaunchedEffect(trekId) {
        val fileExists = MBTilesLoader.isDownloaded(context, trekId)
        val roomSaysDownloaded = withContext(Dispatchers.IO) { downloadDao.isDownloaded(trekId) }
        if (roomSaysDownloaded && !fileExists) {
            withContext(Dispatchers.IO) { downloadDao.deleteByTrekId(trekId) }
            tilesExist = false
        } else {
            tilesExist = fileExists
        }
        isDownloaded = fileExists
    }

    // ─── Load elevation profile ──────────────────────────────────────────────
    LaunchedEffect(trekId) {
        val points = withContext(Dispatchers.IO) { GpxParser.parseElevationProfile(context, trekId) }
        elevationPoints = points
    }

    // ─── Collect custom markers ──────────────────────────────────────────────
    LaunchedEffect(trekId) {
        markerDao.getMarkersForTrek(trekId).collect { markers ->
            customMarkers = markers
            mapInstance?.style?.let { style -> refreshCustomMarkers(style, markers) }
        }
    }

    // ─── Save navigation session ─────────────────────────────────────────────
    LaunchedEffect(navigationState) {
        val state = navigationState ?: return@LaunchedEffect
        if (!isNavigating) return@LaunchedEffect
        if (state.status == NavigationStatus.WRONG_LOCATION) return@LaunchedEffect
        withContext(Dispatchers.IO) {
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

    // ─── Elapsed time ────────────────────────────────────────────────────────
    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
            elapsedSeconds = 0L
            navigationStartTime = 0L
            return@LaunchedEffect
        }
        navigationStartTime = System.currentTimeMillis()
        while (isNavigating) {
            elapsedSeconds = (System.currentTimeMillis() - navigationStartTime) / 1000
            kotlinx.coroutines.delay(1000)
        }
    }

    // ─── Dead reckoning — smooth dot between GPS fixes
    LaunchedEffect(isNavigating) {
        if (!isNavigating) return@LaunchedEffect

        while (isNavigating) {
            val lastState = previousNavState
            val lastLoc = lastState?.currentLocation

            if (lastLoc != null && lastLoc.speed > 0.3f && !isAcquiringGps) {
                val elapsed = System.currentTimeMillis() - lastLoc.timestamp
                if (elapsed in 100..3000) {
                    val estimated = LocationTracker.deadReckon(lastLoc, elapsed)
                    // Snap dead-reckoned position to trail too
                    val snapped = previousNavState?.let { state ->
                        TrailNavigator.snapToTrail(estimated, gpxPoints, state.nearestPointIndex)
                    } ?: estimated
                    mapInstance?.let { map ->
                        if (!routeLoaded) return@let
                        updateGpsDot(map, snapped, sensorBearing)
                    }
                }
            }

            kotlinx.coroutines.delay(100)
        }
    }

    // ─── Location tracking ───────────────────────────────────────────────────
    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
            isAcquiringGps = false
            recentBearings.clear()
            return@LaunchedEffect
        }
        isAcquiringGps = true
        LocationTracker.trackLocationAdaptive(context)
            .catch { e -> Log.e("TrekMap", "Location error: ${e.message}") }
            .collect { location ->
                isAcquiringGps = false

                // Smooth bearing — rolling average of last 5 fixes
                if (recentBearings.size >= 5) recentBearings.removeFirst()
                recentBearings.addLast(location.bearing)
                val smoothedBearing = recentBearings.average().toFloat()

                val state = TrailNavigator.calculateState(
                    location      = location,
                    gpxPoints     = gpxPoints,
                    previousState = previousNavState
                )
                if (state.status == NavigationStatus.WRONG_LOCATION && previousNavState == null) {
                    wrongLocationMessage = state.warningMessage ?: ""
                    showWrongLocationDialog = true
                }
                if (state.status == NavigationStatus.COMPLETED &&
                    previousNavState?.status != NavigationStatus.COMPLETED) {
                    showCompletedDialog = true
                    withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                }
                navigationState = state
                if (state.status == NavigationStatus.ON_TRAIL ||
                    state.status == NavigationStatus.OFF_TRAIL_WARNING) {
                    mapInstance?.let { map ->
                        updateCompletedRoute(map, gpxPoints, state.nearestPointIndex)
                    }
                }
                previousNavState = state
                mapInstance?.let { map ->
                    if (!routeLoaded) return@let  // ← ADD


                    // Snap dot to trail when close enough — eliminates GPS drift jitter
                    val displayLocation = if (state.status == NavigationStatus.ON_TRAIL ||
                        state.status == NavigationStatus.OFF_TRAIL_WARNING) {
                        TrailNavigator.snapToTrail(location, gpxPoints, state.nearestPointIndex)
                    } else {
                        location  // WRONG_LOCATION or COMPLETED — show raw GPS
                    }


                    updateGpsDot(map, displayLocation, sensorBearing)
                    if (cameraFollowMode) {
                        val targetBearing = if (location.speed > 0.5f) location.bearing.toDouble()
                        else map.cameraPosition.bearing

                        // Look-ahead: offset camera behind the dot so route ahead is visible
                        val lookAheadDistance = 0.0003  // ~30m in degrees, adjust to taste
                        val bearingRad = Math.toRadians(targetBearing)
                        val offsetLat = displayLocation.latitude  - (lookAheadDistance * cos(bearingRad))
                        val offsetLng = displayLocation.longitude - (lookAheadDistance * sin(bearingRad))

                        map.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory
                                .newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(offsetLat, offsetLng))
                                        .zoom(17.5)              // closer zoom during nav
                                        .bearing(targetBearing)
                                        .tilt(45.0)             // stronger tilt = more Google Maps feel
                                        .build()
                                ),
                            600   // slightly faster = more responsive
                        )
                    }
                }
            }
    }

    // ────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        if (!tilesExist) {
            DownloadRequiredScreen(
                trek = trek,
                onDownloadClick = { navController?.popBackStack() }
            )
            return@Box
        }

        // Full screen map
        TrekMapView(
            modifier = Modifier.fillMaxSize(),
            trekId = trekId,
            isTrailView = isTrailView,
            onMapReady = { map ->
                map.uiSettings.isCompassEnabled = false
                mapInstance = map
                scope.launch {
                    loadRoute(context, map, trekId)
                    val points = withContext(Dispatchers.IO) {
                        GpxParser.parseToPoints(context, trekId)
                    }
                    gpxPoints = points
                    val waypoints = withContext(Dispatchers.IO) {
                        WaypointParser.parse(context, trekId)
                    }
                    loadWaypoints(context, map, trekId)
                    val existing = withContext(Dispatchers.IO) {
                        markerDao.getMarkersForTrekOnce(trekId)
                    }
                    map.style?.let { refreshCustomMarkers(it, existing) }
                    map.style?.let { registerGpsDotIcon(it) }
                    routeLoaded = true

                    val savedSession = withContext(Dispatchers.IO) {
                        sessionDao.getSession(trekId)
                    }
                    savedSession?.let { session ->
                        val ageHours = (System.currentTimeMillis() - session.updatedAt) / 3_600_000
                        if (ageHours < 24) {
                            showResumeDialog = true
                            savedNavigationSession = session
                        } else {
                            withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                        }
                    }

                    map.addOnMapClickListener { latLng ->
                        val point = map.projection.toScreenLocation(latLng)
                        val customFeatures = map.queryRenderedFeatures(point, "custom-markers-layer")
                        if (customFeatures.isNotEmpty()) {
                            val markerId = customFeatures.first().getStringProperty("id")?.toLongOrNull()
                            selectedCustomMarker = customMarkers.find { it.id == markerId }
                            return@addOnMapClickListener true
                        }
                        val waypointFeatures = map.queryRenderedFeatures(point, "waypoints-layer")
                        if (waypointFeatures.isNotEmpty()) {
                            val waypointId = waypointFeatures.first().getStringProperty("id")
                            selectedWaypoint = waypoints.find { it.id == waypointId }
                            return@addOnMapClickListener true
                        }
                        false
                    }

                    map.addOnMapLongClickListener { latLng ->
                        pendingMarkerLat = latLng.latitude
                        pendingMarkerLng = latLng.longitude
                        showAddMarker = true
                        true
                    }

                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                            cameraFollowMode = false
                        }
                    }
                }
            }
        )

        // ─── Loading indicator ───────────────────────────────────────────────
        if (!routeLoaded) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xFF4CAF50),
                trackColor = Color.Transparent
            )
        }

        // ─── GPS Acquiring banner ────────────────────────────────────────────
        if (isNavigating && isAcquiringGps) {
            GpsAcquiringBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ─── Off-trail / wrong direction warning ─────────────────────────────
        navigationState?.let { state ->
            if (state.warningMessage != null &&
                state.status != NavigationStatus.WRONG_LOCATION) {
                NavigationWarningBanner(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }

        // ─── Single compass / locate button (top-right) ──────────────────────
        // Browse mode : real magnetometer compass, tap → snap to north
        // Navigate mode: GPS locate icon, tap → re-centre + re-enable follow
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color(0xFF1A1A1A),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(48.dp)
                .clickable {
                    if (isNavigating) {
                        cameraFollowMode = true
                        navigationState?.currentLocation?.let { loc ->
                            mapInstance?.animateCamera(
                                org.maplibre.android.camera.CameraUpdateFactory
                                    .newCameraPosition(
                                        CameraPosition.Builder()
                                            .target(LatLng(loc.latitude, loc.longitude))
                                            .zoom(16.0)
                                            .bearing(0.0)  // reset to north
                                            .tilt(0.0)     // reset tilt
                                            .build()
                                    )
                            )
                        }
                    } else {
                        mapInstance?.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory
                                .newCameraPosition(
                                    CameraPosition.Builder()
                                        .bearing(0.0)
                                        .tilt(0.0)
                                        .build()
                                )
                        )
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Show actual compass bearing when rotated
                val currentBearing = mapInstance?.cameraPosition?.bearing?.toFloat() ?: 0f
                Icon(
                    if (isNavigating && cameraFollowMode) Icons.Filled.GpsFixed
                    else Icons.Filled.Explore,
                    contentDescription = "Compass/Locate",
                    tint = when {
                        isNavigating && cameraFollowMode -> Color(0xFF4285F4)
                        currentBearing != 0f             -> Color(0xFFE8622A)
                        else                             -> Color.White
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            if (!isNavigating) rotationZ = -sensorBearing
                        }
                )
            }
        }

        // ─── OSM attribution — always bottom-left ────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // ─── Navigation bottom sheet ─────────────────────────────────────────
        if (isNavigating && !isAcquiringGps) {
            NavigationBottomSheet(
                navigationState = navigationState,
                elapsedSeconds = elapsedSeconds,
                onStop = {
                    isNavigating = false
                    navigationState = null
                    previousNavState = null
                    cameraFollowMode = false
                    recentBearings.clear()
                    // Flatten camera back to 2D browse mode
                    mapInstance?.animateCamera(
                        org.maplibre.android.camera.CameraUpdateFactory
                            .newCameraPosition(
                                CameraPosition.Builder()
                                    .target(mapInstance?.cameraPosition?.target)
                                    .zoom(mapInstance?.cameraPosition?.zoom ?: 14.0)
                                    .tilt(0.0)
                                    .bearing(0.0)
                                    .build()
                            ), 600
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ─── Browsing bottom sheet ───────────────────────────────────────────
        if (!isNavigating) {
            BrowsingBottomSheet(
                trek = trek,
                elevationPoints = elevationPoints,
                isDownloaded = isDownloaded,
                onStart = { showPermissionHandler = true },
                onLocateMe = {
                    mapInstance?.let { map ->
                        locateMeOnce(context, map) { loc -> userLocation = loc }
                    }
                },
                onBack = { navController?.popBackStack() },
                onZoomToRoute = {
                    scope.launch {
                        val bounds = withContext(Dispatchers.IO) {
                            GpxParser.getBounds(context, trekId)
                        }
                        bounds?.let {
                            mapInstance?.animateCamera(
                                buildCameraUpdate(
                                    LatLngBounds.Builder()
                                        .include(LatLng(it.maxLat, it.maxLng))
                                        .include(LatLng(it.minLat, it.minLng))
                                        .build()
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ─── Dialogs ─────────────────────────────────────────────────────────
        if (showPermissionHandler) {
            LocationPermissionHandler(
                onPermissionGranted = {
                    showPermissionHandler = false
                    isNavigating = true
                    cameraFollowMode = true
                },
                onDismiss = { showPermissionHandler = false }
            )
        }

        if (showWrongLocationDialog) {
            WrongLocationDialog(
                message = wrongLocationMessage,
                onStartAnyway = { showWrongLocationDialog = false },
                onDismiss = {
                    showWrongLocationDialog = false
                    isNavigating = false
                }
            )
        }

        if (showCompletedDialog) {
            TrekCompletedDialog(
                trekName = trekName,
                onDismiss = {
                    showCompletedDialog = false
                    isNavigating = false
                    navigationState = null
                    previousNavState = null
                    cameraFollowMode = false
                    scope.launch {
                        withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                    }
                }
            )
        }

        if (showResumeDialog) {
            savedNavigationSession?.let { session ->
                ResumeNavigationDialog(
                    session = session,
                    onResume = {
                        showResumeDialog = false
                        previousNavState = NavigationState(
                            currentLocation = TrekLocation(
                                latitude  = session.lastLatitude,
                                longitude = session.lastLongitude,
                                accuracy  = session.lastAccuracy,
                                speed     = 0f,
                                bearing   = 0f
                            ),
                            nearestPointIndex = session.nearestPointIndex,
                            distanceToTrail   = 0f,
                            distanceCovered   = session.distanceCovered,
                            distanceRemaining = TrailNavigator.calculateTotalDistance(gpxPoints) - session.distanceCovered,
                            progressPercent   = session.progressPercent,
                            currentElevation  = 0,
                            status            = NavigationStatus.ON_TRAIL
                        )
                        isNavigating = true
                        cameraFollowMode = true
                    },
                    onStartFresh = {
                        showResumeDialog = false
                        savedNavigationSession = null
                        scope.launch {
                            withContext(Dispatchers.IO) { sessionDao.clearSession(trekId) }
                        }
                    }
                )
            }
        }

        selectedWaypoint?.let { waypoint ->
            WaypointBottomSheet(
                waypoint = waypoint,
                onDismiss = { selectedWaypoint = null }
            )
        }

        selectedCustomMarker?.let { marker ->
            EditMarkerBottomSheet(
                marker = marker,
                onDelete = {
                    scope.launch {
                        markerDao.deleteById(marker.id)
                        selectedCustomMarker = null
                    }
                },
                onDismiss = { selectedCustomMarker = null }
            )
        }

        if (showAddMarker) {
            AddMarkerBottomSheet(
                latitude = pendingMarkerLat,
                longitude = pendingMarkerLng,
                onSave = { title, note, iconType ->
                    scope.launch {
                        val marker = CustomMarker(
                            trekId    = trekId,
                            latitude  = pendingMarkerLat,
                            longitude = pendingMarkerLng,
                            title     = title,
                            note      = note,
                            iconType  = iconType.name
                        )
                        markerDao.insert(marker)
                        showAddMarker = false
                    }
                },
                onDismiss = { showAddMarker = false }
            )
        }
    }
}

// ─── Map helper functions ────────────────────────────────────────────────────

fun registerGpsDotIcon(mapStyle: Style) {
    val size = 96  // larger = more precise anchor
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f

    // Direction cone — points UP (north), MapLibre rotates it via iconRotate
    val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x554285F4.toInt()
        style = Paint.Style.FILL
    }
    val conePath = android.graphics.Path().apply {
        moveTo(cx, cy - 10f)           // tip at center-top of dot
        lineTo(cx - 18f, cy - 44f)     // bottom-left of cone
        lineTo(cx + 18f, cy - 44f)     // bottom-right of cone
        close()
    }
    canvas.drawPath(conePath, conePaint)

    // Drop shadow
    canvas.drawCircle(cx, cy + 2f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000.toInt()
        style = Paint.Style.FILL
    })
    // White border
    canvas.drawCircle(cx, cy, 17f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    })
    // Blue fill
    canvas.drawCircle(cx, cy, 13f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4285F4.toInt()
        style = Paint.Style.FILL
    })

    mapStyle.addImage("gps-dot", bitmap)
}

fun updateGpsDot(map: MapLibreMap, location: TrekLocation, headingOverride: Float? = null) {
    val style = map.style ?: return

    val clampedAccuracy = minOf(location.accuracy, 50f)  // cap at 50m visually


    val dotFeature = Feature.fromGeometry(
        Point.fromLngLat(location.longitude, location.latitude)
    ).apply {
        addNumberProperty("bearing", headingOverride ?: location.bearing)
    }

    val accuracyFeature = Feature.fromGeometry(
        Point.fromLngLat(location.longitude, location.latitude)
    ).apply {
        addNumberProperty("accuracy", clampedAccuracy)
    }

    val existingAccuracy = style.getSource("gps-accuracy-source")
    if (existingAccuracy == null) {
        style.addSource(GeoJsonSource("gps-accuracy-source", accuracyFeature))
        style.addLayer(
            org.maplibre.android.style.layers.CircleLayer("gps-accuracy-layer", "gps-accuracy-source").apply {
                setProperties(
                    PropertyFactory.circleColor("#1565C0"),
                    PropertyFactory.circleOpacity(0.12f),
                    PropertyFactory.circleStrokeColor("#1565C0"),
                    PropertyFactory.circleStrokeOpacity(0.25f),
                    PropertyFactory.circleStrokeWidth(1f),
                    PropertyFactory.circleRadius(
                        org.maplibre.android.style.expressions.Expression.interpolate(
                            org.maplibre.android.style.expressions.Expression.linear(),
                            org.maplibre.android.style.expressions.Expression.zoom(),
                            org.maplibre.android.style.expressions.Expression.stop(10,
                                org.maplibre.android.style.expressions.Expression.product(
                                    org.maplibre.android.style.expressions.Expression.get("accuracy"),
                                    org.maplibre.android.style.expressions.Expression.literal(0.05f)
                                )
                            ),
                            org.maplibre.android.style.expressions.Expression.stop(15,
                                org.maplibre.android.style.expressions.Expression.product(
                                    org.maplibre.android.style.expressions.Expression.get("accuracy"),
                                    org.maplibre.android.style.expressions.Expression.literal(0.5f)
                                )
                            ),
                            org.maplibre.android.style.expressions.Expression.stop(18,
                                org.maplibre.android.style.expressions.Expression.product(
                                    org.maplibre.android.style.expressions.Expression.get("accuracy"),
                                    org.maplibre.android.style.expressions.Expression.literal(2.0f)
                                )
                            )
                        )
                    )
                )
            }
        )
    } else {
        (existingAccuracy as? GeoJsonSource)?.setGeoJson(accuracyFeature)
    }

    val existingDot = style.getSource("gps-dot-source")
    if (existingDot == null) {
        style.addSource(GeoJsonSource("gps-dot-source", dotFeature))
        style.addLayer(
            SymbolLayer("gps-dot-layer", "gps-dot-source").apply {
                setProperties(
                    PropertyFactory.iconImage("gps-dot"),
                    PropertyFactory.iconSize(1.0f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconRotate(
                        org.maplibre.android.style.expressions.Expression.get("bearing")
                    ),
                    PropertyFactory.iconRotationAlignment("map")
                )
            }
        )
    } else {
        (existingDot as? GeoJsonSource)?.setGeoJson(dotFeature)
    }
}

suspend fun loadRoute(context: Context, map: MapLibreMap, trekId: String) {
    try {
        val geoJson = withContext(Dispatchers.IO) { GpxParser.parseToGeoJson(context, trekId) }
        if (geoJson == null) { Log.e("TrekMap", "Failed to parse GPX for $trekId"); return }
        val bounds = withContext(Dispatchers.IO) { GpxParser.getBounds(context, trekId) }
        withContext(Dispatchers.Main) {
            val style = map.style ?: return@withContext
            style.removeLayer("route-layer")
            style.removeLayer("route-outline-layer")
            style.removeLayer("route-completed-layer")
            style.removeSource("route-source")
            style.removeSource("route-completed-source")

            // Full route source (orange — ahead)
            style.addSource(GeoJsonSource("route-source", geoJson))

            // Completed route source (starts empty, fills grey as you walk)
            style.addSource(GeoJsonSource(
                "route-completed-source",
                FeatureCollection.fromFeatures(emptyList())
            ))

            // White outline behind route
            style.addLayer(LineLayer("route-outline-layer", "route-source").apply {
                setProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(14f),
                    PropertyFactory.lineJoin("round"),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineOpacity(1f)
                )
            })

            // Remaining route — orange
            style.addLayer(LineLayer("route-layer", "route-source").apply {
                setProperties(
                    PropertyFactory.lineColor("#1A73E8"),
                    PropertyFactory.lineWidth(10f),
                    PropertyFactory.lineJoin("round"),
                    PropertyFactory.lineCap("round")
                )
            })

            // Completed route — grey (visually "used up")
            style.addLayer(LineLayer("route-completed-layer", "route-completed-source").apply {
                setProperties(
                    PropertyFactory.lineColor("#9E9E9E"),
                    PropertyFactory.lineWidth(10f),
                    PropertyFactory.lineJoin("round"),
                    PropertyFactory.lineCap("round"),
                    PropertyFactory.lineOpacity(0.9f)
                )
            })

            Log.d("TrekMap", "Route layers added successfully")
            bounds?.let {
                val latLngBounds = LatLngBounds.Builder()
                    .include(LatLng(it.maxLat, it.maxLng))
                    .include(LatLng(it.minLat, it.minLng))
                    .build()
                map.animateCamera(buildCameraUpdate(latLngBounds))
            }
        }
    } catch (e: Exception) { Log.e("TrekMap", "Route load error: ${e.message}") }
}

suspend fun loadWaypoints(context: Context, map: MapLibreMap, trekId: String) {
    val waypoints = withContext(Dispatchers.IO) { WaypointParser.parse(context, trekId) }
    if (waypoints.isEmpty()) { Log.e("TrekMap", "No waypoints found for $trekId"); return }
    withContext(Dispatchers.Main) {
        val style = map.style ?: return@withContext
        WaypointType.values().forEach { type ->
            val iconId = WaypointIcons.getIconId(type)
            if (style.getImage(iconId) == null) {
                style.addImage(iconId, WaypointIcons.getBitmap(context, type))
            }
        }
        val features = waypoints.map { wp ->
            Feature.fromGeometry(Point.fromLngLat(wp.lng, wp.lat)).apply {
                addStringProperty("id", wp.id)
                addStringProperty("name", wp.name)
                addStringProperty("type", wp.type.name.lowercase())
                addNumberProperty("elevation", wp.elevation)
                addStringProperty("description", wp.description)
                addStringProperty("iconId", WaypointIcons.getIconId(wp.type))
            }
        }
        style.removeLayer("waypoints-layer")
        style.removeSource("waypoints-source")
        style.addSource(GeoJsonSource("waypoints-source", FeatureCollection.fromFeatures(features)))
        style.addLayer(SymbolLayer("waypoints-layer", "waypoints-source").apply {
            setProperties(
                PropertyFactory.iconImage("{iconId}"),
                PropertyFactory.iconSize(1.2f),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        })
        Log.d("TrekMap", "Added ${waypoints.size} waypoints to map")
    }
}

fun updateCompletedRoute(
    map: MapLibreMap,
    gpxPoints: List<Point>,
    nearestPointIndex: Int
) {
    val style = map.style ?: return
    if (gpxPoints.isEmpty()) return
    val completedPoints = gpxPoints.take(nearestPointIndex + 1)
    if (completedPoints.size < 2) return
    val completedLine = org.maplibre.geojson.LineString.fromLngLats(completedPoints)
    val source = style.getSource("route-completed-source")
    (source as? GeoJsonSource)?.setGeoJson(
        FeatureCollection.fromFeature(Feature.fromGeometry(completedLine))
    )
}

fun refreshCustomMarkers(mapStyle: Style, markers: List<CustomMarker>) {
    mapStyle.removeLayer("custom-markers-layer")
    mapStyle.removeSource("custom-markers-source")
    if (markers.isEmpty()) return
    MarkerIconType.values().forEach { type ->
        val iconId = "custom-${type.name.lowercase()}"
        if (mapStyle.getImage(iconId) == null) {
            mapStyle.addImage(iconId, createCustomMarkerBitmap(type))
        }
    }
    val features = markers.map { marker ->
        Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).apply {
            addStringProperty("id", marker.id.toString())
            addStringProperty("title", marker.title)
            addStringProperty("note", marker.note)
            addStringProperty("iconType", marker.iconType)
            addStringProperty("iconId", "custom-${marker.iconType.lowercase()}")
        }
    }
    mapStyle.addSource(GeoJsonSource("custom-markers-source", FeatureCollection.fromFeatures(features)))
    mapStyle.addLayer(SymbolLayer("custom-markers-layer", "custom-markers-source").apply {
        setProperties(
            PropertyFactory.iconImage("{iconId}"),
            PropertyFactory.iconSize(1.2f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )
    })
}

fun createCustomMarkerBitmap(type: MarkerIconType): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = size / 2f - 2f
    canvas.drawCircle(size / 2f, size / 2f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD4A017.toInt()
        style = Paint.Style.FILL
    })
    canvas.drawCircle(size / 2f, size / 2f, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    })
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 20f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val yPos = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(type.name.first().toString(), size / 2f, yPos, textPaint)
    return bitmap
}

@SuppressLint("MissingPermission")
fun locateMeOnce(
    context: Context,
    map: MapLibreMap,
    onLocation: (TrekLocation) -> Unit
) {
    val client = com.google.android.gms.location.LocationServices
        .getFusedLocationProviderClient(context)
    client.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            val trekLoc = TrekLocation(
                latitude  = location.latitude,
                longitude = location.longitude,
                accuracy  = location.accuracy,
                speed     = 0f,
                bearing   = 0f
            )
            onLocation(trekLoc)
            updateGpsDot(map, trekLoc)
            map.animateCamera(
                org.maplibre.android.camera.CameraUpdateFactory
                    .newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(location.latitude, location.longitude))
                            .zoom(15.0)
                            .build()
                    )
            )
        }
    }
}