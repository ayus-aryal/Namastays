package com.example.namastays.trek.util

import android.content.Context

fun buildOfflineStyle(context: Context, trekId: String): String? {
    val tileUrl = MBTilesLoader.startServer(context, trekId) ?: return null
    android.util.Log.d("MBTiles", "Tile server URL: $tileUrl")
    return try {
        val styleJson = context.assets
            .open("style/trek_style.json")
            .bufferedReader()
            .readText()
        styleJson.replace("{trek_tiles_url}", tileUrl)
    } catch (e: Exception) {
        android.util.Log.e("MBTiles", "Style load error: ${e.message}")
        null
    }
}

fun buildTrailViewStyle(context: Context, trekId: String): String? {
    val tileUrl = MBTilesLoader.startServer(context, trekId) ?: return null
    return try {
        val styleJson = context.assets
            .open("style/trail_style.json")
            .bufferedReader()
            .readText()
        styleJson.replace("{trek_tiles_url}", tileUrl)
    } catch (e: Exception) {
        android.util.Log.e("MBTiles", "Trail style load error: ${e.message}")
        null
    }
}