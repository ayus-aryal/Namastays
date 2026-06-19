package com.example.namastays.trek.presentation.detail

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.namastays.R
import com.example.namastays.repository.TrekRepository
import com.example.namastays.screens.PlusJakartaSans
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.TrekDetail
import com.example.namastays.trek.domain.TrailHighlight
import com.example.namastays.trek.domain.ItineraryDay
import com.example.namastays.trek.presentataion.list.difficultyColor
import com.example.namastays.viewmodel.DownloadUiState
import com.example.namastays.viewmodel.TrekDetailUiState
import com.example.namastays.viewmodel.TrekDetailViewModel

// ── Typography ────────────────────────────────────────────────────────────────



// ── Palette ───────────────────────────────────────────────────────────────────

private object DetailPalette {
    val Background    = Color(0xFFF2EDE8)
    val Surface       = Color(0xFFFFFFFF)
    val SurfaceDim    = Color(0xFFEDE8E2)
    val TextPrimary   = Color(0xFF1C1916)
    val TextSecondary = Color(0xFF7A6E66)
    val Accent        = Color(0xFFBF5B2A)
    val AccentLight   = Color(0xFFF4DDD1)
    val DarkGreen     = Color(0xFF1E3A2F)
    val ProgressBar   = Color(0xFFBF5B2A)
    val ProgressTrack = Color(0xFF3A5C4A)
    val ErrorRed      = Color(0xFFB83232)
    val ErrorBg       = Color(0xFFFFEBEE)
    val Divider       = Color(0xFFDDD7D0)
    val ItineraryDot  = Color(0xFFBF5B2A)
    val ItineraryLine = Color(0xFFE8DDD5)
    val NavBarBg      = Color(0xFFBF5B2A)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TrekDetailScreen(
    trekId: String,
    navController: NavController
) {
    val context = LocalContext.current
    val db      = remember { TrekDatabase.getInstance(context) }

    val viewModel: TrekDetailViewModel = viewModel(
        key     = trekId,
        factory = TrekDetailViewModel.Factory(
            trekId     = trekId,
            repository = TrekRepository(
                cacheDao      = db.trekCacheDao(),
                itineraryDao  = db.trekItineraryDao(),
                highlightDao  = db.trekHighlightDao(),
                downloadedDao = db.downloadedTrekDao()
            ),
            appContext = context.applicationContext
        )
    )

    val detailState   by viewModel.detailState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    when (val state = detailState) {
        is TrekDetailUiState.Loading -> DetailLoadingScreen(onBack = { navController.popBackStack() })
        is TrekDetailUiState.Error   -> DetailErrorScreen(
            message = state.message,
            onRetry = { viewModel.retryDetail() },
            onBack  = { navController.popBackStack() }
        )
        is TrekDetailUiState.Success -> DetailContentScreen(
            trek          = state.trek,
            downloadState = downloadState,
            navController = navController,
            onDownload    = { viewModel.download() },
            onDelete      = { viewModel.deleteOfflineMap() },
            onDismissStorageWarning = { viewModel.dismissStorageWarning() },
            onClearError  = { viewModel.clearDownloadError() }
        )
    }
}

// ── Loading screen ────────────────────────────────────────────────────────────

@Composable
private fun DetailLoadingScreen(onBack: () -> Unit) {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue  = -400f,
        targetValue   = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(DetailPalette.SurfaceDim, DetailPalette.Surface.copy(alpha = 0.9f), DetailPalette.SurfaceDim),
        start  = Offset(shimmerX, 0f),
        end    = Offset(shimmerX + 400f, 200f)
    )

    Box(Modifier.fillMaxSize().background(DetailPalette.Background)) {
        Column(Modifier.fillMaxSize()) {
            // Hero shimmer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(shimmerBrush)
            )
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Spacer(Modifier.height(8.dp))
                // Stat cards
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        Box(Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(14.dp)).background(shimmerBrush))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        Box(Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(14.dp)).background(shimmerBrush))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth(0.3f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmerBrush))
                Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(shimmerBrush))
            }
        }
        // Floating back button
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .padding(top = 48.dp, start = 8.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.25f), CircleShape)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Error screen ──────────────────────────────────────────────────────────────

@Composable
private fun DetailErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(DetailPalette.Background)) {
        Column(
            modifier            = Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = DetailPalette.TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(52.dp))
            Text(message, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DetailPalette.TextPrimary)
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = DetailPalette.Accent)
            ) {
                Text("Retry", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
            }
        }
        IconButton(
            onClick  = onBack,
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = DetailPalette.TextPrimary)
        }
    }
}

// ── Main content screen ───────────────────────────────────────────────────────

@Composable
private fun DetailContentScreen(
    trek:          TrekDetail,
    downloadState: DownloadUiState,
    navController: NavController,
    onDownload:    () -> Unit,
    onDelete:      () -> Unit,
    onDismissStorageWarning: () -> Unit,
    onClearError:  () -> Unit
) {
    val context = LocalContext.current
    var descExpanded by remember { mutableStateOf(false) }
    var showAllDays  by remember { mutableStateOf(false) }

    val visibleDays = if (showAllDays || trek.itineraryDays.size <= 3)
        trek.itineraryDays else trek.itineraryDays.take(3)

    Box(Modifier.fillMaxSize().background(DetailPalette.Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 80.dp)
        ) {

            // ── Hero ──────────────────────────────────────────────────────
            // Build the full image list: put thumbnailUrl first, then the rest from imagesUrl
            val heroImages = remember(trek) {
                buildList {
                    trek.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                    trek.imagesUrl.filter { it.isNotBlank() && it != trek.thumbnailUrl }.forEach { add(it) }
                }
            }

            val pagerState = rememberPagerState(pageCount = { heroImages.size })

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                // Fallback background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF3D2B1F), Color(0xFF1A1208))))
                )

                if (heroImages.isNotEmpty()) {
                    HorizontalPager(
                        state          = pagerState,
                        modifier       = Modifier.fillMaxSize(),
                        pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                            pagerState, androidx.compose.foundation.gestures.Orientation.Horizontal
                        )
                    ) { page ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(heroImages[page])
                                .crossfade(300)
                                .build(),
                            contentDescription = "${trek.name} image ${page + 1}",
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    }
                }

                // Bottom scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.72f)
                                )
                            )
                        )
                )


                // Dot indicators — only show if more than 1 image
                    if (heroImages.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),   // sits above the text overlay
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            heroImages.indices.forEach { i ->
                                val isSelected = pagerState.currentPage == i
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 8.dp else 5.dp)
                                        .background(
                                            color  = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                                            shape  = CircleShape
                                        )
                                )
                            }
                        }
                    }

                // Region + name + difficulty
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text          = trek.region.uppercase(),
                        fontFamily    = PlusJakartaSans,
                        fontWeight    = FontWeight.Medium,
                        fontSize      = 11.sp,
                        color         = Color.White.copy(alpha = 0.75f),
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text       = trek.name,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 26.sp,
                        color      = Color.White,
                        lineHeight = 30.sp
                    )
                    Surface(shape = RoundedCornerShape(5.dp), color = difficultyColor(trek.difficulty)) {
                        Text(
                            text       = trek.difficulty.uppercase(),
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 10.sp,
                            color      = Color.White,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                // Back button
                IconButton(
                    onClick  = { navController.popBackStack() },
                    modifier = Modifier
                        .padding(top = 48.dp, start = 8.dp)
                        .align(Alignment.TopStart)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // ── Body ──────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .background(DetailPalette.Background)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(20.dp))

                // 2×2 stat grid
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(Icons.Filled.Route,    "Distance", "${trek.distanceKm} km",      Modifier.weight(1f))
                        StatCard(Icons.Filled.Landscape,"Max Elev.","${trek.maxElevation} m",     Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(Icons.Filled.Schedule, "Duration", "${trek.durationDays} Days",  Modifier.weight(1f))
                        StatCard(Icons.Filled.Flag,     "Waypoints","${trek.waypointsCount ?: 0}", Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionDivider()
                Spacer(Modifier.height(20.dp))

                // About
                if (trek.description.isNotBlank()) {
                    SectionTitle("About")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = trek.description,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 14.sp,
                        color      = DetailPalette.TextSecondary,
                        lineHeight = 22.sp,
                        maxLines   = if (descExpanded) Int.MAX_VALUE else 4,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (trek.description.length > 200) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier  = Modifier.clickable { descExpanded = !descExpanded },
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text       = if (descExpanded) "Show less" else "Read more",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 13.sp,
                                color      = DetailPalette.Accent
                            )
                            Icon(
                                imageVector = if (descExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint     = DetailPalette.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionDivider()
                    Spacer(Modifier.height(20.dp))
                }

                // Trail highlights
                if (trek.highlights.isNotEmpty()) {
                    SectionTitle("Trail Highlights")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier              = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trek.highlights.forEach { HighlightChip(it) }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionDivider()
                    Spacer(Modifier.height(20.dp))
                }

                // Day-by-day itinerary
                if (trek.itineraryDays.isNotEmpty()) {
                    SectionTitle("Day-by-Day Itinerary")
                    Spacer(Modifier.height(16.dp))
                    visibleDays.forEachIndexed { index, day ->
                        ItineraryDayRow(
                            day    = day,
                            isLast = index == visibleDays.lastIndex &&
                                    (showAllDays || trek.itineraryDays.size <= 3)
                        )
                    }
                    if (trek.itineraryDays.size > 3) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick        = { showAllDays = !showAllDays },
                            modifier       = Modifier.fillMaxWidth(),
                            shape          = RoundedCornerShape(12.dp),
                            colors         = ButtonDefaults.outlinedButtonColors(contentColor = DetailPalette.TextPrimary),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Text(
                                text       = if (showAllDays) "Show less"
                                else "View Full ${trek.durationDays}-Day Itinerary",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Medium,
                                fontSize   = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionDivider()
                    Spacer(Modifier.height(20.dp))
                }

                // Offline pack card
                OfflinePackCard(
                    trek          = trek,
                    downloadState = downloadState,
                    onDownload    = onDownload,
                    onDelete      = onDelete,
                    onClearError  = onClearError
                )

                Spacer(Modifier.height(8.dp))
            }
        }

        // Sticky Begin Navigation button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(DetailPalette.Background)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick  = { navController.navigate("trek_map/${trek.id}") },
                enabled  = downloadState.isDownloaded,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = DetailPalette.NavBarBg,
                    disabledContainerColor = DetailPalette.NavBarBg.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Begin Navigation", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    }

    // Storage warning dialog
    if (downloadState.showStorageWarning) {
        AlertDialog(
            onDismissRequest = onDismissStorageWarning,
            icon = {
                Icon(Icons.Filled.Storage, contentDescription = null, tint = DetailPalette.Accent, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Not Enough Storage", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DetailPalette.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You need ${trek.fileSizeMb + 20} MB to download this trek.", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = DetailPalette.TextPrimary)
                    Text("Available: ${downloadState.availableStorageMb} MB", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DetailPalette.ErrorRed)
                    Text("Free up some space and try again.", fontFamily = PlusJakartaSans, fontSize = 13.sp, color = DetailPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(onClick = onDismissStorageWarning, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = DetailPalette.Accent)) {
                    Text("OK", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
                }
            },
            containerColor = DetailPalette.Surface
        )
    }
}

// ── Offline pack card ─────────────────────────────────────────────────────────

@Composable
private fun OfflinePackCard(
    trek:          TrekDetail,
    downloadState: DownloadUiState,
    onDownload:    () -> Unit,
    onDelete:      () -> Unit,
    onClearError:  () -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = DetailPalette.DarkGreen) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            downloadState.isDownloaded  -> "Trek Pack Downloaded"
                            downloadState.isDownloading -> "Downloading Trek Pack"
                            else                        -> "Download Trek Pack"
                        },
                        fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Offline maps, GPS logs & logbook templates.", fontFamily = PlusJakartaSans, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
                }
                when {
                    downloadState.isDownloaded  -> Icon(Icons.Filled.OfflinePin, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    downloadState.isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    else                        -> Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }

            // Progress bar
            if (downloadState.isDownloading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress   = { downloadState.progress / 100f },
                    modifier   = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
                    color      = DetailPalette.ProgressBar,
                    trackColor = DetailPalette.ProgressTrack
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = when {
                            downloadState.progress < 15 -> "Downloading waypoints…"
                            downloadState.progress < 20 -> "Downloading route…"
                            downloadState.progress < 95 -> "Downloading map tiles…"
                            else                        -> "Finishing up…"
                        },
                        fontFamily = PlusJakartaSans, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f)
                    )
                    Text(
                        text       = "${trek.fileSizeMb * downloadState.progress / 100} MB of ${trek.fileSizeMb} MB",
                        fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Error
            downloadState.error?.let { error ->
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = DetailPalette.ErrorBg) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = DetailPalette.ErrorRed, modifier = Modifier.size(14.dp))
                        Text(error, fontFamily = PlusJakartaSans, fontSize = 12.sp, color = DetailPalette.ErrorRed, modifier = Modifier.weight(1f))
                        IconButton(onClick = onClearError, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = DetailPalette.ErrorRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Action buttons
            if (!downloadState.isDownloading) {
                Spacer(Modifier.height(14.dp))
                if (downloadState.isDownloaded) {
                    OutlinedButton(
                        onClick        = onDelete,
                        modifier       = Modifier.fillMaxWidth(),
                        shape          = RoundedCornerShape(10.dp),
                        colors         = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Offline Map", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                } else {
                    Button(
                        onClick        = onDownload,
                        modifier       = Modifier.fillMaxWidth(),
                        shape          = RoundedCornerShape(10.dp),
                        colors         = ButtonDefaults.buttonColors(containerColor = DetailPalette.Accent),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (trek.fileSizeMb > 0) "Download · ${trek.fileSizeMb} MB" else "Download",
                            fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = DetailPalette.Surface) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = DetailPalette.Accent, modifier = Modifier.size(20.dp))
            Text(label, fontFamily = PlusJakartaSans, fontSize = 11.sp, color = DetailPalette.TextSecondary)
            Text(value, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DetailPalette.TextPrimary)
        }
    }
}

// ── Highlight chip ────────────────────────────────────────────────────────────

@Composable
private fun HighlightChip(highlight: TrailHighlight) {
    Surface(
        shape    = RoundedCornerShape(100.dp),
        color    = DetailPalette.Surface,
        modifier = Modifier.border(1.dp, DetailPalette.Divider, RoundedCornerShape(100.dp))
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(resolveHighlightIcon(highlight.iconName), contentDescription = null, tint = DetailPalette.Accent, modifier = Modifier.size(14.dp))
            Text(highlight.label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = DetailPalette.TextPrimary)
        }
    }
}

// ── Itinerary day row ─────────────────────────────────────────────────────────

@Composable
private fun ItineraryDayRow(day: ItineraryDay, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(modifier = Modifier.size(10.dp).background(DetailPalette.ItineraryDot, CircleShape))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(52.dp).background(DetailPalette.ItineraryLine))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 16.dp)) {
            Text("Day ${day.dayNumber}: ${day.title}", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = DetailPalette.TextPrimary)
            Spacer(Modifier.height(3.dp))
            Text(day.description, fontFamily = PlusJakartaSans, fontSize = 12.sp, color = DetailPalette.TextSecondary, lineHeight = 18.sp)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DetailPalette.TextPrimary)
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = DetailPalette.Divider, thickness = 1.dp)
}

private fun resolveHighlightIcon(name: String): ImageVector = when (name.lowercase()) {
    "landscape", "mountain", "pass"    -> Icons.Filled.Landscape
    "temple", "monastery", "heritage"  -> Icons.Filled.AccountBalance
    "water", "river", "lake"           -> Icons.Filled.Water
    "forest", "jungle"                 -> Icons.Filled.Park
    "village", "town"                  -> Icons.Filled.OtherHouses
    "viewpoint", "sunrise", "sunset"   -> Icons.Filled.WbSunny
    "wildlife", "bird"                 -> Icons.Filled.Pets
    "camp", "teahouse"                 -> Icons.Filled.NightShelter
    else                               -> Icons.Filled.Place
}

fun getAvailableStorageMb(context: Context): Long {
    val stat = android.os.StatFs(context.filesDir.path)
    return stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
}