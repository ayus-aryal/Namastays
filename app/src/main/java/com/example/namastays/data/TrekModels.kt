package com.example.namastays.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ─── TrekSession ───────────────────────────────────────────────────────────────
/**
 * One continuous Trek Mode activation window (one ON→OFF toggle pair).
 *
 * The user can toggle Trek Mode on/off many times; each pair produces one row.
 * The logbook screen aggregates rows by day.
 *
 * [distanceM]   Haversine accumulation, accuracy-gated (≤ 20 m fix accuracy,
 *               ≥ 5 m displacement).  Stored in metres; UI divides by 1000.
 * [gainM]       Cumulative positive Kalman-filtered altitude delta (≥ 2 m gate).
 * [lossM]       Cumulative negative delta, stored as positive value.
 * [maxAltM]     Peak altitude seen in this session.
 * [avgSpeedKmh] Computed on close: totalDistanceM / elapsedSeconds * 3.6.
 * [isActive]    true while the foreground service is running for this session.
 *               At most one row should have isActive = 1 at any time.
 */
@Entity(tableName = "trek_sessions")
data class TrekSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs     : Long,
    val endMs       : Long    = 0L,
    val distanceM   : Double  = 0.0,
    val gainM       : Double  = 0.0,
    val lossM       : Double  = 0.0,
    val maxAltM     : Double  = 0.0,
    val avgSpeedKmh : Double  = 0.0,
    val isActive    : Boolean = true
)

// ─── TrekElevationPoint ────────────────────────────────────────────────────────
/**
 * One altitude snapshot tied to a [TrekSession].
 * Written every [TrekEngine.ELEVATION_RECORD_INTERVAL_MS] only when accuracy
 * is within [TrekEngine.ACCURACY_THRESHOLD_M].
 *
 * Foreign key CASCADE ensures points are deleted when their session is deleted.
 * Index on [sessionId] makes sparkline queries fast even with thousands of rows.
 */
@Entity(
    tableName    = "trek_elevation_points",
    foreignKeys  = [ForeignKey(
        entity        = TrekSession::class,
        parentColumns = ["id"],
        childColumns  = ["sessionId"],
        onDelete      = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class TrekElevationPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId   : Long,
    val timestampMs : Long,
    val altitudeM   : Double,
    val accuracyM   : Float
)

// ─── TrekSessionDao ────────────────────────────────────────────────────────────
@Dao
interface TrekSessionDao {

    @Insert
    suspend fun insert(session: TrekSession): Long

    @Query("""
        UPDATE trek_sessions
        SET endMs       = :endMs,
            distanceM   = :distanceM,
            gainM       = :gainM,
            lossM       = :lossM,
            maxAltM     = :maxAltM,
            avgSpeedKmh = :avgSpeedKmh,
            isActive    = 0
        WHERE id = :id
    """)
    suspend fun close(
        id          : Long,
        endMs       : Long,
        distanceM   : Double,
        gainM       : Double,
        lossM       : Double,
        maxAltM     : Double,
        avgSpeedKmh : Double
    )

    @Query("SELECT * FROM trek_sessions WHERE isActive = 0 ORDER BY startMs DESC")
    fun allClosed(): Flow<List<TrekSession>>

    @Query("SELECT * FROM trek_sessions WHERE isActive = 1 ORDER BY startMs DESC LIMIT 1")
    suspend fun activeSession(): TrekSession?

    @Query("""
        UPDATE trek_sessions
        SET endMs    = :nowMs,
            isActive = 0
        WHERE isActive = 1
    """)
    suspend fun closeOrphanedSessions(nowMs: Long)

    /**
     * Hard-delete a session by id.
     * Elevation points are removed automatically via CASCADE.
     */
    @Query("DELETE FROM trek_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

// ─── TrekElevationPointDao ─────────────────────────────────────────────────────
@Dao
interface TrekElevationPointDao {

    @Insert
    suspend fun insert(point: TrekElevationPoint)

    @Query("""
        SELECT * FROM trek_elevation_points
        WHERE sessionId = :sessionId
        ORDER BY timestampMs ASC
    """)
    suspend fun forSession(sessionId: Long): List<TrekElevationPoint>

    /**
     * Down-sampled: one point per [bucketMs] bucket.
     * Keeps the sparkline fast even for 10-hour sessions.
     * Default bucket = 5 min = 300_000 ms.
     */
    @Query("""
        SELECT MIN(id) as id, sessionId,
               (timestampMs / :bucketMs) * :bucketMs AS timestampMs,
               AVG(altitudeM)  AS altitudeM,
               MIN(accuracyM)  AS accuracyM
        FROM trek_elevation_points
        WHERE sessionId = :sessionId
        GROUP BY (timestampMs / :bucketMs)
        ORDER BY timestampMs ASC
    """)
    suspend fun sampledForSession(
        sessionId : Long,
        bucketMs  : Long = 300_000L
    ): List<TrekElevationPoint>

    @Query("DELETE FROM trek_elevation_points WHERE timestampMs < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long)
}
