package com.example.namastays.screens

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import android.util.Log
import com.example.namastays.data.BlePermissionHelper
import com.example.namastays.data.SosBleScanService
import kotlin.math.roundToInt

private const val TAG = "SAFETY_HOME"

private val BgPage      = Color(0xFFF2F4F7)
private val BgCard      = Color.White
private val NavyDark    = Color(0xFF0D2137)
private val RedSOS      = Color(0xFFE53935)
private val SliderTrack = Color(0xFFEDEFF2)
private val SubtleText  = Color(0xFF8A99A8)
private val IconBg      = Color(0xFFEBEEF2)
private val IconTint    = Color(0xFF1A3A52)

object SafetyRoutes {
    const val HOME        = "safety_home"
    const val SOS         = "sos"
    const val AMS         = "ams_checker"
    const val LAKE_LOUISE = "lake_louise"
    const val CONTACTS    = "emergency_contacts"
    const val COMPASS     = "compass"
    const val TORCH       = "torch"
    const val ADD_CONTACT = "add_contact"
    const val LOCAL_BODIES = "local_bodies"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHomeScreen(navController: NavController) {
    val context = LocalContext.current

    // ── Notification permission launcher (Android 13+) ────────────────────
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Notification permission granted: $granted")
    }

    // ── BLE permissions launcher ──────────────────────────────────────────
    val blePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        Log.d(TAG, "BLE permissions granted: $allGranted")
        if (allGranted) {
            try {
                if (
                    BlePermissionHelper.isBleSupported(context) &&
                    BlePermissionHelper.isBluetoothEnabled(context)
                ) {
                    SosBleScanService.start(context)
                    Log.d(TAG, "BLE scan service started after permission grant")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE scan service after permission grant: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                notificationPermissionLauncher.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request notification permission: ${e.message}")
            }
        }

        // Only auto-start if user previously turned it ON
        if (SosBleScanService.isUserEnabled(context)) {
            try {
                if (BlePermissionHelper.hasAllPermissions(context)) {
                    if (!BlePermissionHelper.isBleSupported(context)) {
                        Log.w(TAG, "BLE not supported")
                        return@LaunchedEffect
                    }
                    if (!BlePermissionHelper.isBluetoothEnabled(context)) {
                        blePermissionsLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                        return@LaunchedEffect
                    }
                    SosBleScanService.start(context)
                    Log.d(TAG, "BLE scan service auto-started — user had it enabled")
                } else {
                    blePermissionsLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}")
            }
        } else {
            Log.d(TAG, "BLE scanning disabled by user — not auto-starting")
        }
    }

    // ── Keep service running in background when leaving ───────────────────
    DisposableEffect(Unit) {
        onDispose { }
    }

    Scaffold(
        containerColor = BgPage
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            SOSCard(onClick = { navController.navigate(SafetyRoutes.SOS) })

            BleScanToggleRow(context = context)


            Text(
                text = "QUICK ACCESS",
                color = NavyDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Explore,
                        title = "COMPASS",
                        subtitle = "DIGITAL DIRECTION",
                        onClick = { navController.navigate(SafetyRoutes.COMPASS) }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.FlashlightOn,
                        title = "TORCH",
                        subtitle = "FLASHLIGHT",
                        onClick = { navController.navigate(SafetyRoutes.TORCH) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Contacts,
                        title = "EMERGENCY",
                        subtitle = "CONTACTS",
                        onClick = { navController.navigate(SafetyRoutes.CONTACTS) }
                    )
                    QuickCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccountBalance,
                        title = "LOCAL BODIES",
                        subtitle = "RANGERS & BASE",
                        onClick = { navController.navigate(SafetyRoutes.LOCAL_BODIES) }
                    )
                }
            }

            LakeLouiseBanner(onClick = { navController.navigate(SafetyRoutes.LAKE_LOUISE) })
        }
    }
}

@Composable
private fun SOSCard(onClick: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxSlide = 220f
    val threshold = 180f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(RedSOS.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(RedSOS),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SOS EMERGENCY",
                    color = RedSOS,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Slide to activate rescue protocol",
                    color = SubtleText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(SliderTrack)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SLIDE TO SEND",
                        color = SubtleText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BgCard)
                        .shadow(4.dp, CircleShape)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (offsetX >= threshold) {
                                        offsetX = 0f
                                        onClick()
                                    } else {
                                        offsetX = 0f
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    offsetX = (offsetX + dragAmount).coerceIn(0f, maxSlide)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Slide",
                        tint = RedSOS,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}



@Composable
private fun QuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(IconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = IconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                color = NavyDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = SubtleText,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LakeLouiseBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "LAKE LOUISE",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "AMS & SCORE CHECK",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NavyDark)
        }
        Text(
            text = title,
            color = NavyDark,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
fun BleScanToggleRow(context: Context) {
    // Read persisted state — not the volatile isRunning flag
    var isEnabled by remember {
        mutableStateOf(SosBleScanService.isUserEnabled(context))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16213E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF00BCD4) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    "BLE SOS Scanning",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    if (isEnabled) "Scanning every 1 min for nearby SOS"
                    else "Off — enable to detect nearby SOS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                if (checked) {
                    SosBleScanService.start(context)
                } else {
                    SosBleScanService.stop(context)
                }
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF00BCD4),
                checkedTrackColor = Color(0xFF00BCD4).copy(alpha = 0.4f)
            )
        )
    }
}