package com.example.namastays.dto

import com.example.namastays.screens.AltitudeZone

/**
 * Immutable snapshot of everything Trek Mode cares about.
 *
 * All new fields have defaults so existing call-sites (previews, tests)
 * compile without changes.
 *
 * [ascentRateM]      Metres gained per hour over the last 30 minutes.
 *                    Computed in TrekEngine from a sliding window.
 *                    The "500 m rule" threshold is 500 m/day but the
 *                    per-hour rate is the most actionable real-time signal.
 *
 * [inBatterySaver]   True when GPS polling has been reduced because the
 *                    user has been stationary for > 30 s.
 *
 * [currentSessionId] Room PK of the currently-open TrekSession, or null
 *                    when Trek Mode is off.  UI uses this to navigate to
 *                    the detail screen.
 *
 * [barometerAvailable] Carried forward from original — shown in UI as a
 *                    small accuracy indicator.
 */
data class TrekState(
    // ── Core location ────────────────────────────────────────────────────────
    val altitude    : Double = 0.0,
    val latitude    : Double = 0.0,
    val longitude   : Double = 0.0,
    val accuracy    : Float  = 0f,

    // ── Movement ─────────────────────────────────────────────────────────────
    val speedKmh    : Double = 0.0,   // median-smoothed, zero-floored
    val distanceKm  : Double = 0.0,   // accuracy-gated Haversine accumulation
    val gainMeters  : Double = 0.0,
    val lossMeters  : Double = 0.0,

    // ── Derived ──────────────────────────────────────────────────────────────
    val altitudeZone    : AltitudeZone = AltitudeZone.NORMAL,
    val ascentRateM     : Double       = 0.0,   // m/hr over last 30 min window

    // ── Session ──────────────────────────────────────────────────────────────
    val currentSessionId : Long? = null,

    // ── Engine meta ──────────────────────────────────────────────────────────
    val barometerAvailable : Boolean = false,
    val inBatterySaver     : Boolean = false
)