package com.example.namastays.dto

import java.util.UUID

data class PlaceResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val image: String?,
    val categories: List<String>
)
