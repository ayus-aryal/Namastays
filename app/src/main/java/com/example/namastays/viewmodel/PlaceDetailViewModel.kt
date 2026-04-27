package com.example.namastays.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.repository.PlaceRepository
import kotlinx.coroutines.launch

class PlaceDetailViewModel: ViewModel() {

    private val repository = PlaceRepository()

    var place = mutableStateOf<PlaceDetailResponse?>(null)
        private set

    var isLoading = mutableStateOf(false)
        private set

    var error = mutableStateOf<String?>(null)
        private set

    fun loadPlace(citySlug: String, placeSlug: String){
        viewModelScope.launch {
            isLoading.value = true
            error.value = null

            try{
                place.value = repository.getPlaceDetails(citySlug, placeSlug)
            }catch(e: Exception){
                error.value = e.message
            }finally{
                isLoading.value = false
            }
        }
    }
}