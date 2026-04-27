package com.example.namastays.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.CityResponse
import com.example.namastays.repository.CityRepository
import kotlinx.coroutines.launch

class CityViewModel : ViewModel() {

    private val cityRepository = CityRepository()

    var cities by mutableStateOf<List<CityResponse>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        fetchCities()
    }

    private fun fetchCities() {
        viewModelScope.launch {
            isLoading = true
            error = null

            try {
                cities = cityRepository.getCities()
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
}