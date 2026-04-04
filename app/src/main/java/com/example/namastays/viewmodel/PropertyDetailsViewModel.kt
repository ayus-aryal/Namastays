package com.example.namastays.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.repository.PropertyRepository
import kotlinx.coroutines.launch

class PropertyDetailsViewModel : ViewModel() {

    private val repository = PropertyRepository()

    var property = androidx.compose.runtime.mutableStateOf<PropertyDetailsResponse?>(null)
        private set

    var isLoading = androidx.compose.runtime.mutableStateOf(false)
        private set

    var errorMessage = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    fun fetchPropertyDetails(propertyId: String) {
        viewModelScope.launch {
            isLoading.value = true
            errorMessage.value = null

            try {
                property.value = repository.getPropertyDetailsById(propertyId)
            } catch (e: Exception) {
                errorMessage.value = e.message ?: "Something went wrong"
            } finally {
                isLoading.value = false
            }
        }
    }
}