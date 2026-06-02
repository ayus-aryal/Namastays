package com.example.namastays.trek.domain

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {

    @Query("SELECT * FROM custom_markers WHERE trekId = :trekId ORDER BY createdAt DESC")
    fun getMarkersForTrek(trekId: String): Flow<List<CustomMarker>>

    @Query("SELECT * FROM custom_markers WHERE trekId = :trekId ORDER BY createdAt DESC")
    suspend fun getMarkersForTrekOnce(trekId: String): List<CustomMarker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: CustomMarker): Long

    @Update
    suspend fun update(marker: CustomMarker)

    @Delete
    suspend fun delete(marker: CustomMarker)

    @Query("DELETE FROM custom_markers WHERE id = :markerId")
    suspend fun deleteById(markerId: Long)
}