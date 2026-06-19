package com.example.namastays.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.SosForegroundService
import com.example.namastays.data.SosPermissionHelper
import com.example.namastays.data.SosPermissionStatus
import com.example.namastays.data.SosState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SafetyViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    // ── DB ────────────────────────────────────────────────────────────────────

    private val dao = SafetyDatabase.getInstance(context).contactDao()

    val contacts: StateFlow<List<EmergencyContactEntity>> = dao
        .getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addContact(name: String, phone: String, relation: String) {
        viewModelScope.launch {
            dao.insertContact(
                EmergencyContactEntity(name = name, phone = phone, relation = relation)
            )
        }
    }

    fun deleteContact(contact: EmergencyContactEntity) {
        viewModelScope.launch { dao.deleteContact(contact) }
    }

    // ── SOS state (from service) ──────────────────────────────────────────────
    // The service owns the ground truth. ViewModel just exposes it.

    val sosState: StateFlow<SosState> = SosForegroundService.sosState

    // ── Permission status ─────────────────────────────────────────────────────

    private val _permissionStatus = MutableStateFlow(SosPermissionHelper.getStatus(context))
    val permissionStatus: StateFlow<SosPermissionStatus> = _permissionStatus.asStateFlow()

    /**
     * Re-checks permission status. Called on screen resume.
     *
     * PackageManager queries can touch binder on some OEMs and on low-end
     * devices after a cold boot — run on IO to keep the UI thread clear
     * during the resume transition (exactly when jank is most visible).
     */
    fun refreshPermissions() {
        viewModelScope.launch(Dispatchers.IO) {
            val status = SosPermissionHelper.getStatus(context)
            _permissionStatus.value = status
        }
    }

    // ── SOS actions ───────────────────────────────────────────────────────────

    fun startSos() {
        val currentContacts = contacts.value
        com.example.namastays.data.SosManager.setPendingContacts(currentContacts)
        SosForegroundService.start(context)
    }

    fun cancelSos() {
        SosForegroundService.cancel(context)
    }

    fun stopSos() {
        SosForegroundService.stop(context)
    }

    fun resetSosState() {
        SosForegroundService.resetState()
    }

    override fun onCleared() {
        super.onCleared()
        // Intentionally not stopping the service here — it must outlive
        // the ViewModel to keep SOS active during config changes.
    }
}