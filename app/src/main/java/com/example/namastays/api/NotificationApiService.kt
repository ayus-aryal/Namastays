package com.example.namastays.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class DeviceTokenRequest(
    val token: String,
    val platform: String = "android",
    val appVersion: String
)

data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String>?,
    val readAt: String?,
    val createdAt: String
)


interface NotificationApiService {

    @POST("devices")
    suspend fun registerDevice(@Body request: DeviceTokenRequest): Response<Unit>

    @DELETE("devices/{token}")
    suspend fun unregisterDevice(@Path("token") token: String): Response<Unit>

    @GET("notifications")
    suspend fun getNotifications(): Response<List<NotificationDto>>

    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<Unit>
}