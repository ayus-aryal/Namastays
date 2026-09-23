package com.example.namastays.dto

/**
 * Mirrors backend's UserProfileResponse (GET /app-auth/me).
 *
 * email/displayName/avatarUrl are nullable to match AppUser's schema —
 * a user could theoretically have a null displayName right after their
 * very first Google sign-in if the backend hasn't backfilled it yet.
 */
data class UserProfileResponse(
    val id: String,
    val email: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val memberSinceYear: Int
)