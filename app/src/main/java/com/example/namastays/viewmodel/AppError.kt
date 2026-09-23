package com.example.namastays.viewmodel

import com.example.namastays.repository.NetworkResult

/**
 * FIX #17 (audit): Previously every screen's Error state only carried a raw
 * String message (e.g. "Request timed out."), and the screen would then try
 * to re-derive *why* it failed by string-matching keywords like "timeout" or
 * "network" out of that message. This was duplicated near-identically in
 * CityListScreen and PlaceListScreen, and is inherently fragile — it breaks
 * silently if a message is ever reworded, localized, or comes from a
 * different exception type than the ones originally tested against.
 *
 * The actual failure kind was already known at the repository layer
 * (NetworkResult.NoConnectivity / Timeout / ServerError) — it was just being
 * discarded when ViewModels flattened it into a String before exposing it
 * via UiState. AppError preserves that type information end-to-end so the
 * UI can branch on a sealed type instead of guessing from text.
 */
sealed class AppError(val displayMessage: String) {
    object NoConnectivity : AppError("No internet connection.")
    object Timeout        : AppError("Request timed out.")
    data class Server(val reason: String) : AppError(reason)
}

/** Maps a failed [NetworkResult] to an [AppError]. Returns null for Success. */
fun <T> networkResultToAppErrorOrNull(result: NetworkResult<T>): AppError? = when (result) {
    is NetworkResult.Success        -> null
    is NetworkResult.NoConnectivity -> AppError.NoConnectivity
    is NetworkResult.Timeout        -> AppError.Timeout
    is NetworkResult.ServerError    -> AppError.Server(result.message)
}