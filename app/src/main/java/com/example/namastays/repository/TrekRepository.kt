package com.example.namastays.repository

import com.example.namastays.api.TrekRetrofitInstance
import com.example.namastays.dto.TrekApiModel
import com.example.namastays.trek.data.TrekCacheDao
import com.example.namastays.trek.data.TrekCacheEntity
import com.example.namastays.trek.domain.TrekItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrekRepository(private val dao: TrekCacheDao) {

    fun getAllTreks(): Flow<List<TrekItem>> {
        return dao.getAllTreks().map { entities ->
            entities.map { it.toTrekItem() }
        }
    }

    suspend fun getTrekById(id: String): TrekItem? {
        return dao.getTrekById(id)?.toTrekItem()
    }

    suspend fun refreshTreks() {
        try {
            val remote = TrekRetrofitInstance.api.getAllTreks()
            dao.upsertAll(remote.map { it.toCacheEntity() })
        } catch (e: Exception) {
            // Network unavailable — Room cache will be used
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private fun TrekApiModel.toCacheEntity() = TrekCacheEntity(
        id             = id,
        name           = name,
        region         = region,
        difficulty     = difficulty,
        durationDays   = durationDays,
        maxElevation   = maxElevation,
        distanceKm     = distanceKm,
        description    = description,
        tilesUrl       = tilesUrl,
        gpxUrl         = gpxUrl,
        waypointsUrl   = waypointsUrl,
        tilesSizeMb    = tilesSizeMb,
        thumbnailUrl   = thumbnailUrl,
        imagesUrl      = imagesUrl?.joinToString(","),
        waypointsCount = waypointsCount,
        basePoint      = basePoint
    )

    private fun TrekCacheEntity.toTrekItem() = TrekItem(
        id           = id,
        name         = name,
        region       = region ?: "",
        difficulty   = difficulty ?: "Easy",
        durationDays = durationDays ?: 0,
        maxElevation = maxElevation ?: 0,
        distanceKm   = distanceKm?.toInt() ?: 0,
        description  = description ?: "",
        fileSizeMb   = tilesSizeMb?.toInt() ?: 0,
        tilesUrl       = tilesUrl,
        gpxUrl         = gpxUrl,
        waypointsUrl   = waypointsUrl,
        waypointsCount = waypointsCount,
    )
}