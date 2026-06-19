package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.CityResponse
import com.example.namastays.repository.CityRepository
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

class CityViewModel : ViewModel() {

    private val cityRepository = CityRepository()

    // StateFlow instead of mutableStateOf:
    //   - Atomic writes: no partial-state recomposition between isLoading and cities
    //   - Thread-safe: safe to update from any dispatcher
    //   - Lifecycle-aware collection via collectAsStateWithLifecycle()
    private val _uiState = MutableStateFlow<CityUiState>(CityUiState.Loading)
    val uiState: StateFlow<CityUiState> = _uiState.asStateFlow()

    init {
        fetchCities()
    }

    private fun fetchCities() {
        viewModelScope.launch {
            _uiState.value = CityUiState.Loading
            _uiState.value = try {
                CityUiState.Success(cityRepository.getCities())
            } catch (e: Exception) {
                CityUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun retry() {
        fetchCities()
    }
}