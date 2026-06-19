package com.example.namastays.trek.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationSnapshotStore {

    private val _snapshot = MutableStateFlow<TrekLocation?>(null)
    val snapshot: StateFlow<TrekLocation?> = _snapshot

    fun update(location: TrekLocation) {
        _snapshot.value = location
    }

    fun get(): TrekLocation? = _snapshot.value
}