package com.example.namastays.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.NamastaysApp
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.SleepAltitudeRecord
import com.example.namastays.data.SleepAltitudeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TrekViewModel(application: Application) : AndroidViewModel(application) {

    private val engine =
        (application as NamastaysApp).trekEngine

    val trekState = engine.state


    private val sleepRepo = SleepAltitudeRepository(
        SafetyDatabase.getInstance(application).sleepAltitudeDao()
    )


    val allSleepRecords: StateFlow<List<SleepAltitudeRecord>> =
        sleepRepo.allRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun todayRecordExists(): Boolean = sleepRepo.getToday() != null

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveSleepAltitude(altitude: Double) = sleepRepo.save(altitude)
}