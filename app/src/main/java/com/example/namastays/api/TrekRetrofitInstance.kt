package com.example.namastays.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object TrekRetrofitInstance {

    // Replace with your actual backend URL
    private const val BASE_URL = "https://c01d-113-199-249-19.ngrok-free.app/"

    // Explicit timeouts — default OkHttp read timeout is infinite,
    // which means a hung ngrok tunnel can block the coroutine for minutes
    // before throwing. With these set, failure is fast and predictable.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val api: TrekApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrekApiService::class.java)
    }
}