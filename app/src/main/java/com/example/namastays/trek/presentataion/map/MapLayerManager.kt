package com.example.namastays.trek.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.MarkerIconType
import com.example.namastays.trek.domain.WaypointType
import com.example.namastays.trek.util.GpxParser
import com.example.namastays.trek.util.TrekLocation
import com.example.namastays.trek.util.WaypointIcons
import com.example.namastays.trek.util.WaypointParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

class MapLayerManager(private val context: Context) {

    private var gpsDotInitialised = false
    private var routeInitialised = false

    // FIX: track whether the custom-marker layer has been created at least once
    // so refreshCustomMarkers can call setGeoJson on the existing source instead
    // of tearing down and rebuilding the layer on every marker add/delete.
    private var customMarkersLayerInitialised = false

    // FIX: throttle accuracy circle updates — expensive to recalculate at GPS
    // rate in browse mode; update at most once per second.
    private var lastAccuracyUpdateMs = 0L

    // ─── Route ────────────────────────────────────────────────────────────────

    suspend fun loadRoute(map: MapLibreMap, trekId: String) {
        try {
            val geoJson = withContext(Dispatchers.IO) { GpxParser.parseToGeoJson(context, trekId) }
            if (geoJson == null) {
                Log.e("MapLayerManager", "Failed to parse GPX for $trekId")
                return
            }
            val bounds = withContext(Dispatchers.IO) { GpxParser.getBounds(context, trekId) }

            withContext(Dispatchers.Main) {
                val style = map.style ?: return@withContext

                removeLayerSafe(style, "route-completed-layer")
                removeLayerSafe(style, "route-layer")
                removeLayerSafe(style, "route-outline-layer")
                removeSourceSafe(style, "route-completed-source")
                removeSourceSafe(style, "route-source")

                style.addSource(GeoJsonSource("route-source", geoJson))
                style.addSource(GeoJsonSource("route-completed-source", FeatureCollection.fromFeatures(emptyList())))

                addLayerSafe(style, "route-outline-layer") {
                    LineLayer("route-outline-layer", "route-source").apply {
                        setProperties(
                            PropertyFactory.lineColor("#FFFFFF"),
                            PropertyFactory.lineWidth(14f),
                            PropertyFactory.lineJoin("round"),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineOpacity(1f)
                        )
                    }
                }
                addLayerSafe(style, "route-layer") {
                    LineLayer("route-layer", "route-source").apply {
                        setProperties(
                            PropertyFactory.lineColor("#1A73E8"),
                            PropertyFactory.lineWidth(10f),
                            PropertyFactory.lineJoin("round"),
                            PropertyFactory.lineCap("round")
                        )
                    }
                }
                addLayerSafe(style, "route-completed-layer") {
                    LineLayer("route-completed-layer", "route-completed-source").apply {
                        setProperties(
                            PropertyFactory.lineColor("#9E9E9E"),
                            PropertyFactory.lineWidth(10f),
                            PropertyFactory.lineJoin("round"),
                            PropertyFactory.lineCap("round"),
                            PropertyFactory.lineOpacity(0.9f)
                        )
                    }
                }

                routeInitialised = true
                Log.d("MapLayerManager", "Route layers ready")

                bounds?.let {
                    map.animateCamera(
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
        } catch (e: Exception) {
            Log.e("MapLayerManager", "Route load error: ${e.message}")
        }
    }

    // NOTE: nearestPointIndex = 0 means the user hasn't moved yet, so completed
    // will have size < 2 and we skip updating. This is intentional — the
    // completed-route source stays empty until the user actually advances along
    // the trail. When status = OFF_TRAIL_WARNING the LaunchedEffect in the
    // composable skips this call, freezing the completed section at the last
    // on-trail index — also intentional.
    fun updateCompletedRoute(style: Style, gpxPoints: List<Point>, nearestPointIndex: Int) {
        if (gpxPoints.isEmpty()) return
        val completed = gpxPoints.take(nearestPointIndex + 1)
        if (completed.size < 2) return
        val line = org.maplibre.geojson.LineString.fromLngLats(completed)
        (style.getSource("route-completed-source") as? GeoJsonSource)
            ?.setGeoJson(FeatureCollection.fromFeature(Feature.fromGeometry(line)))
    }

    // ─── GPS dot ──────────────────────────────────────────────────────────────

    fun initGpsDotLayers(style: Style) {
        if (gpsDotInitialised) return
        registerGpsDotIcon(style)

        if (style.getSource("gps-accuracy-source") == null) {
            style.addSource(GeoJsonSource("gps-accuracy-source"))
        }
        addLayerSafe(style, "gps-accuracy-layer") {
            CircleLayer("gps-accuracy-layer", "gps-accuracy-source").apply {
                setProperties(
                    PropertyFactory.circleColor("#1565C0"),
                    PropertyFactory.circleOpacity(0.12f),
                    PropertyFactory.circleStrokeColor("#1565C0"),
                    PropertyFactory.circleStrokeOpacity(0.25f),
                    PropertyFactory.circleStrokeWidth(1f),
                    PropertyFactory.circleRadius(
                        Expression.interpolate(
                            Expression.linear(),
                            Expression.zoom(),
                            Expression.stop(10, Expression.product(Expression.get("accuracy"), Expression.literal(0.05f))),
                            Expression.stop(15, Expression.product(Expression.get("accuracy"), Expression.literal(0.5f))),
                            Expression.stop(18, Expression.product(Expression.get("accuracy"), Expression.literal(2.0f)))
                        )
                    )
                )
            }
        }

        if (style.getSource("gps-dot-source") == null) {
            style.addSource(GeoJsonSource("gps-dot-source"))
        }
        addLayerSafe(style, "gps-dot-layer") {
            SymbolLayer("gps-dot-layer", "gps-dot-source").apply {
                setProperties(
                    PropertyFactory.iconImage("gps-dot"),
                    PropertyFactory.iconSize(1.0f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconAnchor("center"),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment("map")
                )
            }
        }

        gpsDotInitialised = true
    }

    // FIX: throttle accuracy circle to at most once per second. The dot itself
    // is still updated on every location event (cheap — single GeoJSON point).
    fun updateGpsDot(style: Style, location: TrekLocation, headingOverride: Float? = null) {
        if (!gpsDotInitialised) return

        val bearing = headingOverride ?: location.bearing

        val dotFeature = Feature.fromGeometry(
            Point.fromLngLat(location.longitude, location.latitude)
        ).apply { addNumberProperty("bearing", bearing) }

        (style.getSource("gps-dot-source") as? GeoJsonSource)?.setGeoJson(dotFeature)

        val now = System.currentTimeMillis()
        if (now - lastAccuracyUpdateMs >= 1_000) {
            lastAccuracyUpdateMs = now
            val clampedAccuracy = minOf(location.accuracy, 50f)
            val accuracyFeature = Feature.fromGeometry(
                Point.fromLngLat(location.longitude, location.latitude)
            ).apply { addNumberProperty("accuracy", clampedAccuracy) }
            (style.getSource("gps-accuracy-source") as? GeoJsonSource)?.setGeoJson(accuracyFeature)
        }
    }

    // ─── Waypoints ────────────────────────────────────────────────────────────

    suspend fun loadWaypoints(map: MapLibreMap, trekId: String) {
        val waypoints = withContext(Dispatchers.IO) { WaypointParser.parse(context, trekId) }
        if (waypoints.isEmpty()) { Log.d("MapLayerManager", "No waypoints for $trekId"); return }

        withContext(Dispatchers.Main) {
            val mapStyle = map.style ?: return@withContext
            WaypointType.entries.forEach { type ->
                val iconId = WaypointIcons.getIconId(type)
                if (mapStyle.getImage(iconId) == null) {
                    mapStyle.addImage(iconId, WaypointIcons.getBitmap(context, type))
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
            removeLayerSafe(mapStyle, "waypoints-layer")
            removeSourceSafe(mapStyle, "waypoints-source")
            mapStyle.addSource(GeoJsonSource("waypoints-source", FeatureCollection.fromFeatures(features)))
            addLayerSafe(mapStyle, "waypoints-layer") {
                SymbolLayer("waypoints-layer", "waypoints-source").apply {
                    setProperties(
                        PropertyFactory.iconImage("{iconId}"),
                        PropertyFactory.iconSize(1.2f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                }
            }
            Log.d("MapLayerManager", "Added ${waypoints.size} waypoints")
        }
    }

    // ─── Custom markers ───────────────────────────────────────────────────────

    // FIX: on the first call (or after a style reload) we build the source and
    // layer from scratch. On subsequent calls we reuse the existing source and
    // just swap the GeoJSON — no layer teardown, no one-frame flash.
    fun refreshCustomMarkers(style: Style, markers: List<CustomMarker>) {
        if (!customMarkersLayerInitialised) {
            // First-time or post-style-reload: full initialisation
            removeLayerSafe(style, "custom-markers-layer")
            removeSourceSafe(style, "custom-markers-source")

            MarkerIconType.entries.forEach { type ->
                val iconId = "custom-${type.name.lowercase()}"
                if (style.getImage(iconId) == null) {
                    style.addImage(iconId, createCustomMarkerBitmap(type))
                }
            }

            val features = markers.map { it.toFeature() }
            style.addSource(GeoJsonSource("custom-markers-source", FeatureCollection.fromFeatures(features)))
            addLayerSafe(style, "custom-markers-layer") {
                SymbolLayer("custom-markers-layer", "custom-markers-source").apply {
                    setProperties(
                        PropertyFactory.iconImage("{iconId}"),
                        PropertyFactory.iconSize(1.2f),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true)
                    )
                }
            }
            customMarkersLayerInitialised = true
        } else {
            // Layer already exists — just update the data, no layer flash
            val features = markers.map { it.toFeature() }
            (style.getSource("custom-markers-source") as? GeoJsonSource)
                ?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun CustomMarker.toFeature(): Feature =
        Feature.fromGeometry(Point.fromLngLat(longitude, latitude)).apply {
            addStringProperty("id", this@toFeature.id.toString())
            addStringProperty("title", title)
            addStringProperty("note", note)
            addStringProperty("iconType", iconType)
            addStringProperty("iconId", "custom-${iconType.lowercase()}")
        }

    // ─── Icon bitmaps ─────────────────────────────────────────────────────────

    private fun registerGpsDotIcon(style: Style) {
        if (style.getImage("gps-dot") != null) return
        val size = 96
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0x554285F4.toInt(); it.style = Paint.Style.FILL }
        canvas.drawPath(android.graphics.Path().apply {
            moveTo(cx, cy - 10f); lineTo(cx - 18f, cy - 44f); lineTo(cx + 18f, cy - 44f); close()
        }, conePaint)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0x44000000.toInt(); it.style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy + 2f, 18f, shadowPaint)

        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0xFFFFFFFF.toInt(); it.style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, 17f, whitePaint)

        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0xFF4285F4.toInt(); it.style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, 13f, bluePaint)

        style.addImage("gps-dot", bitmap)
    }

    private fun createCustomMarkerBitmap(type: MarkerIconType): Bitmap {
        val size = 48
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val r = cx - 2f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = 0xFFD4A017.toInt(); it.style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, r, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = 0xFFFFFFFF.toInt(); it.style = Paint.Style.STROKE; it.strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, r, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = 0xFFFFFFFF.toInt()
            it.textSize = 20f
            it.textAlign = Paint.Align.CENTER
            it.isFakeBoldText = true
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(type.name.first().toString(), cx, textY, textPaint)

        return bitmap
    }

    // ─── Guard helpers ────────────────────────────────────────────────────────

    private fun addLayerSafe(style: Style, layerId: String, factory: () -> org.maplibre.android.style.layers.Layer) {
        if (style.getLayer(layerId) == null) {
            try { style.addLayer(factory()) }
            catch (e: Exception) { Log.e("MapLayerManager", "addLayer $layerId failed: ${e.message}") }
        }
    }

    private fun removeLayerSafe(style: Style, layerId: String) {
        try { style.removeLayer(layerId) } catch (_: Exception) {}
    }

    private fun removeSourceSafe(style: Style, sourceId: String) {
        try { style.removeSource(sourceId) } catch (_: Exception) {}
    }

    fun onStyleReloaded() {
        gpsDotInitialised = false
        routeInitialised = false
        // FIX: reset custom marker flag so refreshCustomMarkers rebuilds the
        // layer from scratch after a style reload (old layer is gone).
        customMarkersLayerInitialised = false
        lastAccuracyUpdateMs = 0L
    }

    // Called from dead-reckoning loop at ~60 fps — only updates the dot symbol,
    // not the accuracy circle (too expensive at that rate).
    fun updateGpsDotDirect(style: Style, lat: Double, lng: Double, bearing: Float) {
        if (!gpsDotInitialised) return
        val dotFeature = Feature.fromGeometry(
            Point.fromLngLat(lng, lat)
        ).apply { addNumberProperty("bearing", bearing) }
        (style.getSource("gps-dot-source") as? GeoJsonSource)?.setGeoJson(dotFeature)
    }
}