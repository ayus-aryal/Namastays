package com.example.namastays.trek.domain

data class TrekItem(
    val id: String,
    val name: String,
    val region: String,
    val difficulty: String,
    val durationDays: Int,
    val maxElevation: Int,
    val distanceKm: Int,
    val description: String,
    val fileSizeMb: Int = 0,
    val isDownloaded: Boolean = false,

    val tilesUrl: String? = null,
    val gpxUrl: String? = null,
    val waypointsUrl: String? = null,
    val waypointsCount: Int? = null
)