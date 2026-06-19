package com.example.namastays.screens

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
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlin.math.*

// ─── Shared palette ────────────────────────────────────────────────────────────
private val BgPage     = Color(0xFFF7F8FA)
private val BgCard     = Color.White
private val NavyDark   = Color(0xFF111827)
private val NavyMid    = Color(0xFF374151)
private val SubText    = Color(0xFF9CA3AF)
private val BorderCol  = Color(0xFFE5E7EB)
private val RedNorth   = Color(0xFFE53935)
private val BlueAccent = Color(0xFF3B82F6)
private val AmberWarn  = Color(0xFFF59E0B)

// ══════════════════════════════════════════════════════════════════════════════
//  COMPASS SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun CompassScreen(navController: NavController) {
    val context = LocalContext.current
    var azimuth       by remember { mutableStateOf(0f) }
    var altitude      by remember { mutableStateOf<Float?>(null) }
    var accuracy      by remember { mutableStateOf("--") }
    var accuracyLevel by remember { mutableStateOf(-1) }
    val smoothAzimuth = remember { Animatable(0f) }

    DisposableEffect(Unit) {
        val sm            = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetometer  = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val pressure      = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        var gravity = FloatArray(3)
        var geo     = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER  -> gravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geo     = event.values.clone()
                    Sensor.TYPE_PRESSURE -> {
                        altitude = SensorManager.getAltitude(
                            SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]
                        )
                    }
                }
                val R = FloatArray(9); val I = FloatArray(9)
                if (SensorManager.getRotationMatrix(R, I, gravity, geo)) {
                    val ori = FloatArray(3)
                    SensorManager.getOrientation(R, ori)
                    val deg = Math.toDegrees(ori[0].toDouble()).toFloat()
                    azimuth = if (deg < 0) deg + 360 else deg
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
        sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, magnetometer,  SensorManager.SENSOR_DELAY_UI)
        pressure?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(azimuth) {
        smoothAzimuth.animateTo(azimuth, spring(dampingRatio = 0.6f, stiffness = 80f))
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        // ── Warning banner ────────────────────────────────────────────────────
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
                    color = Color(0xFF92400E), fontSize = 12.sp,
                    fontFamily = PlusJakartaSans, lineHeight = 17.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "COMPASS",
            color = SubText, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
            fontFamily = PlusJakartaSans
        )

        Spacer(Modifier.height(20.dp))

        // ── Compass rose ──────────────────────────────────────────────────────
        // Box is the fixed 300×300 container; everything inside is positioned within it
        Box(
            modifier         = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            val currentAzimuth = smoothAzimuth.value

            // Full-size Canvas: draws the rotating disc + fixed inner white circle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx     = size.width  / 2f
                val cy     = size.height / 2f
                val outerR = size.minDimension / 2f          // 150.dp in px
                val innerR = outerR * 0.52f                  // white inner circle

                // 1. Outer grey disc (ash colour from screenshot)
                drawCircle(Color(0xFFF0F1F4), outerR, Offset(cx, cy))

                // 2. Everything that rotates with the compass heading
                withTransform({ rotate(-currentAzimuth, Offset(cx, cy)) }) {

                    // Degree numbers + N/S/E/W labels — rotate WITH the disc
                    drawIntoCanvas { canvas ->
                        val numPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign   = android.graphics.Paint.Align.CENTER
                            textSize    = outerR * 0.115f
                            color       = android.graphics.Color.argb(140, 17, 24, 39)
                            typeface    = android.graphics.Typeface.DEFAULT
                        }
                        // Non-cardinal 30° labels
                        val labelDegs = listOf(30, 60, 120, 150, 210, 240, 300, 330)
                        for (deg in labelDegs) {
                            val rad = Math.toRadians(deg.toDouble())
                            val r   = outerR * 0.78f
                            val x   = cx + r * sin(rad).toFloat()
                            val y   = cy - r * cos(rad).toFloat() + numPaint.textSize * 0.35f
                            canvas.nativeCanvas.drawText(deg.toString(), x, y, numPaint)
                        }

                        // Cardinal letters
                        val cardPaint = android.graphics.Paint().apply {
                            isAntiAlias    = true
                            textAlign      = android.graphics.Paint.Align.CENTER
                            textSize       = outerR * 0.19f
                            typeface       = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val cardR      = outerR * 0.78f
                        val cardOffset = cardPaint.textSize * 0.38f

                        cardPaint.color = android.graphics.Color.argb(255, 229, 57, 53) // red N
                        canvas.nativeCanvas.drawText("N", cx, cy - cardR + cardOffset, cardPaint)
                        cardPaint.color = android.graphics.Color.argb(220, 17, 24, 39)
                        canvas.nativeCanvas.drawText("S", cx,         cy + cardR + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("E", cx + cardR, cy         + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("W", cx - cardR, cy         + cardOffset, cardPaint)
                    }
                }

                // 3. Inner white circle — drawn AFTER rotate block so it stays fixed on top
                drawCircle(Color.White, innerR, Offset(cx, cy))
                drawCircle(Color(0xFFE5E7EB), innerR, Offset(cx, cy), style = Stroke(1.5f))
            }

            // 4. Degree + direction readout — Compose overlay, centered in Box, above white circle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "${currentAzimuth.toInt()}°",
                    color      = NavyDark,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 36.sp,
                    fontFamily = PlusJakartaSans
                )
                Text(
                    direction,
                    color      = BlueAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 20.sp,
                    letterSpacing = 1.sp,
                    fontFamily = PlusJakartaSans
                )
            }

            // 5. Fixed red downward triangle pointer at top-centre of the disc
            Canvas(
                modifier = Modifier
                    .size(16.dp, 12.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 6.dp)          // sits just inside the outer rim
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, size.height)  // tip points DOWN into the disc
                    lineTo(size.width,      0f)
                    lineTo(0f,             0f)
                    close()
                }
                drawPath(path, RedNorth)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Info cards ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(IntrinsicSize.Min),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.TrendingUp, null, tint = SubText, modifier = Modifier.size(14.dp))
                        Text("Altitude", color = SubText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                    }
                    Text(
                        text       = altitude?.let { "%,.0f m".format(it) } ?: "— m",
                        color      = NavyDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 22.sp,
                        fontFamily = PlusJakartaSans
                    )
                    if (altitude == null) {
                        Text(
                            "No barometer",
                            color = Color(0xFFF97316), fontSize = 11.sp,
                            fontFamily = PlusJakartaSans
                        )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.GpsFixed, null, tint = SubText, modifier = Modifier.size(14.dp))
                        Text("Accuracy", color = SubText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                    }
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Canvas(Modifier.size(9.dp)) { drawCircle(accuracyColor) }
                        Text(
                            accuracy,
                            color      = accuracyColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 22.sp,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Calibration Tips ──────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
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
                    color = NavyDark, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, fontFamily = PlusJakartaSans
                )
                CalibTip(Icons.Outlined.AllInclusive,        "Move phone in a figure-8 motion repeatedly.")
                CalibTip(Icons.Outlined.StayCurrentPortrait, "Hold device flat, parallel to the ground.")
                CalibTip(Icons.Outlined.PhonelinkErase,      "Avoid large metal objects and magnetic fields.")
                CalibTip(Icons.Outlined.GpsFixed,            "Uses GPS to find True North when moving.")
            }
        }
    }
}

@Composable
private fun CalibTip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFFF3F4F6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = SubText, modifier = Modifier.size(17.dp))
        }
        Text(
            text,
            color = NavyDark.copy(alpha = 0.75f), fontSize = 13.sp,
            lineHeight = 19.sp, fontFamily = PlusJakartaSans,
            modifier = Modifier.weight(1f)
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
//  TORCH SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorchScreen(navController: NavController) {
    val context = LocalContext.current
    var isTorchOn     by remember { mutableStateOf(false) }
    var strobeEnabled by remember { mutableStateOf(false) }
    // 200ms default = 5 Hz, matching screenshot
    var strobeRate    by remember { mutableStateOf(200L) }

    LaunchedEffect(strobeEnabled, strobeRate) {
        if (strobeEnabled) {
            while (strobeEnabled) {
                setTorch(context, true)
                kotlinx.coroutines.delay(strobeRate)
                setTorch(context, false)
                kotlinx.coroutines.delay(strobeRate)
            }
        }
    }

    DisposableEffect(Unit) { onDispose { setTorch(context, false) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar: back button only, no title ───────────────────────────────
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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

        // ── Status pill chip ──────────────────────────────────────────────────
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
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp, fontFamily = PlusJakartaSans
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Torch circle button ───────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    if (isTorchOn)
                        Brush.radialGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFEB3B).copy(alpha = 0.25f)))
                    else
                        Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEEEFF2)))
                )
                .border(1.5.dp, if (isTorchOn) AmberWarn.copy(alpha = 0.5f) else Color(0xFFDDDEE2), CircleShape)
                .clickable {
                    isTorchOn = !isTorchOn
                    strobeEnabled = false
                    setTorch(context, isTorchOn)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (isTorchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                    contentDescription = null,
                    tint     = if (isTorchOn) AmberWarn else NavyMid.copy(alpha = 0.45f),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (isTorchOn) "ON" else "OFF",
                    color        = if (isTorchOn) AmberWarn else NavyMid.copy(alpha = 0.45f),
                    fontWeight   = FontWeight.ExtraBold,
                    fontSize     = 16.sp, letterSpacing = 3.sp,
                    fontFamily   = PlusJakartaSans
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Tap the circle to toggle your flashlight",
            color = SubText, fontSize = 13.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
            fontFamily = PlusJakartaSans
        )

        Spacer(Modifier.weight(1f))

        // ── Emergency Strobe card ─────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
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
                // Header row: red-triangle icon + title/subtitle + switch
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
                        // Salmon circle with red warning triangle
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
                                color = NavyDark, fontWeight = FontWeight.Bold,
                                fontSize = 15.sp, fontFamily = PlusJakartaSans
                            )
                            Text(
                                "SOS lighting mode",
                                color = SubText, fontSize = 12.sp,
                                fontFamily = PlusJakartaSans
                            )
                        }
                    }
                    // Switch — no border outline, clean toggle
                    Switch(
                        checked         = strobeEnabled,
                        onCheckedChange = {
                            strobeEnabled = it
                            if (!it) setTorch(context, false)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor       = Color.White,
                            checkedTrackColor       = BlueAccent,
                            checkedBorderColor      = Color.Transparent,   // ← removes border
                            uncheckedThumbColor     = Color.White,
                            uncheckedTrackColor     = Color(0xFFD1D5DB),
                            uncheckedBorderColor    = Color.Transparent    // ← removes border
                        )
                    )
                }

                HorizontalDivider(color = BorderCol, thickness = 0.5.dp)

                // STROBE FREQUENCY label + Hz pill
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "STROBE FREQUENCY",
                        color = SubText, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        fontFamily = PlusJakartaSans
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEF2FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${1000L / strobeRate} Hz",
                            color = BlueAccent, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans
                        )
                    }
                }

                // Slider: right = Fast (low ms), left = Slow (high ms)
                // sliderVal mapped so dragging right increases Hz
                val sliderVal = 1100f - strobeRate.toFloat()
                Slider(
                    value         = sliderVal,
                    onValueChange = { strobeRate = (1100f - it).toLong().coerceIn(100L, 1000L) },
                    valueRange    = 100f..1000f,
                    steps         = 8,
                    modifier      = Modifier.fillMaxWidth(),
                    thumb         = {
                        // Large filled circle thumb matching screenshot
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B9CC8))  // muted blue-grey from screenshot
                                .shadow(4.dp, CircleShape)
                        )
                    },
                    track         = { sliderState ->
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
                                    .background(Color(0xFFD1D5DB))  // inactive grey, track stays grey per screenshot
                            )
                        }
                    }
                )

                // Labels: Slow left, Fast right — matches screenshot
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

fun setTorch(context: Context, on: Boolean) {
    try {
        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id  = cam.cameraIdList.firstOrNull() ?: return
        cam.setTorchMode(id, on)
    } catch (e: Exception) { e.printStackTrace() }
}