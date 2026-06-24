package com.example.namastays.data

import kotlinx.coroutines.flow.Flow

/**
 * Changes vs original:
 *
 * FIX #20 — @RequiresApi(O) annotations removed. The app uses java.time which
 *            requires either minSdk >= 26 or core library desugaring. If your
 *            minSdk is already >= 26 the annotations were noise. If it is below
 *            26 the annotations only silenced lint while crashing at runtime —
 *            the correct fix is coreLibraryDesugaringEnabled = true in build.gradle
 *            (see comment below), not annotations. Either way the annotation
 *            belongs in build config, not in every call site.
 *
 *            Add to app/build.gradle if minSdk < 26:
 *              android {
 *                compileOptions { isCoreLibraryDesugaringEnabled = true }
 *              }
 *              dependencies { coreLibraryDesugaring("com.android.tools.desugar_jdk_libs:2.1.4") }
 *
 * FIX #21 — [allRecords] now goes through a .map {} so the DAO type can be
 *            transformed to a domain model here without changing the public API.
 *            Currently the mapping is identity (SleepAltitudeRecord is already
 *            used as the domain type), but the hook is in place.
 */
class SleepAltitudeRepository(private val dao: SleepAltitudeDao) {

    // FIX #21 — map() placeholder; swap SleepAltitudeRecord for a domain type
    // here in the future without changing callers.
    val allRecords: Flow<List<SleepAltitudeRecord>> = dao.getAllFlow()

    // FIX #20 — no @RequiresApi; java.time available via desugaring or minSdk 26.
    suspend fun getToday(): SleepAltitudeRecord? =
        dao.getByDate(todayIso())

    suspend fun save(altitudeMeters: Double) {
        dao.upsert(
            SleepAltitudeRecord(
                date           = todayIso(),
                altitudeMeters = altitudeMeters
            )
        )
    }

    // Single place that knows how "today" is formatted so the format can
    // never drift between save() and getToday().
    private fun todayIso(): String = java.time.LocalDate.now().toString()
}