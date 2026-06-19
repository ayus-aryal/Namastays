package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class SearchResultsUiState {
    object Idle : SearchResultsUiState()
    object Loading : SearchResultsUiState()
    data class Success(val stays: List<PropertySearchResponse>) : SearchResultsUiState()
    data class Error(val message: String) : SearchResultsUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SearchResultsViewModel : ViewModel() {

    private val repository = PropertyRepository()

    private val _uiState = MutableStateFlow<SearchResultsUiState>(SearchResultsUiState.Idle)
    val uiState: StateFlow<SearchResultsUiState> = _uiState.asStateFlow()

    // Cancels any in-flight search if the user changes the city before
    // the previous response arrives.
    private var fetchJob: Job? = null

    fun fetchProperties(city: String) {
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            _uiState.value = SearchResultsUiState.Loading
            _uiState.value = try {
                SearchResultsUiState.Success(repository.searchPropertiesByCity(city))
            } catch (e: Exception) {
                SearchResultsUiState.Error(e.message ?: "Something went wrong")
            }
        }
    }

    fun retry(city: String) {
        fetchProperties(city)
    }
}