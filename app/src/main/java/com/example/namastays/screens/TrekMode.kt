package com.example.namastays.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.utilities.TrekTrackingService
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory

// ─── Altitude zone definition ────────────────────────────────────────────────

enum class AltitudeZone(val label: String, val color: Color) {
    NORMAL("Normal", Color(0xFF2E7D32)),
    ACCLIMATIZATION("Acclimatization", Color(0xFFF9A825)),
    HIGH_RISK("High Risk", Color(0xFFEF6C00)),
    EXTREME("Extreme", Color(0xFFC62828))
}

// ─── Colour palette ───────────────────────────────────────────────────────────

private object TrekColors {
    val background    = Color(0xFFFFFFFF)
    val surface       = Color(0xFFF5F6F8)
    val surfaceAlt    = Color(0xFFEEF0F3)
    val onSurface     = Color(0xFF111827)
    val onSurfaceSub  = Color(0xFF6B7280)
    val accent        = Color(0xFF1B5E20)
    val accentLight   = Color(0xFFE8F5E9)
    val divider       = Color(0xFFE5E7EB)
    val activeGreen   = Color(0xFF22C55E)
}

// ─── Main screen ─────────────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
fun TrekModeScreen() {

    val context = LocalContext.current

    val viewModel: TrekViewModel = viewModel(
        factory = TrekViewModelFactory(context.applicationContext as Application)
    )

    val state by viewModel.trekState.collectAsState()
    var trekModeEnabled by remember { mutableStateOf(false) }

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

    TrekModeContent(
        trekModeEnabled   = trekModeEnabled,
        altitude          = state.altitude,
        altitudeZone      = state.altitudeZone,
        gainMeters        = state.gainMeters,
        lossMeters        = state.lossMeters,
        speedKmh          = state.speedKmh,
        distanceKm        = state.distanceKm,
        accuracy          = state.accuracy,
        latitude          = state.latitude,
        longitude         = state.longitude,
        onToggleTrekMode  = { enabled ->
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
        }
    )
}

// ─── Stateless content (also used by preview) ────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
fun TrekModeContent(
    trekModeEnabled  : Boolean,
    altitude         : Double,
    altitudeZone     : AltitudeZone,
    gainMeters       : Double,
    lossMeters       : Double,
    speedKmh         : Double,
    distanceKm       : Double,
    accuracy         : Float,
    latitude         : Double,
    longitude        : Double,
    onToggleTrekMode : (Boolean) -> Unit
) {
    Scaffold(containerColor = TrekColors.background) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            item { Spacer(Modifier.height(8.dp)) }

            // ── Header ────────────────────────────────────────────────────
            item { HeaderRow(trekModeEnabled, onToggleTrekMode) }

            // ── Status pill ───────────────────────────────────────────────
            item { StatusPill(trekModeEnabled) }

            // ── Altitude hero card ────────────────────────────────────────
            item {
                AltitudeHeroCard(
                    altitude     = altitude,
                    altitudeZone = altitudeZone,
                    gainMeters   = gainMeters,
                    lossMeters   = lossMeters,
                    speedKmh     = speedKmh
                )
            }

            // ── Distance + Accuracy ───────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(Modifier.weight(1f), "Distance",
                        String.format("%.2f km", distanceKm))
                    MetricCard(Modifier.weight(1f), "Accuracy",
                        "${accuracy.toInt()} m")
                    MetricCard(Modifier.weight(1f), "Speed",
                        "${speedKmh.toInt()} km/h")
                }
            }

            // ── Altitude zone chart ───────────────────────────────────────
            item { AltitudeZoneChart(currentAltitude = altitude) }

            // ── GPS coordinates ───────────────────────────────────────────
            item { GpsCard(latitude, longitude) }

            // ── Tracking status ───────────────────────────────────────────
            item { TrackingStatusCard(trekModeEnabled) }

            // ── Analytics button ──────────────────────────────────────────
            item { AnalyticsButton() }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HeaderRow(
    trekModeEnabled : Boolean,
    onToggle        : (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "Trek Mode",
                color      = TrekColors.onSurface,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
            Text(
                "Live expedition tracking",
                color    = TrekColors.onSurfaceSub,
                fontSize = 14.sp
            )
        }

        Switch(
            checked  = trekModeEnabled,
            onCheckedChange = onToggle,
            colors   = SwitchDefaults.colors(
                checkedThumbColor       = Color.White,
                checkedTrackColor       = TrekColors.accent,
                uncheckedThumbColor     = Color.White,
                uncheckedTrackColor     = TrekColors.surfaceAlt
            )
        )
    }
}

// ─── Status pill ──────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(active: Boolean) {

    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900), RepeatMode.Reverse
        ), label = "alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (active) TrekColors.accentLight else TrekColors.surfaceAlt)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) TrekColors.activeGreen.copy(alpha = dotAlpha)
                    else        TrekColors.onSurfaceSub
                )
        )
        Text(
            if (active) "Trek Mode Active" else "Trek Mode Inactive",
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (active) TrekColors.accent else TrekColors.onSurfaceSub
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(if (active) TrekColors.accent else TrekColors.divider)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                if (active) "Optimized" else "Standby",
                fontSize = 11.sp,
                color    = if (active) Color.White else TrekColors.onSurfaceSub
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
            colors = listOf(Color(0xFF1B5E20), Color(0xFF2E7D32)),
            start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.ACCLIMATIZATION -> Brush.linearGradient(
            colors = listOf(Color(0xFFF57F17), Color(0xFFF9A825)),
            start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.HIGH_RISK       -> Brush.linearGradient(
            colors = listOf(Color(0xFFBF360C), Color(0xFFEF6C00)),
            start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
        AltitudeZone.EXTREME         -> Brush.linearGradient(
            colors = listOf(Color(0xFF7F0000), Color(0xFFC62828)),
            start = Offset(0f, 0f), end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(zoneGradient)
            .padding(24.dp)
    ) {
        Column {
            Text(
                "CURRENT ALTITUDE",
                color    = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${altitude.toInt()}",
                    color      = Color.White,
                    fontSize   = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp,
                    lineHeight = 64.sp
                )
                Text(
                    " m",
                    color      = Color.White.copy(alpha = 0.8f),
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.padding(bottom = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Zone badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    altitudeZone.label,
                    color      = Color.White,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroMetric(
                    label = "GAIN",
                    value = "+${gainMeters.toInt()}m",
                    icon  = "↗"
                )
                HeroMetric(
                    label = "LOSS",
                    value = "-${lossMeters.toInt()}m",
                    icon  = "↘"
                )
                HeroMetric(
                    label = "SPEED",
                    value = "${speedKmh.toInt()} km/h",
                    icon  = "→"
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, icon: String) {
    Column {
        Text(
            label,
            color    = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$icon $value",
            color      = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp
        )
    }
}

// ─── Metric card ──────────────────────────────────────────────────────────────

@Composable
fun MetricCard(
    modifier : Modifier = Modifier,
    title    : String,
    value    : String
) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Text(title, color = TrekColors.onSurfaceSub, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = TrekColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

// ─── Altitude zone chart ──────────────────────────────────────────────────────

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AltitudeZoneChart(currentAltitude: Double) {

    val maxAltitude = 8849.0  // Everest
    val zones = listOf(
        Triple(AltitudeZone.NORMAL,          0.0,    2500.0),
        Triple(AltitudeZone.ACCLIMATIZATION, 2500.0, 3500.0),
        Triple(AltitudeZone.HIGH_RISK,       3500.0, 5000.0),
        Triple(AltitudeZone.EXTREME,         5000.0, maxAltitude)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Altitude Zones",
                color = TrekColors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${currentAltitude.toInt()} m",
                color = TrekColors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(18.dp))

        // Segmented bar
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50.dp))
        ) {
            val totalWidth = maxWidth
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

            // Current position marker
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

        // Labels row
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0", color = TrekColors.onSurfaceSub, fontSize = 10.sp)
            Text("2.5k", color = TrekColors.onSurfaceSub, fontSize = 10.sp)
            Text("3.5k", color = TrekColors.onSurfaceSub, fontSize = 10.sp)
            Text("5k", color = TrekColors.onSurfaceSub, fontSize = 10.sp)
            Text("8.8k m", color = TrekColors.onSurfaceSub, fontSize = 10.sp)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = TrekColors.divider, thickness = 1.dp)
        Spacer(Modifier.height(16.dp))

        // Legend
        zones.forEach { (zone, from, to) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(zone.color)
                )
                Spacer(Modifier.width(10.dp))
                Text(zone.label, color = TrekColors.onSurface, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    "${from.toInt()} – ${to.toInt()} m",
                    color = TrekColors.onSurfaceSub,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ─── GPS card ─────────────────────────────────────────────────────────────────

@SuppressLint("DefaultLocale")
@Composable
private fun GpsCard(latitude: Double, longitude: Double) {
    val latStr = if (latitude == 0.0) "–" else String.format("%.4f°", latitude)
    val lngStr = if (longitude == 0.0) "–" else String.format("%.4f°", longitude)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Text(
            "GPS Coordinates",
            color = TrekColors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        GpsRow(label = "Latitude", value = latitude)
        Spacer(Modifier.height(8.dp))
        GpsRow(label = "Longitude", value = longitude)
    }
}

@Composable
private fun GpsRow(label: String, value: Double) {
    val displayValue =
        if (value == 0.0) "–"
        else String.format("%.4f°", value)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TrekColors.onSurfaceSub, fontSize = 13.sp)
        Text(displayValue, color = TrekColors.onSurface, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

// ─── Tracking status card ─────────────────────────────────────────────────────

@Composable
private fun TrackingStatusCard(trekModeEnabled: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Text(
            "Tracking Status",
            color = TrekColors.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        StatusItem(
            icon  = { Icon(Icons.Outlined.GpsFixed, contentDescription = null) },
            title = "GPS Active"
        )
        StatusItem(
            icon  = { Icon(Icons.Outlined.CloudOff, contentDescription = null) },
            title = "Offline Tracking Enabled"
        )
        StatusItem(
            icon  = { Icon(Icons.Outlined.Hiking, contentDescription = null) },
            title = if (trekModeEnabled) "Foreground Tracking Running" else "Tracking Disabled"
        )
    }
}

// ─── Analytics button ─────────────────────────────────────────────────────────

@Composable
private fun AnalyticsButton() {
    Button(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TrekColors.onSurface
        ),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        Icon(Icons.Default.ShowChart, contentDescription = null, tint = Color.White)
        Spacer(Modifier.width(10.dp))
        Text(
            "View Altitude Analytics",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
        )
    }
}

// ─── Shared sub-composables ───────────────────────────────────────────────────

@Composable
fun TrekMetric(title: String, value: String) {
    Column {
        Text(title, color = TrekColors.onSurfaceSub, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = TrekColors.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun StatusItem(icon: @Composable () -> Unit, title: String) {
    Row(
        Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides TrekColors.onSurfaceSub) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = TrekColors.onSurface, fontSize = 14.sp)
    }
}

@Composable
fun AltitudeLevelItem(color: Color, label: String, range: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TrekColors.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(range, color = TrekColors.onSurfaceSub, fontSize = 13.sp)
    }
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@Preview(
    name         = "Trek Mode – Acclimatization Zone",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp      = 390,
    heightDp     = 900
)
@Composable
fun TrekModeScreenPreview() {
    TrekModeContent(
        trekModeEnabled  = true,
        altitude         = 3440.0,
        altitudeZone     = AltitudeZone.ACCLIMATIZATION,
        gainMeters       = 610.0,
        lossMeters       = 120.0,
        speedKmh         = 3.0,
        distanceKm       = 8.4,
        accuracy         = 4.0f,
        latitude         = 27.8069,
        longitude        = 86.7139,
        onToggleTrekMode = {}
    )
}

@Preview(
    name         = "Trek Mode – High Risk Zone",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp      = 390,
    heightDp     = 900
)
@Composable
fun TrekModeScreenHighRiskPreview() {
    TrekModeContent(
        trekModeEnabled  = true,
        altitude         = 4800.0,
        altitudeZone     = AltitudeZone.HIGH_RISK,
        gainMeters       = 1200.0,
        lossMeters       = 80.0,
        speedKmh         = 2.0,
        distanceKm       = 14.2,
        accuracy         = 6.0f,
        latitude         = 27.988,
        longitude        = 86.9250,
        onToggleTrekMode = {}
    )
}

@Preview(
    name         = "Trek Mode – Disabled",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp      = 390,
    heightDp     = 900
)
@Composable
fun TrekModeScreenDisabledPreview() {
    TrekModeContent(
        trekModeEnabled  = false,
        altitude         = 0.0,
        altitudeZone     = AltitudeZone.NORMAL,
        gainMeters       = 0.0,
        lossMeters       = 0.0,
        speedKmh         = 0.0,
        distanceKm       = 0.0,
        accuracy         = 0.0f,
        latitude         = 0.0,
        longitude        = 0.0,
        onToggleTrekMode = {}
    )
}