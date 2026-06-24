package com.example.namastays.trek.domain

/**
 * FIX #5 — [distanceKm] changed from Int to Double, matching TrekItem.
 */
data class ItineraryDay(
    val dayNumber:   Int,
    val title:       String,
    val description: String
)

data class TrailHighlight(
    val label:    String,
    val iconName: String
)

data class TrekDetail(
    val id:             String,
    val name:           String,
    val region:         String,
    val difficulty:     String,
    val durationDays:   Int,
    val maxElevation:   Int,
    val distanceKm:     Double,           // FIX #5 — was Int
    val description:    String,
    val fileSizeMb:     Int,
    val tilesUrl:       String?,
    val gpxUrl:         String?,
    val waypointsUrl:   String?,
    val waypointsCount: Int?,
    val thumbnailUrl:   String?,
    val imagesUrl:      List<String>,
    val basePoint:      String?,

    val itineraryDays:  List<ItineraryDay>,
    val highlights:     List<TrailHighlight>
)