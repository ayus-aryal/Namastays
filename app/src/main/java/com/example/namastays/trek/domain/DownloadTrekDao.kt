package com.example.namastays.trek.domain

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedTrekDao {

    @Query("SELECT * FROM downloaded_treks")
    fun getAllDownloaded(): Flow<List<DownloadedTrek>>

    @Query("SELECT * FROM downloaded_treks WHERE trekId = :trekId")
    suspend fun getDownloadedTrek(trekId: String): DownloadedTrek?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_treks WHERE trekId = :trekId)")
    suspend fun isDownloaded(trekId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trek: DownloadedTrek)

    @Query("DELETE FROM downloaded_treks WHERE trekId = :trekId")
    suspend fun deleteByTrekId(trekId: String)
}