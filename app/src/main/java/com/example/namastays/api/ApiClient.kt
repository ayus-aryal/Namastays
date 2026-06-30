package com.example.namastays.api

import com.example.namastays.auth.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single shared Retrofit/OkHttp instance for all API services.
 *
 * Previously each of the four feature areas (Trek, City, Place, Property)
 * built its own OkHttpClient and Retrofit instance independently, meaning
 * four connection pools, four thread pools, and no shared timeout config.
 *
 * All services share the same BASE_URL (one ngrok tunnel). Services that
 * ever need a different host can be given their own Retrofit instance built
 * on top of the same [okHttpClient].
 *
 * This object is still a singleton — constructor injection (fix #22) should
 * inject the individual service interfaces, not this object directly.
 */
internal object ApiClient {

    private const val BASE_URL = "https://054e-2407-1400-aa32-9a98-55b7-7b91-dc8c-885e.ngrok-free.app/"

    lateinit var tokenManager: TokenManager
        private set

    fun init(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
    }

    internal val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager))
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val trekApi:     TrekApiService     by lazy { retrofit.create(TrekApiService::class.java) }
    val cityApi:     CitiesApiService   by lazy { retrofit.create(CitiesApiService::class.java) }
    val placeApi:    PlaceApiService    by lazy { retrofit.create(PlaceApiService::class.java) }
    val propertyApi: PropertyApiService by lazy { retrofit.create(PropertyApiService::class.java) }
    val appAuthApi:  AppAuthApiService  by lazy { retrofit.create(AppAuthApiService::class.java) }
}