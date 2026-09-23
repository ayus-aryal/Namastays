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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.data.AnchorPoint
import com.example.namastays.data.SleepAltitudeRecord
import com.example.namastays.data.TrekSession
import com.example.namastays.dto.GpsSignalState
import com.example.namastays.ui.theme.TrekColors
import com.example.namastays.viewmodel.AnchorSaveResult
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ── WHAT CHANGED vs patch11 ───────────────────────────────────────────────────
// 1. Added `import com.example.namastays.dto.GpsSignalState`
// 2. Added `gpsSignalState: GpsSignalState = GpsSignalState.OK` parameter
//    to TrekModeContent.
// 3. Passed `gpsSignalState = state.gpsSignalState` in the TrekModeContent()
//    call inside TrekModeScreen.
// 4. Passed `gpsSignalState = gpsSignalState` to CoordinatesCard call site.
// 5. Replaced CoordinatesCard to consume gpsSignalState and show three
//    distinct states: OK (coordinates), ACQUIRING (first-fix wait),
//    DEGRADED (amber warning, GpsNotFixed icon, copy disabled).
// 6. New composable: AnchorLocationButton — separate from the existing
//    Mark Sleep Altitude / View Analytics surfaces since it has a distinct
//    two-state look (no anchor set vs anchor set/overwrite).
// Everything else is unchanged from the previous version of this file.// ─────────────────────────────────────────────────────────────────────────────

// ─── Altitude zone ─────────────────────────────────────────────────────────────

enum class AltitudeZone(val label: String, val color: Color) {
    NORMAL("Normal",                   Color(0xFF2E7D32)),
    ACCLIMATIZATION("Acclimatization", Color(0xFFF9A825)),
    HIGH_RISK("High Risk",             Color(0xFFEF6C00)),
    EXTREME("Extreme",                 Color(0xFFC62828))
}

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

private fun fmtDuration(ms: Long): String {
    if (ms <= 0L) return "—"
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return when { h > 0 -> "${h}h ${m}m"; m > 0 -> "${m}m"; else -> "<1 m" }
}

private fun fmtPace(kmh: Double): String {
    if (kmh < 0.5) return "—"
    val minPerKm = 60.0 / kmh
    val min = minPerKm.toInt()
    val sec = ((minPerKm - min) * 60).roundToInt()
    return "${min}′${sec.toString().padStart(2, '0')}″/km"
}

// FIX UI-8: thread-safe DateTimeFormatter replacing module-level SimpleDateFormat.
@RequiresApi(Build.VERSION_CODES.O)
private val SESSION_LOG_DATE_FMT = DateTimeFormatter.ofPattern("d MMM",   Locale.getDefault())
@RequiresApi(Build.VERSION_CODES.O)
private val SESSION_LOG_TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

@RequiresApi(Build.VERSION_CODES.O)
private fun Long.toZonedDateTime() =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

@RequiresApi(Build.VERSION_CODES.O)
private fun weekStart(offset: Int): LocalDate =
    LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())

@RequiresApi(Build.VERSION_CODES.O)
private fun weekRangeLabel(offset: Int): String {
    if (offset == 0) return "This week"
    val monday = weekStart(offset); val sunday = monday.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    return "${monday.format(fmt)} – ${sunday.format(fmt)}"
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatTime(ms: Long): String =
    ms.toZonedDateTime().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))

// ─── Session filter ────────────────────────────────────────────────────────────

enum class SessionFilter(val label: String) {
    THIS_WEEK("This Week"), THIS_MONTH("This Month"), ALL_TIME("All Time")
}

private fun List<TrekSession>.applyFilter(filter: SessionFilter): List<TrekSession> {
    val nowMs  = System.currentTimeMillis()
    val cutoff = when (filter) {
        SessionFilter.THIS_WEEK  -> nowMs - 7L  * 24 * 60 * 60 * 1_000
        SessionFilter.THIS_MONTH -> nowMs - 30L * 24 * 60 * 60 * 1_000
        SessionFilter.ALL_TIME   -> 0L
    }
    return filter { it.startMs >= cutoff }
}

// ─── Stateful screen ───────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TrekModeScreen(onSessionClick: (TrekSession) -> Unit = {}) {
    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val viewModel: TrekViewModel = viewModel(
        factory = TrekViewModelFactory(context.applicationContext as Application)
    )

    val isTracking      by viewModel.isTracking.collectAsStateWithLifecycle()
    val state           by viewModel.trekState.collectAsStateWithLifecycle()
    val allSleepRecords by viewModel.allSleepRecords.collectAsStateWithLifecycle()
    val allSessions     by viewModel.allSessions.collectAsStateWithLifecycle()
    val anchorPoint by viewModel.anchorPoint.collectAsStateWithLifecycle()

    // FIX UI-1: rememberSaveable so dialogs survive rotation.
    var showOverwriteDialog by rememberSaveable { mutableStateOf(false) }
    var showAnalyticsSheet  by rememberSaveable { mutableStateOf(false) }
    var showLocationDialog  by rememberSaveable { mutableStateOf(false) }

    var sleepSavedEvent by remember { mutableStateOf(0) }
    // NEW: mirrors sleepSavedEvent's pattern, but carries the actual result
    // so the toast text can differ (Saved / SavedWithLowAccuracy / NoFixAvailable).
    var anchorSaveEvent by remember { mutableStateOf<AnchorSaveResult?>(null) }

    val requiredPermissions = remember {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        when {
            !granted                    -> {}
            !isLocationEnabled(context) -> showLocationDialog = true
            else                        -> viewModel.startTrekMode()
        }
    }

    val attemptStart: () -> Unit = remember(context) {
        {
            val hasPerm = requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
            when {
                !hasPerm                    -> permissionLauncher.launch(requiredPermissions)
                !isLocationEnabled(context) -> showLocationDialog = true
                else                        -> viewModel.startTrekMode()
            }
        }
    }

    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            shape = RoundedCornerShape(20.dp), containerColor = TrekColors.surface,
            title = { Text("Location Required", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TrekColors.onSurface) },
            text  = { Text("Trek Mode needs GPS to be enabled. Please turn on Location in device settings.", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = TrekColors.onSurfaceSub, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = { showLocationDialog = false; context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text("Open Settings", color = TrekColors.accent, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) {
                    Text("Cancel", fontFamily = PlusJakartaSans, color = TrekColors.onSurfaceSub)
                }
            }
        )
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            shape = RoundedCornerShape(24.dp), containerColor = TrekColors.surface, title = null,
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(TrekColors.accentLight), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = TrekColors.accent, modifier = Modifier.size(28.dp))
                    }
                    Text("Update Sleep Altitude?", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TrekColors.onSurface, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(TrekColors.surfaceAlt).padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("LOGGED TODAY", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
                            Text("Already set", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
                        }
                        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = TrekColors.onSurfaceSub, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(TrekColors.accentLight).padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("NEW READING", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = TrekColors.accent, fontFamily = PlusJakartaSans)
                            Text("${state.altitude.toInt()} m", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TrekColors.accent, fontFamily = PlusJakartaSans)
                        }
                    }
                    Text("Replace today's sleep altitude with the new reading?", fontFamily = PlusJakartaSans, fontSize = 13.sp, color = TrekColors.onSurfaceSub, textAlign = TextAlign.Center, lineHeight = 19.sp)
                }
            },
            confirmButton = {
                Button(onClick = { coroutineScope.launch { viewModel.saveSleepAltitude(state.altitude); sleepSavedEvent++ }; showOverwriteDialog = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = TrekColors.accent), shape = RoundedCornerShape(14.dp)) {
                    Text("Replace Altitude", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = false }, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Keep Existing", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, color = TrekColors.onSurfaceSub, fontSize = 14.sp)
                }
            }
        )
    }

    if (showAnalyticsSheet) {
        SleepAltitudeAnalyticsSheet(records = allSleepRecords, onDismiss = { showAnalyticsSheet = false })
    }

    TrekModeContent(
        isTracking          = isTracking,
        altitude            = state.altitude,
        altitudeZone        = state.altitudeZone,
        gainMeters          = state.gainMeters,
        lossMeters          = state.lossMeters,
        speedKmh            = state.speedKmh,
        distanceKm          = state.distanceKm,
        accuracy            = state.accuracy,
        latitude            = state.latitude,
        longitude           = state.longitude,
        ascentRateM         = state.ascentRateM,
        inBatterySaver      = state.inBatterySaver,
        gpsSignalState      = state.gpsSignalState, // NEW
        anchorPoint = anchorPoint, // NEW
        recentSessions      = allSessions,
        sleepSavedEvent     = sleepSavedEvent,
        anchorSaveEvent = anchorSaveEvent,
        onToggleTrekMode    = { enable -> if (enable) attemptStart() else viewModel.stopTrekMode() },
        onMarkSleepAltitude = {
            if (!isTracking) return@TrekModeContent
            coroutineScope.launch {
                if (viewModel.todayRecordExists()) showOverwriteDialog = true
                else { viewModel.saveSleepAltitude(state.altitude); sleepSavedEvent++ }
            }
        },
        onAnchorLocation = {                              // NEW
            if (!isTracking) return@TrekModeContent
            viewModel.anchorCurrentLocation { result -> anchorSaveEvent = result }
        },
        onViewAnalytics  = { showAnalyticsSheet = true },
        onSessionClick   = onSessionClick
    )
}

// ─── Stateless content ─────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TrekModeContent(
    isTracking          : Boolean,
    altitude            : Double,
    altitudeZone        : AltitudeZone,
    gainMeters          : Double,
    lossMeters          : Double,
    speedKmh            : Double,
    distanceKm          : Double,
    accuracy            : Float,
    latitude            : Double,
    longitude           : Double,
    ascentRateM         : Double                = 0.0,
    inBatterySaver      : Boolean               = false,
    gpsSignalState      : GpsSignalState        = GpsSignalState.OK, // NEW
    anchorPoint         : AnchorPoint?          = null,
    recentSessions      : List<TrekSession>     = emptyList(),
    sleepSavedEvent     : Int                   = 0,
    anchorSaveEvent     : AnchorSaveResult?     = null,
    onToggleTrekMode    : (Boolean) -> Unit,
    onMarkSleepAltitude : () -> Unit            = {},
    onAnchorLocation    : () -> Unit            = {},
    onViewAnalytics     : () -> Unit            = {},
    onSessionClick      : (TrekSession) -> Unit = {}
) {
    var activeFilter by remember { mutableStateOf(SessionFilter.THIS_WEEK) }
    val filteredSessions = remember(recentSessions, activeFilter) {
        recentSessions.applyFilter(activeFilter).take(20)
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toastMessage) { if (toastMessage != null) { delay(2_000L); toastMessage = null } }
    LaunchedEffect(sleepSavedEvent) { if (sleepSavedEvent > 0) toastMessage = "Sleep altitude recorded" }

    // NEW: turns AnchorSaveResult into the right toast copy. Keyed on the
    // event object itself (data class equality) so re-tapping the button
    // with the identical result still re-triggers the toast — matches how
    // sleepSavedEvent uses an incrementing Int for the same reason.
    LaunchedEffect(anchorSaveEvent) {
        when (val evt = anchorSaveEvent) {
            is AnchorSaveResult.Saved ->
                toastMessage = "Anchor point saved"
            is AnchorSaveResult.SavedWithLowAccuracy ->
                toastMessage = "Anchor saved. GPS accuracy is low (±${evt.accuracyMeters.toInt()}m), point may be imprecise"
            is AnchorSaveResult.NoFixAvailable ->
                toastMessage = "Waiting for GPS fix, try again in a moment"
            null -> {}
        }
    }

    Scaffold(containerColor = TrekColors.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding).statusBarsPadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { Spacer(Modifier.height(12.dp)) }
                item { HeaderRow(isTracking, onToggleTrekMode) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(isTracking)
                        if (inBatterySaver) BatterySaverPill()
                    }
                }
                item { AltitudeHeroCard(altitude, altitudeZone, gainMeters, lossMeters, speedKmh) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(Modifier.weight(1f), Icons.Outlined.Straighten, "DISTANCE",  String.format(Locale.US, "%.1f", distanceKm), "km")
                        MetricCard(Modifier.weight(1f), Icons.Outlined.GpsFixed,   "ACCURACY",  "±${accuracy.toInt()}", "m")
                        MetricCard(Modifier.weight(1f), Icons.Outlined.Speed,       "AVG PACE",  fmtPace(speedKmh).substringBefore("/"), if (speedKmh >= 0.5) "/km" else "")
                    }
                }
                if (isTracking && ascentRateM > 0.0) item { AscentRateCard(ascentRateM) }
                item { ElevationProfileCard(altitude) }
                item {
                    // ← CHANGED: gpsSignalState passed through
                    CoordinatesCard(
                        latitude       = latitude,
                        longitude      = longitude,
                        gpsSignalState = gpsSignalState,
                        onCopied       = { toastMessage = "Coordinates copied" }
                    )
                }
                item {
                    // NEW: placed directly after CoordinatesCard, spatially
                    // adjacent to the lat/lng it's capturing.
                    AnchorLocationButton(
                        isTracking  = isTracking,
                        anchorPoint = anchorPoint,
                        onClick     = onAnchorLocation
                    )
                }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(modifier = Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable(enabled = isTracking, onClick = onMarkSleepAltitude), color = if (isTracking) TrekColors.surface else TrekColors.surfaceAlt, shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Bedtime, "Mark Sleep Altitude", tint = if (isTracking) TrekColors.onSurface else TrekColors.onSurfaceSub, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("Mark Sleep Altitude", color = if (isTracking) TrekColors.onSurface else TrekColors.onSurfaceSub, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
                            }
                        }
                        Surface(modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable(onClick = onViewAnalytics), color = TrekColors.accentGreen, shape = RoundedCornerShape(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShowChart, "View Analytics", tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text("View Analytics", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
                            }
                        }
                    }
                }
                if (recentSessions.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("RECENT SESSIONS", color = TrekColors.onSurfaceSub, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SessionFilter.entries.forEach { filter ->
                                    val selected = filter == activeFilter
                                    Box(modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(if (selected) TrekColors.accentGreen else TrekColors.surfaceAlt).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { activeFilter = filter }.padding(horizontal = 10.dp, vertical = 5.dp)) {
                                        Text(filter.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
                                    }
                                }
                            }
                        }
                    }
                    if (filteredSessions.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TrekColors.surface).padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text("No sessions in ${activeFilter.label.lowercase()}", color = TrekColors.onSurfaceSub, fontSize = 13.sp, fontFamily = PlusJakartaSans) } }
                    } else {
                        items(filteredSessions, key = { it.id }) { session -> SessionLogRow(session = session, onClick = { onSessionClick(session) }) }
                    }
                } else if (!isTracking) {
                    item {
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TrekColors.surface).padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Hiking, null, tint = TrekColors.onSurfaceSub, modifier = Modifier.size(32.dp))
                                Text("No sessions yet", color = TrekColors.onSurfaceSub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                                Text("Toggle Trek Mode to start recording", color = TrekColors.onSurfaceSub, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(32.dp)) }
            }
            AnimatedVisibility(visible = toastMessage != null, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }), exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }), modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)) {
                ToastBanner(text = toastMessage ?: "")
            }
        }
    }
}

// ── NEW: Anchor location button ────────────────────────────────────────────

/**
 * "Anchor My Current Location" button.
 *
 * Two visual states:
 *  - No anchor set: neutral surface, "Anchor My Current Location"
 *  - Anchor set: accent-tinted, "Anchor Point Set · Tap to Update" +
 *    relative age ("2h ago", "Just now") so the user knows how stale it is
 *    without having to open the Compass screen.
 *
 * Disabled (dimmed, non-interactive) when Trek Mode isn't tracking, matching
 * the existing Mark Sleep Altitude surface's disabled treatment above.
 */
@Composable
private fun AnchorLocationButton(
    isTracking  : Boolean,
    anchorPoint : AnchorPoint?,
    onClick     : () -> Unit
) {
    val hasAnchor = anchorPoint != null
    val bgColor = when {
        !isTracking -> TrekColors.surfaceAlt
        hasAnchor   -> TrekColors.accentLight
        else        -> TrekColors.surface
    }
    val contentColor = when {
        !isTracking -> TrekColors.onSurfaceSub
        hasAnchor   -> TrekColors.accent
        else        -> TrekColors.onSurface
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = isTracking, onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center   // ← centers the Row as a group
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector        = if (hasAnchor) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint               = contentColor,
                    modifier           = Modifier.size(20.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {  // ← no .weight(1f)
                    Text(
                        if (hasAnchor) "Anchor Point Set" else "Anchor My Current Location",
                        color      = contentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        fontFamily = PlusJakartaSans
                    )
                    if (hasAnchor && anchorPoint != null) {
                        Text(
                            "Tap to update · ${relativeAgeLabel(anchorPoint.timestampMillis)}",
                            color      = contentColor.copy(alpha = 0.75f),
                            fontSize   = 12.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }
    }
}

/** "Just now" / "12m ago" / "3h ago" / "2d ago" — short, no absolute-date fallback needed for this use. */
private fun relativeAgeLabel(timestampMillis: Long): String {
    val ageMs = System.currentTimeMillis() - timestampMillis
    val mins  = TimeUnit.MILLISECONDS.toMinutes(ageMs)
    val hours = TimeUnit.MILLISECONDS.toHours(ageMs)
    val days  = TimeUnit.MILLISECONDS.toDays(ageMs)
    return when {
        mins  < 1  -> "Just now"
        mins  < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        else       -> "${days}d ago"
    }
}

// ─── Analytics bottom sheet ────────────────────────────────────────────────────

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepAltitudeAnalyticsSheet(records: List<SleepAltitudeRecord>, onDismiss: () -> Unit) {
    var weekOffset    by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val recordMap  = remember(records) { records.associateBy { it.date } }
    val weekDays   = remember(weekOffset) { val monday = weekStart(weekOffset); (0..6).map { monday.plusDays(it.toLong()) } }
    val weekData   : List<SleepAltitudeRecord?> = remember(weekDays, recordMap) { weekDays.map { recordMap[it.toString()] } }
    val maxAlt     = remember(weekData) { weekData.filterNotNull().maxOfOrNull { it.altitudeMeters }?.coerceAtLeast(1000.0) ?: 4000.0 }
    val showAmsWarn = remember(weekData) { weekData.filterNotNull().any { it.altitudeMeters >= 3000.0 } }
    LaunchedEffect(weekOffset) { selectedIndex = null }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TrekColors.background, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(50.dp)).background(TrekColors.divider)) }
    ) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Sleep Altitude", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TrekColors.onSurface, fontFamily = PlusJakartaSans); Text("Nightly altitude log", fontSize = 13.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans) }
                    Surface(shape = RoundedCornerShape(50.dp), color = TrekColors.surface, shadowElevation = 2.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            IconButton(onClick = { weekOffset-- }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronLeft, "Previous week", tint = TrekColors.onSurface, modifier = Modifier.size(18.dp)) }
                            Text(weekRangeLabel(weekOffset), fontSize = 11.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans, modifier = Modifier.padding(horizontal = 2.dp))
                            IconButton(onClick = { if (weekOffset < 0) weekOffset++ }, enabled = weekOffset < 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.ChevronRight, "Next week", tint = if (weekOffset < 0) TrekColors.onSurface else TrekColors.divider, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
            }
            item { SleepBarChart(weekData, maxAlt, selectedIndex, weekDays) { idx -> selectedIndex = if (selectedIndex == idx) null else idx } }
            item {
                val sel = selectedIndex
                if (sel != null) SleepDetailCard(date = weekDays[sel], record = weekData[sel])
                else Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TrekColors.surface).padding(vertical = 20.dp), contentAlignment = Alignment.Center) { Text("Tap a bar to see details", color = TrekColors.onSurfaceSub, fontSize = 13.sp, fontFamily = PlusJakartaSans) }
            }
            item { WeeklySummaryRow(weekData) }
            if (showAmsWarn) item { AmsWarningCard() }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SleepBarChart(data: List<SleepAltitudeRecord?>, maxAlt: Double, selectedIndex: Int?, weekDays: List<LocalDate>, onBarClick: (Int) -> Unit) {
    val gridFractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    Column(modifier = Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(TrekColors.surface).padding(20.dp)) {
        val selRecord = selectedIndex?.let { data[it] }
        Box(modifier = Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            if (selRecord != null) Box(Modifier.clip(RoundedCornerShape(50.dp)).background(TrekColors.barGreen).padding(horizontal = 14.dp, vertical = 6.dp)) { Text("${selRecord.altitudeMeters.toInt()} m", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = PlusJakartaSans) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.width(42.dp).height(200.dp), verticalArrangement = Arrangement.SpaceBetween) {
                gridFractions.reversed().forEach { frac ->
                    val v = (maxAlt * frac).toInt()
                    Text("${if (v >= 1000) "${v/1000}k" else if (v == 0) "0" else "$v"}m", fontSize = 9.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).height(200.dp + 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                data.forEachIndexed { i, record ->
                    val frac = if (record != null) (record.altitudeMeters / maxAlt).toFloat().coerceIn(0.05f, 1f) else 0f
                    val isSelected = i == selectedIndex; val isToday = weekDays.getOrNull(i) == LocalDate.now()
                    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "barScale")
                    Column(Modifier.weight(1f).fillMaxHeight().graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(6.dp)).clickable(remember { MutableInteractionSource() }, null) { onBarClick(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(Modifier.fillMaxWidth(0.65f).fillMaxHeight(frac).graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(when { frac == 0f -> Brush.verticalGradient(listOf(Color(0xFFF0F0F0), Color(0xFFF0F0F0))); isSelected -> Brush.verticalGradient(listOf(TrekColors.barGreen, TrekColors.barGreenDark)); else -> Brush.verticalGradient(listOf(TrekColors.barBlueDark, TrekColors.barBlue)) }))
                        Spacer(Modifier.height(4.dp))
                        Text(DAY_LABELS[i], fontSize = 11.sp, textAlign = TextAlign.Center, color = when { isSelected -> TrekColors.barGreen; isToday -> TrekColors.accent; else -> TrekColors.onSurfaceSub }, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal, fontFamily = PlusJakartaSans)
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SleepDetailCard(date: LocalDate, record: SleepAltitudeRecord?) {
    val zone = record?.let { altitudeToZone(it.altitudeMeters) }
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TrekColors.surface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(dateFmt), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TrekColors.onSurface, fontFamily = PlusJakartaSans)
            Icon(Icons.Outlined.Hotel, null, tint = TrekColors.onSurfaceSub, modifier = Modifier.size(20.dp))
        }
        if (record == null) Text("No sleep altitude recorded for this day.", color = TrekColors.onSurfaceSub, fontSize = 13.sp, fontFamily = PlusJakartaSans)
        else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.clip(RoundedCornerShape(50.dp)).background(TrekColors.accentLight).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(TrekColors.accentGreen))
                    Text("Altitude: ${record.altitudeMeters.toInt()} m", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TrekColors.onSurface, fontFamily = PlusJakartaSans)
                }
                Box(Modifier.clip(RoundedCornerShape(50.dp)).background(zone?.color?.copy(alpha = 0.12f) ?: TrekColors.surfaceAlt).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text("Zone: ${zone?.label ?: "–"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = zone?.color ?: TrekColors.onSurface, fontFamily = PlusJakartaSans)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(TrekColors.surfaceAlt).padding(horizontal = 12.dp, vertical = 7.dp)) {
                Icon(Icons.Outlined.Schedule, null, tint = TrekColors.onSurfaceSub, modifier = Modifier.size(14.dp))
                Text(formatTime(record.timestampMs), fontSize = 12.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
            }
        }
    }
}

@Composable
private fun WeeklySummaryRow(weekData: List<SleepAltitudeRecord?>) {
    val recorded = weekData.count { it != null }
    val avgAlt   = weekData.filterNotNull().map { it.altitudeMeters }.average().takeIf { !it.isNaN() }
    val peakAlt  = weekData.filterNotNull().maxOfOrNull { it.altitudeMeters }
    val peakZone = peakAlt?.let { altitudeToZone(it) }
    fun fmt(a: Double?) = when { a == null -> "—"; a >= 1000 -> String.format(Locale.US, "%.1fkm", a / 1000); else -> "${a.toInt()}m" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryChip(Modifier.weight(1f), "NIGHTS",  "$recorded/7",  TrekColors.onSurface)
        SummaryChip(Modifier.weight(1f), "AVG ALT", fmt(avgAlt),    TrekColors.onSurface)
        SummaryChip(Modifier.weight(1f), "PEAK",    fmt(peakAlt),   peakZone?.color ?: TrekColors.onSurface)
    }
}

@Composable
private fun SummaryChip(modifier: Modifier, label: String, value: String, valueColor: Color) {
    Column(modifier.shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(TrekColors.surface).padding(horizontal = 14.dp, vertical = 16.dp), horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 10.sp, color = TrekColors.onSurfaceSub, letterSpacing = 0.8.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor, fontFamily = PlusJakartaSans)
    }
}

@Composable
private fun AmsWarningCard() {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TrekColors.amsBackground)) {
        Box(Modifier.width(4.dp).matchParentSize().background(TrekColors.amsOrange))
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(TrekColors.amsOrange.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Warning, "AMS Warning", tint = TrekColors.amsOrange, modifier = Modifier.size(20.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Check AMS Symptoms", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TrekColors.amsOrange, fontFamily = PlusJakartaSans)
                Text("Sleeping above 3,000 m detected. Ensure proper acclimatization and monitor for headaches or nausea.", fontSize = 13.sp, color = TrekColors.amsOrange.copy(alpha = 0.85f), fontFamily = PlusJakartaSans, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun BatterySaverPill() {
    Row(Modifier.clip(RoundedCornerShape(50.dp)).background(TrekColors.batterySaver.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Icon(Icons.Outlined.BatteryChargingFull, "Battery Saver", tint = TrekColors.batterySaver, modifier = Modifier.size(13.dp))
        Text("BATTERY SAVER", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = TrekColors.batterySaver, fontFamily = PlusJakartaSans)
    }
}

@Composable
private fun AscentRateCard(ascentRateM: Double) {
    val isHighRate = ascentRateM > 300.0
    val color = if (isHighRate) TrekColors.amsOrange else TrekColors.accentGreen
    val bg    = if (isHighRate) TrekColors.amsBackground else TrekColors.surface
    Row(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(bg).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TrendingUp, null, tint = color, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text("ASCENT RATE", color = TrekColors.onSurfaceSub, fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
            Spacer(Modifier.height(2.dp))
            Text("${ascentRateM.roundToInt()} m/hr  ·  30-min avg", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = PlusJakartaSans)
        }
        if (isHighRate) Icon(Icons.Outlined.Warning, "High ascent rate", tint = color, modifier = Modifier.size(18.dp))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun SessionLogRow(session: TrekSession, onClick: () -> Unit) {
    val durationMs = if (session.endMs > 0) session.endMs - session.startMs else 0L
    // FIX UI-8: DateTimeFormatter, memoized.
    val dateStr = remember(session.startMs) { session.startMs.toZonedDateTime().format(SESSION_LOG_DATE_FMT) }
    val timeStr = remember(session.startMs) { session.startMs.toZonedDateTime().format(SESSION_LOG_TIME_FMT) }
    Row(Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(TrekColors.surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(TrekColors.accentLight), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Hiking, null, tint = TrekColors.accent, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$dateStr  ·  $timeStr", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TrekColors.onSurface, fontFamily = PlusJakartaSans)
            Text(buildString {
                if (session.distanceM > 0) append(String.format(Locale.US, "%.1f km", session.distanceM / 1000))
                if (durationMs > 0) { if (isNotEmpty()) append("  ·  "); append(fmtDuration(durationMs)) }
                if (session.gainM > 0) { if (isNotEmpty()) append("  ·  "); append("+${session.gainM.roundToInt()} m") }
            }.ifEmpty { "No data" }, fontSize = 12.sp, color = TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans)
        }
        Icon(Icons.Outlined.ChevronRight, "View details", tint = TrekColors.onSurfaceSub, modifier = Modifier.size(18.dp))
    }
}

// ─── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(isTracking: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("Trek Mode", color = TrekColors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, fontFamily = PlusJakartaSans)
            Text("Live expedition tracking", color = TrekColors.onSurfaceSub, fontSize = 14.sp, fontFamily = PlusJakartaSans)
        }
        Switch(checked = isTracking, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TrekColors.accentGreen, uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFCDD0D6)))
    }
}

// FIX UI-3: ActiveDot extracted so the infinite transition only runs when active.
@Composable
private fun StatusPill(active: Boolean) {
    Row(Modifier.clip(RoundedCornerShape(50.dp)).background(if (active) TrekColors.accentLight else TrekColors.surfaceAlt).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        if (active) ActiveDot()
        else Box(Modifier.size(7.dp).clip(CircleShape).background(TrekColors.onSurfaceSub.copy(alpha = 0.5f)))
        Text(if (active) "TREK MODE ACTIVE" else "TREK MODE INACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = if (active) TrekColors.accent else TrekColors.onSurfaceSub, fontFamily = PlusJakartaSans, maxLines = 1)
    }
}

@Composable
private fun ActiveDot() {
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(initialValue = 0.35f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(Modifier.size(7.dp).clip(CircleShape).background(TrekColors.activeGreen.copy(alpha = dotAlpha)))
}

@Composable
private fun AltitudeHeroCard(altitude: Double, altitudeZone: AltitudeZone, gainMeters: Double, lossMeters: Double, speedKmh: Double) {
    Box(Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(TrekColors.surface).padding(horizontal = 22.dp, vertical = 24.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text("CURRENT ALTITUDE", color = TrekColors.onSurfaceSub, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                Box(Modifier.clip(RoundedCornerShape(50.dp)).background(altitudeZone.color.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp)) { Text(altitudeZone.label.uppercase(), color = altitudeZone.color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, fontFamily = PlusJakartaSans) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                // FIX UI-4: Locale.getDefault() explicit for display-only thousands separator.
                Text(if (altitude > 0) String.format(Locale.getDefault(), "%,d", altitude.toInt()) else "—", color = TrekColors.onSurface, fontSize = 68.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-2).sp, lineHeight = 68.sp, fontFamily = PlusJakartaSans)
                Text(" m", color = TrekColors.onSurfaceSub, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 10.dp), fontFamily = PlusJakartaSans)
            }
            Text("Raw Sensors – Real data may vary", color = TrekColors.onSurfaceSub, fontSize = 11.sp, fontStyle = FontStyle.Italic, fontFamily = PlusJakartaSans)
            Spacer(Modifier.height(20.dp)); HorizontalDivider(color = TrekColors.divider, thickness = 1.dp); Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetricItem("GAIN",  "+${gainMeters.toInt()}m", TrekColors.gainGreen)
                HeroMetricItem("LOSS",  "-${lossMeters.toInt()}m", TrekColors.lossRed)
                HeroMetricItem("SPEED", if (speedKmh >= 0.5) String.format(Locale.US, "%.1f km/h", speedKmh) else "—", TrekColors.onSurface)
            }
        }
    }
}

@Composable
private fun HeroMetricItem(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, color = TrekColors.onSurfaceSub, fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = PlusJakartaSans)
    }
}

@Composable
fun MetricCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, unit: String) {
    Column(modifier.shadow(2.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(TrekColors.surface).padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = title, tint = TrekColors.accentGreen, modifier = Modifier.size(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TrekColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = PlusJakartaSans, lineHeight = 20.sp)
            Text(unit, color = TrekColors.onSurfaceSub, fontSize = 11.sp, fontFamily = PlusJakartaSans, modifier = Modifier.padding(bottom = 2.dp, start = 1.dp))
        }
        Text(title, color = TrekColors.onSurfaceSub, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ElevationProfileCard(currentAltitude: Double) {
    val maxAltitude = 8849.0
    val zones = remember { listOf(Triple(AltitudeZone.NORMAL, 0.0, 2500.0), Triple(AltitudeZone.ACCLIMATIZATION, 2500.0, 3500.0), Triple(AltitudeZone.HIGH_RISK, 3500.0, 5000.0), Triple(AltitudeZone.EXTREME, 5000.0, maxAltitude)) }
    Column(Modifier.fillMaxWidth().shadow(3.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(TrekColors.surface).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ELEVATION PROFILE", color = TrekColors.onSurfaceSub, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
            Icon(Icons.Outlined.Info, null, tint = TrekColors.onSurfaceSub, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50.dp))) {
            val totalWidth = maxWidth; val markerFraction = (currentAltitude.coerceIn(0.0, maxAltitude) / maxAltitude).toFloat()
            Row(Modifier.fillMaxSize()) { zones.forEach { (zone, from, to) -> Box(Modifier.fillMaxHeight().width(totalWidth * ((to - from) / maxAltitude).toFloat()).background(zone.color)) } }
            Box(Modifier.fillMaxHeight().padding(vertical = 1.dp).width(3.dp).offset(x = (totalWidth * markerFraction).coerceIn(0.dp, totalWidth - 3.dp)).clip(RoundedCornerShape(50.dp)).background(Color.White))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("0", "2.5k", "3.5k", "5k", "8.8k m").forEach { Text(it, color = TrekColors.onSurfaceSub, fontSize = 9.sp, fontFamily = PlusJakartaSans) } }
        Spacer(Modifier.height(14.dp)); HorizontalDivider(color = TrekColors.divider, thickness = 1.dp); Spacer(Modifier.height(12.dp))
        zones.forEach { (zone, from, to) ->
            val isCurrent = currentAltitude in from..to
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isCurrent) zone.color.copy(alpha = 0.07f) else Color.Transparent).padding(vertical = 7.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(zone.color)); Spacer(Modifier.width(10.dp))
                Text(zone.label, color = if (isCurrent) zone.color else TrekColors.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f), fontFamily = PlusJakartaSans, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
                Text("${from.toInt()} – ${to.toInt()} m", color = TrekColors.onSurfaceSub, fontSize = 12.sp, fontFamily = PlusJakartaSans)
            }
        }
    }
}

// ── NEW: CoordinatesCard rewritten to consume GpsSignalState ──────────────────
@Composable
private fun CoordinatesCard(
    latitude       : Double,
    longitude      : Double,
    gpsSignalState : GpsSignalState = GpsSignalState.OK,
    onCopied       : () -> Unit = {}
) {
    val hasCoords        = latitude != 0.0 && longitude != 0.0
    val clipboardManager = LocalClipboardManager.current

    // Derive display text and whether the copy button should be active from
    // the typed signal state — not just from lat/lng being zero.
    //
    // Previously: only ACQUIRING (lat/lng == 0.0) was handled. A fix lost
    // mid-session left hasCoords=true (last known coords still non-zero) so
    // the card kept showing stale values with no indicator that signal was gone.
    //
    // Now:
    //   DEGRADED  → amber warning, GpsNotFixed icon, copy disabled
    //   ACQUIRING → "Acquiring GPS fix…" placeholder, copy disabled
    //   OK        → live coordinates, copy enabled
    val isDegraded  = gpsSignalState is GpsSignalState.DEGRADED
    val isAcquiring = gpsSignalState is GpsSignalState.ACQUIRING || (!hasCoords && !isDegraded)
    val isLive      = !isDegraded && !isAcquiring && hasCoords

    val displayText = when {
        isDegraded  -> "Weak GPS signal — data may be stale"
        isAcquiring -> "Acquiring GPS fix…"
        else        ->
            "${String.format(Locale.US, "%.4f", latitude)}° ${if (latitude >= 0) "N" else "S"},  " +
                    "${String.format(Locale.US, "%.4f", longitude)}° ${if (longitude >= 0) "E" else "W"}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDegraded) TrekColors.amsBackground else TrekColors.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isDegraded) TrekColors.amsOrange.copy(alpha = 0.12f) else TrekColors.accentLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isDegraded) Icons.Outlined.GpsNotFixed else Icons.Outlined.GpsFixed,
                contentDescription = "GPS",
                tint               = if (isDegraded) TrekColors.amsOrange else TrekColors.accent,
                modifier           = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "LIVE COORDINATES",
                color         = if (isDegraded) TrekColors.amsOrange else TrekColors.onSurfaceSub,
                fontSize      = 10.sp,
                letterSpacing = 1.sp,
                fontWeight    = FontWeight.SemiBold,
                fontFamily    = PlusJakartaSans
            )
            Spacer(Modifier.height(2.dp))
            Text(
                displayText,
                color      = if (isLive) TrekColors.onSurface else TrekColors.onSurfaceSub,
                fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal,
                fontSize   = 14.sp,
                fontFamily = PlusJakartaSans
            )
        }
        Icon(
            Icons.Outlined.ContentCopy,
            contentDescription = "Copy coordinates",
            tint = if (isLive) TrekColors.onSurfaceSub else TrekColors.divider,
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    enabled           = isLive,
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) {
                    // FIX UI-4: Locale.US so pasted coordinates are always
                    // parseable by maps apps, regardless of device locale.
                    clipboardManager.setText(
                        AnnotatedString(
                            "${String.format(Locale.US, "%.4f", latitude)}, ${String.format(Locale.US, "%.4f", longitude)}"
                        )
                    )
                    onCopied()
                }
        )
    }
}

// ─── Toast banner ──────────────────────────────────────────────────────────────

@Composable
private fun ToastBanner(text: String) {
    Row(Modifier.clip(RoundedCornerShape(50.dp)).background(Color(0xFF4CAF50)).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = PlusJakartaSans)
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeActivePreview() {
    TrekModeContent(isTracking = true, altitude = 3440.0, altitudeZone = AltitudeZone.ACCLIMATIZATION, gainMeters = 610.0, lossMeters = 120.0, speedKmh = 3.0, distanceKm = 12.4, accuracy = 3.0f, latitude = 27.8065, longitude = 86.7140, ascentRateM = 220.0, inBatterySaver = false, gpsSignalState = GpsSignalState.OK, onToggleTrekMode = {})
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeGpsDegradedPreview() {
    // New preview showing the DEGRADED signal state.
    TrekModeContent(isTracking = true, altitude = 3440.0, altitudeZone = AltitudeZone.ACCLIMATIZATION, gainMeters = 610.0, lossMeters = 120.0, speedKmh = 0.0, distanceKm = 12.4, accuracy = 0f, latitude = 27.8065, longitude = 86.7140, ascentRateM = 0.0, inBatterySaver = false, gpsSignalState = GpsSignalState.DEGRADED(7), onToggleTrekMode = {})
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true, backgroundColor = 0xFFF2F3F6, widthDp = 390, heightDp = 900)
@Composable
fun TrekModeInactivePreview() {
    TrekModeContent(isTracking = false, altitude = 0.0, altitudeZone = AltitudeZone.NORMAL, gainMeters = 0.0, lossMeters = 0.0, speedKmh = 0.0, distanceKm = 0.0, accuracy = 0f, latitude = 0.0, longitude = 0.0, gpsSignalState = GpsSignalState.ACQUIRING, onToggleTrekMode = {})
}