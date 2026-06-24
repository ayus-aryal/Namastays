package com.example.namastays.trek.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FIX C1 — Removed Compose imports (ImageVector, material icons) from this file.
 *
 * MarkerIconType with its ImageVector fields created a compile-time dependency
 * from the data/domain layer on the Compose UI layer. This violated layering:
 * - Room entities must compile without the Compose runtime
 * - Tests of the data layer would pull in Compose transitively
 *
 * MarkerIconType has been moved to the UI layer (see MarkerIconType.kt in
 * the screens/components package). The [iconType] field on CustomMarker
 * remains a plain String; the UI layer resolves it to an ImageVector.
 */
@Entity(tableName = "custom_markers")
data class CustomMarker(
    @PrimaryKey(autoGenerate = true)
    val id:        Long   = 0,
    val trekId:    String,
    val latitude:  Double,
    val longitude: Double,
    val title:     String,
    val note:      String = "",
    val iconType:  String = "pin",   // Resolved to ImageVector in the UI layer
    val createdAt: Long   = System.currentTimeMillis()
)