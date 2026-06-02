package com.example.namastays.trek.presentation.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.runtime.livedata.observeAsState
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.TrekTheme
import com.example.namastays.trek.worker.TrekDownloadWorker
import kotlinx.coroutines.launch
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.presentataion.list.getDifficultyColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekDetailScreen(
    trekId: String,
    navController: NavController
) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workManager = remember { WorkManager.getInstance(context) }
    val db = remember { TrekDatabase.getInstance(context) }
    val dao = remember { db.downloadedTrekDao() }

    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showStorageWarning by remember { mutableStateOf(false) }
    var availableStorageMb by remember { mutableStateOf(0L) }


    val repository = remember { TrekRepository(db.trekCacheDao()) }
    var trek by remember { mutableStateOf<TrekItem?>(null) }

    LaunchedEffect(trekId) {
        trek = repository.getTrekById(trekId)
    }

    val currentTrek = trek ?: return
    LaunchedEffect(trekId) {
        isDownloaded = dao.isDownloaded(trekId)
    }

    val workInfosState by workManager
        .getWorkInfosForUniqueWorkLiveData(trekId)
        .observeAsState(emptyList<WorkInfo>())

    LaunchedEffect(workInfosState) {
        val info = workInfosState.firstOrNull() ?: return@LaunchedEffect
        when (info.state) {
            WorkInfo.State.RUNNING -> {
                isDownloading = true
                downloadProgress = info.progress.getInt(TrekDownloadWorker.KEY_PROGRESS, 0)
            }
            WorkInfo.State.SUCCEEDED -> {
                isDownloading = false
                isDownloaded = true
                downloadProgress = 100
            }
            WorkInfo.State.FAILED -> {
                isDownloading = false
                downloadError = "Download failed. Please try again."
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TrekTheme.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TrekTheme.Background
                )
            )
        },
        containerColor = TrekTheme.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                TrekTheme.PrimaryGreen,
                                TrekTheme.AccentGreen
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = getDifficultyColor(currentTrek.difficulty).copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = currentTrek.difficulty,
                            modifier = Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 4.dp
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = currentTrek.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentTrek.region,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailStat(
                        icon = Icons.Filled.Route,
                        value = "${currentTrek.distanceKm} km",
                        label = "Distance"
                    )
                    DetailStat(
                        icon = Icons.Filled.Schedule,
                        value = "${currentTrek.durationDays} days",
                        label = "Duration"
                    )
                    DetailStat(
                        icon = Icons.Filled.Landscape,
                        value = "${currentTrek.maxElevation}m",
                        label = "Max Elev."
                    )
                }

                HorizontalDivider(color = TrekTheme.Border)

                // Description
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrekTheme.TextPrimary
                    )
                    Text(
                        text = currentTrek.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TrekTheme.TextSecondary,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6
                    )
                }

                HorizontalDivider(color = TrekTheme.Border)

                // Offline pack includes
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Offline Pack Includes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrekTheme.TextPrimary
                    )
                    IncludeRow(Icons.Filled.Map, "Offline trail map")
                    IncludeRow(Icons.Filled.Timeline, "GPX route with elevation")
                    IncludeRow(Icons.Filled.Place, "${currentTrek.waypointsCount ?: 0} waypoints")
                    IncludeRow(Icons.Filled.Restaurant, "Teahouse locations")
                    IncludeRow(Icons.Filled.Navigation, "Trail navigation")
                }

                // Error
                downloadError?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = null,
                                tint = TrekTheme.ErrorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = TrekTheme.ErrorRed
                            )
                        }
                    }
                }

                // Download progress
                if (isDownloading) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TrekTheme.Surface
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when {
                                        downloadProgress < 15 -> "Downloading waypoints..."
                                        downloadProgress < 20 -> "Downloading route..."
                                        downloadProgress < 95 -> "Downloading map tiles..."
                                        else                  -> "Finishing up..."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TrekTheme.TextSecondary
                                )
                                Text(
                                    text = "$downloadProgress%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TrekTheme.PrimaryGreen
                                )
                            }
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(100.dp))
                                    .height(6.dp),
                                color = TrekTheme.TrailOrange,
                                trackColor = TrekTheme.Border
                            )
                        }
                    }
                }

                // Action buttons
                when {
                    isDownloaded -> {
                        Button(
                            onClick = { navController.navigate("trek_map/${currentTrek.id}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TrekTheme.PrimaryGreen
                            ),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Open Offline Map",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val filesDir = context.filesDir
                                listOf("$trekId.mbtiles", "$trekId.gpx", "$trekId.json")
                                    .forEach { java.io.File(filesDir, it).delete() }
                                scope.launch {
                                    dao.deleteByTrekId(trekId)
                                    isDownloaded = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TrekTheme.ErrorRed
                            ),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Delete Offline Map",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    isDownloading -> { /* no button */ }

                    else -> {
                        Button(
                            onClick = {
                                downloadError = null
                                val available = getAvailableStorageMb(context)
                                val needed = currentTrek.fileSizeMb + 20
                                if (available < needed) {
                                    availableStorageMb = available
                                    showStorageWarning = true
                                } else {
                                    val request = TrekDownloadWorker.buildRequest(
                                        trekId       = trekId,
                                        trekName     = currentTrek.name,
                                        tilesUrl     = currentTrek.tilesUrl ?: "",
                                        gpxUrl       = currentTrek.gpxUrl ?: "",
                                        waypointsUrl = currentTrek.waypointsUrl ?: ""
                                    )
                                    workManager.enqueueUniqueWork(
                                        trekId,
                                        androidx.work.ExistingWorkPolicy.KEEP,
                                        request
                                    )
                                    isDownloading = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TrekTheme.TrailOrange
                            ),
                            contentPadding = PaddingValues(vertical = 16.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Download Offline Map · ${currentTrek.fileSizeMb}MB",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Storage warning dialog
    if (showStorageWarning) {
        AlertDialog(
            onDismissRequest = { showStorageWarning = false },
            icon = {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    tint = TrekTheme.WarningOrange,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Not Enough Storage", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("You need ${currentTrek.fileSizeMb + 20}MB to download this trek.")
                    Text(
                        "Available: ${availableStorageMb}MB",
                        color = TrekTheme.ErrorRed,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Free up some space and try again.",
                        color = TrekTheme.TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showStorageWarning = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrekTheme.PrimaryGreen
                    )
                ) { Text("OK") }
            }
        )
    }
}

@Composable
fun DetailStat(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = TrekTheme.Surface,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = TrekTheme.PrimaryGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TrekTheme.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TrekTheme.TextSecondary
        )
    }
}

@Composable
fun IncludeRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = TrekTheme.PrimaryGreen.copy(alpha = 0.1f),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = TrekTheme.PrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TrekTheme.TextPrimary
        )
    }
}

fun getAvailableStorageMb(context: Context): Long {
    val stat = android.os.StatFs(context.filesDir.path)
    return stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
}