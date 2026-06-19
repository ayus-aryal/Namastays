package com.example.namastays.repository

import com.example.namastays.api.TrekRetrofitInstance
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
import java.io.IOException
import java.net.SocketTimeoutException

sealed class NetworkResult {
    object Success        : NetworkResult()
    object NoConnectivity : NetworkResult()
    object Timeout        : NetworkResult()
    data class ServerError(val message: String) : NetworkResult()
}

class TrekRepository(
    private val cacheDao:     TrekCacheDao,
    private val itineraryDao: TrekItineraryDao,
    private val highlightDao: TrekHighlightDao,
    private val downloadedDao: DownloadedTrekDao
) {

    // ── List screen ───────────────────────────────────────────────────────────

    // Room Flow — emits whenever trek_cache changes.
    // TreksViewModel collects this; no change needed there.
    fun getAllTreks(): Flow<List<TrekItem>> {
        return cacheDao.getAllTreks().map { entities ->
            entities.map { it.toTrekItem() }
        }
    }

    // ── Detail screen ─────────────────────────────────────────────────────────

    // Fetches TrekDetail from:
    //   1. Room cache (trek_cache + trek_itinerary_days + trek_highlights)
    //      — used when offline or after first network fetch
    //   2. Network (GET /api/treks/{id}) if not cached in Room yet
    //
    // Returns null only if the trek ID doesn't exist in Room at all
    // (should never happen in practice since the list screen populates the cache).
    suspend fun getTrekDetail(trekId: String): TrekDetail? {
        val entity     = cacheDao.getTrekById(trekId) ?: return null
        val days       = itineraryDao.getDaysForTrek(trekId)
        val highlights = highlightDao.getHighlightsForTrek(trekId)

        // If days/highlights are empty it means we haven't fetched the detail
        // for this trek yet — fetch it now and cache it before returning.
        return if (days.isEmpty() && highlights.isEmpty()) {
            fetchAndCacheDetail(trekId)
            // Re-read from Room after caching so the caller always gets
            // data from a single source of truth.
            val freshDays       = itineraryDao.getDaysForTrek(trekId)
            val freshHighlights = highlightDao.getHighlightsForTrek(trekId)
            entity.toTrekDetail(freshDays, freshHighlights)
        } else {
            entity.toTrekDetail(days, highlights)
        }
    }

    // Hits GET /api/treks/{id} and writes days + highlights into Room.
    // trek_cache is NOT re-written here — the list refresh already handles that.
    // Returns NetworkResult so the ViewModel can surface errors.
    suspend fun fetchAndCacheDetail(trekId: String): NetworkResult {
        return try {
            val remote = TrekRetrofitInstance.api.getTrekById(trekId)
            // Write days and highlights atomically per trek
            itineraryDao.replaceForTrek(
                trekId = trekId,
                days   = remote.itineraryDays.map { it.toEntity(trekId) }
            )
            highlightDao.replaceForTrek(
                trekId     = trekId,
                highlights = remote.highlights.map { it.toEntity(trekId) }
            )
            NetworkResult.Success
        } catch (e: SocketTimeoutException) {
            NetworkResult.Timeout
        } catch (e: IOException) {
            NetworkResult.NoConnectivity
        } catch (e: Exception) {
            NetworkResult.ServerError(e.message ?: "Unknown error")
        }
    }

    // ── Refresh (called by TreksViewModel) ───────────────────────────────────

    // Refreshes the full trek list from the network.
    // Only writes trek_cache — does NOT write itinerary/highlights here
    // because the list endpoint returns empty arrays for those.
    // Detail data is fetched lazily when the user opens a trek.
    suspend fun refreshTreks(): NetworkResult {
        return try {
            val remote = TrekRetrofitInstance.api.getAllTreks()
            cacheDao.replaceAll(remote.map { it.toCacheEntity() })
            NetworkResult.Success
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

    suspend fun getTrekById(trekId: String): TrekItem? {
        return cacheDao.getTrekById(trekId)?.toTrekItem()
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

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

    private fun TrekCacheEntity.toTrekItem() = TrekItem(
        id             = id,
        name           = name,
        region         = region ?: "",
        difficulty     = difficulty ?: "Easy",
        durationDays   = durationDays ?: 0,
        maxElevation   = maxElevation ?: 0,
        distanceKm     = distanceKm?.toInt() ?: 0,
        description    = description ?: "",
        fileSizeMb     = tilesSizeMb?.toInt() ?: 0,
        tilesUrl       = tilesUrl,
        gpxUrl         = gpxUrl,
        waypointsUrl   = waypointsUrl,
        waypointsCount = waypointsCount,
        thumbnailUrl   = thumbnailUrl?.toCloudinaryThumbnail(),
        imagesUrl      = imagesUrl
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    )

    private fun TrekCacheEntity.toTrekDetail(
        days:       List<TrekItineraryDayEntity>,
        highlights: List<TrekHighlightEntity>
    ) = TrekDetail(
        id             = id,
        name           = name,
        region         = region ?: "",
        difficulty     = difficulty ?: "Easy",
        durationDays   = durationDays ?: 0,
        maxElevation   = maxElevation ?: 0,
        distanceKm     = distanceKm?.toInt() ?: 0,
        description    = description ?: "",
        fileSizeMb     = tilesSizeMb?.toInt() ?: 0,
        tilesUrl       = tilesUrl,
        gpxUrl         = gpxUrl,
        waypointsUrl   = waypointsUrl,
        waypointsCount = waypointsCount,
        thumbnailUrl   = thumbnailUrl?.toCloudinaryThumbnail(),
        imagesUrl      = imagesUrl
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList(),
        basePoint      = basePoint,
        itineraryDays  = days.map { ItineraryDay(it.dayNumber, it.title, it.description) },
        highlights     = highlights.map { TrailHighlight(it.label, it.iconName) }
    )
}