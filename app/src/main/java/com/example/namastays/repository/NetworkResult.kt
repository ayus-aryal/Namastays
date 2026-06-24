package com.example.namastays.repository

/**
 * Unified result type used by every repository in the app.
 *
 * Previously only TrekRepository used this; CityRepository, PlaceRepository,
 * and PropertyRepository threw raw exceptions. Now all four use the same type
 * so ViewModels have a consistent contract and can't forget to handle errors.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T)        : NetworkResult<T>()
    object NoConnectivity                      : NetworkResult<Nothing>()
    object Timeout                             : NetworkResult<Nothing>()
    data class ServerError(val message: String): NetworkResult<Nothing>()
}