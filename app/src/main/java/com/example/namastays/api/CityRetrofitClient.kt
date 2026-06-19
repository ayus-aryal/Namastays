package com.example.namastays.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object CityRetrofitClient {
    private const val BASE_URL = "https://c01d-113-199-249-19.ngrok-free.app/"

    val api: CitiesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CitiesApiService::class.java)
    }
}

