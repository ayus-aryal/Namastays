package com.example.namastays.trek.util

import android.content.Context
import android.util.Log
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory

private const val TAG = "GpxParser"
private const val MAX_ELEVATION_POINTS = 200

// ─── Result types ──────────────────────────────────────────────────────────────

data class ElevationPoint(
    val distanceKm: Float,
    val elevationM: Double
)

data class RouteBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

/**
 * Single result returned by [GpxParser.parseFull].
 * Holds everything the ViewModel needs from one file read.
 */
data class GpxParseResult(
    val points: List<Point>,
    val elevationProfile: List<ElevationPoint>,
    val bounds: RouteBounds?
)

// ─── Parser ────────────────────────────────────────────────────────────────────

object GpxParser {

    // ── Primary API ────────────────────────────────────────────────────────────

    /**
     * Parse the GPX file exactly ONCE and return all derived data.
     *
     * Previously the ViewModel called parseToGeoJson, parseElevationProfile,
     * getBounds, and parseToPoints separately — four full file reads on startup.
     * Call this instead and destructure the result.
     */
    fun parseFull(context: Context, trekId: String): GpxParseResult {
        val gpxFile = File(context.filesDir, "$trekId.gpx")
        if (!gpxFile.exists()) {
            Log.e(TAG, "GPX file not found for trek '$trekId': ${gpxFile.absolutePath}")
            return GpxParseResult(emptyList(), emptyList(), null)
        }

        return try {
            // Collect both points and elevations in a single SAX pass.
            val rawPoints = mutableListOf<Point>()
            val rawElevations = mutableListOf<Double?>()  // null = no <ele> for that trkpt

            val factory = SAXParserFactory.newInstance()
            val parser  = factory.newSAXParser()
            parser.parse(gpxFile, FullGpxHandler(rawPoints, rawElevations))

            if (rawPoints.isEmpty()) {
                Log.e(TAG, "No track points found in GPX for trek '$trekId'")
                return GpxParseResult(emptyList(), emptyList(), null)
            }

            Log.d(TAG, "Parsed ${rawPoints.size} track points for trek '$trekId'")

            // ── Bounds (derived from point list, no extra I/O) ──────────────
            val bounds = RouteBounds(
                minLat = rawPoints.minOf { it.latitude() },
                maxLat = rawPoints.maxOf { it.latitude() },
                minLng = rawPoints.minOf { it.longitude() },
                maxLng = rawPoints.maxOf { it.longitude() }
            )

            // ── Elevation profile ───────────────────────────────────────────
            val elevationProfile = buildElevationProfile(rawPoints, rawElevations)

            GpxParseResult(
                points           = rawPoints,
                elevationProfile = elevationProfile,
                bounds           = bounds
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error for trek '$trekId': ${e.message}")
            GpxParseResult(emptyList(), emptyList(), null)
        }
    }

    // ── Legacy single-purpose wrappers ─────────────────────────────────────────
    // Kept for call-site compatibility. Each delegates to parseFull() — so
    // calling all four still results in four file reads. Migrate call sites to
    // parseFull() to get the single-read benefit.

    fun parseToGeoJson(context: Context, trekId: String): FeatureCollection? {
        val points = parseFull(context, trekId).points
        if (points.isEmpty()) return null
        val feature = Feature.fromGeometry(LineString.fromLngLats(points))
        return FeatureCollection.fromFeature(feature)
    }

    fun parseElevationProfile(context: Context, trekId: String): List<ElevationPoint> =
        parseFull(context, trekId).elevationProfile

    fun getBounds(context: Context, trekId: String): RouteBounds? =
        parseFull(context, trekId).bounds

    fun parseToPoints(context: Context, trekId: String): List<Point> =
        parseFull(context, trekId).points

    // ── Elevation profile builder ───────────────────────────────────────────────

    private fun buildElevationProfile(
        points: List<Point>,
        elevations: List<Double?>
    ): List<ElevationPoint> {
        // Need at least one point with a valid elevation to build a profile.
        if (points.isEmpty() || elevations.all { it == null }) return emptyList()

        var totalDistance = 0f
        val result = mutableListOf<ElevationPoint>()

        // Use the first available elevation as the starting point.
        result.add(ElevationPoint(0f, elevations.firstOrNull { it != null } ?: 0.0))

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += LocationTracker.distanceBetween(
                prev.latitude(), prev.longitude(),
                curr.latitude(), curr.longitude()
            )
            val ele = elevations.getOrNull(i) ?: result.last().elevationM
            result.add(ElevationPoint(totalDistance / 1000f, ele))
        }

        // ── Downsample to MAX_ELEVATION_POINTS ─────────────────────────────
        // FIX: was using integer division result.size / 200, which meant
        // 201-399 points got step=1 (no reduction at all). Now uses ceiling
        // division so any size > 200 actually reduces.
        return if (result.size > MAX_ELEVATION_POINTS) {
            val step = (result.size + MAX_ELEVATION_POINTS - 1) / MAX_ELEVATION_POINTS
            result.filterIndexed { index, _ -> index % step == 0 }
        } else {
            result
        }
    }
}

// ─── SAX handlers ──────────────────────────────────────────────────────────────

/**
 * Single-pass handler that collects both track points and their elevations.
 * Elevation list is parallel to points list — index N in elevations corresponds
 * to index N in points. Null means the <trkpt> had no <ele> child.
 */
private class FullGpxHandler(
    private val points: MutableList<Point>,
    private val elevations: MutableList<Double?>
) : DefaultHandler() {

    private var pendingPoint: Point? = null
    private var inEle = false
    private val eleBuffer = StringBuilder()

    override fun startElement(
        uri: String?, localName: String?, qName: String?, attributes: Attributes?
    ) {
        when (qName) {
            "trkpt" -> {
                val lat = attributes?.getValue("lat")?.toDoubleOrNull()
                val lng = attributes?.getValue("lon")?.toDoubleOrNull()
                if (lat != null && lng != null) {
                    pendingPoint = Point.fromLngLat(lng, lat)
                }
            }
            "ele" -> {
                inEle = true
                eleBuffer.clear()
            }
        }
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (inEle && ch != null) eleBuffer.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (qName) {
            "ele" -> {
                inEle = false
                // Store elevation against the pending point; will be committed in trkpt end.
                // Note: <ele> appears as a child of <trkpt>, so we just capture it here
                // and commit with the point when </trkpt> fires.
                // (Some GPX files put </ele> before </trkpt>)
            }
            "trkpt" -> {
                val pt = pendingPoint ?: return
                points.add(pt)
                // Commit the elevation captured during this trkpt block (may be null).
                val ele = if (eleBuffer.isNotEmpty())
                    eleBuffer.toString().trim().toDoubleOrNull()
                else null
                elevations.add(ele)
                pendingPoint = null
                eleBuffer.clear()
                inEle = false
            }
        }
    }
}

/**
 * Lightweight handler that only extracts track point coordinates.
 * Used by MapLayerManager.loadRoute (needs GeoJSON) and any path
 * that explicitly only needs points.
 */
class GpxSaxHandler(
    private val points: MutableList<Point>
) : DefaultHandler() {
    override fun startElement(
        uri: String?, localName: String?, qName: String?, attributes: Attributes?
    ) {
        if (qName == "trkpt") {
            val lat = attributes?.getValue("lat")?.toDoubleOrNull()
            val lng = attributes?.getValue("lon")?.toDoubleOrNull()
            if (lat != null && lng != null) {
                points.add(Point.fromLngLat(lng, lat))
            }
        }
    }
}