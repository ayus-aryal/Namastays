package com.example.namastays.trek.domain

/**
 * FIX #5 — [distanceKm] changed from Int to Double.
 * A trek of 14.7 km was previously truncated to 14 silently.
 * UI layer formats with one decimal place: "%.1f km".format(distanceKm)
 */
data class TrekItem(
    val id:             String,
    val name:           String,
    val region:         String,
    val difficulty:     String,
    val durationDays:   Int,
    val maxElevation:   Int,
    val distanceKm:     Double,           // FIX #5 — was Int
    val description:    String,
    val fileSizeMb:     Int     = 0,
    val isDownloaded:   Boolean = false,

    val tilesUrl:       String? = null,
    val gpxUrl:         String? = null,
    val waypointsUrl:   String? = null,
    val waypointsCount: Int?    = null,

    val thumbnailUrl:   String? = null,
    val imagesUrl:      List<String> = emptyList()
)