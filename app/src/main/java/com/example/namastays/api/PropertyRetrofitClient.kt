package com.example.namastays.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PropertyRetrofitClient {

    private const val BASE_URL = "https://9354-113-199-250-210.ngrok-free.app/"

    val api: PropertyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PropertyApiService::class.java)
    }
}