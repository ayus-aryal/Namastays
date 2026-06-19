package com.example.namastays.dto

import java.util.UUID

data class PlaceDetailResponse(
    val id: UUID?,
    val name: String,
    val description: String?,
    val lat: Double,
    val lng: Double,
    val images: List<String>,
    val tags: List<String>
)
