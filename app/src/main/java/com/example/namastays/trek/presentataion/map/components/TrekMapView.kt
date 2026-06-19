package com.example.namastays.trek.presentataion.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun TrekMapView(
    modifier: Modifier = Modifier,
    trekId: String,
    isTrailView: Boolean = false,
    onMapReady: (MapLibreMap) -> Unit = {},
    onStyleReady: (Style) -> Unit = {}
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Guards whether getMapAsync has been called and a map instance exists.
    // We use a separate boolean rather than checking mapView internals because
    // MapView doesn't expose an isReady() API.
    var mapInitialized by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).also { it.onCreate(null) }
    }

    // ── Lifecycle wiring ───────────────────────────────────────────────────────
    // FIX 1: mapView.onDestroy() was called BOTH inside the ON_DESTROY branch
    //         AND in onDispose — causing a double-destroy crash on MapLibre's
    //         GL thread. onDestroy() is now only called inside the observer.
    // FIX 2: MBTilesLoader.stopServer() was in onDispose, which fires on every
    //         recomposition (rotation, back-stack push, etc.) — killing in-flight
    //         tile requests and leaving the next composition with a dead server.
    //         It now lives inside the ON_DESTROY branch so it only stops when
    //         the Activity is truly finishing.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    // FIX 1: single onDestroy call, here only.
                    mapView.onDestroy()
                    // FIX 2: tile server stops only on true activity destroy.
                    MBTilesLoader.stopServer()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            // Only remove the observer — DO NOT call mapView.onDestroy() here.
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ── Style swap when isTrailView toggles ────────────────────────────────────
    // Only runs after first initialization to avoid racing with the factory block.
    LaunchedEffect(isTrailView) {
        if (!mapInitialized) return@LaunchedEffect
        mapView.getMapAsync { map ->
            // FIX: guard against swapping style when a map isn't fully ready.
            if (map.style == null) return@getMapAsync
            val styleJson = if (isTrailView)
                buildTrailViewStyle(context, trekId)
            else
                buildOfflineStyle(context, trekId)
            styleJson?.let { json ->
                map.setStyle(Style.Builder().fromJson(json)) { style ->
                    onStyleReady(style)
                }
            }
        }
    }

    // ── Map initialisation ────────────────────────────────────────────────────
    AndroidView(
        factory  = { mapView },
        modifier = modifier,
        update   = { view ->
            // FIX: was guarding via mapInitialized only, but remember resets
            // when the composable leaves and re-enters composition while the
            // MapView (also in remember) is the same instance and already has
            // a map+style loaded. The second guard (map.style != null) prevents
            // re-calling onStyleReady/onMapReady on a map that never unloaded.
            if (mapInitialized) return@AndroidView

            view.getMapAsync { map ->
                // Double-check: if style already exists this MapView survived
                // a recomposition without destruction — don't re-init layers.
                if (map.style != null) {
                    mapInitialized = true
                    return@getMapAsync
                }

                val styleJson = buildOfflineStyle(view.context, trekId)
                if (styleJson != null) {
                    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        onStyleReady(style)
                        onMapReady(map)
                        mapInitialized = true
                    }
                } else {
                    // Fallback to online demo tiles — dev/debug only.
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
    )
}

fun buildCameraUpdate(bounds: LatLngBounds): CameraUpdate =
    CameraUpdateFactory.newLatLngBounds(bounds, 80)