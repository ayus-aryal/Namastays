package com.example.namastays.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.namastays.data.ContactRepository
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.SosForegroundService
import com.example.namastays.data.SosManager
import com.example.namastays.data.SosPermissionHelper
import com.example.namastays.data.SosPermissionStatus
import com.example.namastays.data.SosState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SafetyViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    // FIX V2 — cached once; avoids re-invoking getApplication() on every access.
    private val appContext: Context = application.applicationContext

    // FIX V1 — route through ContactRepository instead of accessing the DAO
    // directly. ContactRepository is the declared owner of contact data access;
    // bypassing it made the repository dead code and put data logic in the VM.
    private val contactRepository = ContactRepository(
        SafetyDatabase.getInstance(appContext).contactDao()
    )

    // ── Contacts ──────────────────────────────────────────────────────────────

    val contacts: StateFlow<List<EmergencyContactEntity>> = contactRepository
        .getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            contactRepository.addContact(
                EmergencyContactEntity(name = name, phone = phone, relation = relation)
            )
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch { contactRepository.deleteContact(contact) }
    }

    // ── SOS state ─────────────────────────────────────────────────────────────

    val sosState: StateFlow<SosState> = SosForegroundService.sosState

    // ── Permission status ─────────────────────────────────────────────────────

    private val _permissionStatus = MutableStateFlow(SosPermissionHelper.getStatus(appContext))
    val permissionStatus: StateFlow<SosPermissionStatus> = _permissionStatus.asStateFlow()

    fun refreshPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            // FIX V2 — appContext already cached; no repeated getApplication() call.
            _permissionStatus.value = SosPermissionHelper.getStatus(appContext)
        }
    }

    // ── SOS actions ───────────────────────────────────────────────────────────

    fun startSos() {
        SosManager.setPendingContacts(contacts.value)
        SosForegroundService.start(appContext)
    }

    fun cancelSos() { SosForegroundService.cancel(appContext) }
    fun stopSos()   { SosForegroundService.stop(appContext) }
    fun resetSosState() { SosForegroundService.resetState() }

    override fun onCleared() {
        super.onCleared()
        // Intentionally not stopping the service — it must outlive the ViewModel
        // to keep SOS active during config changes.
    }
}