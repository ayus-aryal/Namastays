package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.namastays.data.CityPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * FIX #5 (audit): ExploreScreen previously had no ViewModel at all — it
 * instantiated CityPreferences directly inside the composable via
 * remember { CityPreferences(context) } and ran its navigation side effect
 * straight inside a LaunchedEffect in the UI layer. Every other screen in
 * this flow (CityListScreen, PlaceListScreen, PlaceDetailScreen) follows a
 * ViewModel + sealed UiState + Factory pattern. ExploreScreen now follows
 * the same shape so the flow is architecturally consistent end to end.
 *
 * FIX #6 (audit): the original code did
 *   cityPreferences.savedCity.collect { saved -> if (saved != null) navigate(...) }
 * inside LaunchedEffect(Unit) with no guard. Nothing structurally prevented
 * a second non-null emission (e.g. the preference being touched elsewhere
 * while this screen was still alive/recomposing) from firing navigate()
 * again and pushing a duplicate back-stack entry.
 *
 * Fix: this ViewModel exposes a sealed [ExploreUiState] where the resolved
 * city slug — not a raw nullable string — is the thing the screen keys its
 * one-shot navigation effect on. Combined with [distinctUntilChanged] here,
 * the state only changes (and therefore the screen's LaunchedEffect(state)
 * only re-fires) when the *value* actually changes, not on every emission
 * of the underlying Flow. A repeat emission of the same saved city will not
 * trigger a duplicate navigation.
 */
sealed class ExploreUiState {
    /** Still reading the DataStore preference — screen shows a loading indicator, not a blank screen (FIX #7). */
    object Loading : ExploreUiState()

    /** Resolved: no city saved yet — screen shows the "Select a City" landing UI. */
    object NoCityChosen : ExploreUiState()

    /** Resolved: a city is already saved — screen should navigate to its place list. */
    data class CityChosen(val slug: String) : ExploreUiState()
}

class ExploreViewModel(
    private val cityPreferences: CityPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<ExploreUiState>(ExploreUiState.Loading)
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        cityPreferences.savedCity
            .distinctUntilChanged()
            .onEach { saved ->
                _uiState.value = if (saved != null) {
                    ExploreUiState.CityChosen(saved.slug)
                } else {
                    ExploreUiState.NoCityChosen
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(private val cityPreferences: CityPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ExploreViewModel(cityPreferences) as T
        }
    }
}