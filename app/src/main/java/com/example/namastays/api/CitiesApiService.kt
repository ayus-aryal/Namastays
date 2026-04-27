package com.example.namastays.api

import com.example.namastays.dto.CityResponse
import retrofit2.http.GET

interface CitiesApiService {

    @GET("cities")
    suspend fun getCities(): List<CityResponse>
}