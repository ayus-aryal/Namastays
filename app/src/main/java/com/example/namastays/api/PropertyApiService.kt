package com.example.namastays.api

import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.dto.PropertySearchResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PropertyApiService {

    @GET("/api/properties/search")
    suspend fun searchPropertiesByCity(
        @Query("city") city: String
    ): List<PropertySearchResponse>

    @GET("/api/properties/{id}")
    suspend fun getPropertyDetailsById(
        @Path("id") propertyId: String
    ): PropertyDetailsResponse
}