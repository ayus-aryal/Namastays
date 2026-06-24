package com.example.namastays.dto

data class GoogleLoginRequest(
    val idToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String
)

data class AuthApiResponseBody(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)