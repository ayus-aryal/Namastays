package com.example.namastays.dto

import com.google.gson.annotations.SerializedName

data class TrekApiModel(
    val id: String,
    val name: String,
    val region: String?,
    val difficulty: String?,
    @SerializedName("durationDays") val durationDays: Int?,
    @SerializedName("maxElevation") val maxElevation: Int?,
    @SerializedName("distanceKm") val distanceKm: Double?,
    val description: String?,
    @SerializedName("tilesUrl") val tilesUrl: String?,
    @SerializedName("gpxUrl") val gpxUrl: String?,
    @SerializedName("waypointsUrl") val waypointsUrl: String?,
    @SerializedName("tilesSizeMb") val tilesSizeMb: Double?,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String?,
    @SerializedName("imagesUrl") val imagesUrl: List<String>?,
    @SerializedName("waypointsCount") val waypointsCount: Int?,
    @SerializedName("basePoint") val basePoint: String?
)