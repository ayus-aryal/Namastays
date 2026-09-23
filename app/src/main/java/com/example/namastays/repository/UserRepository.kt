package com.example.namastays.repository

import com.example.namastays.api.AppAuthApiService
import com.example.namastays.dto.UserProfileResponse
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Fetches the authenticated user's own profile (GET /app-auth/me).
 *
 * Deliberately thin — no Room caching for now. Profile data is small,
 * cheap to refetch, and changes rarely, so the added complexity of a
 * cache-invalidation story isn't worth it yet. If Profile needs to render
 * instantly offline later, revisit this the same way TrekRepository caches
 * treks.
 */
class UserRepository(
    private val api: AppAuthApiService
) {

    suspend fun getCurrentUser(): NetworkResult<UserProfileResponse> {
        return try {
            NetworkResult.Success(api.getCurrentUser())
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Access token was invalid/expired and the request reached
                // here anyway — the OkHttp Authenticator handles proactive
                // refresh, so a 401 surfacing this far means refresh also
                // failed. Treat it the same as any other session-dead case.
                NetworkResult.ServerError("Session expired")
            } else {
                NetworkResult.ServerError("Could not load profile")
            }
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }
}