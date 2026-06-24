package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.CityResponse
import com.example.namastays.repository.CityRepository
import com.example.namastays.repository.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class CityUiState {
    object Loading : CityUiState()
    data class Success(val cities: List<CityResponse>) : CityUiState()
    data class Error(val message: String) : CityUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Changes vs original:
 *
 * FIX #12/#13 — [repository] is now injected; [fetchCities] handles
 * [NetworkResult] instead of catching raw exceptions.
 *
 * FIX #22 — ViewModel no longer constructs CityRepository() itself.
 *            A [Factory] is provided so the call site can supply the
 *            repository (created by whatever DI mechanism is in use —
 *            manual factory, Hilt, etc.).
 */
class CityViewModel(
    private val repository: CityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CityUiState>(CityUiState.Loading)
    val uiState: StateFlow<CityUiState> = _uiState.asStateFlow()

    init {
        fetchCities()
    }

    private fun fetchCities() {
        viewModelScope.launch {
            _uiState.value = CityUiState.Loading
            _uiState.value = when (val result = repository.getCities()) {
                is NetworkResult.Success      -> CityUiState.Success(result.data)
                is NetworkResult.NoConnectivity -> CityUiState.Error("No internet connection.")
                is NetworkResult.Timeout        -> CityUiState.Error("Request timed out.")
                is NetworkResult.ServerError    -> CityUiState.Error(result.message)
            }
        }
    }

    fun retry() = fetchCities()

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: CityRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CityViewModel(repository) as T
        }
    }
}