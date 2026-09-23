package com.example.namastays.api

import com.example.namastays.auth.AuthEvents
import com.example.namastays.auth.TokenManager
import com.example.namastays.dto.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.HttpException
import java.io.IOException

/**
 * Handles a 401 encountered mid-session on any authenticated request
 * (Property, Trek, City, Place APIs) — attempts one silent token refresh
 * and retries the original request; only lets the 401 propagate if
 * refresh itself fails.
 *
 * Deliberately uses a SEPARATE Retrofit/OkHttp instance for the refresh
 * call itself (passed in via [authApi], built without this authenticator
 * attached) — otherwise a failing refresh call would recursively trigger
 * this same authenticator, since it also carries a Bearer token.
 *
 * Runs on OkHttp's dispatcher thread, not a coroutine scope — authenticate()
 * is a synchronous callback, so the suspend refresh call is bridged with
 * runBlocking here. This is standard for OkHttp Authenticators; it blocks
 * one dispatcher thread briefly, not the caller's original thread.
 */
class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: () -> AppAuthApiService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid retry loops: if we've already retried this request chain
        // once, don't try again — whatever's wrong isn't fixed by a second
        // refresh attempt.
        if (responseCount(response) >= 2) {
            return null
        }

        val refreshToken = tokenManager.getRefreshToken() ?: return null

        return try {
            val result = runBlocking {
                authApi().refresh(RefreshRequest(refreshToken))
            }
            tokenManager.saveSession(result.accessToken, result.refreshToken, result.expiresIn)

            response.request.newBuilder()
                .header("Authorization", "Bearer ${result.accessToken}")
                .build()
        } catch (e: HttpException) {
            if (e.code() == 401) {
                // Refresh token itself is genuinely dead — no path to
                // silent recovery. Clear local state and tell the app
                // to route to login.
                tokenManager.clear()
                AuthEvents.notifySessionExpired()
            }
            // Any other HTTP error (5xx, etc.): don't clear tokens or force
            // logout — just let this particular request's 401 propagate as
            // a normal ServerError; the session may still be fine.
            null
        } catch (e: IOException) {
            // No connectivity / timeout during the refresh attempt — the
            // session isn't proven invalid, just unreachable. Don't clear
            // anything or force a logout; let the original 401 surface as
            // a normal network error for this one request.
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}