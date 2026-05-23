package com.example.namastays.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Dao
interface SleepAltitudeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SleepAltitudeRecord)

    @Query("SELECT * FROM sleep_altitude WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): SleepAltitudeRecord?

    //Ordered by descending order
    @Query("SELECT * FROM sleep_altitude ORDER BY date DESC")
    fun getAllFlow(): Flow<List<SleepAltitudeRecord>>
}