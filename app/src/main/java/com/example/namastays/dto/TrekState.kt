package com.example.namastays.dto

import com.example.namastays.screens.AltitudeZone

data class TrekState(
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,

    val speedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,

    val gainMeters: Double = 0.0,
    val lossMeters: Double= 0.0,

    val altitudeZone: AltitudeZone = AltitudeZone.NORMAL,

    val accuracy: Float = 0f,
    val barometerAvailable: Boolean = false
)
