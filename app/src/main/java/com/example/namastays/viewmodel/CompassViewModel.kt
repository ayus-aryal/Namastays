package com.example.namastays.viewmodel

import android.app.Application
import android.hardware.GeomagneticField
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.NamastaysApp
import com.example.namastays.data.AnchorPoint
import com.example.namastays.data.AnchorPointRepository
import com.example.namastays.trek.util.LocationTracker
import com.example.namastays.trek.util.TrekLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bearing + distance from the user's current location to the saved anchor,
 * plus local magnetic declination, or null/0 fields if inputs aren't
 * available yet.
 *
 * Kept as a single combined data class (rather than several separate
 * StateFlows) so CompassScreen observes one state and can't end up
 * rendering a bearing computed against a stale location/anchor pairing.
 */
data class AnchorNavState(
    val anchor          : AnchorPoint?,
    val bearingDegrees  : Float?,
    val distanceMeters  : Float?,
    /**
     * Local magnetic declination at the current position, in degrees.
     * 0f until a GPS fix is available (compass will show magnetic north,
     * uncorrected, until then — acceptable since it self-corrects the
     * moment a fix lands).
     */
    val declinationDeg  : Float = 0f
) {
    /** True once the anchor is within AGING_WARNING_MS of expiring. */
    val isExpiringSoon: Boolean
        get() {
            val ts = anchor?.timestampMillis ?: return false
            val age = System.currentTimeMillis() - ts
            return age >= AnchorPointRepository.AGING_WARNING_MS
        }

    /**
     * Below this range, GPS bearing error is too large relative to the
     * distance itself to trust a precise arrow. This is a geometry problem
     * (short baseline amplifies angular error from normal GPS wobble), not
     * something further filtering removes — see CompassViewModel KDoc.
     */
    val isNearAnchor: Boolean
        get() = distanceMeters != null && distanceMeters < NEAR_ANCHOR_THRESHOLD_M

    companion object {
        const val NEAR_ANCHOR_THRESHOLD_M = 15f
    }
}

/**
 * ViewModel backing the Compass screen's anchor/backtrack functionality.
 *
 * ── Location strategy ────────────────────────────────────────────────────
 * Uses [LocationTracker.trackLocationSmooth] — the same Kalman-filtered
 * continuous location flow already used for map/trail tracking elsewhere
 * in the app. Backtracking accuracy is a hard requirement here, so this
 * reuses the filtering that's already proven correct rather than a second,
 * weaker one-shot-polling scheme built from scratch for this screen alone
 * (an earlier version of this file did exactly that — see git history —
 * and was replaced after inconsistent bearing behavior in field testing).
 *
 * ── Displacement gate ────────────────────────────────────────────────────
 * Even with Kalman smoothing, a stationary GPS fix can still wobble by a
 * few meters between updates, and Location.bearingTo() is geometrically
 * unstable over short baselines — a 2-3m wobble near the anchor can swing
 * the calculated bearing by tens of degrees, while barely affecting
 * bearing accuracy at long range. So: only recompute the published bearing
 * when the position has moved at least MIN_DISPLACEMENT_M since the last
 * position actually used for a bearing calc — same principle TrekEngine
 * already applies to distance accumulation. This does not stall bearing
 * updates while walking (you're constantly displacing while moving); it
 * specifically suppresses noise while standing still.
 *
 * ── Near-anchor cutoff ───────────────────────────────────────────────────
 * Below AnchorNavState.NEAR_ANCHOR_THRESHOLD_M, no arrow is precise enough
 * to trust — a property of GPS accuracy vs. geometry, not something a
 * smarter filter fully fixes. CompassScreen switches to a "You're near the
 * anchor" message instead of a jittery arrow at that range.
 *
 * ── Declination ──────────────────────────────────────────────────────────
 * Raw compass sensors report magnetic north, not true north. GeomagneticField
 * computes the local correction from GPS position. Exposed via
 * AnchorNavState.declinationDeg; CompassScreen adds it to the raw sensor
 * heading before use. Altitude is passed as 0f — declination changes
 * negligibly with altitude at trekking elevations, and TrekLocation
 * (from LocationTracker) does not carry an altitude field.
 */
class CompassViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** Minimum movement (meters) between fixes before a new bearing is
         *  computed — see class KDoc, "Displacement gate". */
        private const val MIN_DISPLACEMENT_M = 3.0
    }

    private val appContext = application.applicationContext

    private val anchorRepo: AnchorPointRepository =
        (application as NamastaysApp).anchorPointRepository

    // ── Anchor ─────────────────────────────────────────────────────────────

    /**
     * Current anchor, or null if none set / expired. Exposed directly (not
     * just folded into AnchorNavState) so the screen can render the
     * "No anchor point saved" text immediately without waiting on a location
     * fix — those two things should not be coupled from the UI's perspective.
     */
    val anchorPoint: StateFlow<AnchorPoint?> =
        anchorRepo.anchorPoint
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Clears the anchor. Used by the Compass screen's Clear button. */
    fun clearAnchor() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { anchorRepo.clearAnchor() }
        }
    }

    // ── Live location (continuous, Kalman-smoothed) ─────────────────────────

    private val _currentLocation = MutableStateFlow<TrekLocation?>(null)

    init {
        // Runs for the lifetime of this ViewModel, i.e. only while the
        // Compass screen (or something else holding this ViewModel) is
        // alive — viewModelScope is cancelled in onCleared() automatically,
        // which cancels this collection and lets LocationTracker's
        // awaitClose{} unregister its location callback.
        viewModelScope.launch {
            LocationTracker.trackLocationSmooth(appContext).collect { loc ->
                _currentLocation.value = loc
            }
        }
    }

    // Last position actually used to compute a published bearing — the
    // reference point for the displacement gate. Kept separate from
    // _currentLocation so the freshest raw fix is always available for
    // distance display even when bearing itself hasn't been recomputed.
    private var lastBearingFixLocation: TrekLocation? = null
    private var lastPublishedBearing: Float = 0f

    // ── Combined nav state ────────────────────────────────────────────────

    /**
     * Bearing (degrees, 0-360, true-north-relative — matches what
     * Location.bearingTo returns), distance, and local declination from
     * current location to the anchor. bearingDegrees/distanceMeters are
     * both null if either the anchor or a location fix is missing.
     */
    val anchorNavState: StateFlow<AnchorNavState> =
        combine(anchorPoint, _currentLocation) { anchor, current ->
            if (anchor == null || current == null) {
                return@combine AnchorNavState(
                    anchor         = anchor,
                    bearingDegrees = null,
                    distanceMeters = null
                )
            }

            val anchorLocation = Location("anchor").apply {
                latitude  = anchor.latitude
                longitude = anchor.longitude
            }
            val currentLocation = Location("fused").apply {
                latitude  = current.latitude
                longitude = current.longitude
            }
            val distance = currentLocation.distanceTo(anchorLocation)

            // Displacement gate — see class KDoc.
            val lastFix = lastBearingFixLocation
            val moved = if (lastFix == null) {
                true
            } else {
                val lastLoc = Location("last").apply {
                    latitude  = lastFix.latitude
                    longitude = lastFix.longitude
                }
                lastLoc.distanceTo(currentLocation) >= MIN_DISPLACEMENT_M
            }

            val bearing = if (moved) {
                lastBearingFixLocation = current
                val computed = currentLocation.bearingTo(anchorLocation)
                    .let { if (it < 0) it + 360f else it }
                lastPublishedBearing = computed
                computed
            } else {
                lastPublishedBearing
            }

            val declination = GeomagneticField(
                current.latitude.toFloat(),
                current.longitude.toFloat(),
                0f, // altitude — see class KDoc, "Declination"
                System.currentTimeMillis()
            ).declination

            AnchorNavState(
                anchor         = anchor,
                bearingDegrees = bearing,
                distanceMeters = distance,
                declinationDeg = declination
            )
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = AnchorNavState(anchor = null, bearingDegrees = null, distanceMeters = null)
        )
}

class CompassViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompassViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CompassViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}