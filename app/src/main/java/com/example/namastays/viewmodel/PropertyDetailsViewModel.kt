package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PropertyDetailsUiState {
    object Idle    : PropertyDetailsUiState()
    object Loading : PropertyDetailsUiState()
    data class Success(val property: PropertyDetailsResponse) : PropertyDetailsUiState()
    data class Error(val message: String) : PropertyDetailsUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * FIX #17/#18/#22 — repository injected; NetworkResult handled explicitly.
 */
class PropertyDetailsViewModel(
    private val repository: PropertyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PropertyDetailsUiState>(PropertyDetailsUiState.Idle)
    val uiState: StateFlow<PropertyDetailsUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    fun fetchPropertyDetails(propertyId: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = PropertyDetailsUiState.Loading
            _uiState.value = when (val result = repository.getPropertyDetailsById(propertyId)) {
                is NetworkResult.Success        -> PropertyDetailsUiState.Success(result.data)
                is NetworkResult.NoConnectivity -> PropertyDetailsUiState.Error("No internet connection.")
                is NetworkResult.Timeout        -> PropertyDetailsUiState.Error("Request timed out.")
                is NetworkResult.ServerError    -> PropertyDetailsUiState.Error(result.message)
            }
        }
    }

    fun retry(propertyId: String) = fetchPropertyDetails(propertyId)

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: PropertyRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PropertyDetailsViewModel(repository) as T
        }
    }
}