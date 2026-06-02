package com.example.namastays.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PlaceRetrofitClient {

    private const val BASE_URL = "https://8f19-113-199-255-193.ngrok-free.app/"

    val api: PlaceApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PlaceApiService::class.java)
    }
}