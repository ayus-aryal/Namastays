package com.example.namastays.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EmergencyContactEntity::class,
        SleepAltitudeRecord::class,
        TrekSession::class,
        TrekElevationPoint::class
    ],
    version      = 3,
    exportSchema = true
)
abstract class SafetyDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun sleepAltitudeDao(): SleepAltitudeDao
    abstract fun trekSessionDao(): TrekSessionDao
    abstract fun trekElevationPointDao(): TrekElevationPointDao

    companion object {
        @Volatile private var INSTANCE: SafetyDatabase? = null

        /**
         * v2 → v3: creates the two trek tables.
         * sleep_Altitude and emergency_contacts are untouched.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trek_sessions (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startMs     INTEGER NOT NULL,
                        endMs       INTEGER NOT NULL DEFAULT 0,
                        distanceM   REAL    NOT NULL DEFAULT 0.0,
                        gainM       REAL    NOT NULL DEFAULT 0.0,
                        lossM       REAL    NOT NULL DEFAULT 0.0,
                        maxAltM     REAL    NOT NULL DEFAULT 0.0,
                        avgSpeedKmh REAL    NOT NULL DEFAULT 0.0,
                        isActive    INTEGER NOT NULL DEFAULT 1
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS trek_elevation_points (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId   INTEGER NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        altitudeM   REAL    NOT NULL,
                        accuracyM   REAL    NOT NULL,
                        FOREIGN KEY(sessionId)
                            REFERENCES trek_sessions(id)
                            ON DELETE CASCADE
                    )
                """)
                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS
                        index_trek_elevation_points_sessionId
                    ON trek_elevation_points(sessionId)
                """)
            }
        }

        /**
         * CRITICAL: SQLite disables foreign key enforcement by default on Android.
         * Without this, ON DELETE CASCADE is silently ignored and deleting a
         * trek_session won't remove its elevation points — and on some devices
         * the DELETE itself is silently dropped.
         */
        private val FK_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }

        fun getInstance(context: Context): SafetyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SafetyDatabase::class.java,
                    "safety_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(FK_CALLBACK)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}