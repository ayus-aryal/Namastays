package com.example.namastays.repository

import com.example.namastays.api.TrekApiService
import com.example.namastays.dto.TrekApiModel
import com.example.namastays.dto.TrekHighlightApiModel
import com.example.namastays.dto.TrekItineraryDayApiModel
import com.example.namastays.trek.data.TrekCacheDao
import com.example.namastays.trek.data.TrekCacheEntity
import com.example.namastays.trek.data.TrekHighlightDao
import com.example.namastays.trek.data.TrekHighlightEntity
import com.example.namastays.trek.data.TrekItineraryDao
import com.example.namastays.trek.data.TrekItineraryDayEntity
import com.example.namastays.trek.domain.DownloadedTrekDao
import com.example.namastays.trek.domain.ItineraryDay
import com.example.namastays.trek.domain.TrailHighlight
import com.example.namastays.trek.domain.TrekDetail
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.util.toCloudinaryThumbnail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

// NOTE: NetworkResult is declared in NetworkResult.kt in this same package.
// Do NOT redeclare it here — that caused the "Redeclaration" build error.

class TrekRepository(
    private val api:           TrekApiService,
    private val cacheDao:      TrekCacheDao,
    private val itineraryDao:  TrekItineraryDao,
    private val highlightDao:  TrekHighlightDao,
    private val downloadedDao: DownloadedTrekDao
) {

    private val detailMutexes = ConcurrentHashMap<String, Mutex>()
    private fun mutexFor(trekId: String) =
        detailMutexes.getOrPut(trekId) { Mutex() }

    // ── List screen ───────────────────────────────────────────────────────────

    fun getAllTreks(): Flow<List<TrekItem>> =
        cacheDao.getAllTreks().map { entities -> entities.map { it.toTrekItem() } }

    // ── Detail screen ─────────────────────────────────────────────────────────

    suspend fun getTrekDetail(trekId: String): TrekDetailResult {
        val entity = cacheDao.getTrekById(trekId)
            ?: return TrekDetailResult.NotFound

        return mutexFor(trekId).withLock {
            val days       = itineraryDao.getDaysForTrek(trekId)
            val highlights = highlightDao.getHighlightsForTrek(trekId)

            if (days.isEmpty() && highlights.isEmpty()) {
                val result = fetchAndCacheDetail(trekId)
                if (result !is NetworkResult.Success<*>) {
                    return@withLock TrekDetailResult.NetworkError(result)
                }
                val freshDays       = itineraryDao.getDaysForTrek(trekId)
                val freshHighlights = highlightDao.getHighlightsForTrek(trekId)
                TrekDetailResult.Found(entity.toTrekDetail(freshDays, freshHighlights))
            } else {
                TrekDetailResult.Found(entity.toTrekDetail(days, highlights))
            }
        }
    }

    suspend fun fetchAndCacheDetail(trekId: String): NetworkResult<Unit> {
        return try {
            val remote = api.getTrekById(trekId)
            itineraryDao.replaceForTrek(
                trekId = trekId,
                days   = remote.itineraryDays.map { it.toEntity(trekId) }
            )
            highlightDao.replaceForTrek(
                trekId     = trekId,
                highlights = remote.highlights.map { it.toEntity(trekId) }
            )
            NetworkResult.Success(Unit)
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    suspend fun refreshTreks(): NetworkResult<Unit> {
        return try {
            val remote = api.getAllTreks()
            cacheDao.replaceAll(remote.map { it.toCacheEntity() })
            NetworkResult.Success(Unit)
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteDownload(trekId: String) {
        downloadedDao.deleteByTrekId(trekId)
    }

    suspend fun getTrekById(trekId: String): TrekItem? =
        cacheDao.getTrekById(trekId)?.toTrekItem()

    // ── Mappers ───────────────────────────────────────────────────────────────

    private companion object {
        const val IMG_DELIMITER = "\u001F"
    }

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
        imagesUrl      = imagesUrl?.joinToString(IMG_DELIMITER),
        waypointsCount = waypointsCount,
        basePoint      = basePoint
    )

    private fun TrekItineraryDayApiModel.toEntity(trekId: String) = TrekItineraryDayEntity(
        trekId      = trekId,
        dayNumber   = dayNumber,
        title       = title,
        description = description
    )

    private fun TrekHighlightApiModel.toEntity(trekId: String) = TrekHighlightEntity(
        trekId   = trekId,
        label    = label,
        iconName = iconName
    )

    private fun TrekCacheEntity.parseImagesUrl(): List<String> {
        if (imagesUrl.isNullOrBlank()) return emptyList()
        val delimiter = if (imagesUrl.contains(IMG_DELIMITER)) IMG_DELIMITER else ","
        return imagesUrl.split(delimiter).map { it.trim() }.filter { it.isNotEmpty() }
    }

    private data class CommonFields(
        val id:             String,
        val name:           String,
        val region:         String,
        val difficulty:     String,
        val durationDays:   Int,
        val maxElevation:   Int,
        val distanceKm:     Double,
        val description:    String,
        val fileSizeMb:     Int,
        val tilesUrl:       String?,
        val gpxUrl:         String?,
        val waypointsUrl:   String?,
        val waypointsCount: Int?,
        val thumbnailUrl:   String?,
        val imagesUrl:      List<String>
    )

    private fun TrekCacheEntity.toCommonFields() = CommonFields(
        id             = id,
        name           = name,
        region         = region ?: "",
        difficulty     = difficulty ?: "Easy",
        durationDays   = durationDays ?: 0,
        maxElevation   = maxElevation ?: 0,
        distanceKm     = distanceKm ?: 0.0,
        description    = description ?: "",
        fileSizeMb     = tilesSizeMb?.toInt() ?: 0,
        tilesUrl       = tilesUrl,
        gpxUrl         = gpxUrl,
        waypointsUrl   = waypointsUrl,
        waypointsCount = waypointsCount,
        thumbnailUrl   = thumbnailUrl?.toCloudinaryThumbnail(),
        imagesUrl      = parseImagesUrl()
    )

    private fun TrekCacheEntity.toTrekItem(): TrekItem {
        val c = toCommonFields()
        return TrekItem(
            id             = c.id,
            name           = c.name,
            region         = c.region,
            difficulty     = c.difficulty,
            durationDays   = c.durationDays,
            maxElevation   = c.maxElevation,
            distanceKm     = c.distanceKm,
            description    = c.description,
            fileSizeMb     = c.fileSizeMb,
            tilesUrl       = c.tilesUrl,
            gpxUrl         = c.gpxUrl,
            waypointsUrl   = c.waypointsUrl,
            waypointsCount = c.waypointsCount,
            thumbnailUrl   = c.thumbnailUrl,
            imagesUrl      = c.imagesUrl
        )
    }

    private fun TrekCacheEntity.toTrekDetail(
        days:       List<TrekItineraryDayEntity>,
        highlights: List<TrekHighlightEntity>
    ): TrekDetail {
        val c = toCommonFields()
        return TrekDetail(
            id             = c.id,
            name           = c.name,
            region         = c.region,
            difficulty     = c.difficulty,
            durationDays   = c.durationDays,
            maxElevation   = c.maxElevation,
            distanceKm     = c.distanceKm,
            description    = c.description,
            fileSizeMb     = c.fileSizeMb,
            tilesUrl       = c.tilesUrl,
            gpxUrl         = c.gpxUrl,
            waypointsUrl   = c.waypointsUrl,
            waypointsCount = c.waypointsCount,
            thumbnailUrl   = c.thumbnailUrl,
            imagesUrl      = c.imagesUrl,
            basePoint      = basePoint,
            itineraryDays  = days.map { ItineraryDay(it.dayNumber, it.title, it.description) },
            highlights     = highlights.map { TrailHighlight(it.label, it.iconName) }
        )
    }
}

// ── TrekDetailResult ──────────────────────────────────────────────────────────
// Kept in this file so it's in the same package as TrekRepository.
// TrekDetailViewModel imports it as:
//   import com.example.namastays.repository.TrekDetailResult

sealed class TrekDetailResult {
    data class Found(val detail: TrekDetail)               : TrekDetailResult()
    data class NetworkError(val result: NetworkResult<*>)  : TrekDetailResult()
    object NotFound                                         : TrekDetailResult()
}