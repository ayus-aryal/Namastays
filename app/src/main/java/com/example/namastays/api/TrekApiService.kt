package com.example.namastays.api

import com.example.namastays.dto.TrekApiModel
import retrofit2.http.GET
import retrofit2.http.Path

interface TrekApiService {

    // Returns all treks — itineraryDays and highlights are empty lists here.
    @GET("api/treks")
    suspend fun getAllTreks(): List<TrekApiModel>

    // Returns a single trek with fully populated itineraryDays and highlights.
    @GET("api/treks/{id}")
    suspend fun getTrekById(@Path("id") id: String): TrekApiModel
}