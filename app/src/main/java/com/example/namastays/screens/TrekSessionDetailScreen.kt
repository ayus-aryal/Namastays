package com.example.namastays.screens

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.data.TrekElevationPoint
import com.example.namastays.data.TrekSession
import com.example.namastays.ui.theme.TrekColors
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ── Formatters ─────────────────────────────────────────────────────────────────
private val dateFmt  = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
private val timeFmt  = SimpleDateFormat("hh:mm a", Locale.getDefault())

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
private fun fmtDist(m: Double) = when {
    m <= 0.0 -> "—"
    m < 1000 -> "${m.roundToInt()} m"
    else     -> "${"%.2f".format(m / 1000)} km"
}
private fun fmtAlt(m: Double) = if (m <= 0.0) "—" else "${m.roundToInt()} m"
private fun fmtSpeed(kmh: Double) = if (kmh < 0.1) "—" else "${"%.1f".format(kmh)} km/h"
private fun fmtPace(kmh: Double): String {
    if (kmh < 0.5) return "—"
    val minPerKm = 60.0 / kmh
    val min = minPerKm.toInt()
    val sec = ((minPerKm - min) * 60).roundToInt()
    return "${min}′${sec.toString().padStart(2, '0')}″/km"
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TrekSessionDetailScreen(
    session  : TrekSession,
    onBack   : () -> Unit,
    onDelete : (Long) -> Unit = {}
) {
    val context   = LocalContext.current
    val viewModel : TrekViewModel = viewModel(
        factory = TrekViewModelFactory(context.applicationContext as Application)
    )

    var elevationPoints  by remember { mutableStateOf<List<TrekElevationPoint>>(emptyList()) }
    var prevSleepAlt     by remember { mutableStateOf<Double?>(null) }
    var loading          by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(session.id) {
        elevationPoints = viewModel.elevationPoints(session.id)
        prevSleepAlt    = viewModel.sleepAltitudeBeforeSession(session.startMs)?.altitudeMeters
        loading         = false
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape          = RoundedCornerShape(20.dp),
            containerColor = TrekColors.surface,
            title = {
                Text(
                    "Delete Session?",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp,
                    color      = TrekColors.onSurface
                )
            },
            text = {
                Text(
                    "This will permanently delete the session and its elevation data. This cannot be undone.",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 14.sp,
                    color      = TrekColors.onSurfaceSub,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete(session.id)
                }) {
                    Text(
                        "Delete",
                        color      = Color(0xFFDC2626),
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Cancel",
                        fontFamily = PlusJakartaSans,
                        color      = TrekColors.onSurfaceSub
                    )
                }
            }
        )
    }

    val durationMs = if (session.endMs > 0) session.endMs - session.startMs else 0L

    Scaffold(
        containerColor      = TrekColors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            DetailHeader(
                session         = session,
                onBack          = onBack,
                onDeleteRequest = { showDeleteDialog = true }
            )
            if (loading) {
                Box(
                    Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color    = TrekColors.accentGreen,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                ElevationSparklineCard(elevationPoints)
                PrimaryStatsGrid(session, durationMs)
                prevSleepAlt?.let { sleepAlt ->
                    SleepDeltaCard(sessionMaxAlt = session.maxAltM, sleepAlt = sleepAlt)
                }
                TimeCard(session)
                OptimizationCard(session, durationMs, elevationPoints)
            }
            Spacer(Modifier.navigationBarsPadding().height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DetailHeader(
    session         : TrekSession,
    onBack          : () -> Unit,
    onDeleteRequest : () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick  = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(TrekColors.surface)
        ) {
            Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint     = TrekColors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                dateFmt.format(Date(session.startMs)),
                color      = TrekColors.onSurface,
                fontSize   = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = PlusJakartaSans
            )
            Text(
                "Session details",
                color      = TrekColors.onSurfaceSub,
                fontSize   = 13.sp,
                fontFamily = PlusJakartaSans
            )
        }
        // Trash icon — top-right, confirms before deleting
        IconButton(
            onClick  = onDeleteRequest,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFDC2626).copy(alpha = 0.08f))
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete session",
                tint     = Color(0xFFDC2626),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Elevation sparkline
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ElevationSparklineCard(points: List<TrekElevationPoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
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
                "ELEVATION PROFILE",
                color         = TrekColors.onSurfaceSub,
                fontSize      = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight    = FontWeight.SemiBold,
                fontFamily    = PlusJakartaSans
            )
            if (points.isNotEmpty()) {
                Text(
                    "${points.size} pts",
                    color      = TrekColors.onSurfaceSub,
                    fontSize   = 11.sp,
                    fontFamily = PlusJakartaSans
                )
            }
        }

        when {
            points.isEmpty() -> {
                // Empty state
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TrekColors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ShowChart,
                            contentDescription = null,
                            tint     = TrekColors.onSurfaceSub,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "No elevation data recorded",
                            color      = TrekColors.onSurfaceSub,
                            fontSize   = 13.sp,
                            fontFamily = PlusJakartaSans
                        )
                        Text(
                            "Elevation is logged every minute while Trek Mode is active",
                            color      = TrekColors.onSurfaceSub,
                            fontSize   = 11.sp,
                            fontFamily = PlusJakartaSans,
                            textAlign  = TextAlign.Center
                        )
                    }
                }
            }
            points.size == 1 -> {
                // Single point — can't draw a line
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TrekColors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Only one data point — ${points.first().altitudeM.roundToInt()} m",
                        color      = TrekColors.onSurfaceSub,
                        fontSize   = 13.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
            else -> {
                SparklineCanvas(points)

                // Min / max labels
                val minAlt = points.minOf { it.altitudeM }
                val maxAlt = points.maxOf { it.altitudeM }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "↓ ${minAlt.roundToInt()} m",
                        color      = TrekColors.lossRed,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PlusJakartaSans
                    )
                    Text(
                        "↑ ${maxAlt.roundToInt()} m",
                        color      = TrekColors.gainGreen,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
    }
}

@Composable
private fun SparklineCanvas(points: List<TrekElevationPoint>) {
    val alts     = points.map { it.altitudeM }
    val minAlt   = alts.min()
    val maxAlt   = alts.max()
    val altRange = (maxAlt - minAlt).coerceAtLeast(10.0)   // avoid div/0 on flat terrain

    // Animate line drawing in on first composition
    val progress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(1200, easing = EaseInOutCubic),
        label         = "sparkline"
    )

    val lineColor = TrekColors.accentGreen
    val fillStart = TrekColors.accentGreen.copy(alpha = 0.25f)
    val fillEnd   = TrekColors.accentGreen.copy(alpha = 0f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val w         = size.width
        val h         = size.height
        val padTop    = 8f
        val padBottom = 8f
        val drawH     = h - padTop - padBottom

        // Compute pixel coordinates
        val coords = alts.mapIndexed { i, alt ->
            val x = if (alts.size == 1) w / 2f else i / (alts.size - 1f) * w
            val y = padTop + (1f - ((alt - minAlt) / altRange).toFloat()) * drawH
            Offset(x, y)
        }

        // Clip to animated progress
        val visibleCount = (coords.size * progress).toInt().coerceAtLeast(2)
        val visible      = coords.take(visibleCount)

        if (visible.size < 2) return@Canvas

        // Build path
        val path = Path().apply {
            moveTo(visible.first().x, visible.first().y)
            // Catmull-Rom → cubic bezier for smooth curve
            for (i in 1 until visible.size) {
                val p0 = visible.getOrElse(i - 2) { visible[0] }
                val p1 = visible[i - 1]
                val p2 = visible[i]
                val p3 = visible.getOrElse(i + 1) { visible.last() }
                val cp1x = p1.x + (p2.x - p0.x) / 6f
                val cp1y = p1.y + (p2.y - p0.y) / 6f
                val cp2x = p2.x - (p3.x - p1.x) / 6f
                val cp2y = p2.y - (p3.y - p1.y) / 6f
                cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
            }
        }

        // Fill under the curve
        val fillPath = Path().apply {
            addPath(path)
            lineTo(visible.last().x, h)
            lineTo(visible.first().x, h)
            close()
        }
        drawPath(
            path  = fillPath,
            brush = Brush.verticalGradient(
                colors    = listOf(fillStart, fillEnd),
                startY    = padTop,
                endY      = h
            )
        )

        // Line
        drawPath(
            path  = path,
            color = lineColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // End-point dot
        drawCircle(
            color  = lineColor,
            radius = 5f,
            center = visible.last()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primary stats grid
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PrimaryStatsGrid(session: TrekSession, durationMs: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "DISTANCE",
                value    = fmtDist(session.distanceM),
                icon     = Icons.Outlined.Straighten,
                color    = TrekColors.accentGreen
            )
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "DURATION",
                value    = fmtDuration(durationMs),
                icon     = Icons.Outlined.Timer,
                color    = TrekColors.accent
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "ELEVATION GAIN",
                value    = "+${fmtAlt(session.gainM)}",
                icon     = Icons.Outlined.TrendingUp,
                color    = TrekColors.gainGreen
            )
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "ELEVATION LOSS",
                value    = "-${fmtAlt(session.lossM)}",
                icon     = Icons.Outlined.TrendingDown,
                color    = TrekColors.lossRed
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "MAX ALTITUDE",
                value    = fmtAlt(session.maxAltM),
                icon     = Icons.Outlined.Landscape,
                color    = TrekColors.amsOrange
            )
            DetailStatCard(
                modifier = Modifier.weight(1f),
                label    = "AVG PACE",
                value    = fmtPace(session.avgSpeedKmh),
                icon     = Icons.Outlined.Speed,
                color    = TrekColors.onSurface
            )
        }
    }
}

@Composable
private fun DetailStatCard(
    modifier : Modifier,
    label    : String,
    value    : String,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    color    : Color
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(TrekColors.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Text(
            value,
            color      = TrekColors.onSurface,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSans
        )
        Text(
            label,
            color         = TrekColors.onSurfaceSub,
            fontSize      = 9.sp,
            letterSpacing = 1.sp,
            fontWeight    = FontWeight.SemiBold,
            fontFamily    = PlusJakartaSans
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sleep delta card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SleepDeltaCard(sessionMaxAlt: Double, sleepAlt: Double) {
    val delta      = sessionMaxAlt - sleepAlt
    val isOver500  = delta > 500.0
    val bgColor    = if (isOver500) TrekColors.amsBackground else TrekColors.surface
    val accentColor = if (isOver500) TrekColors.amsOrange else TrekColors.accentGreen

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
    ) {
        if (isOver500) {
            Box(
                Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .background(accentColor)
            )
        }
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(start = if (isOver500) 16.dp else 0.dp)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isOver500) Icons.Outlined.Warning else Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint     = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isOver500) "500 m Rule Exceeded" else "vs Last Sleep Altitude",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = accentColor,
                    fontFamily = PlusJakartaSans
                )
                Text(
                    buildString {
                        append("Last sleep: ${sleepAlt.roundToInt()} m  ·  ")
                        append("Peak: ${sessionMaxAlt.roundToInt()} m  ·  ")
                        if (delta >= 0) append("+${delta.roundToInt()} m")
                        else append("${delta.roundToInt()} m")
                    },
                    fontSize   = 12.sp,
                    color      = TrekColors.onSurfaceSub,
                    fontFamily = PlusJakartaSans
                )
                if (isOver500) {
                    Text(
                        "You ascended more than 500 m above last night's sleep altitude. Consider an acclimatization rest day.",
                        fontSize   = 12.sp,
                        color      = accentColor.copy(alpha = 0.85f),
                        fontFamily = PlusJakartaSans,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Time card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TimeCard(session: TrekSession) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(TrekColors.surface)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        TimeChip(label = "START", time = timeFmt.format(Date(session.startMs)))
        Icon(
            Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint     = TrekColors.onSurfaceSub,
            modifier = Modifier.size(18.dp)
        )
        TimeChip(
            label = "END",
            time  = if (session.endMs > 0) timeFmt.format(Date(session.endMs)) else "—"
        )
    }
}

@Composable
private fun TimeChip(label: String, time: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize      = 10.sp,
            color         = TrekColors.onSurfaceSub,
            letterSpacing = 1.sp,
            fontWeight    = FontWeight.SemiBold,
            fontFamily    = PlusJakartaSans
        )
        Spacer(Modifier.height(4.dp))
        Text(
            time,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = TrekColors.onSurface,
            fontFamily = PlusJakartaSans
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Optimization tips
// ─────────────────────────────────────────────────────────────────────────────
private data class OptTip(val icon: androidx.compose.ui.graphics.vector.ImageVector, val text: String)

@Composable
private fun OptimizationCard(
    session      : TrekSession,
    durationMs   : Long,
    points       : List<TrekElevationPoint>
) {
    val tips = buildList {
        // Short session with significant gain — rest day suggestion
        val durationH = durationMs / 3_600_000.0
        if (session.gainM > 600 && durationH < 4.0) add(
            OptTip(Icons.Outlined.Hotel,
                "You gained ${session.gainM.roundToInt()} m in under 4 hours. " +
                        "A slower ascent or an extra rest day improves acclimatization.")
        )
        // High max altitude — AMS check
        if (session.maxAltM >= 3_000) add(
            OptTip(Icons.Outlined.HealthAndSafety,
                "You reached ${session.maxAltM.roundToInt()} m. " +
                        "Use the AMS Checker in the Safety module to monitor symptoms.")
        )
        // Very short distance + long time — likely stationary
        if (session.distanceM < 500 && durationMs > 30 * 60_000L) add(
            OptTip(Icons.Outlined.Info,
                "Low distance over a long period detected. " +
                        "Battery Saver mode reduces GPS drain during rest stops.")
        )
        // Elevation sparkline is empty
        if (points.isEmpty() && durationMs > 5 * 60_000L) add(
            OptTip(Icons.Outlined.GpsNotFixed,
                "No elevation points were recorded. " +
                        "Ensure GPS permission is granted and accuracy is within 20 m.")
        )
        // Good session — positive reinforcement
        if (isEmpty() && session.distanceM >= 1_000) add(
            OptTip(Icons.Outlined.CheckCircle,
                "Great session! Data quality looks good — " +
                        "accuracy-gated GPS and barometric fusion were active.")
        )
    }

    if (tips.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(TrekColors.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "INSIGHTS",
            color         = TrekColors.onSurfaceSub,
            fontSize      = 11.sp,
            letterSpacing = 1.5.sp,
            fontWeight    = FontWeight.SemiBold,
            fontFamily    = PlusJakartaSans
        )
        tips.forEachIndexed { i, tip ->
            if (i > 0) HorizontalDivider(color = TrekColors.divider, thickness = 0.5.dp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(
                    tip.icon,
                    contentDescription = null,
                    tint     = TrekColors.accent,
                    modifier = Modifier.size(18.dp).padding(top = 1.dp)
                )
                Text(
                    tip.text,
                    fontSize   = 13.sp,
                    color      = TrekColors.onSurface,
                    fontFamily = PlusJakartaSans,
                    lineHeight = 19.sp
                )
            }
        }
    }
}