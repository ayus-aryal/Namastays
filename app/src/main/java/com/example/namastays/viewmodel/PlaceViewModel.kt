package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PlaceUiState {
    object Idle    : PlaceUiState()
    object Loading : PlaceUiState()
    data class Success(
        val city:             CityPlacesResponse,
        val places:           List<PlaceResponse>,
        val selectedCategory: String?
    ) : PlaceUiState()
    data class Error(val message: String) : PlaceUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * FIX #15/#16/#22 — repository injected; NetworkResult handled explicitly.
 */
class PlaceViewModel(
    private val repository: PlaceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaceUiState>(PlaceUiState.Idle)
    val uiState: StateFlow<PlaceUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun loadCityWithPlaces(citySlug: String, category: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = PlaceUiState.Loading
            _uiState.value = when (val result = repository.getCityWithPlaces(citySlug, category)) {
                is NetworkResult.Success -> PlaceUiState.Success(
                    city             = result.data,
                    places           = result.data.places,
                    selectedCategory = category
                )
                is NetworkResult.NoConnectivity -> PlaceUiState.Error("No internet connection.")
                is NetworkResult.Timeout        -> PlaceUiState.Error("Request timed out.")
                is NetworkResult.ServerError    -> PlaceUiState.Error(result.message)
            }
        }
    }

    fun retry(citySlug: String, category: String? = null) =
        loadCityWithPlaces(citySlug, category)

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: PlaceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaceViewModel(repository) as T
        }
    }
}