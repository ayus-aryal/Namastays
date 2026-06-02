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
        const val KEY_TREK_ID      = "trek_id"
        const val KEY_TREK_NAME    = "trek_name"
        const val KEY_TILES_URL    = "tiles_url"
        const val KEY_GPX_URL      = "gpx_url"
        const val KEY_WAYPOINTS_URL = "waypoints_url"
        const val KEY_PROGRESS     = "progress"
        const val KEY_ERROR        = "error"

        fun buildRequest(
            trekId: String,
            trekName: String,
            tilesUrl: String,
            gpxUrl: String,
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
            // Step 1: Download waypoints JSON (tiny, fast)
            Log.d("TrekDownload", "Downloading waypoints from: $waypointsUrl")
            setProgress(workDataOf(KEY_PROGRESS to 5))
            downloadFile(
                url      = waypointsUrl,
                destFile = File(applicationContext.filesDir, "$trekId.json")
            )
            Log.d("TrekDownload", "Waypoints downloaded")


            Log.d("TrekDownload", "Downloading GPX from: $gpxUrl")
            // Step 2: Download GPX file (small, fast)
            setProgress(workDataOf(KEY_PROGRESS to 15))
            downloadFile(
                url      = gpxUrl,
                destFile = File(applicationContext.filesDir, "$trekId.gpx")
            )
            Log.d("TrekDownload", "GPX downloaded")



            Log.d("TrekDownload", "Downloading MBTiles from: $tilesUrl")
            // Step 3: Download MBTiles (large, slow — 30-65MB)
            setProgress(workDataOf(KEY_PROGRESS to 20))
            downloadFileWithProgress(
                url      = tilesUrl,
                destFile = File(applicationContext.filesDir, "$trekId.mbtiles"),
                onProgress = { percent ->
                    // Map 0-100% of tiles download to 20-95% overall
                    val overall = 20 + (percent * 0.75).toInt()
                    setProgress(workDataOf(KEY_PROGRESS to overall))
                }
            )
            Log.d("TrekDownload", "MBTiles downloaded")

            // Step 4: Save to Room
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
            e.printStackTrace()            // Clean up partial files
            File(applicationContext.filesDir, "$trekId.mbtiles").delete()
            File(applicationContext.filesDir, "$trekId.gpx").delete()
            File(applicationContext.filesDir, "$trekId.json").delete()
            Result.failure(workDataOf(KEY_ERROR to e.message))
        }
    }

    private suspend fun downloadFile(url: String, destFile: File) {
        withContext(Dispatchers.IO) {
            var connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()

            // Follow redirects manually if needed
            var responseCode = connection.responseCode
            var redirectUrl = url
            while (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308) {
                redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(redirectUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connect()
                responseCode = connection.responseCode
            }

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
        }
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        destFile: File,
        onProgress: suspend (Int) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            var connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()

            // Follow redirects manually if needed
            var responseCode = connection.responseCode
            var redirectUrl = url
            while (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == 307 || responseCode == 308) {
                redirectUrl = connection.getHeaderField("Location")
                connection.disconnect()
                connection = URL(redirectUrl).openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = true
                connection.connect()
                responseCode = connection.responseCode
            }

            val totalBytes = connection.contentLength.toLong()
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytes = input.read(buffer)
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
            connection.disconnect()
        }
    }
}