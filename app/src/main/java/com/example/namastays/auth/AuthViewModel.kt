package com.example.namastays.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/**
 * Backs LoginScreen specifically — separate from SplashViewModel since
 * this one lives only as long as the login screen is on-screen and
 * handles a button tap, not a one-shot startup decision.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(context: Context) {
        // Guard against double-tap firing two concurrent sign-in attempts —
        // Credential Manager's picker can be tapped multiple times before
        // the first call returns.
        if (_uiState.value is LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            _uiState.value = when (val result = authRepository.signInWithGoogle(context)) {
                AuthResult.Success -> LoginUiState.Success
                AuthResult.Cancelled -> LoginUiState.Idle // user backed out — not an error, no message needed
                AuthResult.NoAccountsAvailable -> LoginUiState.Error(
                    "No Google account found on this device. Please add one in Settings."
                )
                is AuthResult.Failure -> LoginUiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}