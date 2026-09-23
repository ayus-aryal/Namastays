package com.example.namastays.trek.util

import org.maplibre.geojson.Point
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object TrailSnapper {

    /** Max distance to snap — beyond this, show the real GPS position instead. */
    const val SNAP_THRESHOLD_METERS = 25f

    /**
     * Main function — call this instead of using raw GPS position for display.
     * Returns a snapped position if close enough to the trail, otherwise
     * returns the original GPS position unchanged.
     */
    fun snapToTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): TrekLocation {
        if (gpxPoints.size < 2) return location

        val snappedPoint = nearestPointOnTrail(location, gpxPoints, nearestPointIndex)
        val distanceToSnap = LocationTracker.distanceBetween(
            location.latitude, location.longitude,
            snappedPoint.latitude(), snappedPoint.longitude()
        )

        return if (distanceToSnap <= SNAP_THRESHOLD_METERS) {
            location.copy(
                latitude  = snappedPoint.latitude(),
                longitude = snappedPoint.longitude()
            )
        } else {
            location
        }
    }

    /**
     * Finds the nearest point ON the trail's LINE SEGMENTS (not just the
     * nearest GPX vertex) — the closest point on the actual line between two
     * consecutive GPX points. Much more accurate on straight sections,
     * especially with sparse GPX point spacing.
     *
     * Exposed publicly (previously private, inlined only inside snapToTrail)
     * so TrailNavigator can measure distance-to-trail against this SAME
     * projected point that the display location is snapped to. Previously
     * TrailNavigator measured distance-to-trail against the raw nearest
     * vertex while the displayed dot used this projection — the two could
     * disagree on sparse trails, occasionally firing an off-trail warning
     * for a hiker who was in fact exactly on the line between two points.
     */
    fun nearestPointOnTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestIndex: Int
    ): Point {
        if (gpxPoints.size < 2) return gpxPoints.getOrElse(nearestIndex) { gpxPoints.first() }

        // Check segments immediately around the nearest vertex.
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
     * Projects a GPS point onto a line segment, returning the closest point
     * ON the segment (not just the nearer of the two endpoints). This is the
     * math that makes snapping feel smooth: instead of jumping to the
     * nearest GPX vertex, we find the exact closest point on the line.
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
        if (ab2 == 0.0) return segStart // degenerate (zero-length) segment

        // t = how far along the segment (0 = start, 1 = end).
        val t = ((apx * abx + apy * aby) / ab2).coerceIn(0.0, 1.0)

        return Point.fromLngLat(
            ax + t * abx,
            ay + t * aby
        )
    }

    /**
     * Bearing from the nearest trail point toward a point further ahead on
     * the trail. More stable than raw device bearing when moving slowly,
     * since GPS bearing gets noisy at low speed.
     */
    fun getTrailBearing(
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): Float {
        if (nearestPointIndex >= gpxPoints.size - 1) return 0f

        val current = gpxPoints[nearestPointIndex]
        val next    = gpxPoints[(nearestPointIndex + 3).coerceAtMost(gpxPoints.size - 1)]

        val lat1 = Math.toRadians(current.latitude())
        val lat2 = Math.toRadians(next.latitude())
        val dLng = Math.toRadians(next.longitude() - current.longitude())

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }
}