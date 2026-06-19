package com.example.namastays.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.data.SleepAltitudeRecord
import com.example.namastays.data.TrekSession
import com.example.namastays.ui.theme.TrekColors
import com.example.namastays.utilities.TrekTrackingService
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ─── Altitude zone ─────────────────────────────────────────────────────────────
enum class AltitudeZone(val label: String, val color: Color) {
    NORMAL("Normal",                 Color(0xFF2E7D32)),
    ACCLIMATIZATION("Acclimatization", Color(0xFFF9A825)),
    HIGH_RISK("High Risk",           Color(0xFFEF6C00)),
    EXTREME("Extreme",               Color(0xFFC62828))
}

// TrekColors lives in TrekTheme.kt — shared across all Trek screens.

// ─── Helpers ───────────────────────────────────────────────────────────────────
fun altitudeToZone(alt: Double): AltitudeZone = when {
    alt < 2_500 -> AltitudeZone.NORMAL
    alt < 3_500 -> AltitudeZone.ACCLIMATIZATION
    alt < 5_000 -> AltitudeZone.HIGH_RISK
    else        -> AltitudeZone.EXTREME
}

private fun isLocationEnabled(context: android.content.Context): Boolean {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
    return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

/** Format elapsed milliseconds as "2h 14m" or "34m" or "<1 m". */
private fun fmtDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when {
        h > 0  -> "${h}h ${m}m"
        m > 0  -> "${m}m"
        else   -> "<1 m"
    }
}

/** Avg pace as "14′30″/km". Returns "—" when speed is too low to be meaningful. */
private fun fmtPace(kmh: Double): String {
    // Below 0.5 km/h the pace number would be absurdly large; show dash instead.
    if (kmh < 0.5) return "—"
    val minPerKm = 60.0 / kmh
    val min = minPerKm.toInt()
    val sec = ((minPerKm - min) * 60).roundToInt()
    return "${min}′${sec.toString().padStart(2, '0')}″/km"
}

private val sessionDateFmt = SimpleDateFormat("d MMM", Locale.getDefault())
private val sessionTimeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

@RequiresApi(Build.VERSION_CODES.O)
private fun weekStart(offset: Int): LocalDate =
    LocalDate.now()
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        .plusWeeks(offset.toLong())

@RequiresApi(Build.VERSION_CODES.O)
private fun weekRangeLabel(offset: Int): String {
    if (offset == 0) return "This week"
    val monday = weekStart(offset)
    val sunday = monday.plusDays(6)
    val fmt    = DateTimeFormatter.ofPattern("d MMM")
    return "${monday.format(fmt)} – ${sunday.format(fmt)}"
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ms))

// ─── Session filter ────────────────────────────────────────────────────────────
enum class SessionFilter(val label: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    ALL_TIME("All Time")
}

private fun List<TrekSession>.applyFilter(filter: SessionFilter): List<TrekSession> {
    val nowMs   = System.currentTimeMillis()
    val cutoff  = when (filter) {
        SessionFilter.THIS_WEEK  -> nowMs - 7L  * 24 * 60 * 60 * 1_000
        SessionFilter.THIS_MONTH -> nowMs - 30L * 24 * 60 * 60 * 1_000
        SessionFilter.ALL_TIME   -> 0L
    }
    return filter { it.startMs >= cutoff }
}
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("DefaultLocale")
@Composable
fun TrekModeScreen(
    onSessionClick: (TrekSession) -> Unit = {}
) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: TrekViewModel = viewModel(
        factory = TrekViewModelFactory(context.applicationContext as Application)
    )

    val state           by viewModel.trekState.collectAsState()
    val allSleepRecords by viewModel.allSleepRecords.collectAsState()
    val allSessions     by viewModel.allSessions.collectAsState()

    var trekModeEnabled     by rememberSaveable { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showAnalyticsSheet  by remember { mutableStateOf(false) }
    var showLocationDialog  by remember { mutableStateOf(false) }

    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            if (isLocationEnabled(context)) {
                ContextCompat.startForegroundService(
                    context, Intent(context, TrekTrackingService::class.java)
                )
                trekModeEnabled = true
            } else {
                showLocationDialog = true
            }
        } else {
            trekModeEnabled = false
        }
    }

    // ── Location dialog ────────────────────────────────────────────────────────
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            shape          = RoundedCornerShape(20.dp),
            containerColor = TrekColors.surface,
            title = {
                Text("Location Required", fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TrekColors.onSurface)
            },
            text = {
                Text(
                    "Trek Mode needs GPS to be enabled. Please turn on Location in device settings.",
                    fontFamily = PlusJakartaSans, fontSize = 14.sp,
                    color = TrekColors.onSurfaceSub, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) {
                    Text("Open Settings", color = TrekColors.accent,
                        fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Cancel", fontFamily = PlusJakartaSans, color = TrekColors.onSurfaceSub)
                }
            }
        )
    }

    // ── Overwrite sleep altitude dialog ────────────────────────────────────────
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            shape          = RoundedCornerShape(24.dp),
            containerColor = TrekColors.surface,
            title          = null,
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier         = Modifier.size(56.dp).clip(CircleShape)
                            .background(TrekColors.accentLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = null,
                            tint = TrekColors.accent, modifier = Modifier.size(28.dp))
                    }
                    Text("Update Sleep Altitude?", fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = TrekColors.onSurface, textAlign = TextAlign.Center)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(TrekColors.surfaceAlt)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("LOGGED TODAY", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
                            Text("Already set", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
                        }
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null,
                            tint = TrekColors.onSurfaceSub, modifier = Modifier.size(16.dp))
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(TrekColors.accentLight)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("NEW READING", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp, color = TrekColors.accent, fontFamily = PlusJakartaSans)
                            Text("${state.altitude.toInt()} m", fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, color = TrekColors.accent, fontFamily = PlusJakartaSans)
                        }
                    }
                    Text("Replace today's sleep altitude with the new reading?",
                        fontFamily = PlusJakartaSans, fontSize = 13.sp,
                        color = TrekColors.onSurfaceSub, textAlign = TextAlign.Center, lineHeight = 19.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch { viewModel.saveSleepAltitude(state.altitude) }
                        showOverwriteDialog = false
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = TrekColors.accent),
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Text("Replace Altitude", fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = { showOverwriteDialog = false },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text("Keep Existing", fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium, color = TrekColors.onSurfaceSub, fontSize = 14.sp)
                }
            }
        )
    }

    if (showAnalyticsSheet) {
        SleepAltitudeAnalyticsSheet(
            records   = allSleepRecords,
            onDismiss = { showAnalyticsSheet = false }
        )
    }

    TrekModeContent(
        trekModeEnabled  = trekModeEnabled,
        altitude         = state.altitude,
        altitudeZone     = state.altitudeZone,
        gainMeters       = state.gainMeters,
        lossMeters       = state.lossMeters,
        speedKmh         = state.speedKmh,
        distanceKm       = state.distanceKm,
        accuracy         = state.accuracy,
        latitude         = state.latitude,
        longitude        = state.longitude,
        ascentRateM      = state.ascentRateM,
        inBatterySaver   = state.inBatterySaver,
        recentSessions   = allSessions,          // filter applied inside TrekModeContent
        onToggleTrekMode = { enabled ->
            if (enabled) {
                val hasPerm = requiredPermissions.all { perm ->
                    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                }
                when {
                    !hasPerm                   -> permissionLauncher.launch(requiredPermissions)
                    !isLocationEnabled(context) -> showLocationDialog = true
                    else -> {
                        ContextCompat.startForegroundService(
                            context, Intent(context, TrekTrackingService::class.java)
                        )
                        trekModeEnabled = true
                    }
                }
            } else {
                context.stopService(Intent(context, TrekTrackingService::class.java))
                trekModeEnabled = false
            }
        },
        onMarkSleepAltitude = {
            if (!trekModeEnabled) return@TrekModeContent
            coroutineScope.launch {
                if (viewModel.todayRecordExists()) showOverwriteDialog = true
                else viewModel.saveSleepAltitude(state.altitude)
            }
        },
        onViewAnalytics  = { showAnalyticsSheet = true },
        onSessionClick   = onSessionClick
    )
}

// ─── Stateless content ─────────────────────────────────────────────────────────
@SuppressLint("DefaultLocale")
@Composable
fun TrekModeContent(
    trekModeEnabled     : Boolean,
    altitude            : Double,
    altitudeZone        : AltitudeZone,
    gainMeters          : Double,
    lossMeters          : Double,
    speedKmh            : Double,
    distanceKm          : Double,
    accuracy            : Float,
    latitude            : Double,
    longitude           : Double,
    ascentRateM         : Double       = 0.0,
    inBatterySaver      : Boolean      = false,
    recentSessions      : List<TrekSession> = emptyList(),
    onToggleTrekMode    : (Boolean) -> Unit,
    onMarkSleepAltitude : () -> Unit   = {},
    onViewAnalytics     : () -> Unit   = {},
    onSessionClick      : (TrekSession) -> Unit = {}
) {
    // Filter state lives here — survives recomposition but not config change
    // (that's fine; user can re-select a filter chip in one tap)
    var activeFilter by remember { mutableStateOf(SessionFilter.THIS_WEEK) }
    val filteredSessions = remember(recentSessions, activeFilter) {
        recentSessions.applyFilter(activeFilter).take(20)
    }

    Scaffold(
        containerColor      = TrekColors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item { HeaderRow(trekModeEnabled, onToggleTrekMode) }

            // Status pill + battery saver pill side by side
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    StatusPill(trekModeEnabled)
                    if (inBatterySaver) BatterySaverPill()
                }
            }

            item {
                AltitudeHeroCard(
                    altitude     = altitude,
                    altitudeZone = altitudeZone,
                    gainMeters   = gainMeters,
                    lossMeters   = lossMeters,
                    speedKmh     = speedKmh
                )
            }

            // Metric cards row
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Outlined.Straighten,
                        title    = "DISTANCE",
                        value    = String.format("%.1f", distanceKm),
                        unit     = "km"
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Outlined.GpsFixed,
                        title    = "ACCURACY",
                        value    = "±${accuracy.toInt()}",
                        unit     = "m"
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon     = Icons.Outlined.Speed,
                        // fmtPace handles the <0.5 km/h edge case — never shows garbage
                        title    = "AVG PACE",
                        value    = fmtPace(speedKmh).substringBefore("/"),
                        unit     = if (speedKmh >= 0.5) "/km" else ""
                    )
                }
            }

            // Ascent rate card — only shown when Trek Mode is active and moving
            if (trekModeEnabled && ascentRateM > 0.0) {
                item { AscentRateCard(ascentRateM) }
            }

            item { ElevationProfileCard(currentAltitude = altitude) }
            item { CoordinatesCard(latitude, longitude) }

            // Action buttons
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(2.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = trekModeEnabled, onClick = onMarkSleepAltitude),
                        color = if (trekModeEnabled) TrekColors.surface else TrekColors.surfaceAlt,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Bedtime, contentDescription = "Mark Sleep Altitude",
                                tint     = if (trekModeEnabled) TrekColors.onSurface else TrekColors.onSurfaceSub,
                                modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Mark Sleep Altitude",
                                color      = if (trekModeEnabled) TrekColors.onSurface else TrekColors.onSurfaceSub,
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onViewAnalytics),
                        color = TrekColors.accentGreen,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ShowChart, contentDescription = "View Analytics",
                                tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("View Analytics", color = Color.White,
                                fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
                        }
                    }
                }
            }

            // ── Session logbook strip ──────────────────────────────────────────
            if (recentSessions.isNotEmpty()) {
                // Header row: label + filter chips
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "RECENT SESSIONS",
                            color         = TrekColors.onSurfaceSub,
                            fontSize      = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight    = FontWeight.SemiBold,
                            fontFamily    = PlusJakartaSans
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SessionFilter.entries.forEach { filter ->
                                val selected = filter == activeFilter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(
                                            if (selected) TrekColors.accentGreen
                                            else TrekColors.surfaceAlt
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication        = null
                                        ) { activeFilter = filter }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        filter.label,
                                        fontSize   = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color      = if (selected) Color.White
                                        else TrekColors.onSurfaceSub,
                                        fontFamily = PlusJakartaSans
                                    )
                                }
                            }
                        }
                    }
                }

                if (filteredSessions.isEmpty()) {
                    // Empty state for current filter
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(TrekColors.surface)
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No sessions in ${activeFilter.label.lowercase()}",
                                color      = TrekColors.onSurfaceSub,
                                fontSize   = 13.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                } else {
                    items(filteredSessions, key = { it.id }) { session ->
                        SessionLogRow(session = session, onClick = { onSessionClick(session) })
                    }
                }
            } else if (!trekModeEnabled) {
                // Empty state — no sessions at all yet
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(TrekColors.surface)
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Outlined.Hiking, contentDescription = null,
                                tint = TrekColors.onSurfaceSub, modifier = Modifier.size(32.dp))
                            Text("No sessions yet", color = TrekColors.onSurfaceSub,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                            Text("Toggle Trek Mode to start recording",
                                color = TrekColors.onSurfaceSub, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.navigationBarsPadding().height(32.dp))
            }
        }
    }
}


// ─── Analytics bottom sheet ────────────────────────────────────────────────────
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepAltitudeAnalyticsSheet(
    records  : List<SleepAltitudeRecord>,
    onDismiss: () -> Unit
) {
    var weekOffset    by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val recordMap = remember(records) { records.associateBy { it.date } }

    val weekDays = remember(weekOffset) {
        val monday = weekStart(weekOffset)
        (0..6).map { monday.plusDays(it.toLong()) }
    }

    val weekData: List<SleepAltitudeRecord?> = remember(weekDays, recordMap) {
        weekDays.map { recordMap[it.toString()] }
    }

    // Fix #6: minimum sensible scale is 1000m so a single low reading
    // doesn't make the bar fill the entire chart height.
    val maxAlt = remember(weekData) {
        weekData.filterNotNull()
            .maxOfOrNull { it.altitudeMeters }
            ?.coerceAtLeast(1000.0)
            ?: 4000.0
    }

    val showAmsWarning = remember(weekData) {
        weekData.filterNotNull().any { it.altitudeMeters >= 3000.0 }
    }

    LaunchedEffect(weekOffset) { selectedIndex = null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = TrekColors.background,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle       = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(TrekColors.divider)
            )
        }
    ) {
        LazyColumn(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Sleep Altitude",
                            fontSize   = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TrekColors.onSurface,
                            fontFamily = PlusJakartaSans
                        )
                        Text(
                            "Nightly altitude log",
                            fontSize   = 13.sp,
                            color      = TrekColors.onSurfaceSub,
                            fontFamily = PlusJakartaSans
                        )
                    }

                    // Week navigator pill
                    Surface(
                        shape           = RoundedCornerShape(50.dp),
                        color           = TrekColors.surface,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick  = { weekOffset-- },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ChevronLeft,
                                    contentDescription = "Previous week",
                                    tint     = TrekColors.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                weekRangeLabel(weekOffset),
                                fontSize   = 11.sp,
                                color      = TrekColors.onSurfaceSub,
                                fontFamily = PlusJakartaSans,
                                modifier   = Modifier.padding(horizontal = 2.dp)
                            )
                            IconButton(
                                onClick  = { if (weekOffset < 0) weekOffset++ },
                                enabled  = weekOffset < 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = "Next week",
                                    tint     = if (weekOffset < 0) TrekColors.onSurface else TrekColors.divider,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SleepBarChart(
                    data          = weekData,
                    maxAlt        = maxAlt,
                    selectedIndex = selectedIndex,
                    weekDays      = weekDays,
                    onBarClick    = { idx ->
                        selectedIndex = if (selectedIndex == idx) null else idx
                    }
                )
            }

            item {
                val sel = selectedIndex
                if (sel != null) {
                    SleepDetailCard(date = weekDays[sel], record = weekData[sel])
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TrekColors.surface)
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tap a bar to see details",
                            color      = TrekColors.onSurfaceSub,
                            fontSize   = 13.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }

            item { WeeklySummaryRow(weekData) }

            if (showAmsWarning) {
                item { AmsWarningCard() }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}


// ─── Bar chart ─────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SleepBarChart(
    data          : List<SleepAltitudeRecord?>,
    maxAlt        : Double,
    selectedIndex : Int?,
    weekDays      : List<LocalDate>,
    onBarClick    : (Int) -> Unit
) {
    val gridFractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(TrekColors.surface)
            .padding(20.dp)
    ) {
        // Tooltip — fixed height box to prevent layout shift
        val selRecord = selectedIndex?.let { data[it] }
        Box(
            modifier         = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selRecord != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(TrekColors.barGreen)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${selRecord.altitudeMeters.toInt()} m",
                        color      = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Y-axis labels
            // Fix #7: widened to 42.dp so 3-digit labels don't clip
            Column(
                modifier            = Modifier
                    .width(42.dp)
                    .height(200.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                gridFractions.reversed().forEach { frac ->
                    val altValue = (maxAlt * frac).toInt()
                    val label = when {
                        altValue >= 1000 -> "${altValue / 1000}k"
                        altValue == 0    -> "0"
                        else             -> "$altValue"
                    }
                    Text(
                        text       = "${label}m",
                        fontSize   = 9.sp,
                        color      = TrekColors.onSurfaceSub,
                        fontFamily = PlusJakartaSans,
                        textAlign  = TextAlign.End,
                        modifier   = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Fix #1: each day is a fully-clickable Column; graphicsLayer
            // is applied only to the visual Bar Box, not the touch target.
            Row(
                modifier              = Modifier
                    .weight(1f)
                    .height(200.dp + 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.Bottom
            ) {
                data.forEachIndexed { i, record ->
                    val frac = if (record != null)
                        (record.altitudeMeters / maxAlt).toFloat().coerceIn(0.05f, 1f)
                    else 0f

                    val isSelected = i == selectedIndex
                    val isToday    = weekDays.getOrNull(i) == LocalDate.now()

                    // Scale animates only the bar visual, touch target stays full size
                    val scale by animateFloatAsState(
                        targetValue    = if (isSelected) 1.05f else 1f,
                        animationSpec  = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness    = Spring.StiffnessMedium
                        ),
                        label = "barScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = null
                            ) { onBarClick(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        // Bar visual with scale animation — graphicsLayer here only,
                        // so touch bounds are unaffected
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight(frac)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    when {
                                        frac == 0f  -> Brush.verticalGradient(
                                            listOf(Color(0xFFF0F0F0), Color(0xFFF0F0F0))
                                        )
                                        isSelected  -> Brush.verticalGradient(
                                            listOf(TrekColors.barGreen, TrekColors.barGreenDark)
                                        )
                                        else        -> Brush.verticalGradient(
                                            listOf(TrekColors.barBlueDark, TrekColors.barBlue)
                                        )
                                    }
                                )
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text       = DAY_LABELS[i],
                            fontSize   = 11.sp,
                            textAlign  = TextAlign.Center,
                            color      = when {
                                isSelected -> TrekColors.barGreen
                                isToday    -> TrekColors.accent
                                else       -> TrekColors.onSurfaceSub
                            },
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }
    }
}

// ─── Detail card ───────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SleepDetailCard(date: LocalDate, record: SleepAltitudeRecord?) {
    val zone    = record?.let { altitudeToZone(it.altitudeMeters) }
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TrekColors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                date.format(dateFmt),
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = TrekColors.onSurface,
                fontFamily = PlusJakartaSans
            )
            Icon(
                Icons.Outlined.Hotel,
                contentDescription = null,
                tint     = TrekColors.onSurfaceSub,
                modifier = Modifier.size(20.dp)
            )
        }

        if (record == null) {
            Text(
                "No sleep altitude recorded for this day.",
                color      = TrekColors.onSurfaceSub,
                fontSize   = 13.sp,
                fontFamily = PlusJakartaSans
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Altitude chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(TrekColors.accentLight)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(TrekColors.accentGreen)
                    )
                    Text(
                        "Altitude: ${record.altitudeMeters.toInt()} m",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = TrekColors.onSurface,
                        fontFamily = PlusJakartaSans
                    )
                }

                // Zone chip
                val zoneBg = zone?.color?.copy(alpha = 0.12f) ?: TrekColors.surfaceAlt
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(zoneBg)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Zone: ${zone?.label ?: "–"}",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = zone?.color ?: TrekColors.onSurface,
                        fontFamily = PlusJakartaSans
                    )
                }
            }

            // Logged time
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier              = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(TrekColors.surfaceAlt)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint     = TrekColors.onSurfaceSub,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    formatTime(record.timestampMs),
                    fontSize   = 12.sp,
                    color      = TrekColors.onSurfaceSub,
                    fontFamily = PlusJakartaSans
                )
            }
        }
    }
}

// ─── Weekly summary row ────────────────────────────────────────────────────────
@Composable
private fun WeeklySummaryRow(weekData: List<SleepAltitudeRecord?>) {
    val recorded = weekData.count { it != null }
    val avgAlt   = weekData.filterNotNull().map { it.altitudeMeters }.average().takeIf { !it.isNaN() }
    val peakAlt  = weekData.filterNotNull().maxOfOrNull { it.altitudeMeters }
    val peakZone = peakAlt?.let { altitudeToZone(it) }

    fun formatAlt(alt: Double?) = when {
        alt == null -> "—"
        alt >= 1000 -> "${"%.1f".format(alt / 1000)}km"
        else        -> "${alt.toInt()}m"
    }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryChip(
            modifier   = Modifier.weight(1f),
            label      = "NIGHTS",
            value      = "$recorded/7",
            valueColor = TrekColors.onSurface
        )
        SummaryChip(
            modifier   = Modifier.weight(1f),
            label      = "AVG ALT",
            value      = formatAlt(avgAlt),
            valueColor = TrekColors.onSurface
        )
        SummaryChip(
            modifier   = Modifier.weight(1f),
            label      = "PEAK",
            value      = formatAlt(peakAlt),
            valueColor = peakZone?.color ?: TrekColors.onSurface
        )
    }
}

@Composable
private fun SummaryChip(modifier: Modifier, label: String, value: String, valueColor: Color) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(TrekColors.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            fontSize      = 10.sp,
            color         = TrekColors.onSurfaceSub,
            letterSpacing = 0.8.sp,
            fontFamily    = PlusJakartaSans,
            fontWeight    = FontWeight.SemiBold
        )
        Text(
            value,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            color      = valueColor,
            fontFamily = PlusJakartaSans
        )
    }
}

// ─── AMS warning card ──────────────────────────────────────────────────────────
// Fix #5: left accent border is now properly drawn via a layered Box.
@Composable
private fun AmsWarningCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TrekColors.amsBackground)
    ) {
        // Left accent border strip
        Box(
            modifier = Modifier
                .width(4.dp)
                .matchParentSize()
                .background(TrekColors.amsOrange)
        )
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TrekColors.amsOrange.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = "AMS Warning",
                    tint     = TrekColors.amsOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Check AMS Symptoms",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = TrekColors.amsOrange,
                    fontFamily = PlusJakartaSans
                )
                Text(
                    "Sleeping above 3,000 m detected. Ensure proper acclimatization and monitor for headaches or nausea.",
                    fontSize   = 13.sp,
                    color      = TrekColors.amsOrange.copy(alpha = 0.85f),
                    fontFamily = PlusJakartaSans,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

// ─── Battery saver pill ────────────────────────────────────────────────────────
@Composable
private fun BatterySaverPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(TrekColors.batterySaver.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            Icons.Outlined.BatteryChargingFull,
            contentDescription = "Battery Saver",
            tint     = TrekColors.batterySaver,
            modifier = Modifier.size(13.dp)
        )
        Text(
            "BATTERY SAVER",
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color         = TrekColors.batterySaver,
            fontFamily    = PlusJakartaSans
        )
    }
}

// ─── Ascent rate card ──────────────────────────────────────────────────────────
@Composable
private fun AscentRateCard(ascentRateM: Double) {
    val isHighRate = ascentRateM > 300.0   // > 300 m/hr is aggressive for acclimatization
    val color      = if (isHighRate) TrekColors.amsOrange else TrekColors.accentGreen
    val bg         = if (isHighRate) TrekColors.amsBackground else TrekColors.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier         = Modifier.size(40.dp).clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.TrendingUp, contentDescription = null,
                tint = color, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "ASCENT RATE",
                color         = TrekColors.onSurfaceSub,
                fontSize      = 10.sp,
                letterSpacing = 1.sp,
                fontWeight    = FontWeight.SemiBold,
                fontFamily    = PlusJakartaSans
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${ascentRateM.roundToInt()} m/hr  ·  30-min avg",
                color      = color,
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                fontFamily = PlusJakartaSans
            )
        }
        if (isHighRate) {
            Icon(Icons.Outlined.Warning, contentDescription = "High ascent rate",
                tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Session log row ───────────────────────────────────────────────────────────
@Composable
private fun SessionLogRow(session: TrekSession, onClick: () -> Unit) {
    val durationMs = if (session.endMs > 0) session.endMs - session.startMs else 0L

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(TrekColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier         = Modifier.size(40.dp).clip(CircleShape)
                .background(TrekColors.accentLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Hiking, contentDescription = null,
                tint = TrekColors.accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                sessionDateFmt.format(Date(session.startMs)) +
                        "  ·  " + sessionTimeFmt.format(Date(session.startMs)),
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TrekColors.onSurface,
                fontFamily = PlusJakartaSans
            )
            Text(
                buildString {
                    if (session.distanceM > 0) append("%.1f km".format(session.distanceM / 1000))
                    if (durationMs > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append(fmtDuration(durationMs))
                    }
                    if (session.gainM > 0) {
                        if (isNotEmpty()) append("  ·  ")
                        append("+${session.gainM.roundToInt()} m")
                    }
                }.ifEmpty { "No data" },
                fontSize   = 12.sp,
                color      = TrekColors.onSurfaceSub,
                fontFamily = PlusJakartaSans
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = "View details",
            tint = TrekColors.onSurfaceSub, modifier = Modifier.size(18.dp))
    }
}

// ─── All sub-composables below are unchanged from original ─────────────────────
// (HeaderRow, StatusPill, AltitudeHeroCard, HeroMetricItem, MetricCard,
//  ElevationProfileCard, CoordinatesCard, SleepAltitudeAnalyticsSheet,
//  SleepBarChart, SleepDetailCard, WeeklySummaryRow, SummaryChip, AmsWarningCard)
// Paste your existing implementations here — none of their logic changed.

@Composable
private fun HeaderRow(trekModeEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text("Trek Mode", color = TrekColors.onSurface, fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, fontFamily = PlusJakartaSans)
            Text("Live expedition tracking", color = TrekColors.onSurfaceSub,
                fontSize = 14.sp, fontFamily = PlusJakartaSans)
        }
        Switch(
            checked         = trekModeEnabled,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = TrekColors.accentGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCDD0D6)
            )
        )
    }
}

@Composable
private fun StatusPill(active: Boolean) {
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue  = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (active) TrekColors.accentLight else TrekColors.surfaceAlt)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(
            if (active) TrekColors.activeGreen.copy(alpha = dotAlpha)
            else TrekColors.onSurfaceSub.copy(alpha = 0.5f)
        ))
        Text(
            if (active) "TREK MODE ACTIVE" else "TREK MODE INACTIVE",
            fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
            color = if (active) TrekColors.accent else TrekColors.onSurfaceSub,
            fontFamily = PlusJakartaSans, maxLines = 1
        )
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun AltitudeHeroCard(
    altitude     : Double,
    altitudeZone : AltitudeZone,
    gainMeters   : Double,
    lossMeters   : Double,
    speedKmh     : Double
) {
    Box(
        modifier = Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp)).background(TrekColors.surface)
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top) {
                Text("CURRENT ALTITUDE", color = TrekColors.onSurfaceSub, fontSize = 11.sp,
                    letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                Box(modifier = Modifier.clip(RoundedCornerShape(50.dp))
                    .background(altitudeZone.color.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)) {
                    Text(altitudeZone.label.uppercase(), color = altitudeZone.color,
                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp, fontFamily = PlusJakartaSans)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (altitude > 0) "%,d".format(altitude.toInt()) else "—",
                    color = TrekColors.onSurface, fontSize = 68.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp,
                    lineHeight = 68.sp, fontFamily = PlusJakartaSans
                )
                Text(" m", color = TrekColors.onSurfaceSub, fontSize = 26.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp),
                    fontFamily = PlusJakartaSans)
            }
            Text("Raw Sensors – Real data may vary", color = TrekColors.onSurfaceSub,
                fontSize = 11.sp, fontStyle = FontStyle.Italic, fontFamily = PlusJakartaSans)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = TrekColors.divider, thickness = 1.dp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetricItem("GAIN",  "+${gainMeters.toInt()}m", TrekColors.gainGreen)
                HeroMetricItem("LOSS",  "-${lossMeters.toInt()}m", TrekColors.lossRed)
                // Speed: show "—" below noise floor instead of garbage near-zero values
                HeroMetricItem(
                    "SPEED",
                    if (speedKmh >= 0.5) "${String.format("%.1f", speedKmh)} km/h" else "—",
                    TrekColors.onSurface
                )
            }
        }
    }
}

@Composable
private fun HeroMetricItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = TrekColors.onSurfaceSub, fontSize = 10.sp,
            letterSpacing = 1.5.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, fontFamily = PlusJakartaSans)
    }
}

@Composable
fun MetricCard(
    modifier : Modifier = Modifier,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    title    : String,
    value    : String,
    unit     : String
) {
    Column(
        modifier = modifier.shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)).background(TrekColors.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = title, tint = TrekColors.accentGreen, modifier = Modifier.size(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TrekColors.onSurface, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, fontFamily = PlusJakartaSans, lineHeight = 20.sp)
            Text(unit, color = TrekColors.onSurfaceSub, fontSize = 11.sp,
                fontFamily = PlusJakartaSans, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
        }
        Text(title, color = TrekColors.onSurfaceSub, fontSize = 9.sp,
            letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ElevationProfileCard(currentAltitude: Double) {
    val maxAltitude = 8849.0
    val zones = listOf(
        Triple(AltitudeZone.NORMAL,          0.0,    2500.0),
        Triple(AltitudeZone.ACCLIMATIZATION, 2500.0, 3500.0),
        Triple(AltitudeZone.HIGH_RISK,       3500.0, 5000.0),
        Triple(AltitudeZone.EXTREME,         5000.0, maxAltitude)
    )
    Column(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(TrekColors.surface).padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("ELEVATION PROFILE", color = TrekColors.onSurfaceSub, fontSize = 11.sp,
                letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
            Icon(Icons.Outlined.Info, contentDescription = null,
                tint = TrekColors.onSurfaceSub, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50.dp))) {
            val totalWidth     = maxWidth
            val markerFraction = (currentAltitude.coerceIn(0.0, maxAltitude) / maxAltitude).toFloat()
            Row(Modifier.fillMaxSize()) {
                zones.forEach { (zone, from, to) ->
                    val fraction = ((to - from) / maxAltitude).toFloat()
                    Box(Modifier.fillMaxHeight().width(totalWidth * fraction).background(zone.color))
                }
            }
            Box(
                Modifier.fillMaxHeight().padding(vertical = 1.dp).width(3.dp)
                    .offset(x = (totalWidth * markerFraction).coerceIn(0.dp, totalWidth - 3.dp))
                    .clip(RoundedCornerShape(50.dp)).background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "2.5k", "3.5k", "5k", "8.8k m").forEach { label ->
                Text(label, color = TrekColors.onSurfaceSub, fontSize = 9.sp, fontFamily = PlusJakartaSans)
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = TrekColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))
        zones.forEach { (zone, from, to) ->
            val isCurrent = currentAltitude in from..to
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) zone.color.copy(alpha = 0.07f) else Color.Transparent)
                    .padding(vertical = 7.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(zone.color))
                Spacer(Modifier.width(10.dp))
                Text(zone.label, color = if (isCurrent) zone.color else TrekColors.onSurface,
                    fontSize = 13.sp, modifier = Modifier.weight(1f), fontFamily = PlusJakartaSans,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                Text("${from.toInt()} – ${to.toInt()} m", color = TrekColors.onSurfaceSub,
                    fontSize = 12.sp, fontFamily = PlusJakartaSans)
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun CoordinatesCard(latitude: Double, longitude: Double) {
    val hasCoords        = latitude != 0.0 && longitude != 0.0
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp)).background(TrekColors.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(TrekColors.accentLight),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.GpsFixed, contentDescription = "GPS",
                tint = TrekColors.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("LIVE COORDINATES", color = TrekColors.onSurfaceSub, fontSize = 10.sp,
                letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
            Spacer(Modifier.height(2.dp))
            Text(
                if (hasCoords)
                    "${"%.4f".format(latitude)}° ${if (latitude >= 0) "N" else "S"},  " +
                            "${"%.4f".format(longitude)}° ${if (longitude >= 0) "E" else "W"}"
                else "Acquiring GPS fix…",
                color      = if (hasCoords) TrekColors.onSurface else TrekColors.onSurfaceSub,
                fontWeight = if (hasCoords) FontWeight.SemiBold else FontWeight.Normal,
                fontSize   = 14.sp, fontFamily = PlusJakartaSans
            )
        }
        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy coordinates",
            tint     = if (hasCoords) TrekColors.onSurfaceSub else TrekColors.divider,
            modifier = Modifier.size(18.dp).clickable(
                enabled = hasCoords,
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                clipboardManager.setText(AnnotatedString("${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"))
            }
        )
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────
@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeActivePreview() {
    TrekModeContent(
        trekModeEnabled = true,  altitude = 3440.0,
        altitudeZone    = AltitudeZone.ACCLIMATIZATION,
        gainMeters      = 610.0, lossMeters = 120.0, speedKmh = 3.0,
        distanceKm      = 12.4,  accuracy   = 3.0f,  latitude = 27.8065,
        longitude       = 86.7140, ascentRateM = 220.0, inBatterySaver = false,
        onToggleTrekMode = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeBatterySaverPreview() {
    TrekModeContent(
        trekModeEnabled = true,  altitude = 4200.0,
        altitudeZone    = AltitudeZone.HIGH_RISK,
        gainMeters      = 900.0, lossMeters = 50.0, speedKmh = 0.3,
        distanceKm      = 8.1,   accuracy   = 12f,  latitude = 27.988,
        longitude       = 86.925, ascentRateM = 0.0, inBatterySaver = true,
        onToggleTrekMode = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeInactivePreview() {
    TrekModeContent(
        trekModeEnabled = false, altitude = 0.0,
        altitudeZone    = AltitudeZone.NORMAL,
        gainMeters      = 0.0, lossMeters = 0.0, speedKmh = 0.0,
        distanceKm      = 0.0, accuracy   = 0f,  latitude = 0.0,
        longitude       = 0.0, onToggleTrekMode = {}
    )
}