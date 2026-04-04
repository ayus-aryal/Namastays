package com.example.namastays.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PropertyRetrofitClient {

    private const val BASE_URL = "https://3caa-2407-2bc0-504-45-e406-e1c1-850b-456.ngrok-free.app/"

    val api: PropertyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PropertyApiService::class.java)
    }
}