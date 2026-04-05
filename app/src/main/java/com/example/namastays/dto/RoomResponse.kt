package com.example.namastays.dto

data class RoomResponse(
    val id: String?,
    val category: String,
    val maxGuests: Int,
    val bedType: String,
    val totalRooms: Int,
    val pricePerNight: Int
)