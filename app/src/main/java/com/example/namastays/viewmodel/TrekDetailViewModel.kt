package com.example.namastays.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.TrekDetailResult          // explicit import
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.domain.TrekDetail
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.trek.worker.TrekDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ── UI state ──────────────────────────────────────────────────────────────────

sealed class TrekDetailUiState {
    object Loading : TrekDetailUiState()
    data class Success(val trek: TrekDetail) : TrekDetailUiState()
    data class Error(
        val message:       String,
        val networkResult: NetworkResult<*>
    ) : TrekDetailUiState()
}

data class DownloadUiState(
    val isDownloaded:       Boolean = false,
    val isDownloading:      Boolean = false,
    val progress:           Int     = 0,
    val error:              String? = null,
    val showStorageWarning: Boolean = false,
    val availableStorageMb: Long    = 0L
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TrekDetailViewModel(
    private val trekId:     String,
    private val repository: TrekRepository,
    private val appContext: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(appContext)

    private val _detailState = MutableStateFlow<TrekDetailUiState>(TrekDetailUiState.Loading)
    val detailState: StateFlow<TrekDetailUiState> = _detailState.asStateFlow()

    private val _downloadState = MutableStateFlow(DownloadUiState())
    val downloadState: StateFlow<DownloadUiState> = _downloadState.asStateFlow()

    init {
        loadDetail()
        observeWorkManager()
    }

    // ── Detail loading ────────────────────────────────────────────────────────

    fun loadDetail() {
        viewModelScope.launch {
            _detailState.value = TrekDetailUiState.Loading

            when (val result = withContext(Dispatchers.IO) { repository.getTrekDetail(trekId) }) {
                is TrekDetailResult.Found -> {
                    _detailState.value = TrekDetailUiState.Success(result.detail)
                    refreshDownloadedState()
                }
                is TrekDetailResult.NetworkError -> {
                    val message = when (result.result) {
                        is NetworkResult.NoConnectivity ->
                            "No internet connection. Showing cached data if available."
                        is NetworkResult.Timeout ->
                            "Request timed out. Please try again."
                        is NetworkResult.ServerError ->
                            (result.result as NetworkResult.ServerError).message
                        else -> "Unknown error"
                    }
                    _detailState.value = TrekDetailUiState.Error(
                        message       = message,
                        networkResult = result.result
                    )
                }
                is TrekDetailResult.NotFound -> {
                    _detailState.value = TrekDetailUiState.Error(
                        message       = "Trek not found.",
                        networkResult = NetworkResult.ServerError("Trek not found in cache")
                    )
                }
            }
        }
    }

    fun retryDetail() = loadDetail()

    // ── Download state ────────────────────────────────────────────────────────

    private fun refreshDownloadedState() {
        viewModelScope.launch(Dispatchers.IO) {
            val downloaded = MBTilesLoader.isDownloaded(appContext, trekId)
            _downloadState.update { it.copy(isDownloaded = downloaded) }
        }
    }

    fun download() {
        val trek = (_detailState.value as? TrekDetailUiState.Success)?.trek ?: return

        viewModelScope.launch {
            _downloadState.update { it.copy(error = null) }

            val networkAvailable = withContext(Dispatchers.IO) { isNetworkAvailable() }
            if (!networkAvailable) {
                _downloadState.update { it.copy(error = "No internet connection. Please try again.") }
                return@launch
            }

            val available = withContext(Dispatchers.IO) { getAvailableStorageMb() }
            val needed    = trek.fileSizeMb + 20
            if (available < needed) {
                _downloadState.update {
                    it.copy(showStorageWarning = true, availableStorageMb = available)
                }
                return@launch
            }

            val request = TrekDownloadWorker.buildRequest(
                trekId       = trekId,
                trekName     = trek.name,
                tilesUrl     = trek.tilesUrl ?: "",
                gpxUrl       = trek.gpxUrl ?: "",
                waypointsUrl = trek.waypointsUrl ?: ""
            )
            workManager.enqueueUniqueWork(trekId, ExistingWorkPolicy.KEEP, request)
            _downloadState.update { it.copy(isDownloading = true) }
        }
    }

    fun deleteOfflineMap() {
        viewModelScope.launch(Dispatchers.IO) {
            val fileNames    = listOf("$trekId.mbtiles", "$trekId.gpx", "$trekId.json")
            val deleteErrors = fileNames.mapNotNull { name ->
                val file = File(appContext.filesDir, name)
                if (file.exists() && !file.delete()) name else null
            }

            if (deleteErrors.isNotEmpty()) {
                _downloadState.update {
                    it.copy(error = "Could not delete some files: ${deleteErrors.joinToString()}")
                }
            }

            repository.deleteDownload(trekId)
            _downloadState.update {
                it.copy(isDownloaded = false, isDownloading = false, progress = 0)
            }
        }
    }

    fun dismissStorageWarning() = _downloadState.update { it.copy(showStorageWarning = false) }
    fun clearDownloadError()    = _downloadState.update { it.copy(error = null) }

    // ── WorkManager observer ──────────────────────────────────────────────────

    private fun observeWorkManager() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(trekId).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(TrekDownloadWorker.KEY_PROGRESS, 0)
                        _downloadState.update { it.copy(isDownloading = true, progress = progress) }
                    }
                    WorkInfo.State.SUCCEEDED ->
                        _downloadState.update {
                            it.copy(isDownloading = false, isDownloaded = true, progress = 100)
                        }
                    WorkInfo.State.FAILED ->
                        _downloadState.update {
                            it.copy(isDownloading = false,
                                error = "Download failed. Please try again.")
                        }
                    WorkInfo.State.CANCELLED ->
                        _downloadState.update { it.copy(isDownloading = false) }
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> {
                        val networkOk = withContext(Dispatchers.IO) { isNetworkAvailable() }
                        if (!networkOk) {
                            workManager.cancelUniqueWork(trekId)
                            _downloadState.update {
                                it.copy(isDownloading = false,
                                    error = "No internet connection. Please try again.")
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps    = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun getAvailableStorageMb(): Long {
        val stat = android.os.StatFs(appContext.filesDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val trekId:     String,
        private val repository: TrekRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TrekDetailViewModel(trekId, repository, appContext) as T
        }
    }
}