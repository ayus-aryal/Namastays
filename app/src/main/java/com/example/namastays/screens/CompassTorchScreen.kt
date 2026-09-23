package com.example.namastays.screens

import android.app.Application
import android.content.Context
import android.hardware.*
import android.hardware.camera2.CameraManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.viewmodel.AnchorNavState
import com.example.namastays.viewmodel.CompassViewModel
import com.example.namastays.viewmodel.CompassViewModelFactory
import kotlin.math.*

// ─── Shared palette ────────────────────────────────────────────────────────────
private val BgPage      = Color(0xFFF7F8FA)
private val BgCard      = Color.White
private val NavyDark    = Color(0xFF111827)
private val NavyMid     = Color(0xFF374151)
private val SubText     = Color(0xFF9CA3AF)
private val BorderCol   = Color(0xFFE5E7EB)
private val RedNorth    = Color(0xFFE53935)
private val BlueAccent  = Color(0xFF3B82F6)
private val AmberWarn   = Color(0xFFF59E0B)
private val AnchorGreen = Color(0xFF22C55E)

// ══════════════════════════════════════════════════════════════════════════════
//  COMPASS SCREEN
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Digital compass screen with backtrack-to-anchor support.
 *
 * **Heading source — TYPE_ROTATION_VECTOR (revised):**
 * Previously used raw TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD fused via
 * getRotationMatrix/getOrientation. This is known to be noisy — no
 * gyroscope stabilization, sensitive to tilt and magnetic interference —
 * and field testing confirmed inconsistent behavior. Replaced with
 * TYPE_ROTATION_VECTOR, a gyro-stabilized fused sensor computed by the OS,
 * matching what the app's MapLibre trail compass already uses successfully.
 *
 * A light exponential low-pass filter is applied to the sensor's angle
 * output (see filteredAzimuth in the listener) in addition to — not
 * instead of — the spring animation on smoothAzimuth below. These solve
 * different problems: the low-pass filter reduces raw DATA jitter before
 * it's used for bearing math; the spring animation eases DISPLAY motion.
 *
 * **Declination correction:**
 * Raw sensor heading is magnetic north, not true north. CompassViewModel
 * computes local declination (via GeomagneticField, from GPS position) and
 * exposes it on AnchorNavState; it's added to the raw heading here before
 * any further use.
 *
 * **Anchor bearing arrow:**
 * Drawn as a separate, non-rotating Canvas layer — NOT inside the
 * withTransform{ rotate(...) } block that spins the N/S/E/W rose. Its own
 * angle (anchorRelativeAngle) already has the current heading subtracted
 * out, so it visually tracks the real-world anchor direction regardless of
 * which way the disc has rotated. See inline comment at the draw call.
 *
 * **Sensor cleanup:** unregistered in onDispose, same as before.
 */
@Composable
fun CompassScreen(navController: NavController) {
    val context = LocalContext.current

    val compassViewModel: CompassViewModel = viewModel(
        factory = CompassViewModelFactory(context.applicationContext as Application)
    )
    val anchorNavState by compassViewModel.anchorNavState.collectAsStateWithLifecycle()

    var azimuth       by remember { mutableStateOf(0f) }
    var altitude      by remember { mutableStateOf<Float?>(null) }
    var accuracy      by remember { mutableStateOf("--") }
    var accuracyLevel by remember { mutableStateOf(-1) }

    val smoothAzimuth = remember { Animatable(0f) }

    // Declination is read into a plain var (not mutableStateOf) captured by
    // the sensor listener closure below. It's updated via a side-effect that
    // re-registers nothing — DisposableEffect is keyed on Unit so sensors
    // stay registered continuously; declination is instead pushed into a
    // holder the listener reads from on every event. See declinationHolder.
    val declinationHolder = remember { FloatArray(1) } // [0] = current declination
    LaunchedEffect(anchorNavState.declinationDeg) {
        declinationHolder[0] = anchorNavState.declinationDeg
    }

    // ── Sensor registration ───────────────────────────────────────────────────
    DisposableEffect(Unit) {
        val sm             = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val pressure       = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)

        val rotationMatrix = FloatArray(9)
        val orientation    = FloatArray(3)

        // Exponential low-pass filter state — see class KDoc.
        var filteredAzimuth = 0f
        var hasFilterSeed   = false
        val filterAlpha     = 0.15f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)

                        var deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        if (deg < 0) deg += 360f

                        // Declination correction — magnetic → true north.
                        deg += declinationHolder[0]
                        if (deg < 0) deg += 360f
                        if (deg >= 360f) deg -= 360f

                        // Exponential low-pass, shortest-path-aware so it
                        // doesn't spin the wrong way through the 360°/0° wrap.
                        if (!hasFilterSeed) {
                            filteredAzimuth = deg
                            hasFilterSeed = true
                        } else {
                            var delta = deg - filteredAzimuth
                            if (delta > 180f) delta -= 360f
                            if (delta < -180f) delta += 360f
                            filteredAzimuth = (filteredAzimuth + filterAlpha * delta + 360f) % 360f
                        }
                        azimuth = filteredAzimuth
                    }
                    Sensor.TYPE_PRESSURE -> {
                        altitude = SensorManager.getAltitude(
                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                            event.values[0]
                        )
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, acc: Int) {
                accuracyLevel = acc
                accuracy = when (acc) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH   -> "High"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> "Low"
                    else                                         -> "Poor"
                }
            }
        }

        sm.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        pressure?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        onDispose { sm.unregisterListener(listener) }
    }

    // ── Animation loop (single coroutine, not per-reading) ─────────────────────
    LaunchedEffect(Unit) {
        snapshotFlow { azimuth }.collect { target ->
            smoothAzimuth.animateTo(
                targetValue   = target,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f)
            )
        }
    }

    val direction     = getCardinalDirection(smoothAzimuth.value)
    val accuracyColor = when (accuracyLevel) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH   -> Color(0xFF22C55E)
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> AmberWarn
        SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> AmberWarn
        else                                         -> Color(0xFFEF4444)
    }
    val showWarning = accuracyLevel == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracyLevel == SensorManager.SENSOR_STATUS_ACCURACY_LOW

    // Anchor arrow angle within the rose — bearing to anchor minus current
    // heading. Drawn on a non-rotating layer (see draw call below), so it
    // tracks the real-world anchor direction regardless of device rotation.
    // Null (no arrow drawn) when there's no bearing yet OR the user is
    // within the near-anchor cutoff, where a precise arrow can't be trusted.
    val anchorRelativeAngle: Float? =
        if (anchorNavState.isNearAnchor) null
        else anchorNavState.bearingDegrees?.let { bearing ->
            ((bearing - smoothAzimuth.value) % 360f).let { if (it < 0) it + 360f else it }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        if (showWarning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFFBEB))
                    .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Warning, null, tint = AmberWarn, modifier = Modifier.size(16.dp))
                Text(
                    "Low accuracy — move phone in a figure-8 to calibrate",
                    color      = Color(0xFF92400E),
                    fontSize   = 12.sp,
                    fontFamily = PlusJakartaSans,
                    lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "COMPASS",
            color         = SubText,
            fontSize      = 11.sp,
            fontWeight    = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 3.sp,
            fontFamily    = PlusJakartaSans
        )

        Spacer(Modifier.height(20.dp))

        // ── Compass rose ──────────────────────────────────────────────────────
        Box(
            modifier         = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentAzimuth = smoothAzimuth.value

            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx     = size.width  / 2f
                val cy     = size.height / 2f
                val outerR = size.minDimension / 2f
                val innerR = outerR * 0.52f

                drawCircle(Color(0xFFF0F1F4), outerR, Offset(cx, cy))

                withTransform({ rotate(-currentAzimuth, Offset(cx, cy)) }) {
                    drawIntoCanvas { canvas ->
                        val numPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign   = android.graphics.Paint.Align.CENTER
                            textSize    = outerR * 0.115f
                            color       = android.graphics.Color.argb(140, 17, 24, 39)
                            typeface    = android.graphics.Typeface.DEFAULT
                        }
                        for (deg in listOf(30, 60, 120, 150, 210, 240, 300, 330)) {
                            val rad = Math.toRadians(deg.toDouble())
                            val r   = outerR * 0.78f
                            val x   = cx + r * sin(rad).toFloat()
                            val y   = cy - r * cos(rad).toFloat() + numPaint.textSize * 0.35f
                            canvas.nativeCanvas.drawText(deg.toString(), x, y, numPaint)
                        }

                        val cardPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign   = android.graphics.Paint.Align.CENTER
                            textSize    = outerR * 0.19f
                            typeface    = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val cardR      = outerR * 0.78f
                        val cardOffset = cardPaint.textSize * 0.38f

                        cardPaint.color = android.graphics.Color.argb(255, 229, 57, 53)
                        canvas.nativeCanvas.drawText("N", cx, cy - cardR + cardOffset, cardPaint)
                        cardPaint.color = android.graphics.Color.argb(220, 17, 24, 39)
                        canvas.nativeCanvas.drawText("S", cx,         cy + cardR + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("E", cx + cardR, cy         + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("W", cx - cardR, cy         + cardOffset, cardPaint)
                    }
                }

                drawCircle(Color.White, innerR, Offset(cx, cy))
                drawCircle(Color(0xFFE5E7EB), innerR, Offset(cx, cy), style = Stroke(1.5f))

                // Anchor arrow — deliberately OUTSIDE the withTransform{}
                // block above, so it does NOT rotate with the disc. Its own
                // angle already accounts for currentAzimuth (see
                // anchorRelativeAngle calculation above the Column), which
                // is what makes it point at the real-world anchor direction
                // regardless of which way the disc has rotated.
                anchorRelativeAngle?.let { angle ->
                    val rad  = Math.toRadians(angle.toDouble())
                    val tipR = outerR * 0.88f
                    val tipX = cx + tipR * sin(rad).toFloat()
                    val tipY = cy - tipR * cos(rad).toFloat()

                    drawLine(
                        color       = AnchorGreen,
                        start       = Offset(cx, cy),
                        end         = Offset(tipX, tipY),
                        strokeWidth = 5f,
                        cap         = StrokeCap.Round
                    )
                    drawCircle(AnchorGreen, radius = 7f, center = Offset(tipX, tipY))
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "${currentAzimuth.toInt()}°",
                    color      = NavyDark,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    fontSize   = 36.sp,
                    fontFamily = PlusJakartaSans
                )
                Text(
                    direction,
                    color         = BlueAccent,
                    fontWeight    = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize      = 20.sp,
                    letterSpacing = 1.sp,
                    fontFamily    = PlusJakartaSans
                )
            }

            Canvas(
                modifier = Modifier
                    .size(16.dp, 12.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)
                    lineTo(size.width,      0f)
                    lineTo(0f,             0f)
                    close()
                }
                drawPath(path, RedNorth)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Info cards: altitude + accuracy ─────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier  = Modifier.weight(1f).fillMaxHeight(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = BgCard),
                border    = BorderStroke(1.dp, BorderCol),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.TrendingUp, null, tint = SubText, modifier = Modifier.size(14.dp))
                        Text("Altitude", color = SubText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                    }
                    Text(
                        text       = altitude?.let { "%,.0f m".format(it) } ?: "— m",
                        color      = NavyDark,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        fontSize   = 22.sp,
                        fontFamily = PlusJakartaSans
                    )
                    if (altitude == null) {
                        Text("No barometer", color = Color(0xFFF97316), fontSize = 11.sp, fontFamily = PlusJakartaSans)
                    }
                }
            }

            Card(
                modifier  = Modifier.weight(1f).fillMaxHeight(),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = BgCard),
                border    = BorderStroke(1.dp, BorderCol),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.GpsFixed, null, tint = SubText, modifier = Modifier.size(14.dp))
                        Text("Accuracy", color = SubText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(accuracyColor) }
                        Text(
                            accuracy,
                            color      = accuracyColor,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            fontSize   = 22.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AnchorStatusCard(
            navState = anchorNavState,
            onClear  = { compassViewModel.clearAnchor() }
        )

        Spacer(Modifier.height(16.dp))

        // ── Calibration tips card ─────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            shape     = RoundedCornerShape(18.dp),
            colors    = CardDefaults.cardColors(containerColor = BgCard),
            border    = BorderStroke(1.dp, BorderCol),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Calibration Tips",
                    color      = NavyDark,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize   = 14.sp,
                    fontFamily = PlusJakartaSans
                )
                CalibTip(Icons.Outlined.AllInclusive,        "Move phone in a figure-8 motion repeatedly.")
                CalibTip(Icons.Outlined.StayCurrentPortrait, "Hold device flat, parallel to the ground.")
                CalibTip(Icons.Outlined.PhonelinkErase,      "Avoid large metal objects and magnetic fields.")
                CalibTip(Icons.Outlined.GpsFixed,            "Uses GPS to find True North when moving.")
            }
        }
    }
}

// ── Anchor status card ───────────────────────────────────────────────────────

/**
 * Shows one of four states:
 *  - No anchor: "No anchor point saved" + hint to set one in Trek Mode
 *  - Anchor set, near (within NEAR_ANCHOR_THRESHOLD_M): "You're near the
 *    anchor" — no distance/bearing precision claimed at this range
 *  - Anchor set, has a fix, not near: distance + age + accuracy + Clear
 *  - Anchor set, no location fix yet: age + "Waiting for GPS fix" + Clear
 */
@Composable
private fun AnchorStatusCard(
    navState : AnchorNavState,
    onClear  : () -> Unit
) {
    val anchor = navState.anchor

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = if (anchor != null) Color(0xFFF0FDF4) else BgCard),
        border    = BorderStroke(1.dp, if (anchor != null) AnchorGreen.copy(alpha = 0.35f) else BorderCol),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = if (anchor != null) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                tint               = if (anchor != null) AnchorGreen else SubText,
                modifier           = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                if (anchor == null) {
                    Text(
                        "No anchor point saved",
                        color      = NavyDark,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize   = 14.sp,
                        fontFamily = PlusJakartaSans
                    )
                    Text(
                        "Anchor a point in Trek Mode to backtrack here",
                        color      = SubText,
                        fontSize   = 12.sp,
                        fontFamily = PlusJakartaSans
                    )
                } else if (navState.isNearAnchor) {
                    Text(
                        "You're near the anchor",
                        color      = NavyDark,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize   = 16.sp,
                        fontFamily = PlusJakartaSans
                    )
                    Text(
                        "Within ${AnchorNavState.NEAR_ANCHOR_THRESHOLD_M.toInt()}m · ${relativeAgeLabel(anchor.timestampMillis)}",
                        color      = SubText,
                        fontSize   = 12.sp,
                        fontFamily = PlusJakartaSans
                    )
                } else {
                    Text(
                        navState.distanceMeters?.let { formatDistance(it) } ?: "Anchor set",
                        color      = NavyDark,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize   = 16.sp,
                        fontFamily = PlusJakartaSans
                    )
                    val subtext = buildString {
                        append(relativeAgeLabel(anchor.timestampMillis))
                        anchor.accuracyMeters?.let { append(" · ±${it.toInt()}m accuracy") }
                        if (navState.distanceMeters == null) append(" · Waiting for GPS fix")
                        else if (navState.isExpiringSoon) append(" · Expiring soon")
                    }
                    Text(subtext, color = SubText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                }
            }
            if (anchor != null) {
                TextButton(onClick = onClear) {
                    Text(
                        "Clear",
                        color      = Color(0xFFEF4444),
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        fontSize   = 13.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
    }
}

/** "180m away" under 1km, "1.4km away" at/above. */
private fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.roundToInt()}m away"
    else "%.1fkm away".format(meters / 1000f)

private fun relativeAgeLabel(timestampMillis: Long): String {
    val ageMs = System.currentTimeMillis() - timestampMillis
    val mins  = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ageMs)
    val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ageMs)
    val days  = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(ageMs)
    return when {
        mins  < 1  -> "Just now"
        mins  < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        else       -> "${days}d ago"
    }
}

// ─── Calibration Tip Row ──────────────────────────────────────────────────────

@Composable
private fun CalibTip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50.dp)).background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SubText, modifier = Modifier.size(17.dp))
        }
        Text(
            text,
            color      = NavyDark.copy(alpha = 0.75f),
            fontSize   = 13.sp,
            lineHeight = 19.sp,
            fontFamily = PlusJakartaSans,
            modifier   = Modifier.weight(1f)
        )
    }
}

fun getCardinalDirection(azimuth: Float): String = when {
    azimuth < 22.5  || azimuth >= 337.5 -> "N"
    azimuth < 67.5                       -> "NE"
    azimuth < 112.5                      -> "E"
    azimuth < 157.5                      -> "SE"
    azimuth < 202.5                      -> "S"
    azimuth < 247.5                      -> "SW"
    azimuth < 292.5                      -> "W"
    else                                 -> "NW"
}

// ══════════════════════════════════════════════════════════════════════════════
//  TORCH SCREEN — unchanged from original
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorchScreen(navController: NavController) {
    val context = LocalContext.current
    var isTorchOn     by remember { mutableStateOf(false) }
    var strobeEnabled by remember { mutableStateOf(false) }
    var strobeRate    by remember { mutableLongStateOf(200L) }

    LaunchedEffect(strobeEnabled) {
        if (strobeEnabled) {
            while (strobeEnabled) {
                setTorch(context, true)
                kotlinx.coroutines.delay(strobeRate)
                setTorch(context, false)
                kotlinx.coroutines.delay(strobeRate)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { setTorch(context, false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                setTorch(context, false)
                navController.popBackStack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(
                    when {
                        strobeEnabled -> BlueAccent.copy(alpha = 0.12f)
                        isTorchOn     -> AmberWarn.copy(alpha = 0.12f)
                        else          -> Color(0xFFE5E7EB)
                    }
                )
                .padding(horizontal = 28.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    strobeEnabled -> "STROBE"
                    isTorchOn     -> "ON"
                    else          -> "OFF"
                },
                color = when {
                    strobeEnabled -> BlueAccent
                    isTorchOn     -> AmberWarn
                    else          -> NavyMid
                },
                fontSize      = 13.sp,
                fontWeight    = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = 2.sp,
                fontFamily    = PlusJakartaSans
            )
        }

        Spacer(Modifier.height(28.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    if (isTorchOn)
                        Brush.radialGradient(
                            listOf(Color(0xFFFFF9C4), Color(0xFFFFEB3B).copy(alpha = 0.25f))
                        )
                    else
                        Brush.radialGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFEEEFF2))
                        )
                )
                .border(
                    1.5.dp,
                    if (isTorchOn) AmberWarn.copy(alpha = 0.5f) else Color(0xFFDDDEE2),
                    CircleShape
                )
                .clickable {
                    isTorchOn     = !isTorchOn
                    strobeEnabled = false
                    setTorch(context, isTorchOn)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = null,
                    tint               = if (isTorchOn) AmberWarn else NavyMid.copy(alpha = 0.45f),
                    modifier           = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isTorchOn) "ON" else "OFF",
                    color         = if (isTorchOn) AmberWarn else NavyMid.copy(alpha = 0.45f),
                    fontWeight    = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    fontSize      = 16.sp,
                    letterSpacing = 3.sp,
                    fontFamily    = PlusJakartaSans
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Tap the circle to toggle your flashlight",
            color      = SubText,
            fontSize   = 13.sp,
            textAlign  = TextAlign.Center,
            lineHeight = 20.sp,
            fontFamily = PlusJakartaSans
        )

        Spacer(Modifier.weight(1f))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            shape     = RoundedCornerShape(20.dp),
            colors    = CardDefaults.cardColors(containerColor = BgCard),
            border    = BorderStroke(1.dp, BorderCol),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier              = Modifier.weight(1f),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFE4E4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Warning, null,
                                tint     = Color(0xFFE53935),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                "Emergency Strobe",
                                color      = NavyDark,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize   = 15.sp,
                                fontFamily = PlusJakartaSans
                            )
                            Text(
                                "SOS lighting mode",
                                color      = SubText,
                                fontSize   = 12.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                    Switch(
                        checked         = strobeEnabled,
                        onCheckedChange = { checked ->
                            strobeEnabled = checked
                            if (!checked) setTorch(context, false)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor    = Color.White,
                            checkedTrackColor    = BlueAccent,
                            checkedBorderColor   = Color.Transparent,
                            uncheckedThumbColor  = Color.White,
                            uncheckedTrackColor  = Color(0xFFD1D5DB),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                HorizontalDivider(color = BorderCol, thickness = 0.5.dp)

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "STROBE FREQUENCY",
                        color         = SubText,
                        fontSize      = 10.sp,
                        fontWeight    = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily    = PlusJakartaSans
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${1000L / strobeRate} Hz",
                            color      = BlueAccent,
                            fontSize   = 13.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }

                val sliderVal = 1100f - strobeRate.toFloat()
                Slider(
                    value         = sliderVal,
                    onValueChange = {
                        strobeRate = (1100f - it).toLong().coerceIn(100L, 1000L)
                    },
                    valueRange = 100f..1000f,
                    steps      = 8,
                    modifier   = Modifier.fillMaxWidth(),
                    thumb      = {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B9CC8))
                                .shadow(4.dp, CircleShape)
                        )
                    },
                    track = { sliderState ->
                        val fraction = (sliderState.value - 100f) / (1000f - 100f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFE5E7EB))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(Color(0xFFD1D5DB))
                            )
                        }
                    }
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Slow", color = SubText, fontSize = 11.sp, fontFamily = PlusJakartaSans)
                    Text("Fast", color = SubText, fontSize = 11.sp, fontFamily = PlusJakartaSans)
                }
            }
        }
    }
}

// ─── Torch Helper ─────────────────────────────────────────────────────────────

fun setTorch(context: Context, on: Boolean) {
    try {
        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id  = cam.cameraIdList.firstOrNull() ?: return
        cam.setTorchMode(id, on)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}