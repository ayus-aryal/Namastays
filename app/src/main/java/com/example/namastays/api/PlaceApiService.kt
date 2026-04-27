package com.example.namastays.api

import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.dto.PlaceResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PlaceApiService {

    @GET("places")
    suspend fun getPlacesByCity(
        @Query("city") city: String,
        @Query("category") category: String? = null
    ): List<PlaceResponse>

    @GET("places/{citySlug}/{placeSlug}")
    suspend fun getPlaceDetails(
        @Path("citySlug") citySlug: String,
        @Path("placeSlug") placeSlug: String
    ): PlaceDetailResponse
}