package com.example.namastays.trek

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.namastays.trek.data.TrekCacheDao
import com.example.namastays.trek.data.TrekCacheEntity
import com.example.namastays.trek.domain.CustomMarker
import com.example.namastays.trek.domain.DownloadedTrek
import com.example.namastays.trek.domain.DownloadedTrekDao
import com.example.namastays.trek.domain.MarkerDao
import com.example.namastays.trek.domain.NavigationSessionDao
import com.example.namastays.trek.domain.TrekNavigationSession

@Database(
    entities = [CustomMarker::class, DownloadedTrek::class, TrekNavigationSession::class, TrekCacheEntity::class],
    version = 4,
    exportSchema = false
)
abstract class TrekDatabase : RoomDatabase() {

    abstract fun markerDao(): MarkerDao
    abstract fun downloadedTrekDao(): DownloadedTrekDao
    abstract fun navigationSessionDao(): NavigationSessionDao

    abstract fun trekCacheDao(): TrekCacheDao

    companion object {
        @Volatile
        private var INSTANCE: TrekDatabase? = null

        fun getInstance(context: Context): TrekDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TrekDatabase::class.java,
                    "trek_database"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}