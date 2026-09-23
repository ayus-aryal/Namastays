package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PlaceDetailUiState {
    object Idle    : PlaceDetailUiState()
    object Loading : PlaceDetailUiState()
    data class Success(val place: PlaceDetailResponse) : PlaceDetailUiState()
    // FIX #17 — typed AppError instead of String message
    data class Error(val error: AppError) : PlaceDetailUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlaceDetailViewModel(
    private val repository: PlaceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaceDetailUiState>(PlaceDetailUiState.Idle)
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadPlace(citySlug: String, placeSlug: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlaceDetailUiState.Loading
            _uiState.value = when (val result = repository.getPlaceDetails(citySlug, placeSlug)) {
                is NetworkResult.Success -> PlaceDetailUiState.Success(result.data)
                else -> PlaceDetailUiState.Error(
                    networkResultToAppErrorOrNull(result) ?: AppError.Server("Unknown error")
                )
            }
        }
    }

    fun retry(citySlug: String, placeSlug: String) = loadPlace(citySlug, placeSlug)

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: PlaceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaceDetailViewModel(repository) as T
        }
    }
}