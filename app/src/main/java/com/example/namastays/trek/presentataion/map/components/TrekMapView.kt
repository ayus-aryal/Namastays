package com.example.namastays.trek.presentataion.map.components

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.namastays.trek.presentataion.map.registerGpsDotIcon
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.trek.util.buildOfflineStyle
import com.example.namastays.trek.util.buildTrailViewStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun TrekMapView(
    modifier: Modifier = Modifier,
    trekId: String = "ghorepani-poonhill",
    initialLat: Double = 28.3967,
    initialLng: Double = 83.6912,
    initialZoom: Double = 11.0,
    isTrailView: Boolean = false,
    onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            // Reduce tile cache for low-end devices
            val activityManager = context.getSystemService(
                Context.ACTIVITY_SERVICE
            ) as android.app.ActivityManager
            val memoryClass = activityManager.memoryClass

            // Low memory device (< 128MB heap) → reduce cache aggressively
            val cacheSize = when {
                memoryClass < 128 -> 20L   // 20MB cache
                memoryClass < 256 -> 50L   // 50MB cache
                else              -> 100L  // 100MB cache
            }

            android.util.Log.d(
                "TrekMap",
                "Device memory class: ${memoryClass}MB, tile cache: ${cacheSize}MB"
            )
        }
    }

    // Switch style when isTrailView changes
    LaunchedEffect(isTrailView) {
        mapView.getMapAsync { map ->
            val styleJson = if (isTrailView) {
                buildTrailViewStyle(context, trekId)
            } else {
                buildOfflineStyle(context, trekId)
            }
            styleJson?.let { json ->
                map.setStyle(Style.Builder().fromJson(json)) { style ->
                    // Re-register GPS dot icon after style switch
                    registerGpsDotIcon(style)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
            // Stop tile server when map is destroyed
            MBTilesLoader.stopServer()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(initialLat, initialLng))
                    .zoom(initialZoom)
                    .build()

                val styleJson = buildOfflineStyle(view.context, trekId)

                if (styleJson != null) {
                    map.setStyle(Style.Builder().fromJson(styleJson)) {
                        onMapReady(map)
                    }
                } else {
                    map.setStyle(
                        Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")
                    ) {
                        onMapReady(map)
                    }
                }
            }
        }
    )
}

fun buildCameraUpdate(bounds: LatLngBounds): CameraUpdate {
    return CameraUpdateFactory.newLatLngBounds(bounds, 80)
}