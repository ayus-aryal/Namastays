package com.example.namastays.api

import com.example.namastays.auth.TokenManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

internal object ApiClient {

    private const val BASE_URL = "https://namastays-backend.onrender.com/api/v1/"

    lateinit var tokenManager: TokenManager
        private set

    fun init(tokenManager: TokenManager) {
        this.tokenManager = tokenManager
    }

    // ── Auth-only client ──────────────────────────────────────────────────
    // Deliberately has NO AuthInterceptor and NO Authenticator attached.
    // Used only by TokenAuthenticator to perform the refresh call itself —
    // if this shared the main client's authenticator, a failing refresh
    // would recursively re-trigger authentication on itself.
    private val authOnlyOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val authOnlyRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authOnlyOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val authOnlyAppAuthApi: AppAuthApiService by lazy {
        authOnlyRetrofit.create(AppAuthApiService::class.java)
    }

    // ── Main client — used by every feature repository ──────────────────
    internal val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenManager))
            .authenticator(TokenAuthenticator(tokenManager) { authOnlyAppAuthApi })
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

    val notificationApi: NotificationApiService by lazy { retrofit.create(NotificationApiService::class.java) }
}