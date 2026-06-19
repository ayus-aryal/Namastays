package com.example.namastays.data

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TrekRepository(
    private val sessionDao   : TrekSessionDao,
    private val elevationDao : TrekElevationPointDao,
    private val sleepDao     : SleepAltitudeDao
) {

    // ── Sessions ──────────────────────────────────────────────────────────────

    /** Live list of all closed sessions, newest first. */
    val allSessions: Flow<List<TrekSession>> = sessionDao.allClosed()

    /**
     * Returns elevation points for the sparkline, down-sampled to one point
     * per [bucketMs] (default 5 min). Falls back to raw for short sessions.
     */
    suspend fun elevationPoints(
        sessionId : Long,
        bucketMs  : Long = 300_000L
    ): List<TrekElevationPoint> {
        val sampled = elevationDao.sampledForSession(sessionId, bucketMs)
        return if (sampled.size >= 3) sampled
        else elevationDao.forSession(sessionId)
    }

    /**
     * Hard-deletes a session and all its elevation points (CASCADE).
     * Does NOT swallow errors — let them propagate so the ViewModel
     * can observe the failure instead of silently doing nothing.
     */
    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }

    // ── Sleep altitude ────────────────────────────────────────────────────────

    /** Live list of all sleep altitude records, newest first. */
    val allSleepRecords: Flow<List<SleepAltitudeRecord>> = sleepDao.getAllFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sleepRecordForToday(): SleepAltitudeRecord? =
        sleepDao.getByDate(LocalDate.now().toString())

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun saveSleepAltitude(altitudeM: Double) {
        sleepDao.upsert(
            SleepAltitudeRecord(
                date           = LocalDate.now().toString(),
                altitudeMeters = altitudeM,
                timestampMs    = System.currentTimeMillis()
            )
        )
    }

    suspend fun sleepRecordForDate(isoDate: String): SleepAltitudeRecord? =
        sleepDao.getByDate(isoDate)
}