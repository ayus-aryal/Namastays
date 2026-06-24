package com.example.namastays.repository

import com.example.namastays.api.PlaceApiService
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.dto.PlaceResponse
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Changes vs original:
 *
 * FIX #15 — [api] is now injected instead of accessed via PlaceRetrofitClient.
 * FIX #16 — All three methods return [NetworkResult] instead of throwing.
 */
class PlaceRepository(
    private val api: PlaceApiService    // FIX #15 — injected
) {

    // FIX #16
    suspend fun getPlaces(
        city:     String,
        category: String?
    ): NetworkResult<List<PlaceResponse>> = safeCall { api.getPlacesByCity(city, category) }

    suspend fun getCityWithPlaces(
        citySlug: String,
        category: String? = null
    ): NetworkResult<CityPlacesResponse> = safeCall { api.getCityWithPlaces(citySlug, category) }

    suspend fun getPlaceDetails(
        citySlug:  String,
        placeSlug: String
    ): NetworkResult<PlaceDetailResponse> = safeCall { api.getPlaceDetails(citySlug, placeSlug) }

    // Shared error-handling wrapper — keeps each method free of boilerplate.
    private inline fun <T> safeCall(block: () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(block())
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }
}