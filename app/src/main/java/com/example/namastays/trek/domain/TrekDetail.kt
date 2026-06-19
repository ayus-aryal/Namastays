package com.example.namastays.trek.domain

// Lightweight value types used only by the detail screen.
// Kept separate from TrekItem intentionally — the list screen
// never needs to load or hold this data.

data class ItineraryDay(
    val dayNumber:   Int,
    val title:       String,
    val description: String
)

data class TrailHighlight(
    val label:    String,
    val iconName: String  // resolved to an ImageVector in the UI layer
)

// The full detail model: flat trek data (reused from TrekItem)
// plus the two child collections fetched only on the detail screen.
data class TrekDetail(
    // Core trek fields — mirrors TrekItem to avoid an extra mapping step
    val id:            String,
    val name:          String,
    val region:        String,
    val difficulty:    String,
    val durationDays:  Int,
    val maxElevation:  Int,
    val distanceKm:    Int,
    val description:   String,
    val fileSizeMb:    Int,
    val tilesUrl:      String?,
    val gpxUrl:        String?,
    val waypointsUrl:  String?,
    val waypointsCount: Int?,
    val thumbnailUrl:  String?,
    val imagesUrl:     List<String>,
    val basePoint:     String?,

    // Detail-only data
    val itineraryDays: List<ItineraryDay>,
    val highlights:    List<TrailHighlight>
)