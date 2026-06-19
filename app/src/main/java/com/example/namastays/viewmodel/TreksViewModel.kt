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
        val networkResult: NetworkResult
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

    // Thread-safe: only ever written/read via StateFlow atomicity.
    // Replaces the previous plain `var List` that had a latent data race
    // between the Room collector and concurrent refresh() calls.
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
                // Suppress duplicate emissions (e.g. Room re-emitting the same
                // list after an unrelated table write in the same DB).
                .distinctUntilChanged()
                // Debounce rapid back-to-back emissions (network refresh writes
                // rows one-by-one on some Room versions, causing N emissions
                // for an N-trek update). 300 ms is imperceptible to the user.
                .debounce(300L)
                .collect { treks ->
                    _cachedTreks.value = treks

                    val current = _uiState.value
                    _uiState.value = when {
                        current is TrekUiState.Loading && treks.isNotEmpty() ->
                            TrekUiState.Success(treks)
                        current is TrekUiState.Success ->
                            current.copy(treks = treks)
                        // Keep Error state's trek list up to date but don't
                        // clear the error — that only happens via refresh().
                        current is TrekUiState.Error ->
                            current.copy(treks = treks)
                        else -> current
                    }

                    // refreshDownloadedIds() is triggered here — not inside
                    // refresh() — so file-system checks only run when the
                    // trek list actually changes, not on every network call.
                    refreshDownloadedIds()
                }
        }
    }

    // ── Network refresh ───────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val cached = _cachedTreks.value

            // Transition to appropriate loading state before the network call.
            val current = _uiState.value
            _uiState.value = when (current) {
                is TrekUiState.Error -> {
                    if (cached.isNotEmpty())
                        TrekUiState.Success(cached, isRefreshing = true)
                    else
                        TrekUiState.Loading
                }
                is TrekUiState.Success -> current.copy(isRefreshing = true)
                else -> TrekUiState.Loading
            }

            when (val result = repository.refreshTreks()) {
                is NetworkResult.Success -> {
                    // Room Flow delivers the updated list reactively; just
                    // clear the refreshing spinner here.
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

    // Runs on IO. Each MBTilesLoader.isDownloaded() call is a disk stat()
    // so we keep this off the main thread and only trigger it when the trek
    // list actually changes (via the distinctUntilChanged + debounce above).
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