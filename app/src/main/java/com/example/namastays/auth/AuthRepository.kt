package com.example.namastays.auth

import android.content.Context
import com.example.namastays.api.AppAuthApiService
import com.example.namastays.dto.GoogleLoginRequest
import com.example.namastays.dto.LogoutRequest
import com.example.namastays.dto.RefreshRequest
import com.example.namastays.repository.NetworkResult
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

class AuthRepository(
    private val googleAuthManager: GoogleAuthManager,
    private val appAuthApi: AppAuthApiService,
    private val tokenManager: TokenManager
) {

    /**
     * Full sign-in flow: device-level Google picker, then exchange with
     * our backend. Returns AuthResult, not NetworkResult — see AuthResult
     * for why these are kept separate.
     */
    suspend fun signInWithGoogle(context: Context): AuthResult {
        val idToken = when (val googleResult = googleAuthManager.signIn(context)) {
            is GoogleSignInResult.Success -> googleResult.idToken
            GoogleSignInResult.Cancelled -> return AuthResult.Cancelled
            GoogleSignInResult.NoAccountsAvailable -> return AuthResult.NoAccountsAvailable
            is GoogleSignInResult.Failure -> return AuthResult.Failure(googleResult.message)
        }

        return try {
            val response = appAuthApi.loginWithGoogle(GoogleLoginRequest(idToken))
            tokenManager.saveSession(response.accessToken, response.refreshToken, response.expiresIn)
            AuthResult.Success
        } catch (e: HttpException) {
            // 401 here means the backend rejected the Google token itself
            // (expired, bad signature, audience mismatch) — distinct from
            // a network problem, but the user-facing action is the same:
            // try signing in again.
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("AuthRepository", "Google login failed: code=${e.code()} body=$errorBody")
            AuthResult.Failure("Sign-in failed. Please try again.")
        } catch (e: SocketTimeoutException) {
            AuthResult.Failure("Request timed out. Please check your connection and try again.")
        } catch (e: IOException) {
            AuthResult.Failure("No internet connection. Please try again.")
        } catch (e: Exception) {
            AuthResult.Failure("Something went wrong. Please try again.")
        }

    }

    /**
     * Silent refresh — called by splash on cold start (when the access
     * token has expired but a refresh token exists) and later by the
     * OkHttp Authenticator on a 401 mid-session.
     *
     * On a 401 specifically, the refresh token itself is dead (expired,
     * already rotated, or reused/theft-detected) — there's no path to
     * recovery except a full re-login, so we proactively clear local
     * tokens here rather than leaving stale, unusable values around.
     */
    suspend fun refresh(): NetworkResult<Unit> {
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken == null) {
            tokenManager.clear()
            return NetworkResult.ServerError("No active session")
        }

        return try {
            val response = appAuthApi.refresh(RefreshRequest(refreshToken))
            tokenManager.saveSession(response.accessToken, response.refreshToken, response.expiresIn)
            NetworkResult.Success(Unit)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                tokenManager.clear()
            }
            NetworkResult.ServerError("Session expired")
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }

    /**
     * Logout is intentionally best-effort on the network side — local
     * tokens are cleared regardless of whether the backend call succeeds,
     * since the user's intent ("log me out of this device") must be
     * honored locally even if, say, they're offline at the moment.
     */
    suspend fun logout(): NetworkResult<Unit> {
        val refreshToken = tokenManager.getRefreshToken()

        return try {
            if (refreshToken != null) {
                appAuthApi.logout(LogoutRequest(refreshToken))
            }
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            // Swallow network errors here — logout proceeds locally either way.
            NetworkResult.Success(Unit)
        } finally {
            tokenManager.clear()
        }
    }

    suspend fun logoutAllDevices(): NetworkResult<Unit> {
        return try {
            appAuthApi.logoutAllDevices()
            tokenManager.clear()
            NetworkResult.Success(Unit)
        } catch (e: HttpException) {
            NetworkResult.ServerError("Could not log out of all devices")
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }

    /** Quick local check — used by splash before deciding whether a
     *  network refresh call is even needed. */
    fun hasStoredSession(): Boolean = tokenManager.hasRefreshToken()

    fun hasValidAccessToken(): Boolean = tokenManager.isAccessTokenValid()
}