package com.example.namastays.trek.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns the single running [MBTileServer] instance for the app and exposes
 * suspend-safe start/stop/query functions.
 *
 * ── Idempotency ──────────────────────────────────────────────────────────
 * [startServer] is a no-op (returns the existing URL) if a server is already
 * running for the SAME [trekId]. It only tears down and restarts when the
 * caller asks for a DIFFERENT trek's tiles. This matters because
 * buildOfflineStyle()/buildTrailViewStyle() both call startServer() on every
 * style (re)build — including toggling isTrailView, which does NOT need a
 * new tile server, just a different style layered on the same tiles.
 * Restarting unconditionally used to kill in-flight tile requests and
 * reopen the mbtiles SQLite file (blocking I/O) on every toggle.
 *
 * ── Threading ────────────────────────────────────────────────────────────
 * All start/stop logic now runs on Dispatchers.IO. Callers (buildOfflineStyle
 * etc.) must be suspend functions invoked from a coroutine, not directly
 * from a synchronous map-ready callback on the main thread.
 *
 * ── Thread-safety ────────────────────────────────────────────────────────
 * A single lock guards [server] and [currentTrekId] so concurrent calls
 * (e.g. a fast isTrailView toggle) can't race and leave two servers running
 * or a half-closed database.
 */
object MBTilesLoader {

    private const val PORT = 8887

    private val lock = Any()
    private var server: MBTileServer? = null
    private var currentTrekId: String? = null

    /** Base URL used for tile requests. Exposed so glyphs URLs stay in sync with [PORT]. */
    fun baseUrl(): String = "http://localhost:$PORT"

    fun getFilePath(context: Context, trekId: String): String? {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Ensures a tile server is running for [trekId] and returns the tile URL
     * template to embed in a style JSON. Safe to call repeatedly for the same
     * trek — only restarts the server when switching to a different trek.
     *
     * Must be called from a coroutine; performs blocking file I/O on
     * [Dispatchers.IO].
     */
    suspend fun startServer(context: Context, trekId: String): String? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val path = getFilePath(context, trekId) ?: return@withContext null

                if (server != null && currentTrekId == trekId) {
                    // Already serving this trek's tiles — reuse it.
                    return@withContext "${baseUrl()}/tiles/{z}/{x}/{y}"
                }

                // Switching trek (or first start): stop any previous server first.
                server?.stopServer()
                server = null
                currentTrekId = null

                val newServer = MBTileServer(context.applicationContext, path, PORT)
                newServer.startServer()
                server = newServer
                currentTrekId = trekId

                android.util.Log.d("MBTiles", "Server started for '$trekId' at ${baseUrl()}")
                "${baseUrl()}/tiles/{z}/{x}/{y}"
            }
        }

    /** Stops the running server, if any. Safe to call multiple times. */
    suspend fun stopServer() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            server?.stopServer()
            server = null
            currentTrekId = null
        }
    }

    fun isDownloaded(context: Context, trekId: String): Boolean {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return file.exists() && file.length() > 1_048_576
    }

    fun getFileSizeMb(context: Context, trekId: String): Float {
        val file = File(context.filesDir, "$trekId.mbtiles")
        return if (file.exists()) file.length() / (1024f * 1024f) else 0f
    }

    /** Deletes the mbtiles file for [trekId]. Stops the server first if it's serving that trek. */
    suspend fun deleteFile(context: Context, trekId: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (currentTrekId == trekId) {
                server?.stopServer()
                server = null
                currentTrekId = null
            }
        }
        val file = File(context.filesDir, "$trekId.mbtiles")
        if (file.exists()) file.delete() else false
    }
}