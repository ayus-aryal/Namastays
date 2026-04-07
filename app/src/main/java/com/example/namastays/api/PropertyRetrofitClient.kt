package com.example.namastays.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PropertyRetrofitClient {

    private const val BASE_URL = "https://fd93-2407-2bc0-504-45-616c-1652-a273-5e2.ngrok-free.app/"

    val api: PropertyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PropertyApiService::class.java)
    }
}