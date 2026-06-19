package com.example.namastays.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PlaceUiState {
    object Idle : PlaceUiState()
    object Loading : PlaceUiState()
    data class Success(
        val city:             CityPlacesResponse,
        val places:           List<PlaceResponse>,
        val selectedCategory: String?
    ) : PlaceUiState()
    data class Error(val message: String) : PlaceUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlaceViewModel : ViewModel() {

    private val repository = PlaceRepository()

    private val _uiState = MutableStateFlow<PlaceUiState>(PlaceUiState.Idle)
    val uiState: StateFlow<PlaceUiState> = _uiState.asStateFlow()

    // Cancels any in-flight load when the city/category changes, preventing
    // stale responses from overwriting fresher state.
    private var loadJob: Job? = null

    fun loadCityWithPlaces(citySlug: String, category: String? = null) {
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _uiState.value = PlaceUiState.Loading
            _uiState.value = try {
                val response = repository.getCityWithPlaces(citySlug, category)

                PlaceUiState.Success(
                    city             = response,
                    places           = response.places,
                    selectedCategory = category
                )
            } catch (e: Exception) {
                PlaceUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun retry(citySlug: String, category: String? = null) {
        loadCityWithPlaces(citySlug, category)
    }
}