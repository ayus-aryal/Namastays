package com.example.namastays.dto

data class PropertyDetailsResponse(
    val id: String,
    val propertyName: String,
    val propertyType: String,
    val propertyDescription: String?,
    val yearEstablished: String,
    val address: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)