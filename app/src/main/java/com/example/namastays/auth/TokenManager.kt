package com.example.namastays.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Encrypted, on-device storage for the app's own session tokens
 * (issued by /app-auth/google and /app-auth/refresh) — never Google's
 * raw ID token, which is single-use and discarded right after exchange.
 *
 * Access token: short-lived JWT, attached to every authenticated request.
 * Refresh token: long-lived opaque string, used only to silently mint a
 * new access token when the old one expires.
 */
class TokenManager(context: Context) {

    private val appContext = context.applicationContext

    // Lazy: EncryptedSharedPreferences setup touches the Keystore and disk,
    // so we don't want this running on app start unless something actually
    // needs a token — which, given our splash-first flow, is immediately,
    // but still better to not pay this cost at class-construction time.
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    private fun buildPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: GeneralSecurityException) {
            // Corrupted Keystore entry — can happen after a device restore
            // to different hardware, or an OS-level Keystore reset. The
            // previously stored bytes are unrecoverable either way, so the
            // only safe move is to wipe and start clean — NOT crash on
            // every app launch from here on.
            Log.w("TokenManager", "Encrypted prefs corrupted, resetting", e)
            appContext.deleteSharedPreferences(PREFS_FILE_NAME)
            createEncryptedPrefs()
        } catch (e: IOException) {
            Log.w("TokenManager", "Encrypted prefs IO failure, resetting", e)
            appContext.deleteSharedPreferences(PREFS_FILE_NAME)
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        val expiresAtMillis = System.currentTimeMillis() + (expiresInSeconds * 1000)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtMillis)
            .apply()
    }

    /** Updates only the access token + expiry — used after a silent refresh
     *  that issues a new access token but keeps (or rotates) the refresh token
     *  separately via [saveSession] being called again with the new pair. */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun isAccessTokenValid(): Boolean {
        val token = getAccessToken()
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        // 30-second buffer: avoids a token that's technically still valid
        // by the time we read it, but expires mid-flight before the
        // request actually reaches the backend.
        return token != null && expiresAt > System.currentTimeMillis() + 30_000
    }

    fun hasRefreshToken(): Boolean = !getRefreshToken().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "namastays_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
    }
}