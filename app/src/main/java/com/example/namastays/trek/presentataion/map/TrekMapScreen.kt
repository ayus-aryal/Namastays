package com.example.namastays.trek.presentataion.map

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.Waypoint
import com.example.namastays.trek.util.GpxParser
import com.example.namastays.trek.util.LookAheadCamera
import com.example.namastays.trek.util.NavigationStatus
import com.example.namastays.trek.util.SpeedAdaptiveZoom
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
import com.example.namastays.trek.presentation.map.MapLayerManager
import com.example.namastays.trek.presentation.map.TrekMapViewModel
import com.example.namastays.trek.presentation.navigation.LocationPermissionHandler
import com.example.namastays.trek.util.TrekLocation
import com.example.namastays.trek.util.WaypointParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap


// TrekMapScreen.kt
object MapLibreInitializer {
    @Volatile private var initialized = false

    fun ensureInitialized(context: Context) {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    MapLibre.getInstance(context)
                    initialized = true
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekMapScreen(
    trekId: String,
    navController: NavController? = null,
    viewModel: TrekMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    val layerManager = remember { MapLayerManager(context) }

    LaunchedEffect(Unit) {
        MapLibreInitializer.ensureInitialized(context)
    }

    // ─── Map instance (ephemeral, not in VM) ──────────────────────────────────
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // ─── Ephemeral dialog / sheet state ───────────────────────────────────────
    var selectedWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var selectedCustomMarker by remember { mutableStateOf<CustomMarker?>(null) }
    var showAddMarker by remember { mutableStateOf(false) }
    var pendingMarkerLat by remember { mutableDoubleStateOf(0.0) }
    var pendingMarkerLng by remember { mutableDoubleStateOf(0.0) }
    var showPermissionHandler by remember { mutableStateOf(false) }
    var waypoints by remember { mutableStateOf<List<Waypoint>>(emptyList()) }

    // ─── FIX: DisposableEffect is the SINGLE owner of the sensor lifecycle.
    // Previously stopNavigation() called startSensor() AND this effect also
    // called it on the next recomposition, causing double-registration.
    // Now the VM's start/stopNavigation() do NOT touch the sensor at all.
    DisposableEffect(state.isNavigating) {
        if (!state.isNavigating) {
            viewModel.startSensor()
        } else {
            viewModel.stopSensor()
        }
        onDispose { viewModel.stopSensor() }
    }

    // ─── FIX: null onDotUpdate when the composable leaves composition.
    // Without this, the dead-reckoning coroutine kept firing the lambda into a
    // destroyed map instance whenever the user pressed Back during navigation.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onDotUpdate = null
        }
    }

    // ─── React to custom marker changes → refresh map layer ───────────────────
    LaunchedEffect(state.customMarkers) {
        mapInstance?.style?.let { style ->
            layerManager.refreshCustomMarkers(style, state.customMarkers)
        }
    }

    // ─── Update GPS dot in browse mode on every location change ───────────────
    LaunchedEffect(state.lastKnownLocation) {
        if (state.isNavigating) return@LaunchedEffect
        val loc = state.lastKnownLocation ?: return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        if (!state.routeLoaded) return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        layerManager.updateGpsDot(style, loc, null)
    }

    // ─── Completed route colouring ────────────────────────────────────────────
    LaunchedEffect(state.navigationState?.nearestPointIndex) {
        val idx = state.navigationState?.nearestPointIndex ?: return@LaunchedEffect
        val navStatus = state.navigationState?.status ?: return@LaunchedEffect
        if (navStatus != NavigationStatus.ON_TRAIL &&
            navStatus != NavigationStatus.OFF_TRAIL_WARNING) return@LaunchedEffect
        val map = mapInstance ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        layerManager.updateCompletedRoute(style, state.gpxPoints, idx)
    }

    // ─── FIX: BackHandler for WrongLocationDialog.
    // Previously pressing Back while the dialog was visible dismissed it but
    // left navigation running. Now Back properly stops navigation too.
    BackHandler(enabled = state.showWrongLocationDialog) {
        viewModel.dismissWrongLocationAndStop()
    }

    // ─────────────────────────────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        if (!state.tilesExist) {
            DownloadRequiredScreen(
                trek = state.trek,
                onDownloadClick = { navController?.popBackStack() }
            )
            return@Box
        }

        // ─── Map ──────────────────────────────────────────────────────────────
        TrekMapView(
            modifier = Modifier.fillMaxSize(),
            trekId = trekId,
            isTrailView = false,
            onStyleReady = { style ->
                layerManager.onStyleReloaded()
                layerManager.initGpsDotLayers(style)
            },
            onMapReady = { map ->
                map.uiSettings.isCompassEnabled = false
                mapInstance = map

                // ─── FIX: store listener references so they can be removed on
                // dispose. MapLibre accumulates listeners on every onMapReady
                // call if they are never removed, leaking memory and causing
                // duplicate callbacks after style reloads.
                val cameraMoveListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        viewModel.disableCameraFollow()
                    }
                }
                val mapClickListener = MapLibreMap.OnMapClickListener { latLng ->
                    val point = map.projection.toScreenLocation(latLng)
                    val customFeatures = map.queryRenderedFeatures(point, "custom-markers-layer")
                    if (customFeatures.isNotEmpty()) {
                        val id = customFeatures.first().getStringProperty("id")?.toLongOrNull()
                        selectedCustomMarker = state.customMarkers.find { it.id == id }
                        return@OnMapClickListener true
                    }
                    val wpFeatures = map.queryRenderedFeatures(point, "waypoints-layer")
                    if (wpFeatures.isNotEmpty()) {
                        val wpId = wpFeatures.first().getStringProperty("id")
                        selectedWaypoint = waypoints.find { it.id == wpId }
                        return@OnMapClickListener true
                    }
                    false
                }
                val mapLongClickListener = MapLibreMap.OnMapLongClickListener { latLng ->
                    pendingMarkerLat = latLng.latitude
                    pendingMarkerLng = latLng.longitude
                    showAddMarker = true
                    true
                }

                map.addOnCameraMoveStartedListener(cameraMoveListener)
                map.addOnMapClickListener(mapClickListener)
                map.addOnMapLongClickListener(mapLongClickListener)

                // Register the dot-update callback; nulled by stopNavigation()
                // or the DisposableEffect(Unit) onDispose, whichever fires first.
                viewModel.onDotUpdate = { lat, lng, bearing ->
                    map.style?.let { style ->
                        layerManager.updateGpsDotDirect(style, lat, lng, bearing)
                    }

                    if (viewModel.uiState.value.cameraFollowMode) {
                        val speed = viewModel.uiState.value.navigationState?.currentLocation?.speed ?: 0f
                        val isMoving = speed > 0.5f
                        val adaptiveZoom = viewModel.speedAdaptiveZoom.getSmoothedZoom(
                            speedMs = speed,
                            currentTimeMs = System.currentTimeMillis()
                        )
                        val (targetLat, targetLng) = if (isMoving) {
                            LookAheadCamera.calculateTarget(
                                position = TrekLocation(
                                    latitude = lat, longitude = lng,
                                    accuracy = 0f, speed = speed, bearing = bearing
                                ),
                                bearing = bearing,
                                zoom = adaptiveZoom,
                                offsetFraction = 0.35
                            )
                        } else Pair(lat, lng)

                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(targetLat, targetLng))
                                    .zoom(adaptiveZoom)
                                    .bearing(if (isMoving) bearing.toDouble() else map.cameraPosition.bearing)
                                    .tilt(if (isMoving) 45.0 else 0.0)
                                    .build()
                            ),
                            14
                        )
                    }
                }

                scope.launch {
                    map.style?.let { layerManager.initGpsDotLayers(it) }

                    layerManager.loadRoute(map, trekId)
                    layerManager.loadWaypoints(map, trekId)

                    waypoints = withContext(Dispatchers.IO) {
                        WaypointParser.parse(context, trekId)
                    }

                    map.style?.let {
                        layerManager.refreshCustomMarkers(it, state.customMarkers)
                    }

                    viewModel.onRouteLoaded()
                }

                // FIX: remove all listeners when the map instance is replaced
                // (style reload) or when the composable leaves composition,
                // preventing listener accumulation.
                // Note: this DisposableEffect key is the map instance itself so
                // it re-runs if onMapReady fires again with a new map object.
                // The actual removal is hoisted to the outer DisposableEffect
                // below so it captures the stored listener refs.
                //
                // We store them in the outer scope via a side-channel so the
                // outer DisposableEffect can reach them — see below.
                _storedCameraMoveListener = cameraMoveListener
                _storedMapClickListener = mapClickListener
                _storedMapLongClickListener = mapLongClickListener
                _storedMap = map
            }
        )

        // ─── Loading bar ───────────────────────────────────────────────────────
        if (!state.routeLoaded) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = Color(0xFF4CAF50),
                trackColor = Color.Transparent
            )
        }

        // ─── GPS acquiring banner ──────────────────────────────────────────────
        if (state.isNavigating && state.isAcquiringGps) {
            GpsAcquiringBanner(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )
        }

        // ─── Off-trail warning ─────────────────────────────────────────────────
        state.navigationState?.let { navState ->
            if (navState.warningMessage != null &&
                navState.status != NavigationStatus.WRONG_LOCATION) {
                NavigationWarningBanner(
                    state = navState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                )
            }
        }

        // ─── Compass button (top-right) ────────────────────────────────────────
        Surface(
            shape = CircleShape,
            color = Color(0xFF1A1A1A),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(48.dp)
                .clickable {
                    if (state.isNavigating) {
                        viewModel.enableCameraFollow()
                        state.navigationState?.currentLocation?.let { loc ->
                            mapInstance?.animateCamera(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(loc.latitude, loc.longitude))
                                        .zoom(16.0)
                                        .bearing(0.0)
                                        .tilt(0.0)
                                        .build()
                                )
                            )
                        }
                    } else {
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
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
                val currentBearing = mapInstance?.cameraPosition?.bearing?.toFloat() ?: 0f
                Icon(
                    imageVector = if (state.isNavigating && state.cameraFollowMode)
                        Icons.Filled.GpsFixed else Icons.Filled.Explore,
                    contentDescription = if (state.isNavigating) "Re-centre" else "Compass",
                    tint = when {
                        state.isNavigating && state.cameraFollowMode -> Color(0xFF4285F4)
                        currentBearing != 0f -> Color(0xFFE8622A)
                        else -> Color.White
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            if (!state.isNavigating) rotationZ = -state.sensorBearing
                        }
                )
            }
        }

        // ─── FIX: "Locate me" button — browse mode only.
        // Previously rendered during navigation too (no isNavigating guard),
        // with a comment saying it was browse-only. During nav the click handler
        // silently acted as re-centre while the label still said "Locate me".
        // Now hidden during navigation; the compass/GPS button above handles
        // re-centring in nav mode.
        if (!state.isNavigating) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1A1A1A),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            viewModel.locateMe { loc ->
                                mapInstance?.animateCamera(
                                    CameraUpdateFactory.newCameraPosition(
                                        CameraPosition.Builder()
                                            .target(LatLng(loc.latitude, loc.longitude))
                                            .zoom(
                                                mapInstance?.cameraPosition?.zoom
                                                    ?.coerceAtLeast(15.0) ?: 15.0
                                            )
                                            .build()
                                    )
                                )
                            }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Locate me",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    text = "Locate me",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // ─── OSM attribution ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp)
                .background(color = Color.Black.copy(alpha = 0.45f), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // ─── Navigation bottom sheet ───────────────────────────────────────────
        if (state.isNavigating && !state.isAcquiringGps) {
            NavigationBottomSheet(
                navigationState = state.navigationState,
                elapsedSeconds = state.elapsedSeconds,
                onStop = {
                    // onDotUpdate is nulled inside stopNavigation() itself now,
                    // so we don't need to null it here separately.
                    viewModel.stopNavigation()
                    mapInstance?.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .bearing(0.0)
                                .tilt(0.0)
                                .zoom(SpeedAdaptiveZoom.DEFAULT_ZOOM)
                                .build()
                        )
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ─── Browsing bottom sheet ─────────────────────────────────────────────
        if (!state.isNavigating) {
            BrowsingBottomSheet(
                trek = state.trek,
                elevationPoints = state.elevationPoints,
                isDownloaded = state.isDownloaded,
                onStart = { showPermissionHandler = true },
                onLocateMe = {
                    viewModel.locateMe { loc ->
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                    .target(LatLng(loc.latitude, loc.longitude))
                                    .zoom(
                                        mapInstance?.cameraPosition?.zoom
                                            ?.coerceAtLeast(15.0) ?: 15.0
                                    )
                                    .build()
                            )
                        )
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
                                CameraUpdateFactory.newLatLngBounds(
                                    LatLngBounds.Builder()
                                        .include(LatLng(it.maxLat, it.maxLng))
                                        .include(LatLng(it.minLat, it.minLng))
                                        .build(),
                                    64
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // ─── Permission handler ────────────────────────────────────────────────
        if (showPermissionHandler) {
            LocationPermissionHandler(
                onPermissionGranted = {
                    showPermissionHandler = false
                    viewModel.startNavigation()
                },
                onDismiss = { showPermissionHandler = false }
            )
        }

        // ─── Wrong location dialog ─────────────────────────────────────────────
        // FIX: BackHandler above intercepts system Back and calls
        // dismissWrongLocationAndStop(). The dialog's own dismiss callback
        // (tapping outside / system gesture) also goes to the same method,
        // so navigation is always stopped when this dialog is dismissed.
        if (state.showWrongLocationDialog) {
            WrongLocationDialog(
                message = state.wrongLocationMessage,
                onStartAnyway = { viewModel.dismissWrongLocationDialog() },
                onDismiss = { viewModel.dismissWrongLocationAndStop() }
            )
        }

        // ─── Trek completed dialog ─────────────────────────────────────────────
        if (state.showCompletedDialog) {
            TrekCompletedDialog(
                trekName = state.trek?.name ?: trekId,
                onDismiss = { viewModel.dismissCompletedDialog() }
            )
        }

        // ─── Resume navigation dialog ──────────────────────────────────────────
        if (state.showResumeDialog) {
            state.savedNavigationSession?.let { session ->
                ResumeNavigationDialog(
                    session = session,
                    onResume = { viewModel.resumeNavigation(session) },
                    onStartFresh = { viewModel.startFresh() }
                )
            }
        }

        // ─── Waypoint bottom sheet ─────────────────────────────────────────────
        selectedWaypoint?.let { wp ->
            WaypointBottomSheet(
                waypoint = wp,
                onDismiss = { selectedWaypoint = null }
            )
        }

        // ─── Edit marker bottom sheet ──────────────────────────────────────────
        selectedCustomMarker?.let { marker ->
            EditMarkerBottomSheet(
                marker = marker,
                onDelete = {
                    viewModel.deleteMarker(marker.id)
                    selectedCustomMarker = null
                },
                onDismiss = { selectedCustomMarker = null }
            )
        }

        // ─── Add marker bottom sheet ───────────────────────────────────────────
        if (showAddMarker) {
            AddMarkerBottomSheet(
                latitude = pendingMarkerLat,
                longitude = pendingMarkerLng,
                onSave = { title, note, iconType ->
                    viewModel.insertMarker(
                        CustomMarker(
                            trekId = trekId,
                            latitude = pendingMarkerLat,
                            longitude = pendingMarkerLng,
                            title = title,
                            note = note,
                            iconType = iconType.name
                        )
                    )
                    showAddMarker = false
                },
                onDismiss = { showAddMarker = false }
            )
        }
    }

    // ─── FIX: remove MapLibre listeners on dispose to prevent accumulation
    // across style reloads and recompositions. Using a separate DisposableEffect
    // outside the Box so it always runs even if the tilesExist early-return
    // fires and skips the map content entirely.
    DisposableEffect(Unit) {
        onDispose {
            val map = _storedMap ?: return@onDispose
            _storedCameraMoveListener?.let { map.removeOnCameraMoveStartedListener(it) }
            _storedMapClickListener?.let { map.removeOnMapClickListener(it) }
            _storedMapLongClickListener?.let { map.removeOnMapLongClickListener(it) }
            _storedMap = null
            _storedCameraMoveListener = null
            _storedMapClickListener = null
            _storedMapLongClickListener = null
        }
    }
}

// ─── Listener side-channel ────────────────────────────────────────────────────
// These are file-level vars (not in the composable) because we need to pass
// listener references from the onMapReady lambda (which runs inside the
// composition) to the outer DisposableEffect(Unit) onDispose block. Composable
// local `remember` state can't be read inside onDispose after the composable
// has left composition, so we park them here instead.
// They are only ever written/read on the main thread.
private var _storedMap: MapLibreMap? = null
private var _storedCameraMoveListener: MapLibreMap.OnCameraMoveStartedListener? = null
private var _storedMapClickListener: MapLibreMap.OnMapClickListener? = null
private var _storedMapLongClickListener: MapLibreMap.OnMapLongClickListener? = null