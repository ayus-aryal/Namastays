package com.example.namastays.dto

import com.google.gson.annotations.SerializedName

data class TrekItineraryDayApiModel(
    @SerializedName("dayNumber")   val dayNumber:   Int,
    @SerializedName("title")       val title:       String,
    @SerializedName("description") val description: String
)

data class TrekHighlightApiModel(
    @SerializedName("label")    val label:    String,
    @SerializedName("iconName") val iconName: String
)