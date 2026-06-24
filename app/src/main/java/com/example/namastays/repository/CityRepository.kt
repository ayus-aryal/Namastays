package com.example.namastays.repository

import com.example.namastays.api.CitiesApiService
import com.example.namastays.dto.CityResponse
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Changes vs original:
 *
 * FIX #12 — [api] is now injected instead of accessed via CityRetrofitClient.
 * FIX #13 — Returns [NetworkResult] instead of throwing raw exceptions.
 * FIX #14 — Constructor parameter removed; unused import ([CitiesApiService]
 *            is now a constructor parameter, so the import is used).
 */
class CityRepository(
    private val api: CitiesApiService   // FIX #12 — injected
) {

    // FIX #13 — callers no longer need a try/catch; result type is explicit.
    suspend fun getCities(): NetworkResult<List<CityResponse>> {
        return try {
            NetworkResult.Success(api.getCities())
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }
}