package com.example.namastays.trek.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "navigation_sessions")
data class TrekNavigationSession(
    @PrimaryKey
    val trekId: String,
    val lastLatitude: Double,
    val lastLongitude: Double,
    val lastAccuracy: Float,
    val distanceCovered: Float,
    val progressPercent: Float,
    val nearestPointIndex: Int,
    val updatedAt: Long = System.currentTimeMillis()
)