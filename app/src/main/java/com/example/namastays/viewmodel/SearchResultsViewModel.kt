package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class SearchResultsUiState {
    object Idle    : SearchResultsUiState()
    object Loading : SearchResultsUiState()
    data class Success(val stays: List<PropertySearchResponse>) : SearchResultsUiState()
    data class Error(val message: String) : SearchResultsUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * FIX #17/#18/#22 — repository injected; NetworkResult handled explicitly.
 */
class SearchResultsViewModel(
    private val repository: PropertyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchResultsUiState>(SearchResultsUiState.Idle)
    val uiState: StateFlow<SearchResultsUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    fun fetchProperties(city: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.value = SearchResultsUiState.Loading
            _uiState.value = when (val result = repository.searchPropertiesByCity(city)) {
                is NetworkResult.Success        -> SearchResultsUiState.Success(result.data)
                is NetworkResult.NoConnectivity -> SearchResultsUiState.Error("No internet connection.")
                is NetworkResult.Timeout        -> SearchResultsUiState.Error("Request timed out.")
                is NetworkResult.ServerError    -> SearchResultsUiState.Error(result.message)
            }
        }
    }

    fun retry(city: String) = fetchProperties(city)

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val repository: PropertyRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SearchResultsViewModel(repository) as T
        }
    }
}