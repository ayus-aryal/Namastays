package com.example.namastays.trek.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trek_itinerary_days",
    foreignKeys = [
        ForeignKey(
            entity        = TrekCacheEntity::class,
            parentColumns = ["id"],
            childColumns  = ["trekId"],
            // When a trek is evicted from the cache, its days are automatically
            // deleted — no orphan rows possible.
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trekId")]
)
data class TrekItineraryDayEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId:       Long   = 0,
    val trekId:      String,
    val dayNumber:   Int,
    val title:       String,
    val description: String
)

@Entity(
    tableName = "trek_highlights",
    foreignKeys = [
        ForeignKey(
            entity        = TrekCacheEntity::class,
            parentColumns = ["id"],
            childColumns  = ["trekId"],
            onDelete      = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trekId")]
)
data class TrekHighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val rowId:    Long   = 0,
    val trekId:   String,
    val label:    String,
    val iconName: String
)