package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.launch

class SearchResultsViewModel : ViewModel() {

    private val repository = PropertyRepository()

    var stays = androidx.compose.runtime.mutableStateOf<List<PropertySearchResponse>>(emptyList())
        private set

    var isLoading = androidx.compose.runtime.mutableStateOf(false)
        private set

    var errorMessage = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun fetchProperties(city: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                stays.value = repository.searchPropertiesByCity(city)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Something went wrong"
            } finally {
                isLoading.value = false
            }
        }
    }
}