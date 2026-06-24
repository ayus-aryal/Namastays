package com.example.namastays.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.util.MBTilesLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class TrekUiState {
    object Loading : TrekUiState()
    data class Success(
        val treks:        List<TrekItem>,
        val isRefreshing: Boolean = false
    ) : TrekUiState()
    data class Error(
        val treks:         List<TrekItem>,
        val networkResult: NetworkResult<*>
    ) : TrekUiState()
}

class TreksViewModel(
    private val repository: TrekRepository,
    private val appContext: Context
) : ViewModel() {

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _uiState = MutableStateFlow<TrekUiState>(TrekUiState.Loading)
    val uiState: StateFlow<TrekUiState> = _uiState.asStateFlow()

    private val _cachedTreks = MutableStateFlow<List<TrekItem>>(emptyList())

    init {
        observeLocalTreks()
        refresh()
    }

    // ── Room observer ─────────────────────────────────────────────────────────

    private fun observeLocalTreks() {
        viewModelScope.launch {
            repository.getAllTreks()
                .flowOn(Dispatchers.IO)
                .distinctUntilChanged()
                .debounce(300L)
                .collect { treks ->
                    _cachedTreks.value = treks

                    val current = _uiState.value
                    _uiState.value = when {
                        current is TrekUiState.Loading && treks.isNotEmpty() ->
                            TrekUiState.Success(treks)
                        current is TrekUiState.Success ->
                            current.copy(treks = treks)
                        current is TrekUiState.Error ->
                            current.copy(treks = treks)
                        else -> current
                    }
                    refreshDownloadedIds()
                }
        }
    }

    // ── Network refresh ───────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val cached  = _cachedTreks.value
            val current = _uiState.value
            _uiState.value = when (current) {
                is TrekUiState.Error -> {
                    if (cached.isNotEmpty()) TrekUiState.Success(cached, isRefreshing = true)
                    else TrekUiState.Loading
                }
                is TrekUiState.Success -> current.copy(isRefreshing = true)
                else -> TrekUiState.Loading
            }

            // FIX — NetworkResult is now generic; use is NetworkResult.Success<*>
            // (wildcard) because the type parameter is Unit and we don't need the value.
            when (val result = repository.refreshTreks()) {
                is NetworkResult.Success<*> -> {
                    val updated = _uiState.value
                    if (updated is TrekUiState.Success) {
                        _uiState.value = updated.copy(isRefreshing = false)
                    }
                }
                else -> {
                    _uiState.value = TrekUiState.Error(
                        treks         = _cachedTreks.value,
                        networkResult = result
                    )
                }
            }
        }
    }

    // ── Downloaded IDs ────────────────────────────────────────────────────────

    private suspend fun refreshDownloadedIds() {
        withContext(Dispatchers.IO) {
            val ids = _cachedTreks.value
                .filter { MBTilesLoader.isDownloaded(appContext, it.id) }
                .map { it.id }
                .toSet()
            _downloadedIds.value = ids
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val repository: TrekRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TreksViewModel(repository, appContext) as T
        }
    }
}