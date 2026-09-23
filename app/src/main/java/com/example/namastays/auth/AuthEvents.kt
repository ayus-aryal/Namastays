package com.example.namastays.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * App-wide signal for "the session died mid-use and can't be silently
 * recovered" — distinct from the splash-time auth check, which only runs
 * once at cold start. Emitted by [TokenAuthenticator] when a background
 * token refresh fails with a genuine rejection (not just no connectivity).
 *
 * Collected once, at the NavController's lifetime (see MainScreen), and
 * routed through the exact same "clear back stack, go to login" call
 * already used for manual logout in ProfileScreen — deliberately not a
 * second, parallel navigation mechanism.
 */
object AuthEvents {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired

    fun notifySessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }
}