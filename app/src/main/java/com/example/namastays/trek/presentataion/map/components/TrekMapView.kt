package com.example.namastays.trek.presentataion.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.trek.util.buildOfflineStyle
import com.example.namastays.trek.util.buildTrailViewStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Composable wrapper around MapLibre's [MapView], handling its Android
 * lifecycle and (re)loading the appropriate style JSON.
 *
 * [buildOfflineStyle] / [buildTrailViewStyle] are `suspend` functions —
 * starting the tile server involves blocking SQLite file I/O, and reading
 * the style JSON asset is also blocking disk I/O. Both call sites here
 * (initial load in the [AndroidView] `update` block, and the style swap in
 * [LaunchedEffect]) launch a coroutine via [scope] and hop to
 * [Dispatchers.IO] for the style-building work, then back to the main
 * thread implicitly (MapLibre callbacks always land on main) to apply it.
 */
@Composable
fun TrekMapView(
    modifier: Modifier = Modifier,
    trekId: String,
    isTrailView: Boolean = false,
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleReady: (Style) -> Unit = {}
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope          = rememberCoroutineScope()

    // Guards whether getMapAsync has completed a first style load. We use a
    // separate boolean rather than checking mapView internals because
    // MapView doesn't expose an isReady() API.
    var mapInitialized by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).also { it.onCreate(null) }
    }

    // ── Lifecycle wiring ───────────────────────────────────────────────────────
    // mapView.onDestroy() is called ONLY inside the ON_DESTROY branch — never
    // also in onDispose — since MapLibre's GL thread crashes on a double-destroy.
    // MBTilesLoader.stopServer() likewise only runs on ON_DESTROY (true Activity
    // finish), not in onDispose, since onDispose fires on every recomposition
    // (rotation, back-stack push, etc.) and stopping the tile server there would
    // kill in-flight tile requests and leave the next composition with a dead server.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    scope.launch { MBTilesLoader.stopServer() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // Only remove the observer — mapView.onDestroy() must not be
            // called again here, it already happens in the ON_DESTROY branch.
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Style swap when isTrailView toggles ────────────────────────────────────
    // Only runs after first initialization to avoid racing with the factory
    // block below. Because MBTilesLoader.startServer() is now idempotent per
    // trekId, toggling isTrailView reuses the already-running tile server and
    // only swaps which style JSON is applied — it does not restart the server.
    LaunchedEffect(isTrailView) {
        if (!mapInitialized) return@LaunchedEffect
        mapView.getMapAsync { map ->
            if (map.style == null) return@getMapAsync // map not fully ready yet — skip
            scope.launch {
                val styleJson = withContext(Dispatchers.IO) {
                    if (isTrailView) buildTrailViewStyle(context, trekId)
                    else buildOfflineStyle(context, trekId)
                }
                styleJson?.let { json ->
                    map.setStyle(Style.Builder().fromJson(json)) { style ->
                        onStyleReady(style)
                    }
                }
            }
        }
    }

    // ── Map initialisation ────────────────────────────────────────────────────
    AndroidView(
        factory  = { mapView },
        modifier = modifier,
        update   = { view ->
            // Guards against re-running init logic across recompositions. The
            // secondary check inside getMapAsync (map.style != null) covers the
            // case where this MapView instance survived a recomposition without
            // being destroyed but mapInitialized was reset (e.g. after process
            // restore) — in that case we mark it initialized without re-adding
            // layers to an already-styled map.
            if (mapInitialized) return@AndroidView

            view.getMapAsync { map ->
                if (map.style != null) {
                    mapInitialized = true
                    return@getMapAsync
                }

                scope.launch {
                    val styleJson = withContext(Dispatchers.IO) {
                        buildOfflineStyle(view.context, trekId)
                    }
                    if (styleJson != null) {
                        map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                            onStyleReady(style)
                            onMapReady(map)
                            mapInitialized = true
                        }
                    } else {
                        // Fallback to online demo tiles — dev/debug only, used
                        // when the mbtiles pack for this trek isn't downloaded.
                        map.setStyle(
                            Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
                        ) { style ->
                            onStyleReady(style)
                            onMapReady(map)
                            mapInitialized = true
                        }
                    }
                }
            }
        }
    )
}

fun buildCameraUpdate(bounds: LatLngBounds): CameraUpdate =
    CameraUpdateFactory.newLatLngBounds(bounds, 80)