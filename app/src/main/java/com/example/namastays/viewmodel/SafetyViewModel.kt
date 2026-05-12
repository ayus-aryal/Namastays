package com.example.namastays.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.namastays.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SafetyViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SafetyDatabase.getInstance(application).contactDao()
    private val repository = ContactRepository(dao)

    val contacts: StateFlow<List<EmergencyContactEntity>> =
        repository.getAllContacts()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun addContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            repository.addContact(
                EmergencyContactEntity(
                    name = name.trim(),
                    phone = phone.trim(),
                    relation = relation.trim().ifBlank { "Contact" }
                )
            )
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
        }
    }
}