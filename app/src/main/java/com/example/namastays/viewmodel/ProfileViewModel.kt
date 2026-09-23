package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.auth.AuthRepository
import com.example.namastays.dto.UserProfileResponse
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI state for [ProfileScreen]. Kept separate from [LogOutUiState] since
 * the two are independent — a failed profile fetch shouldn't block the
 * user from logging out, and vice versa.
 */
sealed class ProfileUiState {
    data object Loading : ProfileUiState()
    data class Success(val profile: UserProfileResponse) : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}

/**
 * Separate from [ProfileUiState] because log-out is a one-shot action with
 * its own lifecycle (idle -> in-progress -> done), not a data-loading state
 * that the profile content depends on.
 */
sealed class LogOutUiState {
    data object Idle : LogOutUiState()
    data object InProgress : LogOutUiState()
    data object Done : LogOutUiState()
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profileState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _logOutState = MutableStateFlow<LogOutUiState>(LogOutUiState.Idle)
    val logOutState: StateFlow<LogOutUiState> = _logOutState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _profileState.update { ProfileUiState.Loading }

            val result = withContext(Dispatchers.IO) {
                userRepository.getCurrentUser()
            }

            _profileState.update {
                when (result) {
                    is NetworkResult.Success -> ProfileUiState.Success(result.data)
                    is NetworkResult.ServerError -> ProfileUiState.Error(result.message)
                    NetworkResult.Timeout -> ProfileUiState.Error("Request timed out. Please try again.")
                    NetworkResult.NoConnectivity -> ProfileUiState.Error("No internet connection.")
                }
            }
        }
    }

    /**
     * Fire-and-forget from the UI's perspective — [LogOutUiState.Done] fires
     * regardless of network outcome, since [AuthRepository.logout] already
     * guarantees local tokens are cleared either way. The screen observing
     * [logOutState] is responsible for navigating to the auth flow once it
     * sees [LogOutUiState.Done].
     */
    fun logOut() {
        viewModelScope.launch {
            _logOutState.update { LogOutUiState.InProgress }
            withContext(Dispatchers.IO) {
                authRepository.logout()
            }
            _logOutState.update { LogOutUiState.Done }
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProfileViewModel(userRepository, authRepository) as T
        }
    }
}