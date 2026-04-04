package com.example.namastays.repository

import com.example.namastays.api.PropertyRetrofitClient
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.dto.PropertySearchResponse

class PropertyRepository {

    suspend fun searchPropertiesByCity(city: String): List<PropertySearchResponse>{
        return PropertyRetrofitClient.api.searchPropertiesByCity(city)
    }

    suspend fun getPropertyDetailsById(propertyId: String): PropertyDetailsResponse{
        return PropertyRetrofitClient.api.getPropertyDetailsById(propertyId)
    }
}