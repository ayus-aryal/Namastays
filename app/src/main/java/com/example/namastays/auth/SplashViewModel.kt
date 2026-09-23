package com.example.namastays.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.repository.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SplashAuthState {
    object Loading : SplashAuthState()
    object Authenticated : SplashAuthState()
    object Unauthenticated : SplashAuthState()
}

class SplashViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<SplashAuthState>(SplashAuthState.Loading)
    val authState: StateFlow<SplashAuthState> = _authState

    init {
        checkAuth()
    }

    private fun checkAuth() {
        viewModelScope.launch {
            if (!authRepository.hasStoredSession()) {
                // No refresh token at all — never logged in, or already
                // logged out. Straight to login, no network call needed.
                _authState.value = SplashAuthState.Unauthenticated
                return@launch
            }

            if (authRepository.hasValidAccessToken()) {
                // Access token still valid — skip the network entirely.
                _authState.value = SplashAuthState.Authenticated
                return@launch
            }

            // Have a refresh token, but access token is expired/missing —
            // attempt one silent refresh before giving up.
            when (authRepository.refresh()) {
                is NetworkResult.Success -> {
                    _authState.value = SplashAuthState.Authenticated
                }

                NetworkResult.NoConnectivity, NetworkResult.Timeout -> {
                    // Couldn't reach the backend to confirm — but nothing
                    // here proved the session is invalid either. Trust the
                    // cached refresh token; offline-capable screens (Trek
                    // Mode, Safety/SOS) don't need a fresh access token,
                    // and TokenAuthenticator will handle refresh-and-retry
                    // once connectivity returns for any online-only call.
                    _authState.value = SplashAuthState.Authenticated
                }

                is NetworkResult.ServerError -> {
                    // AuthRepository.refresh() only clears tokenManager on
                    // a genuine 401. Any other server error shouldn't force
                    // a logout — check whether the session actually survived.
                    _authState.value = if (authRepository.hasStoredSession()) {
                        SplashAuthState.Authenticated
                    } else {
                        SplashAuthState.Unauthenticated
                    }
                }
            }
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SplashViewModel(authRepository) as T
        }
    }
}