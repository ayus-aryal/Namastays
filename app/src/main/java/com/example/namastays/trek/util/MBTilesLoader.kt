package com.example.namastays.trek.util

import android.content.Context
import java.io.File

object MBTilesLoader {

    private var server: MBTileServer? = null
    private const val PORT = 8887

    fun getFilePath(context: Context, trekId: String): String? {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return if (file.exists()) file.absolutePath else null
    }

    fun startServer(context: Context, trekId: String): String? {
        val path = getFilePath(context, trekId) ?: return null
        server?.stopServer()
        server = MBTileServer(context.applicationContext, path, PORT)
        server?.startServer()
        android.util.Log.d("MBTiles", "Server started at http://localhost:$PORT")
        return "http://localhost:$PORT/tiles/{z}/{x}/{y}"
    }

    fun stopServer() {
        server?.stopServer()
        server = null
    }

    fun isDownloaded(context: Context, trekId: String): Boolean {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return file.exists() && file.length() > 1_048_576
    }

    fun getFileSizeMb(context: Context, trekId: String): Float {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return if (file.exists()) file.length() / (1024f * 1024f) else 0f
    }

    fun deleteFile(context: Context, trekId: String): Boolean {
        stopServer()
        val file = File(context.filesDir, "$trekId.mbtiles")
        return if (file.exists()) file.delete() else false
    }
}