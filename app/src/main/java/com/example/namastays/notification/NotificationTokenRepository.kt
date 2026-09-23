package com.example.namastays.notification

import android.content.Context
import android.util.Log
import com.example.namastays.api.ApiClient
import com.example.namastays.api.DeviceTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object NotificationTokenRepository {

    private const val TAG = "NotifTokenRepo"

    fun registerToken(context: Context, fcmToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "unknown"

                val response = ApiClient.notificationApi.registerDevice(
                    DeviceTokenRequest(token = fcmToken, appVersion = appVersion)
                )
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token registered")
                } else {
                    // Common cause: called before login, so AuthInterceptor sent no
                    // Authorization header and the backend returned 401/403.
                    // Harmless here, we re-register right after login succeeds.
                    Log.w(TAG, "FCM token registration failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM token registration error: ${e.message}")
            }
        }
    }

    fun unregisterToken(fcmToken: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ApiClient.notificationApi.unregisterDevice(fcmToken)
            } catch (e: Exception) {
                Log.w(TAG, "FCM token unregister failed: ${e.message}")
            }
        }
    }

    suspend fun getCurrentFcmToken(): String? = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> cont.resume(token) }
            .addOnFailureListener { cont.resume(null) }
    }
}