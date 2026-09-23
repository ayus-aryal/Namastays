package com.example.namastays.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit




/**
 * DataStore extension property — single instance per process, scoped to
 * Context. File name is "anchor_point_prefs" to keep it isolated from any
 * other Preferences DataStore the app may add later (settings, etc.),
 * matching the same "own file per concern" reasoning as TrekEngine living
 * as its own lazy val on NamastaysApp rather than folded into a shared blob.
 */
private val Context.anchorPointDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anchor_point_prefs"
)

/**
 * A single user-set backtracking anchor point.
 *
 * @param accuracyMeters GPS accuracy (meters) reported at the moment the
 *   anchor was saved, or null if unknown. Surfaced on the Compass screen so
 *   the user can judge how much to trust the bearing — not used for any
 *   internal logic.
 */
data class AnchorPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float? = null
)


/**
 * Repository for the single global "backtrack anchor" point.
 *
 * Design decisions (confirmed with user):
 *  - Persists across app restarts (DataStore, not in-memory) — anchor is
 *    meant to survive a killed app / phone restart mid-trek.
 *  - Single global anchor, not scoped to a trek session — saving overwrites
 *    whatever was there before. Overwrite is just a second saveAnchor() call.
 *  - Auto-expires 10 days after it was set. Expiry is evaluated lazily on
 *    read (no WorkManager/background job) — cheaper and can't be missed if
 *    the app was closed for weeks. See EXPIRY_MS.
 *  - GPS accuracy is stored but never blocks a save — see AnchorPointRepository
 *    usage note in TrekViewModel: warn, don't block, on a degraded fix.
 */
class AnchorPointRepository(context: Context) {
    private val dataStore = context.applicationContext.anchorPointDataStore

    companion object {
        private val LAT = doublePreferencesKey("anchor_lat")
        private val LNG = doublePreferencesKey("anchor_lng")
        private val TS = longPreferencesKey("anchor_ts")
        private val ACCURACY = floatPreferencesKey("anchor_accuracy")

        /** Anchor is treated as absent once older than this, on next read. */
        val EXPIRY_MS: Long = TimeUnit.DAYS.toMillis(10)


        /**
         * Age threshold at which the Compass screen should start showing an
         * "expiring soon" hint next to the anchor, rather than silently
         * disappearing at the 10-day cliff. UI-only constant, kept here so
         * it stays next to EXPIRY_MS.
         */
        val AGING_WARNING_MS: Long = TimeUnit.DAYS.toMillis(7)

    }

    /**
     * Emits the current anchor, or null if none is set OR the stored anchor
     * has passed [EXPIRY_MS]. Expired data is NOT actively deleted here —
     * it simply reads as null. It will be overwritten on the next
     * [saveAnchor] call, or can be explicitly purged via [clearAnchor].
     */
    val anchorPoint: Flow<AnchorPoint?> = dataStore.data.map { prefs ->
        val lat = prefs[LAT] ?: return@map null
        val lng = prefs[LNG] ?: return@map null
        val ts = prefs[TS] ?: return@map null

        val age = System.currentTimeMillis() - ts
        if (age > EXPIRY_MS) null
        else AnchorPoint(
            latitude = lat,
            longitude = lng,
            timestampMillis = ts,
            accuracyMeters = prefs[ACCURACY]
        )
    }


    /**
     * Saves (or overwrites) the anchor point. Always succeeds regardless of
     * GPS accuracy — accuracy is stored for display only. Callers that want
     * a "low accuracy" warning at save time should check accuracy themselves
     * before/after calling this (e.g. against GpsSignalState in TrekViewModel)
     * and show a snackbar; this function does not gate or reject the save.
     */
    suspend fun saveAnchor(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        dataStore.edit { prefs ->
            prefs[LAT] = latitude
            prefs[LNG] = longitude
            prefs[TS] = System.currentTimeMillis()
            if (accuracyMeters != null) prefs[ACCURACY] = accuracyMeters
            else prefs.remove(ACCURACY)
        }
    }

    /** Explicitly clears the anchor. Used by the Compass screen's Clear button. */
    suspend fun clearAnchor() {
        dataStore.edit { prefs ->
            prefs.remove(LAT)
            prefs.remove(LNG)
            prefs.remove(TS)
            prefs.remove(ACCURACY)
        }
    }
}