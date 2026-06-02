package com.example.namastays.trek.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_markers")
data class CustomMarker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trekId: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val note: String = "",
    val iconType: String = "pin",
    val createdAt: Long = System.currentTimeMillis()
)

enum class MarkerIconType(val label: String, val icon: ImageVector) {
    PIN("Pin", Icons.Filled.PushPin),
    CAMP("Camp", Icons.Filled.NightShelter),
    PHOTO("Photo", Icons.Filled.PhotoCamera),
    DANGER("Danger", Icons.Filled.Warning),
    REST("Rest", Icons.Filled.Hotel),
    SUMMIT("Summit", Icons.Filled.Landscape)
}