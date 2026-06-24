package com.example.namastays.trek.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.DownloadedTrek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class TrekDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TREK_ID       = "trek_id"
        const val KEY_TREK_NAME     = "trek_name"
        const val KEY_TILES_URL     = "tiles_url"
        const val KEY_GPX_URL       = "gpx_url"
        const val KEY_WAYPOINTS_URL = "waypoints_url"
        const val KEY_PROGRESS      = "progress"
        const val KEY_ERROR         = "error"

        // FIX W1 — hard cap on redirect chain depth.
        private const val MAX_REDIRECTS = 5

        // FIX W6 — 64 KB buffer reduces loop iterations ~8× for large files.
        private const val BUFFER_SIZE = 65_536

        fun buildRequest(
            trekId:       String,
            trekName:     String,
            tilesUrl:     String,
            gpxUrl:       String,
            waypointsUrl: String
        ): OneTimeWorkRequest {
            val data = workDataOf(
                KEY_TREK_ID       to trekId,
                KEY_TREK_NAME     to trekName,
                KEY_TILES_URL     to tilesUrl,
                KEY_GPX_URL       to gpxUrl,
                KEY_WAYPOINTS_URL to waypointsUrl
            )
            return OneTimeWorkRequestBuilder<TrekDownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val trekId       = inputData.getString(KEY_TREK_ID)       ?: return Result.failure()
        val trekName     = inputData.getString(KEY_TREK_NAME)     ?: return Result.failure()
        val tilesUrl     = inputData.getString(KEY_TILES_URL)     ?: return Result.failure()
        val gpxUrl       = inputData.getString(KEY_GPX_URL)       ?: return Result.failure()
        val waypointsUrl = inputData.getString(KEY_WAYPOINTS_URL) ?: return Result.failure()

        return try {
            Log.d("TrekDownload", "Downloading waypoints from: $waypointsUrl")
            setProgress(workDataOf(KEY_PROGRESS to 5))
            downloadFile(
                url      = waypointsUrl,
                destFile = File(applicationContext.filesDir, "$trekId.json")
            )
            Log.d("TrekDownload", "Waypoints downloaded")

            Log.d("TrekDownload", "Downloading GPX from: $gpxUrl")
            setProgress(workDataOf(KEY_PROGRESS to 15))
            downloadFile(
                url      = gpxUrl,
                destFile = File(applicationContext.filesDir, "$trekId.gpx")
            )
            Log.d("TrekDownload", "GPX downloaded")

            Log.d("TrekDownload", "Downloading MBTiles from: $tilesUrl")
            setProgress(workDataOf(KEY_PROGRESS to 20))
            downloadFileWithProgress(
                url        = tilesUrl,
                destFile   = File(applicationContext.filesDir, "$trekId.mbtiles"),
                onProgress = { percent ->
                    val overall = 20 + (percent * 0.75).toInt()
                    setProgress(workDataOf(KEY_PROGRESS to overall))
                }
            )
            Log.d("TrekDownload", "MBTiles downloaded")

            setProgress(workDataOf(KEY_PROGRESS to 98))
            val db  = TrekDatabase.getInstance(applicationContext)
            val dao = db.downloadedTrekDao()

            val tilesFile = File(applicationContext.filesDir, "$trekId.mbtiles")
            dao.insert(
                DownloadedTrek(
                    trekId        = trekId,
                    trekName      = trekName,
                    fileSizeMb    = tilesFile.length() / (1024f * 1024f),
                    tilesPath     = tilesFile.absolutePath,
                    gpxPath       = File(applicationContext.filesDir, "$trekId.gpx").absolutePath,
                    waypointsPath = File(applicationContext.filesDir, "$trekId.json").absolutePath
                )
            )

            setProgress(workDataOf(KEY_PROGRESS to 100))
            Log.d("TrekDownload", "Download complete for $trekId")
            Result.success()

        } catch (e: Exception) {
            Log.e("TrekDownload", "Download failed: ${e.javaClass.simpleName}: ${e.message}")
            // FIX W5 — documented: the use{} block in downloadFile/downloadFileWithProgress
            // closes FileOutputStream before the exception propagates, so delete()
            // here is safe (the file handle is already released).
            File(applicationContext.filesDir, "$trekId.mbtiles").delete()
            File(applicationContext.filesDir, "$trekId.gpx").delete()
            File(applicationContext.filesDir, "$trekId.json").delete()
            Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    /**
     * Opens [url], follows up to [MAX_REDIRECTS] redirects, and streams the
     * response body into [destFile].
     *
     * FIX W1 — redirect depth is capped at [MAX_REDIRECTS]; throws if exceeded.
     * FIX W2 — every HttpURLConnection is disconnected in a finally block so
     *           sockets are released even if an exception occurs mid-redirect.
     */
    private suspend fun downloadFile(url: String, destFile: File) {
        withContext(Dispatchers.IO) {
            var currentUrl = url
            var redirectCount = 0
            var connection: HttpURLConnection? = null

            try {
                while (true) {
                    connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false   // we handle redirects manually
                        connect()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == 307 || responseCode == 308
                    ) {
                        // FIX W1 — enforce the redirect cap.
                        if (++redirectCount > MAX_REDIRECTS) {
                            throw Exception("Too many redirects (max $MAX_REDIRECTS) for $url")
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw Exception("Redirect with no Location header")
                        // FIX W2 — disconnect before opening the next connection.
                        connection.disconnect()
                        connection = null
                        currentUrl = location
                        continue
                    }

                    // Not a redirect — read the body.
                    connection.inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            // FIX W6 — 64 KB buffer.
                            input.copyTo(output, bufferSize = BUFFER_SIZE)
                        }
                    }
                    break
                }
            } finally {
                // FIX W2 — always disconnect, even on exception.
                connection?.disconnect()
            }
        }
    }

    /**
     * Same as [downloadFile] but calls [onProgress] with 0–100 as bytes arrive.
     *
     * FIX W1 — redirect depth capped.
     * FIX W2 — connections always disconnected in finally.
     * FIX W3 — uses [HttpURLConnection.getContentLengthLong] instead of
     *           [HttpURLConnection.getContentLength] (Int) to avoid silent
     *           overflow on files > 2 GB.
     * FIX W6 — 64 KB buffer.
     */
    private suspend fun downloadFileWithProgress(
        url:        String,
        destFile:   File,
        onProgress: suspend (Int) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var currentUrl    = url
            var redirectCount = 0
            var connection: HttpURLConnection? = null

            try {
                while (true) {
                    connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connect()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                        responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                        responseCode == 307 || responseCode == 308
                    ) {
                        if (++redirectCount > MAX_REDIRECTS) {
                            throw Exception("Too many redirects (max $MAX_REDIRECTS) for $url")
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw Exception("Redirect with no Location header")
                        connection.disconnect()
                        connection = null
                        currentUrl = location
                        continue
                    }

                    // FIX W3 — getContentLengthLong() returns Long; safe for any file size.
                    val totalBytes      = connection.contentLengthLong
                    var downloadedBytes = 0L

                    connection.inputStream.use { input ->
                        FileOutputStream(destFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)   // FIX W6
                            var bytes  = input.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                downloadedBytes += bytes
                                if (totalBytes > 0) {
                                    val percent = (downloadedBytes * 100 / totalBytes).toInt()
                                    onProgress(percent)
                                }
                                bytes = input.read(buffer)
                            }
                        }
                    }
                    break
                }
            } finally {
                connection?.disconnect()   // FIX W2
            }
        }
    }
}