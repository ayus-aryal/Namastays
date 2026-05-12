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
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import kotlin.math.*

// ═════════════════════════════════════════════════════════════════════════════
//  COMPASS SCREEN — Immersive full-screen compass using SensorManager
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun CompassScreen(navController: NavController) {
    val context = LocalContext.current
    var azimuth by remember { mutableStateOf(0f) }
    val smoothAzimuth = remember { Animatable(0f) }

    // Sensor setup
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var gravityValues = FloatArray(3)
        var geoMagValues  = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER   -> gravityValues = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD  -> geoMagValues  = event.values.clone()
                }
                val R = FloatArray(9)
                val I = FloatArray(9)
                if (SensorManager.getRotationMatrix(R, I, gravityValues, geoMagValues)) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(R, orientation)
                    val degrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    azimuth = if (degrees < 0) degrees + 360 else degrees
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnetometer,  SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Animate rotation
    LaunchedEffect(azimuth) {
        smoothAzimuth.animateTo(
            targetValue = azimuth,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 80f)
        )
    }

    val direction = getCardinalDirection(smoothAzimuth.value)
    val compassColor = when(direction) {
        "N" -> Color(0xFFFF5252)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("COMPASS", color = Color.White.copy(alpha = 0.5f), letterSpacing = 3.sp, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // ── Compass rose canvas ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .rotate(-smoothAzimuth.value),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCompassRose()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Bearing readout ───────────────────────────────────────────────
            Text(
                text = direction,
                color = compassColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 72.sp,
                letterSpacing = 4.sp
            )
            Text(
                text = "${smoothAzimuth.value.toInt()}°",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light
            )

            Spacer(modifier = Modifier.weight(1f))

            // ── Cardinal strip ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (card, deg) ->
                    val diff = abs(((smoothAzimuth.value - deg + 360) % 360)).let { if (it > 180) 360 - it else it }
                    val alpha = (1f - diff / 90f).coerceIn(0.2f, 1f)
                    Text(
                        card,
                        color = if (card == "N") Color(0xFFFF5252).copy(alpha = alpha) else Color.White.copy(alpha = alpha),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Fixed north pointer at top
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = "North",
            tint = Color(0xFFFF5252),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 120.dp)
                .size(24.dp)
        )
    }
}

fun DrawScope.drawCompassRose() {
    val cx = size.width / 2
    val cy = size.height / 2
    val radius = size.minDimension / 2

    // Outer circle
    drawCircle(color = Color(0xFF1E2A45), radius = radius * 0.95f, center = Offset(cx, cy))
    drawCircle(
        color = Color.White.copy(alpha = 0.15f),
        radius = radius * 0.95f,
        center = Offset(cx, cy),
        style = Stroke(width = 2f)
    )

    // Tick marks
    for (i in 0 until 360 step 5) {
        val rad = Math.toRadians(i.toDouble())
        val isMajor = i % 45 == 0
        val isMinor = i % 15 == 0
        val tickLen = when {
            isMajor -> radius * 0.15f
            isMinor -> radius * 0.10f
            else    -> radius * 0.05f
        }
        val startR = radius * 0.95f - tickLen
        val endR   = radius * 0.95f
        drawLine(
            color = Color.White.copy(alpha = if (isMajor) 0.9f else 0.4f),
            start = Offset(cx + startR * sin(rad).toFloat(), cy - startR * cos(rad).toFloat()),
            end   = Offset(cx + endR   * sin(rad).toFloat(), cy - endR   * cos(rad).toFloat()),
            strokeWidth = if (isMajor) 3f else 1.5f
        )
    }

    // N pointer (red)
    val northPath = Path().apply {
        moveTo(cx, cy - radius * 0.7f)
        lineTo(cx - radius * 0.08f, cy)
        lineTo(cx, cy - radius * 0.3f)
        close()
    }
    drawPath(northPath, color = Color(0xFFFF5252))

    // S pointer (white)
    val southPath = Path().apply {
        moveTo(cx, cy + radius * 0.7f)
        lineTo(cx + radius * 0.08f, cy)
        lineTo(cx, cy + radius * 0.3f)
        close()
    }
    drawPath(southPath, color = Color.White.copy(alpha = 0.9f))

    // Center dot
    drawCircle(color = Color(0xFFFF5252), radius = 8f, center = Offset(cx, cy))
    drawCircle(color = Color.White, radius = 4f, center = Offset(cx, cy))
}

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
//  TORCH SCREEN — Toggle flashlight + strobe mode
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun TorchScreen(navController: NavController) {
    val context = LocalContext.current
    var isTorchOn by remember { mutableStateOf(false) }
    var strobeEnabled by remember { mutableStateOf(false) }
    var strobeRate by remember { mutableStateOf(500L) } // ms interval

    // Glow animation when torch is ON
    val glowAlpha by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Strobe effect
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

    // Turn off torch on leave
    DisposableEffect(Unit) {
        onDispose {
            setTorch(context, false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isTorchOn) Color(0xFF1A1A00) else Color(0xFF1A1A2E)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ScreenHeader("Torch", onBack = {
                setTorch(context, false)
                navController.popBackStack()
            })

            Spacer(modifier = Modifier.weight(1f))

            // ── Glow effect ───────────────────────────────────────────────────
            if (isTorchOn) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .alpha(glowAlpha)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFFFCC).copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )
            }

            // ── Main Torch Button ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTorchOn)
                            Brush.radialGradient(listOf(Color(0xFFFFEB3B), Color(0xFFFFA000)))
                        else
                            Brush.radialGradient(listOf(Color(0xFF2E2E2E), Color(0xFF1A1A1A)))
                    )
                    .border(3.dp, if (isTorchOn) Color(0xFFFFEB3B) else Color.White.copy(alpha = 0.1f), CircleShape)
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
                        tint = if (isTorchOn) Color(0xFF1A1A00) else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        if (isTorchOn) "ON" else "OFF",
                        color = if (isTorchOn) Color(0xFF1A1A00) else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Strobe section ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF16213E))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Strobe Mode", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("SOS emergency strobe", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    Switch(
                        checked = strobeEnabled,
                        onCheckedChange = {
                            strobeEnabled = it
                            if (!it) setTorch(context, false)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFEB3B), checkedTrackColor = Color(0xFFFFA000))
                    )
                }

                if (strobeEnabled) {
                    Text(
                        "Speed: ${1000L / strobeRate} Hz",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Slider(
                        value = strobeRate.toFloat(),
                        onValueChange = { strobeRate = it.toLong() },
                        valueRange = 100f..1000f,
                        steps = 8,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFFFFEB3B), thumbColor = Color(0xFFFFEB3B))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Fast (10Hz)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                        Text("Slow (1Hz)", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun setTorch(context: Context, on: Boolean) {
    try {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
        cameraManager.setTorchMode(cameraId, on)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}