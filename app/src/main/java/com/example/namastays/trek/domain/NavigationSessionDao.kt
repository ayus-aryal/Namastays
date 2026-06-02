package com.example.namastays.trek.domain

import androidx.room.*

@Dao
interface NavigationSessionDao {

    @Query("SELECT * FROM navigation_sessions WHERE trekId = :trekId")
    suspend fun getSession(trekId: String): TrekNavigationSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: TrekNavigationSession)

    @Query("DELETE FROM navigation_sessions WHERE trekId = :trekId")
    suspend fun clearSession(trekId: String)
}