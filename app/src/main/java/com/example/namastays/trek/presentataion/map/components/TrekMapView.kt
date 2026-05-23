package com.example.namastays.trek.presentataion.map.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@Composable
fun TrekMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 28.3967,   // Ghorepani
    initialLng: Double = 83.6912,
    initialZoom: Double = 11.0,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Keep reference to MapView for lifecycle handling
    val mapView = remember { MapView(context) }

    // Handle MapLibre lifecycle tied to Compose lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START  -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                Lifecycle.Event.ON_STOP   -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                // Set initial camera position over Poon Hill
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialLat, initialLng))
                    .zoom(initialZoom)
                    .build()

                // Load OpenStreetMap style (online for now)
                // We'll switch this to offline MBTiles in Phase 2
                map.setStyle(
                    Style.Builder().fromUri(
                        "https://demotiles.maplibre.org/style.json"
                    )
                ) { _ ->
                    onMapReady(map)
                }
            }
        }
    )
}