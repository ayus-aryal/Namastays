package com.example.namastays.trek.util

import org.maplibre.geojson.Point
import kotlin.math.*

object TrailSnapper {

    // Max distance to snap — beyond this show real GPS position
    private const val SNAP_THRESHOLD_METERS = 25f

    /**
     * Main function — call this instead of using raw GPS position
     * Returns snapped position if close enough to trail,
     * otherwise returns original GPS position
     */
    fun snapToTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): TrekLocation {
        if (gpxPoints.size < 2) return location

        // Check segment before and after nearest point
        val snappedPoint = findNearestPointOnSegments(
            location      = location,
            gpxPoints     = gpxPoints,
            nearestIndex  = nearestPointIndex
        )

        val distanceToSnap = LocationTracker.distanceBetween(
            location.latitude, location.longitude,
            snappedPoint.latitude(), snappedPoint.longitude()
        )

        return if (distanceToSnap <= SNAP_THRESHOLD_METERS) {
            // Snap to trail — use trail point position
            // but keep original speed/bearing/accuracy
            location.copy(
                latitude  = snappedPoint.latitude(),
                longitude = snappedPoint.longitude()
            )
        } else {
            // Too far from trail — show real position
            location
        }
    }

    /**
     * Finds the nearest point ON the trail LINE SEGMENTS
     * (not just nearest GPX point, but nearest point on the
     * actual line between two consecutive GPX points)
     * Much more accurate — especially on straight sections
     */
    private fun findNearestPointOnSegments(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestIndex: Int
    ): Point {
        // Check segments around nearest index
        // (segment before and segment after nearest point)
        val startIdx = (nearestIndex - 3).coerceAtLeast(0)
        val endIdx   = (nearestIndex + 5).coerceAtMost(gpxPoints.size - 2)

        var bestPoint    = gpxPoints[nearestIndex]
        var bestDistance = Float.MAX_VALUE

        for (i in startIdx..endIdx) {
            val segStart = gpxPoints[i]
            val segEnd   = gpxPoints[i + 1]

            val projected = projectPointOnSegment(
                point    = location,
                segStart = segStart,
                segEnd   = segEnd
            )

            val distance = LocationTracker.distanceBetween(
                location.latitude, location.longitude,
                projected.latitude(), projected.longitude()
            )

            if (distance < bestDistance) {
                bestDistance = distance
                bestPoint    = projected
            }
        }

        return bestPoint
    }

    /**
     * Projects a GPS point onto a line segment
     * Returns the closest point ON the segment
     *
     * This is the math that makes snapping feel smooth:
     * instead of jumping to the nearest GPX vertex,
     * we find the exact closest point on the line
     */
    private fun projectPointOnSegment(
        point: TrekLocation,
        segStart: Point,
        segEnd: Point
    ): Point {
        val ax = segStart.longitude()
        val ay = segStart.latitude()
        val bx = segEnd.longitude()
        val by = segEnd.latitude()
        val px = point.longitude
        val py = point.latitude

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay

        val ab2 = abx * abx + aby * aby
        if (ab2 == 0.0) return segStart  // degenerate segment

        // Parameter t = how far along segment (0=start, 1=end)
        val t = ((apx * abx + apy * aby) / ab2).coerceIn(0.0, 1.0)

        return Point.fromLngLat(
            ax + t * abx,
            ay + t * aby
        )
    }

    /**
     * Calculates the bearing from the snapped position
     * to the NEXT point on the trail
     * More accurate than device bearing when going slowly
     */
    fun getTrailBearing(
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): Float {
        if (nearestPointIndex >= gpxPoints.size - 1) return 0f

        val current = gpxPoints[nearestPointIndex]
        val next    = gpxPoints[
            (nearestPointIndex + 3).coerceAtMost(gpxPoints.size - 1)
        ]

        val lat1 = Math.toRadians(current.latitude())
        val lat2 = Math.toRadians(next.latitude())
        val dLng = Math.toRadians(next.longitude() - current.longitude())

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) -
                sin(lat1) * cos(lat2) * cos(dLng)

        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}