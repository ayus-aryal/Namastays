package com.example.namastays.dto

data class PropertyDetailsResponse(
    val id: String?,
    val propertyName: String,
    val propertyType: String,
    val propertyDescription: String?,
    val yearEstablished: String,
    val address: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val imageUrls: List<String>,

    val checkInTime: String?,
    val checkOutTime: String?,
    val extraGuestPrice: String?,

    val smokingAllowed: Boolean?,
    val childrenAllowed: Boolean?,
    val petsAllowed: Boolean?,
    val breakfastIncluded: Boolean?,

    val cancellationPolicy: String?,

    val amenities: List<String>,
    val rooms: List<RoomResponse>

)