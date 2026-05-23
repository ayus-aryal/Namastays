package com.example.namastays.data

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "sleep_Altitude")
data class SleepAltitudeRecord(
    @PrimaryKey
    val date: String,
    val altitudeMeters: Double,
    val timestampMs: Long = System.currentTimeMillis()
)
