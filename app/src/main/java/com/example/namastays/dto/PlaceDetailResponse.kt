package com.example.namastays.dto

data class PlaceDetailResponse(
    val id: String,
    val name: String,
    val description: String?,
    val lat: Double,
    val lng: Double,
    val images: List<String>,
    val tags: List<String>
)
