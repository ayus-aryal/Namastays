package com.example.namastays.repository

import com.example.namastays.api.PlaceRetrofitClient
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.dto.PlaceResponse

class PlaceRepository {

    suspend fun getPlaces(city: String, category: String?): List<PlaceResponse>{
        return PlaceRetrofitClient.api.getPlacesByCity(city, category)
    }

    suspend fun getCityWithPlaces(citySlug: String, category: String? = null): CityPlacesResponse {
        return PlaceRetrofitClient.api.getCityWithPlaces(citySlug, category)
    }

    suspend fun getPlaceDetails(citySlug: String, placeSlug: String): PlaceDetailResponse {
        return PlaceRetrofitClient.api.getPlaceDetails(citySlug, placeSlug)
    }
}