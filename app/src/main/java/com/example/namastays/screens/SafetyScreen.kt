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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.viewmodel.SafetyViewModel


private const val TAG = "SAFETY_HOME"

// ─── Palette ──────────────────────────────────────────────────────────────────
private val ShPageBg   = Color(0xFFF7F8FA)
private val ShCardBg   = Color.White
private val ShCardBdr  = Color(0xFFE5E7EB)
private val ShNavyDark = Color(0xFF111827)
private val ShRedSOS   = Color(0xFFE53935)
private val ShSubText  = Color(0xFF9CA3AF)
private val ShIconBg   = Color(0xFFF3F4F6)
private val ShIconTint = Color(0xFF374151)

object SafetyRoutes {
    const val HOME         = "safety_home"
    const val SOS          = "sos"
    const val AMS          = "ams_checker"
    const val LAKE_LOUISE  = "lake_louise"
    const val CONTACTS     = "emergency_contacts"
    const val COMPASS      = "compass"
    const val TORCH        = "torch"
    const val ADD_CONTACT  = "add_contact"
    const val LOCAL_BODIES = "local_bodies"
}


// Keep all your existing imports, add these:

// Inside SafetyHomeScreen composable:

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHomeScreen(
    navController: NavController,
    vm           : SafetyViewModel = viewModel(),   // ← add vm parameter
) {
    val context         = LocalContext.current
    val permissionStatus by vm.permissionStatus.collectAsStateWithLifecycle()

    // Controls whether the permission bottom sheet is showing
    var showPermissionSheet by remember { mutableStateOf(false) }

    // Refresh permissions whenever screen comes into view
    LaunchedEffect(Unit) {
        vm.refreshPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> Log.d(TAG, "Notification permission granted: $granted") }

    val blePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            try {
                if (BlePermissionHelper.isBleSupported(context) && BlePermissionHelper.isBluetoothEnabled(context)) {
                    SosBleScanService.start(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BLE scan service: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try { notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            catch (e: Exception) { Log.e(TAG, "Failed to request notification permission: ${e.message}") }
        }
        if (SosBleScanService.isUserEnabled(context)) {
            try {
                if (BlePermissionHelper.hasAllPermissions(context)) {
                    if (!BlePermissionHelper.isBleSupported(context)) { Log.w(TAG, "BLE not supported"); return@LaunchedEffect }
                    if (!BlePermissionHelper.isBluetoothEnabled(context)) { blePermissionsLauncher.launch(BlePermissionHelper.getRequiredPermissions()); return@LaunchedEffect }
                    SosBleScanService.start(context)
                } else {
                    blePermissionsLauncher.launch(BlePermissionHelper.getRequiredPermissions())
                }
            } catch (e: SecurityException) { Log.e(TAG, "SecurityException: ${e.message}") }
            catch (e: Exception)          { Log.e(TAG, "Error: ${e.message}") }
        }
    }

    DisposableEffect(Unit) { onDispose { } }

    Scaffold(
        containerColor      = ShPageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())

                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ── SOS card — permission gate on tap ─────────────────────────────
            ShSOSCard(
                onClick = {
                    vm.refreshPermissions()
                    if (permissionStatus.allGranted) {
                        navController.navigate(SafetyRoutes.SOS)
                    } else {
                        showPermissionSheet = true
                    }
                }
            )

            // ── Rest of your existing content stays exactly the same ──────────
            BleScanToggleRow(context = context)

            Text(
                text          = "QUICK ACCESS",
                color         = ShNavyDark,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 11.sp,
                letterSpacing = 2.sp,
                fontFamily    = PlusJakartaSans,
                modifier      = Modifier.padding(top = 4.dp, start = 2.dp),
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShQuickCard(modifier = Modifier.weight(1f), icon = Icons.Default.Explore,       title = "Compass",   subtitle = "Digital direction", onClick = { navController.navigate(SafetyRoutes.COMPASS) })
                    ShQuickCard(modifier = Modifier.weight(1f), icon = Icons.Default.FlashlightOn,  title = "Torch",     subtitle = "Flashlight",       onClick = { navController.navigate(SafetyRoutes.TORCH) })
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ShQuickCard(modifier = Modifier.weight(1f), icon = Icons.Default.Contacts,       title = "Emergency", subtitle = "Contacts",        onClick = { navController.navigate(SafetyRoutes.CONTACTS) })
                    ShQuickCard(modifier = Modifier.weight(1f), icon = Icons.Default.AccountBalance,  title = "Local Bodies", subtitle = "Rangers & base", onClick = { navController.navigate(SafetyRoutes.LOCAL_BODIES) })
                }
            }

            ShLakeLouiseBanner(onClick = { navController.navigate(SafetyRoutes.LAKE_LOUISE) })
        }
    }

    // ── Permission bottom sheet ───────────────────────────────────────────────
    if (showPermissionSheet) {
        SosPermissionSheet(
            permissionStatus = permissionStatus,
            onAllGranted     = {
                showPermissionSheet = false
                navController.navigate(SafetyRoutes.SOS)
            },
            onDismiss        = { showPermissionSheet = false },
            onRefresh        = { vm.refreshPermissions() },
        )
    }
}

// ─── SOS Card ─────────────────────────────────────────────────────────────────
@Composable
private fun ShSOSCard(onClick: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxSlide = 220f
    val threshold = 180f

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(22.dp),
        colors    = CardDefaults.cardColors(containerColor = ShCardBg),
        border    = BorderStroke(1.dp, ShCardBdr),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SOS icon ring
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(ShRedSOS.copy(alpha = 0.07f))
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(ShRedSOS),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint     = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SOS EMERGENCY",
                    color         = ShRedSOS,
                    fontWeight    = FontWeight.ExtraBold,
                    fontSize      = 22.sp,
                    letterSpacing = 1.5.sp,
                    fontFamily    = PlusJakartaSans
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Slide right to activate rescue protocol",
                    color      = ShSubText,
                    fontSize   = 13.sp,
                    textAlign  = TextAlign.Center,
                    fontFamily = PlusJakartaSans
                )
            }

            // Slide track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFFF3F4F6))
                    .border(1.dp, ShCardBdr, RoundedCornerShape(50.dp))
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SLIDE TO SEND SOS",
                        color         = ShSubText,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        fontFamily    = PlusJakartaSans
                    )
                }
                // Thumb
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ShCardBg)
                        .border(1.dp, ShCardBdr, CircleShape)
                        .shadow(2.dp, CircleShape)
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
                        imageVector        = Icons.Default.ChevronRight,
                        contentDescription = "Slide to send SOS",
                        tint               = ShRedSOS,
                        modifier           = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

// ─── Quick card ───────────────────────────────────────────────────────────────
@Composable
private fun ShQuickCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier  = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(containerColor = ShCardBg),
        border    = BorderStroke(1.dp, ShCardBdr),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(ShIconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = ShIconTint,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text          = title,
                color         = ShNavyDark,
                fontWeight    = FontWeight.Bold,
                fontSize      = 13.sp,
                textAlign     = TextAlign.Center,
                fontFamily    = PlusJakartaSans
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text       = subtitle,
                color      = ShSubText,
                fontSize   = 11.sp,
                textAlign  = TextAlign.Center,
                fontFamily = PlusJakartaSans
            )
        }
    }
}

// ─── Lake Louise banner ────────────────────────────────────────────────────────
@Composable
private fun ShLakeLouiseBanner(onClick: () -> Unit) {
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = ShNavyDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.BarChart,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text          = "AMS Checker",
                color         = Color.White,
                fontWeight    = FontWeight.ExtraBold,
                fontSize      = 18.sp,
                letterSpacing = 0.5.sp,
                fontFamily    = PlusJakartaSans
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = "Lake Louise Score — assess altitude sickness",
                color      = Color.White.copy(alpha = 0.6f),
                fontSize   = 12.sp,
                fontFamily = PlusJakartaSans,
                textAlign  = TextAlign.Center
            )
        }
    }
}

// ─── Screen header (shared) ────────────────────────────────────────────────────
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF111827))
        }
        Text(
            text       = title,
            color      = Color(0xFF111827),
            fontWeight = FontWeight.Bold,
            fontSize   = 20.sp,
            fontFamily = PlusJakartaSans
        )
    }
}

// ─── BLE scan toggle row ───────────────────────────────────────────────────────
@Composable
fun BleScanToggleRow(context: Context) {
    var isEnabled by remember { mutableStateOf(SosBleScanService.isUserEnabled(context)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) Color(0xFF00BCD4).copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint     = if (isEnabled) Color(0xFF00BCD4) else Color(0xFF9CA3AF),
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    "BLE SOS Scanning",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    fontFamily = PlusJakartaSans
                )
                Text(
                    if (isEnabled) "Active — scanning every 1 min for nearby SOS"
                    else "Off — enable to detect nearby SOS signals",
                    color      = Color.White.copy(alpha = 0.5f),
                    fontSize   = 11.sp,
                    fontFamily = PlusJakartaSans
                )
            }
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = { checked ->
                isEnabled = checked
                if (checked) SosBleScanService.start(context)
                else SosBleScanService.stop(context)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor   = Color(0xFF00BCD4),
                checkedTrackColor   = Color(0xFF00BCD4).copy(alpha = 0.3f),
                uncheckedThumbColor = Color(0xFF6B7280),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}