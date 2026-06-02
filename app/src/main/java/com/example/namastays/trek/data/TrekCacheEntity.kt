package com.example.namastays.trek.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trek_cache")
data class TrekCacheEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String?,
    val difficulty: String?,
    val durationDays: Int?,
    val maxElevation: Int?,
    val distanceKm: Double?,
    val description: String?,
    val tilesUrl: String?,
    val gpxUrl: String?,
    val waypointsUrl: String?,
    val tilesSizeMb: Double?,
    val thumbnailUrl: String?,
    val imagesUrl: String?,        // stored as comma-separated string
    val waypointsCount: Int?,
    val basePoint: String?
)