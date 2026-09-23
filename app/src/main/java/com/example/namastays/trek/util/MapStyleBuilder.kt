package com.example.namastays.trek.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the offline (main navigation) map style JSON for [trekId].
 *
 * Now suspend: starting the tile server involves blocking file I/O
 * (SQLiteDatabase.openDatabase) and reading a style asset off disk — both
 * moved to Dispatchers.IO so this never blocks the caller's thread.
 *
 * Both {trek_tiles_url} and {glyphs_url} placeholders are replaced from
 * MBTilesLoader's single source of truth for the server's base URL, so the
 * style JSON never has to hardcode a port number that could drift out of
 * sync with MBTilesLoader.PORT.
 */
suspend fun buildOfflineStyle(context: Context, trekId: String): String? =
    withContext(Dispatchers.IO) {
        val tileUrl = MBTilesLoader.startServer(context, trekId) ?: return@withContext null
        android.util.Log.d("MBTiles", "Tile server URL: $tileUrl")
        try {
            val styleJson = context.assets
                .open("style/trek_style.json")
                .bufferedReader()
                .use { it.readText() }
            styleJson
                .replace("{trek_tiles_url}", tileUrl)
                .replace("{glyphs_url}", "${MBTilesLoader.baseUrl()}/fonts/{fontstack}/{range}.pbf")
        } catch (e: Exception) {
            android.util.Log.e("MBTiles", "Style load error: ${e.message}")
            null
        }
    }

/** Same as [buildOfflineStyle] but for the trail-only viewing style. Reuses the same running tile server. */
suspend fun buildTrailViewStyle(context: Context, trekId: String): String? =
    withContext(Dispatchers.IO) {
        val tileUrl = MBTilesLoader.startServer(context, trekId) ?: return@withContext null
        try {
            val styleJson = context.assets
                .open("style/trail_style.json")
                .bufferedReader()
                .use { it.readText() }
            styleJson
                .replace("{trek_tiles_url}", tileUrl)
                .replace("{glyphs_url}", "${MBTilesLoader.baseUrl()}/fonts/{fontstack}/{range}.pbf")
        } catch (e: Exception) {
            android.util.Log.e("MBTiles", "Trail style load error: ${e.message}")
            null
        }
    }