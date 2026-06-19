package com.example.namastays.trek.util

import org.maplibre.geojson.Point
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class NavigationState(
    val currentLocation: TrekLocation,
    val nearestPointIndex: Int,
    val distanceToTrail: Float,
    val distanceCovered: Float,
    val distanceRemaining: Float,
    val progressPercent: Float,
    val currentElevation: Int,
    val status: NavigationStatus,
    val warningMessage: String? = null,
    val eta: String = "",
    val bearingToTrail: Float = 0f
)

enum class NavigationStatus {
    ACQUIRING,
    ON_TRAIL,
    OFF_TRAIL_WARNING,
    OFF_TRAIL_CRITICAL,
    WRONG_DIRECTION,
    WRONG_LOCATION,
    IN_VEHICLE,
    POOR_GPS,
    COMPLETED
}

object TrailNavigator {

    private const val OFF_TRAIL_WARNING_METERS  = 100f
    private const val OFF_TRAIL_CRITICAL_METERS = 500f
    private const val COMPLETION_RADIUS_METERS  = 50f
    private const val FAR_FROM_TRAILHEAD_METERS = 2000f

    /**
     * Main navigation state calculator. Call on every location update.
     *
     * [totalTrailDistance] must be passed in pre-computed by the caller
     * (e.g. cached in ViewModel after route loads). Computing it here on every
     * GPS fix would iterate all N GPX points at ~1 Hz — O(N) per second for
     * a constant value.
     *
     * Distance covered is computed incrementally from [previousState] rather
     * than re-summing from index 0 every fix, cutting per-fix work from O(N)
     * to O(1) after the first call.
     */
    fun calculateState(
        location: TrekLocation,
        gpxPoints: List<Point>,
        previousState: NavigationState?,
        elevationPoints: List<ElevationPoint> = emptyList(),
        trailheadName: String = "the trailhead",
        avgSpeedMs: Float = 0f,
        totalTrailDistance: Float = 0f   // pre-computed by caller; 0 = fallback to full scan
    ): NavigationState {

        // ── FIX: guard at the very top — before any helper calls that would
        // crash or produce nonsense on an empty point list.
        if (gpxPoints.isEmpty()) {
            return NavigationState(
                currentLocation   = location,
                nearestPointIndex = 0,
                distanceToTrail   = 0f,
                distanceCovered   = previousState?.distanceCovered ?: 0f,
                distanceRemaining = 0f,
                progressPercent   = previousState?.progressPercent ?: 0f,
                currentElevation  = elevationAtIndex(elevationPoints, 0),
                status            = NavigationStatus.ON_TRAIL
            )
        }

        // ── Find nearest trail point inside a forward-biased search window.
        val nearestIndex = findNearestPointIndex(
            location      = location,
            gpxPoints     = gpxPoints,
            previousIndex = previousState?.nearestPointIndex ?: 0
        )

        // ── Snap + smooth location before using it for any distance checks.
        val snappedLocation = TrailSnapper.snapToTrail(
            location          = location,
            gpxPoints         = gpxPoints,
            nearestPointIndex = nearestIndex
        )
        val effectiveBearing = if (location.speed < 1.0f)
            TrailSnapper.getTrailBearing(gpxPoints, nearestIndex)
        else
            location.bearing
        val locationToUse = snappedLocation.copy(bearing = effectiveBearing)

        // ── IN_VEHICLE: preserve all previous distances so progress isn't lost.
        if (locationToUse.isVehicleSpeed()) {
            return NavigationState(
                currentLocation   = locationToUse,
                nearestPointIndex = previousState?.nearestPointIndex ?: nearestIndex,
                distanceToTrail   = previousState?.distanceToTrail ?: 0f,
                distanceCovered   = previousState?.distanceCovered ?: 0f,
                distanceRemaining = previousState?.distanceRemaining ?: 0f,
                progressPercent   = previousState?.progressPercent ?: 0f,
                currentElevation  = previousState?.currentElevation
                    ?: elevationAtIndex(elevationPoints, nearestIndex),
                status            = NavigationStatus.IN_VEHICLE,
                warningMessage    = "You appear to be in a vehicle. Navigation paused."
            )
        }

        // ── POOR_GPS: same — don't reset progress on a bad fix.
        if (locationToUse.quality() == LocationQuality.UNUSABLE) {
            return NavigationState(
                currentLocation   = locationToUse,
                nearestPointIndex = previousState?.nearestPointIndex ?: nearestIndex,
                distanceToTrail   = previousState?.distanceToTrail ?: 0f,
                distanceCovered   = previousState?.distanceCovered ?: 0f,
                distanceRemaining = previousState?.distanceRemaining ?: 0f,
                progressPercent   = previousState?.progressPercent ?: 0f,
                currentElevation  = previousState?.currentElevation
                    ?: elevationAtIndex(elevationPoints, nearestIndex),
                status            = NavigationStatus.POOR_GPS,
                warningMessage    = "GPS signal too weak. Move to open ground."
            )
        }

        val nearestPoint = gpxPoints[nearestIndex]
        val distanceToTrail = LocationTracker.distanceBetween(
            locationToUse.latitude, locationToUse.longitude,
            nearestPoint.latitude(), nearestPoint.longitude()
        )

        // ── WRONG_LOCATION: only checked on very first fix (previousState == null).
        if (previousState == null) {
            val trailhead = gpxPoints.first()
            val distFromTrailhead = LocationTracker.distanceBetween(
                locationToUse.latitude, locationToUse.longitude,
                trailhead.latitude(), trailhead.longitude()
            )
            if (distFromTrailhead > FAR_FROM_TRAILHEAD_METERS) {
                val km = "%.0f".format(distFromTrailhead / 1000f)
                return NavigationState(
                    currentLocation   = locationToUse,
                    nearestPointIndex = nearestIndex,
                    distanceToTrail   = distanceToTrail,
                    distanceCovered   = 0f,
                    distanceRemaining = effectiveTotalDistance(gpxPoints, totalTrailDistance),
                    progressPercent   = 0f,
                    currentElevation  = elevationAtIndex(elevationPoints, 0),
                    status            = NavigationStatus.WRONG_LOCATION,
                    warningMessage    = "You are ${km}km from $trailheadName. " +
                            "Travel to $trailheadName to begin the trek."
                )
            }
        }

        // ── Distance covered: incremental delta from previous state.
        // Only advances forward — never decreases even if GPS briefly puts
        // the user behind their last known position.
        val total = effectiveTotalDistance(gpxPoints, totalTrailDistance)
        val distanceCovered = computeDistanceCovered(
            gpxPoints      = gpxPoints,
            nearestIndex   = nearestIndex,
            previousState  = previousState,
            totalDistance  = total
        )
        val distanceRemaining = (total - distanceCovered).coerceAtLeast(0f)
        val progressPercent   = if (total > 0f)
            (distanceCovered / total * 100f).coerceIn(0f, 100f) else 0f

        // ── COMPLETED: use smoothed location, require meaningful progress.
        // FIX: was using raw `location` — now uses `locationToUse` so a 40 m
        // GPS drift near the summit doesn't prevent completion triggering.
        val distanceToEnd = LocationTracker.distanceBetween(
            locationToUse.latitude, locationToUse.longitude,
            gpxPoints.last().latitude(), gpxPoints.last().longitude()
        )
        if (distanceToEnd < COMPLETION_RADIUS_METERS && progressPercent > 10f) {
            return NavigationState(
                currentLocation   = locationToUse,
                nearestPointIndex = nearestIndex,
                distanceToTrail   = distanceToTrail,
                distanceCovered   = distanceCovered,
                distanceRemaining = 0f,
                progressPercent   = 100f,
                currentElevation  = elevationAtIndex(elevationPoints, gpxPoints.size - 1),
                status            = NavigationStatus.COMPLETED
            )
        }

        // ── WRONG_DIRECTION: only when on-trail and actually moving.
        // FIX 1: was firing while off-trail (nearestIndex oscillates off-trail).
        // FIX 2: threshold raised to 20 points to tolerate tight switchbacks.
        val isWrongDirection = previousState != null
                && distanceCovered > 50f
                && location.speed > 0.5f
                && distanceToTrail < OFF_TRAIL_WARNING_METERS   // FIX: on-trail only
                && nearestIndex < (previousState.nearestPointIndex - 20) // FIX: wider margin

        val status = when {
            distanceToTrail > OFF_TRAIL_CRITICAL_METERS -> NavigationStatus.OFF_TRAIL_CRITICAL
            distanceToTrail > OFF_TRAIL_WARNING_METERS  -> NavigationStatus.OFF_TRAIL_WARNING
            isWrongDirection                            -> NavigationStatus.WRONG_DIRECTION
            else                                        -> NavigationStatus.ON_TRAIL
        }

        val warningMessage = when (status) {
            NavigationStatus.OFF_TRAIL_CRITICAL ->
                "You are ${distanceToTrail.toInt()}m off trail. Return to the route."
            NavigationStatus.OFF_TRAIL_WARNING ->
                "You are ${distanceToTrail.toInt()}m from the trail."
            NavigationStatus.WRONG_DIRECTION ->
                "You are heading away from your destination."
            else -> null
        }

        val eta     = estimateEta(distanceRemaining, locationToUse.speed, avgSpeedMs)
        val bearing = if (distanceToTrail > OFF_TRAIL_WARNING_METERS)
            bearingToTrail(locationToUse, gpxPoints, nearestIndex) else 0f

        return NavigationState(
            currentLocation   = locationToUse,
            nearestPointIndex = nearestIndex,
            distanceToTrail   = distanceToTrail,
            distanceCovered   = distanceCovered,
            distanceRemaining = distanceRemaining,
            progressPercent   = progressPercent,
            currentElevation  = elevationAtIndex(elevationPoints, nearestIndex),
            status            = status,
            warningMessage    = warningMessage,
            eta               = eta,
            bearingToTrail    = bearing
        )
    }

    // ── Nearest point search ───────────────────────────────────────────────────
    // FIX: window widened to -5 / +100 (was -3 / +50).
    // At 2 m/s hiking speed with 10 m GPX point spacing and a 4 s adaptive
    // emit rate, the user can advance ~80 m = ~8 points between emits.
    // +100 gives 2× headroom for bursts; -5 allows minor backtracking.
    private fun findNearestPointIndex(
        location: TrekLocation,
        gpxPoints: List<Point>,
        previousIndex: Int = 0
    ): Int {
        if (gpxPoints.isEmpty()) return 0

        val searchStart = (previousIndex - 5).coerceAtLeast(0)
        val searchEnd   = (previousIndex + 100).coerceAtMost(gpxPoints.size - 1)

        var minDistance  = Float.MAX_VALUE
        var nearestIndex = previousIndex

        for (i in searchStart..searchEnd) {
            val d = LocationTracker.distanceBetween(
                location.latitude, location.longitude,
                gpxPoints[i].latitude(), gpxPoints[i].longitude()
            )
            if (d < minDistance) {
                minDistance  = d
                nearestIndex = i
            }
        }
        return nearestIndex
    }

    // ── Distance helpers ───────────────────────────────────────────────────────

    /**
     * Use the pre-computed total if provided, otherwise fall back to a full
     * scan. Callers (ViewModel) should cache this after route load.
     */
    private fun effectiveTotalDistance(
        points: List<Point>,
        preComputed: Float
    ): Float = if (preComputed > 0f) preComputed else calculateTotalDistance(points)

    /**
     * Incremental distance covered: takes the previous covered distance and
     * adds only the delta from the previous nearest index to the current one.
     * Never decreases (GPS jitter won't un-do progress).
     * Falls back to a full sum from 0 on the first fix (previousState == null).
     */
    private fun computeDistanceCovered(
        gpxPoints: List<Point>,
        nearestIndex: Int,
        previousState: NavigationState?,
        totalDistance: Float
    ): Float {
        if (previousState == null) {
            // First fix: full sum from start to nearest point.
            return calculateDistanceCovered(gpxPoints, nearestIndex)
        }
        val prevIndex = previousState.nearestPointIndex
        if (nearestIndex <= prevIndex) {
            // Not advanced (backtrack or stationary) — keep previous value.
            return previousState.distanceCovered
        }
        // Add only the new segment.
        var delta = 0f
        for (i in (prevIndex + 1)..nearestIndex.coerceAtMost(gpxPoints.size - 1)) {
            delta += LocationTracker.distanceBetween(
                gpxPoints[i - 1].latitude(), gpxPoints[i - 1].longitude(),
                gpxPoints[i].latitude(),     gpxPoints[i].longitude()
            )
        }
        return (previousState.distanceCovered + delta).coerceAtMost(totalDistance)
    }

    /** Full sum from index 0 — used only on first fix or as fallback. */
    private fun calculateDistanceCovered(points: List<Point>, upToIndex: Int): Float {
        var total = 0f
        for (i in 1..upToIndex.coerceAtMost(points.size - 1)) {
            total += LocationTracker.distanceBetween(
                points[i - 1].latitude(), points[i - 1].longitude(),
                points[i].latitude(),     points[i].longitude()
            )
        }
        return total
    }

    /** Full trail length — call once and cache in ViewModel. */
    fun calculateTotalDistance(points: List<Point>): Float {
        var total = 0f
        for (i in 1 until points.size) {
            total += LocationTracker.distanceBetween(
                points[i - 1].latitude(), points[i - 1].longitude(),
                points[i].latitude(),     points[i].longitude()
            )
        }
        return total
    }

    // ── ETA ───────────────────────────────────────────────────────────────────
    // FIX: guard against zero/near-zero speed to prevent division by zero and
    // absurdly large ETA values when the user is standing still.

    fun estimateEta(
        distanceRemainingMeters: Float,
        currentSpeedMs: Float,
        avgSpeedMs: Float = 0f
    ): String {
        if (distanceRemainingMeters <= 0f) return "Arrived"

        // Default trekking pace ~3.5 km/h = 0.972 m/s
        val speedMs = when {
            currentSpeedMs > 0.5f -> currentSpeedMs
            avgSpeedMs > 0.3f     -> avgSpeedMs
            else                  -> 0.972f
        }.coerceAtLeast(0.1f)   // FIX: never divide by zero

        val secondsRemaining  = (distanceRemainingMeters / speedMs).toLong()
        val hoursRemaining    = secondsRemaining / 3600
        val minutesRemaining  = (secondsRemaining % 3600) / 60

        return when {
            hoursRemaining >= 1  -> "${hoursRemaining}h ${minutesRemaining}m"
            minutesRemaining >= 1 -> "${minutesRemaining} min"
            else                 -> "< 1 min"
        }
    }

    // ── Bearing to trail ──────────────────────────────────────────────────────

    fun bearingToTrail(
        location: TrekLocation,
        gpxPoints: List<Point>,
        nearestPointIndex: Int
    ): Float {
        if (gpxPoints.isEmpty()) return 0f
        val nearest = gpxPoints[nearestPointIndex]

        val lat1 = Math.toRadians(location.latitude)
        val lat2 = Math.toRadians(nearest.latitude())
        val dLng = Math.toRadians(nearest.longitude() - location.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    // ── Elevation ─────────────────────────────────────────────────────────────

    fun elevationAtIndex(elevationPoints: List<ElevationPoint>, index: Int): Int {
        if (elevationPoints.isEmpty()) return 0
        return elevationPoints.getOrNull(index)?.elevationM?.toInt()
            ?: elevationPoints.last().elevationM.toInt()
    }
}