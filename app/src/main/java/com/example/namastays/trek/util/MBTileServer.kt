package com.example.namastays.trek.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream

class MBTileServer(
    context: Context,
    private val mbtilesPath: String,
    port: Int = 8887
) : NanoHTTPD(port) {

    private val appContext = context.applicationContext
    private var db: SQLiteDatabase? = null

    fun startServer() {
        db = SQLiteDatabase.openDatabase(
            mbtilesPath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        start(SOCKET_READ_TIMEOUT, false)
    }

    fun stopServer() {
        stop()
        db?.close()
        db = null
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

            val cursor = db?.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?",
                arrayOf(z.toString(), x.toString(), y.toString())
            )

            if (cursor != null && cursor.moveToFirst()) {
                var tileData = cursor.getBlob(0)
                cursor.close()
                tileData = decompress(tileData)
                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/x-protobuf",
                    ByteArrayInputStream(tileData),
                    tileData.size.toLong()
                )
            } else {
                cursor?.close()
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "Tile not found"
                )
            }
        } catch (e: Exception) {
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
            val bytes = inputStream.readBytes()
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

    private fun decompress(data: ByteArray): ByteArray {
        if (data.size < 2 || data[0] != 0x1f.toByte() || data[1] != 0x8b.toByte()) {
            return data
        }
        return try {
            java.util.zip.GZIPInputStream(ByteArrayInputStream(data))
                .use { it.readBytes() }
        } catch (e: Exception) {
            data
        }
    }
}