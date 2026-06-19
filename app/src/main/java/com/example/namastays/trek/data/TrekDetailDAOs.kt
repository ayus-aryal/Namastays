package com.example.namastays.trek.data

import androidx.room.*

@Dao
interface TrekItineraryDao {

    @Query("SELECT * FROM trek_itinerary_days WHERE trekId = :trekId ORDER BY dayNumber ASC")
    suspend fun getDaysForTrek(trekId: String): List<TrekItineraryDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(days: List<TrekItineraryDayEntity>)

    @Query("DELETE FROM trek_itinerary_days WHERE trekId = :trekId")
    suspend fun deleteByTrekId(trekId: String)

    // Atomic replace — if the process dies mid-write, SQLite rolls back.
    // Called during refreshTreks() inside the outer transaction.
    @Transaction
    suspend fun replaceForTrek(trekId: String, days: List<TrekItineraryDayEntity>) {
        deleteByTrekId(trekId)
        if (days.isNotEmpty()) insertAll(days)
    }
}

@Dao
interface TrekHighlightDao {

    @Query("SELECT * FROM trek_highlights WHERE trekId = :trekId")
    suspend fun getHighlightsForTrek(trekId: String): List<TrekHighlightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(highlights: List<TrekHighlightEntity>)

    @Query("DELETE FROM trek_highlights WHERE trekId = :trekId")
    suspend fun deleteByTrekId(trekId: String)

    @Transaction
    suspend fun replaceForTrek(trekId: String, highlights: List<TrekHighlightEntity>) {
        deleteByTrekId(trekId)
        if (highlights.isNotEmpty()) insertAll(highlights)
    }
}