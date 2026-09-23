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
    // FIX #17 — was Error(val message: String); now carries a typed AppError
    // so CityErrorState can branch on the real failure kind instead of
    // string-matching the message text.
    data class Error(val error: AppError) : CityUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

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
                is NetworkResult.Success -> CityUiState.Success(result.data)
                else -> CityUiState.Error(
                    networkResultToAppErrorOrNull(result) ?: AppError.Server("Unknown error")
                )
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