package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PlaceDetailUiState {
    object Idle : PlaceDetailUiState()
    object Loading : PlaceDetailUiState()
    data class Success(val place: PlaceDetailResponse) : PlaceDetailUiState()
    data class Error(val message: String) : PlaceDetailUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlaceDetailViewModel : ViewModel() {

    private val repository = PlaceRepository()

    private val _uiState = MutableStateFlow<PlaceDetailUiState>(PlaceDetailUiState.Idle)
    val uiState: StateFlow<PlaceDetailUiState> = _uiState.asStateFlow()

    // Tracks the in-flight load so rapid back/forward navigation cancels the
    // previous request instead of letting two coroutines race to write state.
    private var loadJob: Job? = null

    fun loadPlace(citySlug: String, placeSlug: String) {
        // Cancel any in-flight request for a previous slug combination.
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = PlaceDetailUiState.Loading
            _uiState.value = try {
                PlaceDetailUiState.Success(repository.getPlaceDetails(citySlug, placeSlug))
            } catch (e: Exception) {
                PlaceDetailUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun retry(citySlug: String, placeSlug: String) {
        loadPlace(citySlug, placeSlug)
    }
}