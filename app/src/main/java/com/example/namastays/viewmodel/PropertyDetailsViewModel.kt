package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class PropertyDetailsUiState {
    object Idle : PropertyDetailsUiState()
    object Loading : PropertyDetailsUiState()
    data class Success(val property: PropertyDetailsResponse) : PropertyDetailsUiState()
    data class Error(val message: String) : PropertyDetailsUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PropertyDetailsViewModel : ViewModel() {

    private val repository = PropertyRepository()

    private val _uiState = MutableStateFlow<PropertyDetailsUiState>(PropertyDetailsUiState.Idle)
    val uiState: StateFlow<PropertyDetailsUiState> = _uiState.asStateFlow()

    // Cancels any in-flight fetch if fetchPropertyDetails() is called again
    // before the previous one completes (e.g. deep-link re-entry).
    private var fetchJob: Job? = null

    fun fetchPropertyDetails(propertyId: String) {
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            _uiState.value = PropertyDetailsUiState.Loading
            _uiState.value = try {
                PropertyDetailsUiState.Success(repository.getPropertyDetailsById(propertyId))
            } catch (e: Exception) {
                PropertyDetailsUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun retry(propertyId: String) {
        fetchPropertyDetails(propertyId)
    }
}