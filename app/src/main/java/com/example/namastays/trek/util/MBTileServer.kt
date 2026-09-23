package com.example.namastays.trek.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Local HTTP server that serves vector tiles and font glyphs out of a single
 * .mbtiles SQLite file, for MapLibre to consume as an offline tile source.
 *
 * ── Thread-safety ────────────────────────────────────────────────────────
 * NanoHTTPD dispatches each connection to its own worker thread, so [serve]
 * can be called concurrently from many threads while [stopServer] is called
 * from the caller's thread (via MBTilesLoader, on Dispatchers.IO). Without
 * synchronization, stopServer() nulling/closing [db] mid-query from another
 * thread crashes with "attempt to re-open an already-closed object".
 *
 * [db] is now only ever read/written inside [dbLock], and every query path
 * takes a local snapshot of the reference before using it, so a concurrent
 * stopServer() can't yank the database out from under an in-flight query.
 */
class MBTileServer(
    context: Context,
    private val mbtilesPath: String,
    port: Int = 8887
) : NanoHTTPD(port) {

    private val appContext = context.applicationContext

    private val dbLock = Any()
    private var db: SQLiteDatabase? = null

    fun startServer() {
        synchronized(dbLock) {
            db = SQLiteDatabase.openDatabase(
                mbtilesPath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        }
        start(SOCKET_READ_TIMEOUT, false)
    }

    fun stopServer() {
        // Stop accepting new connections / let in-flight ones finish their
        // NanoHTTPD-level work first, THEN close the database.
        stop()
        synchronized(dbLock) {
            db?.close()
            db = null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        if (uri.startsWith("/fonts/")) {
            return serveFontFile(uri.removePrefix("/"))
        }

        val parts = uri.removePrefix("/tiles/").split("/")
        if (parts.size != 3) {
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "text/plain",
                "Bad request"
            )
        }

        return try {
            val z = parts[0].toInt()
            val x = parts[1].toInt()
            val y = (1 shl z) - 1 - parts[2].toInt()

            // Snapshot the reference under the lock so a concurrent
            // stopServer() can't close it between the null-check and use.
            val database = synchronized(dbLock) { db }
                ?: return newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Tile server not ready"
                )

            database.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                arrayOf(z.toString(), x.toString(), y.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val tileData = decompress(cursor.getBlob(0))
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/x-protobuf",
                        ByteArrayInputStream(tileData),
                        tileData.size.toLong()
                    )
                } else {
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "Tile not found"
                    )
                }
            }
        } catch (e: Exception) {
            // Covers: malformed z/x/y, a database closed concurrently between
            // the snapshot and the query, or corrupt tile blobs.
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                e.message ?: "Error"
            )
        }
    }

    private fun serveFontFile(path: String): Response {
        return try {
            val inputStream: InputStream = appContext.assets.open(path)
            val bytes = inputStream.use { it.readBytes() }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/x-protobuf",
                ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Font not found: $path"
            )
        }
    }

    /** Vector tiles in mbtiles are typically gzip-compressed PBF; detect and inflate. */
    private fun decompress(data: ByteArray): ByteArray {
        if (data.size < 2 || data[0] != 0x1f.toByte() || data[1] != 0x8b.toByte()) {
            return data
        }
        return try {
            java.util.zip.GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        } catch (e: Exception) {
            data
        }
    }
}