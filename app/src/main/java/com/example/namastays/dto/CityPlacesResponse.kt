package com.example.namastays.dto

data class CityPlacesResponse(
    val name: String,
    val slug: String,
    val imageUrl: String?,
    val placesCount: Int,
    val places: List<PlaceResponse>
)