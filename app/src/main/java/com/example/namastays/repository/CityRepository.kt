package com.example.namastays.repository

import com.example.namastays.api.CitiesApiService
import com.example.namastays.api.CityRetrofitClient
import com.example.namastays.dto.CityResponse

class CityRepository() {
    suspend fun getCities(): List<CityResponse>{
        return CityRetrofitClient.api.getCities()
    }
}