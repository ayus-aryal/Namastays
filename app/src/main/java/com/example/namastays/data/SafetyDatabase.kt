package com.example.namastays.data

import android.content.Context
import androidx.room.*

@Database(entities = [EmergencyContactEntity::class, SleepAltitudeRecord::class], version = 2)
abstract class SafetyDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun sleepAltitudeDao(): SleepAltitudeDao

    companion object {
        @Volatile private var INSTANCE: SafetyDatabase? = null

        fun getInstance(context: Context): SafetyDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SafetyDatabase::class.java,
                    "safety_db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}