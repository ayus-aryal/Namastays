package com.example.namastays.trek.util

import android.content.Context
import android.util.Log
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File

data class ElevationPoint(
    val distanceKm: Float,
    val elevationM: Double
)

object GpxParser {

    /**
     * Reads the GPX file for a trek from device storage
     * and converts it to a GeoJSON FeatureCollection
     * containing a single LineString (the trail route)
     */
    fun parseToGeoJson(context: Context, trekId: String): FeatureCollection? {
        val gpxFile = File(context.filesDir, "$trekId.gpx")

        if (!gpxFile.exists()) {
            Log.e("GpxParser", "GPX file not found: ${gpxFile.absolutePath}")
            return null
        }

        return try {
            val points = mutableListOf<Point>()

            // Parse XML manually — lightweight, no extra dependency needed
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val handler = GpxSaxHandler(points)
            parser.parse(gpxFile, handler)

            if (points.isEmpty()) {
                Log.e("GpxParser", "No track points found in GPX file")
                return null
            }

            Log.d("GpxParser", "Parsed ${points.size} track points")

            val lineString = LineString.fromLngLats(points)
            val feature = Feature.fromGeometry(lineString)
            FeatureCollection.fromFeature(feature)

        } catch (e: Exception) {
            Log.e("GpxParser", "Parse error: ${e.message}")
            null
        }
    }




    fun parseElevationProfile(context: Context, trekId: String): List<ElevationPoint> {
        val gpxFile = File(context.filesDir, "$trekId.gpx")
        if (!gpxFile.exists()) return emptyList()

        return try {
            val points = mutableListOf<Pair<Point, Double>>() // lat/lng + elevation

            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val handler = object : org.xml.sax.helpers.DefaultHandler() {
                private var currentEle = ""
                private var inEle = false
                private var lastPoint: Point? = null

                override fun startElement(
                    uri: String?, localName: String?,
                    qName: String?, attributes: org.xml.sax.Attributes?
                ) {
                    if (qName == "trkpt" || qName == "wpt") {
                        val lat = attributes?.getValue("lat")?.toDoubleOrNull()
                        val lng = attributes?.getValue("lon")?.toDoubleOrNull()
                        if (lat != null && lng != null) {
                            lastPoint = Point.fromLngLat(lng, lat)
                        }
                    }
                    if (qName == "ele") {
                        inEle = true
                        currentEle = ""
                    }
                }

                override fun characters(ch: CharArray?, start: Int, length: Int) {
                    if (inEle) currentEle += String(ch ?: charArrayOf(), start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    if (qName == "ele") {
                        inEle = false
                        val ele = currentEle.trim().toDoubleOrNull()
                        val pt = lastPoint
                        if (ele != null && pt != null) {
                            points.add(Pair(pt, ele))
                            lastPoint = null
                        }
                    }
                }
            }
            parser.parse(gpxFile, handler)

            if (points.isEmpty()) return emptyList()

            // Calculate cumulative distance
            var totalDistance = 0f
            val result = mutableListOf<ElevationPoint>()

            result.add(ElevationPoint(0f, points.first().second))

            for (i in 1 until points.size) {
                val prev = points[i - 1].first
                val curr = points[i].first
                totalDistance += LocationTracker.distanceBetween(
                    prev.latitude(), prev.longitude(),
                    curr.latitude(), curr.longitude()
                )
                result.add(ElevationPoint(totalDistance / 1000f, points[i].second))
            }

            // Downsample to max 200 points for performance
            if (result.size > 200) {
                val step = result.size / 200
                result.filterIndexed { index, _ -> index % step == 0 }
            } else result

        } catch (e: Exception) {
            Log.e("GpxParser", "Elevation parse error: ${e.message}")
            emptyList()
        }
    }





    /**
     * Returns the bounding box of a trek route
     * Used to auto-fit the camera to show the full route
     */
    fun getBounds(context: Context, trekId: String): RouteBounds? {
        val gpxFile = File(context.filesDir, "$trekId.gpx")
        if (!gpxFile.exists()) return null

        return try {
            val points = mutableListOf<Point>()
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val handler = GpxSaxHandler(points)
            parser.parse(gpxFile, handler)

            if (points.isEmpty()) return null

            RouteBounds(
                minLat = points.minOf { it.latitude() },
                maxLat = points.maxOf { it.latitude() },
                minLng = points.minOf { it.longitude() },
                maxLng = points.maxOf { it.longitude() }
            )
        } catch (e: Exception) {
            null
        }
    }

    fun parseToPoints(context: Context, trekId: String): List<Point> {
        val gpxFile = File(context.filesDir, "$trekId.gpx")
        if (!gpxFile.exists()) return emptyList()
        return try {
            val points = mutableListOf<Point>()
            val factory = javax.xml.parsers.SAXParserFactory.newInstance()
            val parser = factory.newSAXParser()
            val handler = GpxSaxHandler(points)
            parser.parse(gpxFile, handler)
            points
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class RouteBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

/**
 * SAX handler that extracts trkpt lat/lng from GPX XML
 * Handles both <trkpt> and <wpt> elements
 */
class GpxSaxHandler(
    private val points: MutableList<Point>
) : org.xml.sax.helpers.DefaultHandler() {

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: org.xml.sax.Attributes?
    ) {
        if (qName == "trkpt" || qName == "wpt") {
            val lat = attributes?.getValue("lat")?.toDoubleOrNull()
            val lng = attributes?.getValue("lon")?.toDoubleOrNull()
            if (lat != null && lng != null) {
                points.add(Point.fromLngLat(lng, lat))
            }
        }
    }
}


