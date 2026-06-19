package com.example.namastays.viewmodel

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.namastays.NamastaysApp
import com.example.namastays.data.SafetyDatabase
import com.example.namastays.data.SleepAltitudeRecord
import com.example.namastays.data.TrekElevationPoint
import com.example.namastays.data.TrekRepository
import com.example.namastays.data.TrekSession
import com.example.namastays.dto.TrekState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * ViewModel for [TrekModeScreen] and [TrekSessionDetailScreen].
 *
 * Single source of truth:
 *   [trekState]        — live sensor data from TrekEngine
 *   [allSessions]      — closed session logbook
 *   [allSleepRecords]  — sleep altitude history
 *
 * Threading contract:
 *   All StateFlows are safe to collect on any dispatcher.
 *   Every suspend function that touches Room or the filesystem is guarded
 *   with withContext(Dispatchers.IO) so callers may invoke them from
 *   Dispatchers.Main without blocking the UI thread.
 */
class TrekViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = (application as NamastaysApp).trekEngine

    private val db   = SafetyDatabase.getInstance(application)
    private val repo = TrekRepository(
        sessionDao   = db.trekSessionDao(),
        elevationDao = db.trekElevationPointDao(),
        sleepDao     = db.sleepAltitudeDao()
    )

    // ── Live sensor state ─────────────────────────────────────────────────────

    /** Raw engine state — always up to date while Trek Mode is active. */
    val trekState: StateFlow<TrekState> = engine.state

    // ── Session logbook ───────────────────────────────────────────────────────

    /** All closed sessions, newest first. Emits an empty list when none exist. */
    val allSessions: StateFlow<List<TrekSession>> =
        repo.allSessions
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Sleep altitude history ────────────────────────────────────────────────

    /** All sleep altitude records, newest first. */
    val allSleepRecords: StateFlow<List<SleepAltitudeRecord>> =
        repo.allSleepRecords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Detail screen navigation state ───────────────────────────────────────

    /**
     * The session currently open on the detail screen, or null when the
     * logbook list is visible. Driven by [selectSession] instead of
     * NavController arguments — avoids serialising TrekSession through
     * nav args (no Parcelable needed).
     */
    private val _selectedSession = MutableStateFlow<TrekSession?>(null)
    val selectedSession: StateFlow<TrekSession?> = _selectedSession.asStateFlow()

    fun selectSession(session: TrekSession?) {
        _selectedSession.value = session
    }

    /**
     * Deletes [sessionId] and its elevation points on IO, then clears
     * [selectedSession] so the nav layer automatically pops back to the list.
     */
    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.deleteSession(sessionId)
            }
            if (_selectedSession.value?.id == sessionId) {
                _selectedSession.value = null
            }
        }
    }

    // ── Sleep altitude actions ────────────────────────────────────────────────

    /**
     * Returns true if a sleep altitude record already exists for today.
     * Safe to call from Dispatchers.Main — IO is enforced internally.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun todayRecordExists(): Boolean =
        withContext(Dispatchers.IO) {
            repo.sleepRecordForToday() != null
        }

    /**
     * Persists [altitude] as today's sleep altitude record.
     * Safe to call from Dispatchers.Main — IO is enforced internally.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveSleepAltitude(altitude: Double) =
        withContext(Dispatchers.IO) {
            repo.saveSleepAltitude(altitude)
        }

    // ── Detail screen data ────────────────────────────────────────────────────

    /**
     * Loads elevation points for [sessionId], down-sampled to 5-min buckets.
     * Falls back to raw points for short sessions (< 3 sampled points).
     * Returns an empty list on any DB error — UI must handle this.
     *
     * Safe to call from Dispatchers.Main — IO is enforced internally.
     */
    suspend fun elevationPoints(sessionId: Long): List<TrekElevationPoint> =
        withContext(Dispatchers.IO) {
            runCatching { repo.elevationPoints(sessionId) }.getOrElse { emptyList() }
        }

    /**
     * Fetches the sleep altitude record for the night *before* [sessionStartMs].
     * Used on the detail screen to show "gained X m vs last sleep altitude".
     * Returns null if no record exists for that date.
     *
     * Safe to call from Dispatchers.Main — IO is enforced internally.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sleepAltitudeBeforeSession(sessionStartMs: Long): SleepAltitudeRecord? =
        withContext(Dispatchers.IO) {
            val date = Instant
                .ofEpochMilli(sessionStartMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .minusDays(1)
                .toString()
            runCatching { repo.sleepRecordForDate(date) }.getOrNull()
        }
}