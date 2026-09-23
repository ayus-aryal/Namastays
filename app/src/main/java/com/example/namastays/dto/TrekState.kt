package com.example.namastays.dto

import com.example.namastays.screens.AltitudeZone

// ── WHAT CHANGED ──────────────────────────────────────────────────────────────
// 1. Added `gpsSignalState: GpsSignalState = GpsSignalState.ACQUIRING` field
//    to TrekState (at the bottom of the engine-meta block).
// 2. Added the `GpsSignalState` sealed class below TrekState.
//
// Everything else is identical to the original file.
// ─────────────────────────────────────────────────────────────────────────────

data class TrekState(
    // ── Core location ────────────────────────────────────────────────────────
    val altitude    : Double = 0.0,
    val latitude    : Double = 0.0,
    val longitude   : Double = 0.0,
    val accuracy    : Float  = 0f,

    // ── Movement ─────────────────────────────────────────────────────────────
    val speedKmh    : Double = 0.0,
    val distanceKm  : Double = 0.0,
    val gainMeters  : Double = 0.0,
    val lossMeters  : Double = 0.0,

    // ── Derived ──────────────────────────────────────────────────────────────
    val altitudeZone : AltitudeZone = AltitudeZone.NORMAL,
    val ascentRateM  : Double       = 0.0,

    // ── Session ──────────────────────────────────────────────────────────────
    val currentSessionId : Long? = null,

    // ── Engine meta ──────────────────────────────────────────────────────────
    val barometerAvailable : Boolean = false,
    val inBatterySaver     : Boolean = false,

    // ── NEW FIELD ─────────────────────────────────────────────────────────────
    // Default is ACQUIRING (not OK) so the state before the first accepted fix
    // always shows as "waiting" rather than "fine." All existing call-sites and
    // previews compile unchanged since it has a default value.
    val gpsSignalState : GpsSignalState = GpsSignalState.ACQUIRING
)

/**
 * GPS signal quality as observed by TrekEngine.
 *
 * WHY: The original TrekState had no signal-quality field. When GPS accuracy
 * degraded mid-session (entering a valley, dense forest), fixes were silently
 * rejected with a Log.v and _state was never updated — so the UI had no way
 * to distinguish "good signal, user standing still" from "signal lost 5 minutes
 * ago, every value on screen is stale." On a safety-adjacent trekking app
 * this is a real problem: a trekker relying on displayed altitude for AMS risk
 * decisions has no idea the sensor has lost signal.
 *
 * Sealed class (not Boolean) so future granularity (e.g. a WEAK intermediate)
 * can be added without breaking existing when-expressions — the compiler
 * flags unhandled branches.
 *
 * State machine:
 *   Trek Mode starts         → ACQUIRING (TrekState default)
 *   First accepted fix       → OK
 *   N consecutive rejections → DEGRADED (threshold = GPS_DEGRADED_THRESHOLD)
 *   Trek Mode stops          → reset to ACQUIRING on next start
 */
sealed class GpsSignalState {
    /** Waiting for the first accepted fix since Trek Mode started. */
    object ACQUIRING : GpsSignalState()

    /** Most recent fix was within the accuracy threshold. */
    object OK : GpsSignalState()

    /**
     * Recent fixes have all been rejected due to poor accuracy.
     * [consecutiveRejections] is for logging; UI only needs to know it's degraded.
     */
    data class DEGRADED(val consecutiveRejections: Int) : GpsSignalState()
}