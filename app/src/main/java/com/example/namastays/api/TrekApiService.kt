package com.example.namastays.api

import com.example.namastays.dto.TrekApiModel
import retrofit2.http.GET
import retrofit2.http.Path

interface TrekApiService {
    @GET("api/treks")
    suspend fun getAllTreks(): List<TrekApiModel>

    @GET("api/treks/{id}")
    suspend fun getTrekById(@Path("id") id: String): TrekApiModel
}