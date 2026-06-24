package com.example.namastays.repository

import com.example.namastays.api.PropertyApiService
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.dto.PropertySearchResponse
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Changes vs original:
 *
 * FIX #17 — [api] is now injected instead of accessed via PropertyRetrofitClient.
 * FIX #18 — Both methods return [NetworkResult] instead of throwing.
 */
class PropertyRepository(
    private val api: PropertyApiService    // FIX #17 — injected
) {

    // FIX #18
    suspend fun searchPropertiesByCity(
        city: String
    ): NetworkResult<List<PropertySearchResponse>> =
        safeCall { api.searchPropertiesByCity(city) }

    suspend fun getPropertyDetailsById(
        propertyId: String
    ): NetworkResult<PropertyDetailsResponse> =
        safeCall { api.getPropertyDetailsById(propertyId) }

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