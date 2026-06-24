package com.example.namastays.api

import com.example.namastays.auth.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val accessToken = tokenManager.getAccessToken()

        val request = if (accessToken != null) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}