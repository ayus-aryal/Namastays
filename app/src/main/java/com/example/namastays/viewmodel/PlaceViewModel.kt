package com.example.namastays.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.launch

class PlaceViewModel : ViewModel() {

    private val repository = PlaceRepository()

    var places by mutableStateOf<List<PlaceResponse>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var selectedCategory by mutableStateOf<String?>(null)
        private set

    fun loadPlaces(city: String, category: String? = null) {
        viewModelScope.launch {
            isLoading = true
            error = null

            try {
                selectedCategory = category
                places = repository.getPlaces(city, category)
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }
}