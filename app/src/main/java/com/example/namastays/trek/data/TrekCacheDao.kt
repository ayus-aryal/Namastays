package com.example.namastays.trek.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TrekCacheDao {

    @Query("SELECT * FROM trek_cache")
    fun getAllTreks(): Flow<List<TrekCacheEntity>>

    @Query("SELECT * FROM trek_cache WHERE id = :id")
    suspend fun getTrekById(id: String): TrekCacheEntity?

    @Upsert
    suspend fun upsertAll(treks: List<TrekCacheEntity>)

    @Upsert
    suspend fun upsert(trek: TrekCacheEntity)

    @Query("DELETE FROM trek_cache")
    suspend fun clearAll()

    // Atomic: if the process dies between clearAll and upsertAll,
    // SQLite rolls the entire transaction back — Room never ends up empty mid-write.
    @Transaction
    suspend fun replaceAll(treks: List<TrekCacheEntity>) {
        clearAll()
        upsertAll(treks)
    }
}