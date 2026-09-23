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

/** Single result returned by [GpxParser.parseFull] — everything derived from one file read. */
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
     * Call this instead of the legacy single-purpose wrappers below, each of
     * which re-parses the whole file — parseFull() does one SAX pass total.
     */
    fun parseFull(context: Context, trekId: String): GpxParseResult {
        val gpxFile = File(context.filesDir, "$trekId.gpx")
        if (!gpxFile.exists()) {
            Log.e(TAG, "GPX file not found for trek '$trekId': ${gpxFile.absolutePath}")
            return GpxParseResult(emptyList(), emptyList(), null)
        }

        return try {
            val rawPoints = mutableListOf<Point>()
            val rawElevations = mutableListOf<Double?>() // null = no <ele> for that trkpt; parallel to rawPoints

            val factory = SAXParserFactory.newInstance()
            val parser  = factory.newSAXParser()
            parser.parse(gpxFile, FullGpxHandler(rawPoints, rawElevations))

            if (rawPoints.isEmpty()) {
                Log.e(TAG, "No track points found in GPX for trek '$trekId'")
                return GpxParseResult(emptyList(), emptyList(), null)
            }

            Log.d(TAG, "Parsed ${rawPoints.size} track points for trek '$trekId'")

            val bounds = RouteBounds(
                minLat = rawPoints.minOf { it.latitude() },
                maxLat = rawPoints.maxOf { it.latitude() },
                minLng = rawPoints.minOf { it.longitude() },
                maxLng = rawPoints.maxOf { it.longitude() }
            )

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
    // Kept for call-site compatibility; each delegates to parseFull(), so
    // calling all four still re-reads the file four times. Prefer parseFull().

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

    /**
     * Builds a distance-vs-elevation profile from parallel points/elevations
     * lists, then downsamples to at most [MAX_ELEVATION_POINTS] entries for
     * cheap chart rendering.
     *
     * FIX 1 — starting elevation: previously used
     * `elevations.firstOrNull { it != null }`, which picks the first
     * NON-NULL elevation anywhere in the list — silently substituting a
     * LATER point's elevation as "the start" if the very first <trkpt> (a
     * common GPS-cold-start case) has no <ele>. Now explicitly reads
     * `elevations[0]` and only falls back to the first available non-null
     * value if that exact entry is missing, so the profile's starting value
     * is accurate whenever the data actually supports it.
     *
     * FIX 2 — downsampling could silently drop the final point: taking every
     * Nth element by `index % step == 0` has no guarantee the last index is
     * a multiple of step (e.g. 250 points, step=2 keeps indices 0,2,4,...248
     * and drops 249 — the trek's actual endpoint/summit elevation). The
     * downsampled list now always force-includes the last point.
     */
    private fun buildElevationProfile(
        points: List<Point>,
        elevations: List<Double?>
    ): List<ElevationPoint> {
        if (points.isEmpty() || elevations.all { it == null }) return emptyList()

        val fallbackFirstElevation = elevations.firstOrNull { it != null } ?: 0.0
        val startElevation = elevations.getOrNull(0) ?: fallbackFirstElevation

        var totalDistance = 0f
        val result = mutableListOf<ElevationPoint>()
        result.add(ElevationPoint(0f, startElevation))

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            totalDistance += LocationTracker.distanceBetween(
                prev.latitude(), prev.longitude(),
                curr.latitude(), curr.longitude()
            )
            // Missing elevation for this point: carry forward the last known
            // value rather than leaving a gap in the profile.
            val ele = elevations.getOrNull(i) ?: result.last().elevationM
            result.add(ElevationPoint(totalDistance / 1000f, ele))
        }

        if (result.size <= MAX_ELEVATION_POINTS) return result

        val step = (result.size + MAX_ELEVATION_POINTS - 1) / MAX_ELEVATION_POINTS
        val downsampled = result.filterIndexed { index, _ -> index % step == 0 }

        // Force-include the true last point if the stride skipped past it —
        // guarantees the profile always ends at the trek's actual endpoint.
        return if (downsampled.last() !== result.last()) {
            downsampled + result.last()
        } else {
            downsampled
        }
    }
}

// ─── SAX handlers ──────────────────────────────────────────────────────────────

/**
 * Single-pass handler collecting both track points and their elevations.
 * [elevations] is parallel to [points] — index N in one corresponds to
 * index N in the other. Null means that <trkpt> had no <ele> child.
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
                // Captured into eleBuffer; committed alongside the point when
                // </trkpt> fires below (handles GPX files where </ele> closes
                // before </trkpt>, which is the normal/only valid ordering).
            }
            "trkpt" -> {
                val pt = pendingPoint ?: return
                points.add(pt)
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
 * Lightweight handler that only extracts track point coordinates. Used
 * anywhere that only needs the point list, not elevation data.
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