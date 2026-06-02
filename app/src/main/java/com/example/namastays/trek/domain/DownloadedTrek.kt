package com.example.namastays.trek.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_treks")
data class DownloadedTrek(
    @PrimaryKey
    val trekId: String,
    val trekName: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSizeMb: Float,
    val tilesPath: String,
    val gpxPath: String,
    val waypointsPath: String
)