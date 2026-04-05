package com.example.namastays.dto

data class PropertySearchResponse(
    val id: String?,
    val propertyName: String,
    val propertyType: String,
    val propertyDescription: String?,
    val city: String,
    val state: String,
    val country: String,
    val thumbnailUrl: String?,
    val startingPrice: Int?
)
