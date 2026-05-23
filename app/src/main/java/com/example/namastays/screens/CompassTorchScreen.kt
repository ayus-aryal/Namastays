package com.example.namastays.screens

import android.content.Context
import android.hardware.*
import android.hardware.camera2.CameraManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

// ─── Shared color palette (mirrors SafetyHomeScreen) ─────────────────────────
private val BgPage    = Color(0xFFF2F4F7)
private val BgCard    = Color.White
private val NavyDark  = Color(0xFF0D2137)
private val NavyMid   = Color(0xFF1A3A52)
private val SubText   = Color(0xFF8A99A8)
private val RedNorth  = Color(0xFFE53935)
private val BlueAccent = Color(0xFF3B82F6)

// ═════════════════════════════════════════════════════════════════════════════
//  COMPASS SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CompassScreen(navController: NavController) {
    val context = LocalContext.current
    var azimuth  by remember { mutableStateOf(0f) }
    var altitude by remember { mutableStateOf<Float?>(null) }
    var accuracy by remember { mutableStateOf("--") }
    val smoothAzimuth = remember { Animatable(0f) }

    // ── Sensors ───────────────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
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
                    Sensor.TYPE_PRESSURE       -> {
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
                accuracy = when (acc) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH   -> "High"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> "Low"
                    else -> "Poor"
                }
            }
        }

        sm.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sm.registerListener(listener, magnetometer,  SensorManager.SENSOR_DELAY_UI)
        pressure?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }

        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(azimuth) {
        smoothAzimuth.animateTo(
            targetValue = azimuth,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f)
        )
    }

    val direction = getCardinalDirection(smoothAzimuth.value)

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        // "COMPASS" label
        Text(
            "COMPASS",
            color = SubText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Full compass rose ─────────────────────────────────────────────────
        Box(
            modifier = Modifier.size(300.dp),
            contentAlignment = Alignment.Center
        ) {
            // Rotating rose canvas
            val currentAzimuth = smoothAzimuth.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx       = size.width / 2
                val cy       = size.height / 2
                val outerR   = size.minDimension / 2f * 0.95f
                val innerR   = size.minDimension / 2f * 0.50f

                // ── Outer white circle ────────────────────────────────────
                drawCircle(Color.White, outerR, Offset(cx, cy))
                drawCircle(
                    Color(0xFFDDE3EA), outerR, Offset(cx, cy),
                    style = Stroke(1.5f)
                )

                // ── Rotating ring (ticks + labels) ────────────────────────
                withTransform({ rotate(-currentAzimuth, Offset(cx, cy)) }) {

                    // Tick marks every 5°
                    for (deg in 0 until 360 step 5) {
                        val rad       = Math.toRadians(deg.toDouble())
                        val s         = sin(rad).toFloat()
                        val c         = cos(rad).toFloat()
                        val isMajor   = deg % 30 == 0
                        val isCardinal= deg % 90 == 0
                        val tickLen = when {
                            isCardinal -> outerR * 0.14f
                            isMajor    -> outerR * 0.10f
                            else       -> outerR * 0.05f
                        }
                        val strokeW = when {
                            isCardinal -> 3f
                            isMajor    -> 2f
                            else       -> 1f
                        }
                        val alpha = when {
                            isCardinal -> 0.70f
                            isMajor    -> 0.45f
                            else       -> 0.20f
                        }
                        drawLine(
                            color       = NavyDark.copy(alpha = alpha),
                            start       = Offset(cx + (outerR - tickLen) * s, cy - (outerR - tickLen) * c),
                            end         = Offset(cx + outerR * s,              cy - outerR * c),
                            strokeWidth = strokeW
                        )
                    }

                    // Degree labels (every 30°, skip cardinals 0/90/180/270)
                    val labelDegs = listOf(30, 60, 120, 150, 210, 240, 300, 330)
                    drawIntoCanvas { canvas ->
                        val textPaint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            textAlign   = android.graphics.Paint.Align.CENTER
                            textSize    = outerR * 0.115f
                            color       = android.graphics.Color.argb(120, 13, 33, 55)
                            typeface    = android.graphics.Typeface.DEFAULT
                        }
                        for (deg in labelDegs) {
                            val rad = Math.toRadians(deg.toDouble())
                            val r   = outerR * 0.76f
                            val x   = cx + r * sin(rad).toFloat()
                            val y   = cy - r * cos(rad).toFloat() + textPaint.textSize * 0.35f
                            canvas.nativeCanvas.drawText(deg.toString(), x, y, textPaint)
                        }

                        // Cardinal letters N E S W
                        val cardPaint = android.graphics.Paint().apply {
                            isAntiAlias    = true
                            textAlign      = android.graphics.Paint.Align.CENTER
                            textSize       = outerR * 0.175f
                            isFakeBoldText = true
                            typeface       = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val cardR      = outerR * 0.76f
                        val cardOffset = cardPaint.textSize * 0.38f

                        // N – red
                        cardPaint.color = android.graphics.Color.argb(255, 229, 57, 53)
                        canvas.nativeCanvas.drawText("N", cx, cy - cardR + cardOffset, cardPaint)
                        // S, E, W – dark navy
                        cardPaint.color = android.graphics.Color.argb(200, 13, 33, 55)
                        canvas.nativeCanvas.drawText("S", cx,          cy + cardR  + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("E", cx + cardR,  cy          + cardOffset, cardPaint)
                        canvas.nativeCanvas.drawText("W", cx - cardR,  cy          + cardOffset, cardPaint)
                    }
                }

                // ── Inner light circle (center readout backdrop) ──────────
                drawCircle(Color(0xFFF2F4F7), innerR, Offset(cx, cy))
                drawCircle(
                    Color(0xFFDDE3EA), innerR, Offset(cx, cy),
                    style = Stroke(1f)
                )
            }

            // ── Center readout (fixed, not rotating) ──────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${smoothAzimuth.value.toInt()}°",
                    color = NavyDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )
                Text(
                    text = direction,
                    color = BlueAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    letterSpacing = 1.sp
                )
            }

            // ── Fixed red triangle pointer at top ─────────────────────────
            Canvas(
                modifier = Modifier
                    .size(18.dp, 14.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 4.dp)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, RedNorth)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.weight(1f))

        // ── Info cards row ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Altitude
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = BlueAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = altitude?.let { "${it.toInt()}m" } ?: "--",
                        color = NavyDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Altitude",
                        color = SubText,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Accuracy
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = BlueAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = accuracy,
                        color = NavyDark,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Accuracy",
                        color = SubText,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Calibration Tips card ─────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = NavyDark.copy(0.7f), modifier = Modifier.size(18.dp))
                    Text("Calibration Tips", color = NavyDark,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                CalibTip(Icons.Default.Refresh,      "Move phone in a figure-8 to calibrate")
                CalibTip(Icons.Default.PhoneAndroid,  "Hold phone flat for best accuracy")
                CalibTip(Icons.Default.Block,         "Stay away from metal objects & electronics")
                CalibTip(Icons.Default.GpsFixed,      "True north needs GPS — best outdoors")
            }
        }
    }
}

@Composable
private fun CalibTip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null,
            tint = SubText, modifier = Modifier.size(16.dp))
        Text(text, color = SubText, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

// Keep helper functions (unchanged)
fun getCardinalDirection(azimuth: Float): String = when {
    azimuth < 22.5  || azimuth >= 337.5 -> "N"
    azimuth < 67.5  -> "NE"
    azimuth < 112.5 -> "E"
    azimuth < 157.5 -> "SE"
    azimuth < 202.5 -> "S"
    azimuth < 247.5 -> "SW"
    azimuth < 292.5 -> "W"
    else             -> "NW"
}


// ═════════════════════════════════════════════════════════════════════════════
//  TORCH SCREEN
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TorchScreen(navController: NavController) {
    val context = LocalContext.current
    var isTorchOn     by remember { mutableStateOf(false) }
    var strobeEnabled by remember { mutableStateOf(false) }
    var strobeRate    by remember { mutableStateOf(500L) }   // ms → Hz = 1000/strobeRate

    // Strobe effect (logic unchanged)
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

    DisposableEffect(Unit) {
        onDispose { setTorch(context, false) }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                setTorch(context, false)
                navController.popBackStack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Neumorphic torch button ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    if (isTorchOn)
                        Brush.radialGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFEB3B).copy(alpha = 0.3f)))
                    else
                        Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE8ECF0)))
                )
                .shadow(if (isTorchOn) 12.dp else 6.dp, CircleShape, ambientColor = Color(0xFFB0BEC5))
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
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color(0xFFF59E0B) else NavyMid.copy(alpha = 0.5f),
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isTorchOn) "ON" else "OFF",
                    color = if (isTorchOn) Color(0xFFF59E0B) else NavyMid.copy(alpha = 0.5f),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    letterSpacing = 3.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hint text
        Text(
            "Tap the center button to activate\nyour flashlight.",
            color = SubText,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // ── Strobe card ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BgCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Strobe toggle row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Strobe Mode",
                            color = NavyDark,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            "SOS emergency strobe lighting",
                            color = SubText,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = strobeEnabled,
                        onCheckedChange = {
                            strobeEnabled = it
                            if (!it) setTorch(context, false)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = Color.White,
                            checkedTrackColor  = BlueAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFDDE3EA)
                        )
                    )
                }

                // Frequency row (always visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "FREQUENCY",
                        color = SubText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "${1000L / strobeRate} Hz",
                        color = BlueAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Slider
                Slider(
                    value = strobeRate.toFloat(),
                    onValueChange = { strobeRate = it.toLong() },
                    valueRange = 100f..1000f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        activeTrackColor   = BlueAccent,
                        inactiveTrackColor = Color(0xFFDDE3EA),
                        thumbColor         = Color(0xFFF59E0B)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fast (10Hz)", color = SubText, fontSize = 11.sp)
                    Text("Slow (1Hz)",  color = SubText, fontSize = 11.sp)
                }
            }
        }
    }
}

// setTorch helper (unchanged)
fun setTorch(context: Context, on: Boolean) {
    try {
        val cam = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id  = cam.cameraIdList.firstOrNull() ?: return
        cam.setTorchMode(id, on)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}