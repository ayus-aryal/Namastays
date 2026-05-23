package com.example.namastays.screens

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Path
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.R
import com.example.namastays.data.SleepAltitudeRecord
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

// ─── Plus Jakarta Sans Font ───────────────────────────────────────────────────
// Requires: implementation("androidx.compose.ui:ui-text-google-fonts:<version>")
// in your build.gradle, and R.array.com_google_android_gms_fonts_certs in res/values/

// ─── Altitude zone definition ─────────────────────────────────────────────────

enum class AltitudeZone(val label: String, val color: Color) {
    NORMAL("Normal", Color(0xFF2E7D32)),
    ACCLIMATIZATION("Acclimatization", Color(0xFFF9A825)),
    HIGH_RISK("High Risk", Color(0xFFEF6C00)),
    EXTREME("Extreme", Color(0xFFC62828))
}

// ─── Colour palette ───────────────────────────────────────────────────────────

private object TrekColors {
    val background   = Color(0xFFF2F3F6)
    val surface      = Color(0xFFFFFFFF)
    val surfaceAlt   = Color(0xFFEEEFF2)
    val onSurface    = Color(0xFF0D1117)
    val onSurfaceSub = Color(0xFF8A92A0)
    val accent       = Color(0xFF1B5E20)
    val accentLight  = Color(0xFFE6F2E6)
    val divider      = Color(0xFFE8EAED)
    val activeGreen  = Color(0xFF22C55E)
    val navyDark     = Color(0xFF0D1117)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun altitudeToZone(alt: Double): AltitudeZone = when {
    alt < 2500 -> AltitudeZone.NORMAL
    alt < 3500 -> AltitudeZone.ACCLIMATIZATION
    alt < 5000 -> AltitudeZone.HIGH_RISK
    else       -> AltitudeZone.EXTREME
}

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
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

// ─── Main screen ──────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("DefaultLocale")
@Composable
fun TrekModeScreen() {

    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: TrekViewModel = viewModel(
        factory = TrekViewModelFactory(context.applicationContext as Application)
    )

    val state           by viewModel.trekState.collectAsState()
    val allSleepRecords by viewModel.allSleepRecords.collectAsState()

    var trekModeEnabled     by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var showAnalyticsSheet  by remember { mutableStateOf(false) }

    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, TrekTrackingService::class.java))
            trekModeEnabled = true
        } else {
            trekModeEnabled = false
        }
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            title   = { Text("Overwrite Sleep Altitude?", fontFamily = PlusJakartaSans) },
            text    = {
                Text(
                    "A sleep altitude of ${state.altitude.toInt()} m is already saved for today. Replace it?",
                    fontFamily = PlusJakartaSans
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { viewModel.saveSleepAltitude(state.altitude) }
                    showOverwriteDialog = false
                }) { Text("Overwrite", color = TrekColors.accent, fontFamily = PlusJakartaSans) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }) {
                    Text("Cancel", fontFamily = PlusJakartaSans)
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
        trekModeEnabled     = trekModeEnabled,
        altitude            = state.altitude,
        altitudeZone        = state.altitudeZone,
        gainMeters          = state.gainMeters,
        lossMeters          = state.lossMeters,
        speedKmh            = state.speedKmh,
        distanceKm          = state.distanceKm,
        accuracy            = state.accuracy,
        latitude            = state.latitude,
        longitude           = state.longitude,
        onToggleTrekMode    = { enabled ->
            if (enabled) {
                val missing = requiredPermissions.any { perm ->
                    ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
                }
                if (missing) permissionLauncher.launch(requiredPermissions)
                else {
                    ContextCompat.startForegroundService(context, Intent(context, TrekTrackingService::class.java))
                    trekModeEnabled = true
                }
            } else {
                context.stopService(Intent(context, TrekTrackingService::class.java))
                trekModeEnabled = false
            }
        },
        onMarkSleepAltitude = {
            coroutineScope.launch {
                if (viewModel.todayRecordExists()) showOverwriteDialog = true
                else viewModel.saveSleepAltitude(state.altitude)
            }
        },
        onViewAnalytics = { showAnalyticsSheet = true }
    )
}

// ─── Stateless content ────────────────────────────────────────────────────────

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
    onToggleTrekMode    : (Boolean) -> Unit,
    onMarkSleepAltitude : () -> Unit = {},
    onViewAnalytics     : () -> Unit = {}
) {
    Scaffold(containerColor = TrekColors.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            // Header
            item { HeaderRow(trekModeEnabled, onToggleTrekMode) }

            // Status pill
            item { StatusPill(trekModeEnabled) }

            // Hero altitude card
            item {
                AltitudeHeroCard(
                    altitude     = altitude,
                    altitudeZone = altitudeZone,
                    gainMeters   = gainMeters,
                    lossMeters   = lossMeters,
                    speedKmh     = speedKmh
                )
            }

            // Distance / Accuracy / Speed
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(Modifier.weight(1f), "DISTANCE",
                        String.format("%.2f km", distanceKm))
                    MetricCard(Modifier.weight(1f), "ACCURACY",
                        "±${accuracy.toInt()} m")
                    MetricCard(Modifier.weight(1f), "SPEED",
                        "${speedKmh.toInt()} km/h")
                }
            }

            // Altitude zone chart
            item { AltitudeZoneChart(currentAltitude = altitude) }

            // GPS card
            item { GpsCard(latitude, longitude) }

            // Tracking status

            // Action buttons – stacked vertically, full width
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mark Sleep Altitude – full-width white card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onMarkSleepAltitude() },
                        color    = TrekColors.surface,
                        shape    = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier             = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Bedtime,
                                contentDescription = null,
                                tint               = TrekColors.onSurface,
                                modifier           = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Mark Sleep Altitude",
                                color      = TrekColors.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }

                    // View Analytics – full-width dark navy button
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(3.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onViewAnalytics() },
                        color    = TrekColors.navyDark,
                        shape    = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                tint               = Color.White,
                                modifier           = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "View Analytics",
                                color      = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

// ─── Analytics bottom sheet ───────────────────────────────────────────────────

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

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

    val maxAlt = remember(weekData) {
        weekData.filterNotNull().maxOfOrNull { it.altitudeMeters }?.coerceAtLeast(500.0) ?: 4000.0
    }

    LaunchedEffect(weekOffset) { selectedIndex = null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = TrekColors.background,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                            fontSize   = 22.sp,
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

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { weekOffset-- }) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous week", tint = TrekColors.onSurface)
                        }
                        Text(
                            weekRangeLabel(weekOffset),
                            fontSize   = 11.sp,
                            color      = TrekColors.onSurfaceSub,
                            fontFamily = PlusJakartaSans
                        )
                        IconButton(
                            onClick  = { if (weekOffset < 0) weekOffset++ },
                            enabled  = weekOffset < 0
                        ) {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = "Next week",
                                tint = if (weekOffset < 0) TrekColors.onSurface else TrekColors.divider
                            )
                        }
                    }
                }
            }

            item {
                SleepBarChart(
                    data          = weekData,
                    maxAlt        = maxAlt,
                    selectedIndex = selectedIndex,
                    onBarClick    = { idx -> selectedIndex = if (selectedIndex == idx) null else idx }
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
                            .clip(RoundedCornerShape(20.dp))
                            .background(TrekColors.surface)
                            .padding(20.dp),
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

            item { WeeklySummaryCard(weekData) }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ─── Bar chart ────────────────────────────────────────────────────────────────

@Composable
private fun SleepBarChart(
    data          : List<SleepAltitudeRecord?>,
    maxAlt        : Double,
    selectedIndex : Int?,
    onBarClick    : (Int) -> Unit
) {
    val gridLines = listOf(0.25f, 0.5f, 0.75f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(TrekColors.surface)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            // ── Y-axis labels ──────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                gridLines.reversed().forEach { frac ->
                    val altValue = (maxAlt * frac).toInt()
                    val label = if (altValue >= 1000) "${altValue / 1000}k" else "$altValue"
                    Text(
                        text       = label,
                        fontSize   = 9.sp,
                        color      = TrekColors.onSurfaceSub,
                        fontFamily = PlusJakartaSans,
                        textAlign  = TextAlign.End,
                        modifier   = Modifier.width(28.dp)
                    )
                }
            }

            // ── Chart area ─────────────────────────────────────────
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val chartW  = size.width
                val chartH  = size.height
                val count   = 7
                val spacing = chartW * 0.03f
                val barW    = (chartW - spacing * (count + 1)) / count


                data.forEachIndexed { i, record ->
                    val left = spacing + i * (barW + spacing)
                    val frac = if (record != null)
                        (record.altitudeMeters / maxAlt).toFloat().coerceIn(0.04f, 1f)
                    else 0f

                    val barH = chartH * frac
                    val top  = chartH - barH

                    val color = when {
                        frac == 0f         -> Color(0xFFF0F0F0)
                        i == selectedIndex -> Color(0xFFE8A87C)   // darker peach when selected
                        else               -> Color(0xFFF5C9A0)   // warm peach default
                    }

                    // Flat-top bar with only bottom corners rounded (like iOS Screen Time)
                    val path = Path().apply {
                        moveTo(left, top)                          // top-left (flat)
                        lineTo(left + barW, top)                   // top-right (flat)
                        lineTo(left + barW, chartH - 6.dp.toPx()) // bottom-right before curve
                        quadraticBezierTo(
                            left + barW, chartH,
                            left + barW - 6.dp.toPx(), chartH
                        )
                        lineTo(left + 6.dp.toPx(), chartH)         // bottom edge
                        quadraticBezierTo(
                            left, chartH,
                            left, chartH - 6.dp.toPx()
                        )
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Day labels ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp),   // align with chart area (offset for y-axis labels)
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            DAY_LABELS.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBarClick(i) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = label,
                        fontSize   = 10.sp,
                        textAlign  = TextAlign.Center,
                        color      = if (i == selectedIndex) TrekColors.accent else TrekColors.onSurfaceSub,
                        fontWeight = if (i == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
    }
}
// ─── Detail card ──────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SleepDetailCard(date: LocalDate, record: SleepAltitudeRecord?) {
    val zone    = record?.let { altitudeToZone(it.altitudeMeters) }
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")
    val bgColor = zone?.color?.copy(alpha = 0.08f) ?: TrekColors.surfaceAlt

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            date.format(dateFmt),
            fontWeight = FontWeight.SemiBold,
            fontSize   = 14.sp,
            color      = TrekColors.onSurface,
            fontFamily = PlusJakartaSans
        )

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
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                DetailChip(label = "Altitude", value = "${record.altitudeMeters.toInt()} m", color = zone?.color ?: TrekColors.accent)
                DetailChip(label = "Zone",     value = zone?.label ?: "–",                  color = zone?.color ?: TrekColors.accent)
                DetailChip(label = "Saved at", value = formatTime(record.timestampMs),       color = TrekColors.onSurfaceSub)
            }
        }
    }
}

@Composable
private fun DetailChip(label: String, value: String, color: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = TrekColors.onSurfaceSub, letterSpacing = 0.5.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = PlusJakartaSans)
    }
}

// ─── Weekly summary card ──────────────────────────────────────────────────────

@Composable
private fun WeeklySummaryCard(weekData: List<SleepAltitudeRecord?>) {
    val recorded = weekData.count { it != null }
    val avgAlt   = weekData.filterNotNull().map { it.altitudeMeters }.average().takeIf { !it.isNaN() }
    val peakAlt  = weekData.filterNotNull().maxOfOrNull { it.altitudeMeters }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(TrekColors.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Weekly Summary", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TrekColors.onSurface, fontFamily = PlusJakartaSans)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryItem("Nights Logged", "$recorded / 7")
            SummaryItem("Avg Altitude",  avgAlt?.let { "${it.toInt()} m" } ?: "–")
            SummaryItem("Peak Alt",      peakAlt?.let { "${it.toInt()} m" } ?: "–")
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TrekColors.onSurface, fontFamily = PlusJakartaSans)
        Text(label, fontSize = 11.sp, color = TrekColors.onSurfaceSub, textAlign = TextAlign.Center, fontFamily = PlusJakartaSans)
    }
}

// ─── Header row ───────────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(trekModeEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Trek Mode",
                color         = TrekColors.onSurface,
                fontSize      = 30.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                fontFamily    = PlusJakartaSans
            )
            Text(
                "Live expedition tracking",
                color      = TrekColors.onSurfaceSub,
                fontSize   = 14.sp,
                fontFamily = PlusJakartaSans
            )
        }
        Switch(
            checked         = trekModeEnabled,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor   = Color.White,
                checkedTrackColor   = TrekColors.accent,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCDD0D6)
            )
        )
    }
}

// ─── Status pill ──────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(active: Boolean) {
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue  = 0.35f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label         = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main pill: dot + label
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(if (active) TrekColors.accentLight else TrekColors.surfaceAlt)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) TrekColors.activeGreen.copy(alpha = dotAlpha)
                        else TrekColors.onSurfaceSub.copy(alpha = 0.5f)
                    )
            )
            Text(
                if (active) "TREK MODE ACTIVE" else "TREK MODE INACTIVE",
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color         = if (active) TrekColors.accent else TrekColors.onSurfaceSub,
                fontFamily    = PlusJakartaSans
            )
        }

        // Standby / Optimized badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(if (active) TrekColors.accent else Color(0xFFDDE0E5))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                if (active) "Optimized" else "Standby",
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (active) Color.White else TrekColors.onSurfaceSub,
                fontFamily = PlusJakartaSans
            )
        }
    }
}

// ─── Altitude hero card ───────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
private fun AltitudeHeroCard(
    altitude     : Double,
    altitudeZone : AltitudeZone,
    gainMeters   : Double,
    lossMeters   : Double,
    speedKmh     : Double
) {
    val zoneGradient = when (altitudeZone) {
        AltitudeZone.NORMAL          -> Brush.linearGradient(
            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)),
            Offset(0f, 0f), Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.ACCLIMATIZATION -> Brush.linearGradient(
            listOf(Color(0xFFF57F17), Color(0xFFF9A825)),
            Offset(0f, 0f), Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.HIGH_RISK       -> Brush.linearGradient(
            listOf(Color(0xFFBF360C), Color(0xFFEF6C00)),
            Offset(0f, 0f), Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.EXTREME         -> Brush.linearGradient(
            listOf(Color(0xFF7F0000), Color(0xFFC62828)),
            Offset(0f, 0f), Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(zoneGradient)
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column {
            Text(
                "CURRENT ALTITUDE",
                color         = Color.White.copy(alpha = 0.65f),
                fontSize      = 11.sp,
                letterSpacing = 2.sp,
                fontWeight    = FontWeight.SemiBold,
                fontFamily    = PlusJakartaSans
            )
            Spacer(Modifier.height(10.dp))

            // Big altitude number
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${altitude.toInt()}",
                    color         = Color.White,
                    fontSize      = 72.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    letterSpacing = (-3).sp,
                    lineHeight    = 72.sp,
                    fontFamily    = PlusJakartaSans
                )
                Text(
                    " m",
                    color      = Color.White.copy(alpha = 0.75f),
                    fontSize   = 30.sp,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.padding(bottom = 10.dp),
                    fontFamily = PlusJakartaSans
                )
            }

            Spacer(Modifier.height(10.dp))

            // Zone badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    altitudeZone.label.uppercase(),
                    color         = Color.White,
                    fontSize      = 12.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily    = PlusJakartaSans
                )
            }

            Spacer(Modifier.height(8.dp))

            // Disclaimer text
            Text(
                "Raw sensors – actual altitude may differ",
                color      = Color.White.copy(alpha = 0.5f),
                fontSize   = 11.sp,
                fontStyle  = FontStyle.Italic,
                fontFamily = PlusJakartaSans
            )

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f), thickness = 1.dp)
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroMetric("GAIN",  "+${gainMeters.toInt()}m",  "↗")
                HeroMetric("LOSS",  "-${lossMeters.toInt()}m",  "↘")
                HeroMetric("SPEED", "${speedKmh.toInt()} km/h", "→")
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, icon: String) {
    Column {
        Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(5.dp))
        Text("$icon $value", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = PlusJakartaSans)
    }
}

// ─── Metric card ─────────────────────────────────────────────────────────────

@Composable
fun MetricCard(modifier: Modifier = Modifier, title: String, value: String) {
    Column(
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(TrekColors.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Text(
            title,
            color         = TrekColors.onSurfaceSub,
            fontSize      = 9.sp,
            letterSpacing = 1.2.sp,
            fontWeight    = FontWeight.SemiBold,
            fontFamily    = PlusJakartaSans
        )
        Spacer(Modifier.height(7.dp))
        Text(
            value,
            color      = TrekColors.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize   = 17.sp,
            fontFamily = PlusJakartaSans
        )
    }
}

// ─── Altitude zone chart ──────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AltitudeZoneChart(currentAltitude: Double) {
    val maxAltitude = 8849.0
    val zones = listOf(
        Triple(AltitudeZone.NORMAL,          0.0,    2500.0),
        Triple(AltitudeZone.ACCLIMATIZATION, 2500.0, 3500.0),
        Triple(AltitudeZone.HIGH_RISK,       3500.0, 5000.0),
        Triple(AltitudeZone.EXTREME,         5000.0, maxAltitude)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(TrekColors.surface)
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Altitude Zones", color = TrekColors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans)
            Text("${currentAltitude.toInt()} m", color = TrekColors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans)
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(50.dp))
        ) {
            val totalWidth     = maxWidth
            val markerFraction = (currentAltitude.coerceIn(0.0, maxAltitude) / maxAltitude).toFloat()
            Row(Modifier.fillMaxSize()) {
                zones.forEach { (zone, from, to) ->
                    val fraction = ((to - from) / maxAltitude).toFloat()
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(totalWidth * fraction)
                            .background(zone.color)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 1.dp)
                    .width(3.dp)
                    .offset(x = totalWidth * markerFraction - 1.5.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "2.5k", "3.5k", "5k", "8.8k m").forEach { label ->
                Text(label, color = TrekColors.onSurfaceSub, fontSize = 10.sp, fontFamily = PlusJakartaSans)
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = TrekColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(14.dp))
        zones.forEach { (zone, from, to) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(zone.color))
                Spacer(Modifier.width(10.dp))
                Text(zone.label, color = TrekColors.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f), fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium)
                Text("${from.toInt()} – ${to.toInt()} m", color = TrekColors.onSurfaceSub, fontSize = 12.sp, fontFamily = PlusJakartaSans)
            }
        }
    }
}

// ─── GPS card ─────────────────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
private fun GpsCard(latitude: Double, longitude: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(TrekColors.surface)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "GPS Coordinates",
                color      = TrekColors.onSurface,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSans
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint               = TrekColors.onSurfaceSub,
                modifier           = Modifier.size(20.dp)
            )
        }
        GpsRow("Latitude",  latitude)
        GpsRow("Longitude", longitude)
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun GpsRow(label: String, value: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TrekColors.onSurfaceSub, fontSize = 14.sp, fontFamily = PlusJakartaSans)
        Text(
            if (value == 0.0) "—" else String.format("%.4f°", value),
            color      = TrekColors.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 14.sp,
            fontFamily = PlusJakartaSans
        )
    }
}

// ─── Tracking status card ─────────────────────────────────────────────────────


// ─── Shared sub-composables ───────────────────────────────────────────────────

@Composable
fun TrekMetric(title: String, value: String) {
    Column {
        Text(title, color = TrekColors.onSurfaceSub, fontSize = 12.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(4.dp))
        Text(value, color = TrekColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = PlusJakartaSans)
    }
}


@Composable
fun AltitudeLevelItem(color: Color, label: String, range: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TrekColors.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp, fontFamily = PlusJakartaSans)
        Text(range, color = TrekColors.onSurfaceSub, fontSize = 13.sp, fontFamily = PlusJakartaSans)
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

@Preview(name = "Trek Mode – Acclimatization", showBackground = true,
    backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeScreenPreview() {
    TrekModeContent(
        trekModeEnabled = true,  altitude = 3440.0,
        altitudeZone    = AltitudeZone.ACCLIMATIZATION,
        gainMeters = 610.0,  lossMeters = 120.0, speedKmh   = 3.0,
        distanceKm = 8.4,    accuracy   = 4.0f,  latitude   = 27.8069,
        longitude  = 86.7139, onToggleTrekMode = {}
    )
}

@Preview(name = "Trek Mode – High Risk", showBackground = true,
    backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeScreenHighRiskPreview() {
    TrekModeContent(
        trekModeEnabled = true,  altitude = 4800.0,
        altitudeZone    = AltitudeZone.HIGH_RISK,
        gainMeters = 1200.0, lossMeters = 80.0,  speedKmh  = 2.0,
        distanceKm = 14.2,   accuracy   = 6.0f,  latitude  = 27.988,
        longitude  = 86.9250, onToggleTrekMode = {}
    )
}

@Preview(name = "Trek Mode – Disabled", showBackground = true,
    backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeScreenDisabledPreview() {
    TrekModeContent(
        trekModeEnabled = false, altitude = 0.0,
        altitudeZone    = AltitudeZone.NORMAL,
        gainMeters = 0.0, lossMeters = 0.0, speedKmh  = 0.0,
        distanceKm = 0.0, accuracy   = 0f,  latitude  = 0.0,
        longitude  = 0.0, onToggleTrekMode = {}
    )
}