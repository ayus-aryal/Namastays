package com.example.namastays.api

import com.example.namastays.dto.AuthApiResponseBody
import com.example.namastays.dto.GoogleLoginRequest
import com.example.namastays.dto.LogoutRequest
import com.example.namastays.dto.RefreshRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AppAuthApiService {

    @POST("app-auth/google")
    suspend fun loginWithGoogle(@Body request: GoogleLoginRequest): AuthApiResponseBody

    @POST("app-auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthApiResponseBody

    @POST("app-auth/logout")
    suspend fun logout(@Body request: LogoutRequest)

    @POST("app-auth/logout-all")
    suspend fun logoutAllDevices()
}