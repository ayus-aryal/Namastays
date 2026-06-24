package com.example.namastays.data

import kotlinx.coroutines.flow.Flow

/**
 * FIX R1 — @RequiresApi(O) removed from sleepRecordForToday() and
 * saveSleepAltitude(). java.time.LocalDate requires either minSdk >= 26 or
 * coreLibraryDesugaringEnabled = true in app/build.gradle. The annotation
 * only silences lint while crashing at runtime on devices below API 26 if
 * desugaring is absent. Fix it at the build level, not the call site:
 *
 *   android {
 *     compileOptions { isCoreLibraryDesugaringEnabled = true }
 *   }
 *   dependencies {
 *     coreLibraryDesugaring("com.android.tools.desugar_jdk_libs:2.1.4")
 *   }
 */
class TrekRepository(
    private val sessionDao   : TrekSessionDao,
    private val elevationDao : TrekElevationPointDao,
    private val sleepDao     : SleepAltitudeDao
) {

    // ── Sessions ──────────────────────────────────────────────────────────────

    val allSessions: Flow<List<TrekSession>> = sessionDao.allClosed()

    suspend fun elevationPoints(
        sessionId : Long,
        bucketMs  : Long = 300_000L
    ): List<TrekElevationPoint> {
        val sampled = elevationDao.sampledForSession(sessionId, bucketMs)
        return if (sampled.size >= 3) sampled
        else elevationDao.forSession(sessionId)
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }

    // ── Sleep altitude ────────────────────────────────────────────────────────

    val allSleepRecords: Flow<List<SleepAltitudeRecord>> = sleepDao.getAllFlow()

    // FIX R1 — @RequiresApi removed.
    suspend fun sleepRecordForToday(): SleepAltitudeRecord? =
        sleepDao.getByDate(todayIso())

    // FIX R1 — @RequiresApi removed.
    suspend fun saveSleepAltitude(altitudeM: Double) {
        sleepDao.upsert(
            SleepAltitudeRecord(
                date           = todayIso(),
                altitudeMeters = altitudeM,
                timestampMs    = System.currentTimeMillis()
            )
        )
    }

    suspend fun sleepRecordForDate(isoDate: String): SleepAltitudeRecord? =
        sleepDao.getByDate(isoDate)

    // Single source of truth for the ISO date format used by all sleep queries.
    private fun todayIso(): String = java.time.LocalDate.now().toString()
}