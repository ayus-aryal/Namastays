package com.example.namastays.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.BleManager
import com.example.namastays.data.BlePermissionHelper
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.data.SosAlert
import com.example.namastays.data.SosManager
import com.example.namastays.viewmodel.SafetyViewModel
import kotlinx.coroutines.delay

// ── Light theme color palette ─────────────────────────────────────────────────
private object SosColors {
    val background    = Color(0xFFF5F5F7)
    val surface       = Color(0xFFFFFFFF)
    val surfaceAlt    = Color(0xFFF0F0F2)
    val divider       = Color(0xFFD1D1D6)
    val textPrimary   = Color(0xFF1C1C1E)
    val textSecondary = Color(0xFF6B6B6B)
    val textHint      = Color(0xFF8E8E93)
    val sosRed        = Color(0xFFDC2626)
    val sosRedLight   = Color(0xFFFEE2E2)
    val warnAmber     = Color(0xFFD97706)
    val warnAmberBg   = Color(0xFFFFFBEB)
    val warnAmberBdr  = Color(0xFFFCD34D)
    val warnAmberText = Color(0xFF92400E)
    val green         = Color(0xFF16A34A)
    val greenBg       = Color(0xFFDCFCE7)
    val blue          = Color(0xFF2563EB)
    val blueBg        = Color(0xFFDBEAFE)
    val purple        = Color(0xFF7C3AED)
    val purpleBg      = Color(0xFFEDE9FE)
    val cyanIcon      = Color(0xFF0891B2)
    val cyanBg        = Color(0xFFCFFAFE)
}

@Composable
fun SOSScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()

    var sosActivated     by remember { mutableStateOf(false) }
    var countdown        by remember { mutableStateOf(5) }
    var counting         by remember { mutableStateOf(false) }
    var statusMessage    by remember { mutableStateOf("Tap the button to start SOS countdown") }
    var hasSmsPermission by remember { mutableStateOf(false) }
    var bleStatusMessage by remember { mutableStateOf("") }
    var nearbyAlerts     by remember { mutableStateOf<List<SosAlert>>(emptyList()) }

    // ── Permission launcher ───────────────────────────────────────────────────
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.SEND_SMS] == true
    }

    LaunchedEffect(Unit) {
        permissionsLauncher.launch(
            arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    LaunchedEffect(Unit) {
        bleStatusMessage = if (
            BlePermissionHelper.hasAllPermissions(context) &&
            BlePermissionHelper.isBleSupported(context) &&
            BlePermissionHelper.isBluetoothEnabled(context)
        ) "Scanning for nearby SOS signals"
        else BlePermissionHelper.getStatusMessage(context)
    }

    DisposableEffect(Unit) {
        onDispose { if (!SosManager.isSosActive) BleManager.stopAdvertising() }
    }

    LaunchedEffect(counting) {
        if (counting) {
            while (countdown > 0) { delay(1000L); countdown-- }
            sosActivated  = true
            counting      = false
            statusMessage = "Sending SOS..."
            triggerSOS(context, contacts) { _, message -> statusMessage = message }
        }
    }

    // ── Pulse animation ───────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SosColors.background)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = PlusJakartaSans)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SosColors.surfaceAlt)
                            .clickable {
                                SosManager.cancelSos(context)
                                navController.popBackStack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint               = SosColors.textPrimary,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Emergency SOS",
                        color      = SosColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 17.sp
                    )
                }

                // ── SMS permission warning ────────────────────────────────────
                if (!hasSmsPermission) {
                    SosWarningBanner(
                        icon    = Icons.Default.Warning,
                        text    = "SMS permission needed. Tap to grant.",
                        onClick = { permissionsLauncher.launch(arrayOf(Manifest.permission.SEND_SMS)) }
                    )
                }

                // ── No contacts warning ───────────────────────────────────────
                if (contacts.isEmpty()) {
                    SosWarningBanner(
                        icon    = Icons.Default.PersonOff,
                        text    = "No emergency contacts saved. Tap to add.",
                        onClick = { navController.navigate(SafetyRoutes.CONTACTS) }
                    )
                }

                // ── BLE status ────────────────────────────────────────────────
                if (bleStatusMessage.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SosColors.blueBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint               = SosColors.blue,
                            modifier           = Modifier.size(16.dp)
                        )
                        Text(
                            bleStatusMessage,
                            color    = SosColors.blue,
                            fontSize = 12.sp
                        )
                    }
                }

                // ── Nearby SOS alerts ─────────────────────────────────────────
                if (nearbyAlerts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SosColors.sosRedLight)
                            .border(0.5.dp, SosColors.sosRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Nearby SOS Signals",
                            color      = SosColors.sosRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 13.sp
                        )
                        nearbyAlerts.takeLast(3).forEach { alert ->
                            SosNearbyAlertRow(alert = alert, context = context)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── SOS ring button ───────────────────────────────────────────
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(if (counting || sosActivated) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(SosColors.sosRed.copy(alpha = 0.07f))
                    )
                    Box(
                        modifier = Modifier
                            .size(168.dp)
                            .clip(CircleShape)
                            .background(SosColors.sosRed.copy(alpha = 0.12f))
                    )
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(SosColors.sosRed)
                            .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .clickable(enabled = !sosActivated) {
                                if (!counting) {
                                    counting  = true
                                    countdown = 5
                                } else {
                                    counting      = false
                                    countdown     = 5
                                    statusMessage = "Tap the button to start SOS countdown"
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            when {
                                counting -> {
                                    Text(
                                        "$countdown",
                                        color      = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize   = 48.sp
                                    )
                                    Text(
                                        "Tap to cancel",
                                        color    = Color.White.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                                sosActivated -> {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        "SOS SENT",
                                        color      = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize   = 18.sp
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        "SOS",
                                        color         = Color.White,
                                        fontWeight    = FontWeight.ExtraBold,
                                        fontSize      = 22.sp,
                                        letterSpacing = 3.sp
                                    )
                                    Text(
                                        "Hold to activate",
                                        color    = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Status pill ───────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SosColors.surface)
                        .border(0.5.dp, SosColors.divider, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        statusMessage,
                        color     = SosColors.textSecondary,
                        fontSize  = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                HorizontalDivider(color = SosColors.divider, thickness = 0.5.dp)

                // ── Quick actions label ───────────────────────────────────────
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "QUICK ACTIONS",
                        color         = SosColors.textHint,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }

                // ── Quick action buttons ──────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.Call,
                        label     = "Call 112",
                        iconColor = SosColors.green,
                        iconBg    = SosColors.greenBg,
                        onClick   = { dialNumber(context, "112") }
                    )
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.LocalHospital,
                        label     = "Ambulance",
                        iconColor = SosColors.blue,
                        iconBg    = SosColors.blueBg,
                        onClick   = { dialNumber(context, "108") }
                    )
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.Shield,
                        label     = "Police",
                        iconColor = SosColors.purple,
                        iconBg    = SosColors.purpleBg,
                        onClick   = { dialNumber(context, "100") }
                    )
                }

                HorizontalDivider(color = SosColors.divider, thickness = 0.5.dp)

                // ── Info section label ────────────────────────────────────────
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "WHAT HAPPENS WHEN ACTIVATED",
                        color         = SosColors.textHint,
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }

                SosInfoCard(
                    icon      = Icons.Default.Message,
                    iconColor = SosColors.sosRed,
                    iconBg    = SosColors.sosRedLight,
                    title     = "Auto SMS",
                    body      = "Emergency SMS with location sent to ${contacts.size} saved contact(s)."
                )
                SosInfoCard(
                    icon      = Icons.Default.Bluetooth,
                    iconColor = SosColors.cyanIcon,
                    iconBg    = SosColors.cyanBg,
                    title     = "BLE Broadcast",
                    body      = "SOS signal broadcast to nearby Namastays users within ~80 meters."
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Nearby alert row ──────────────────────────────────────────────────────────
@Composable
fun SosNearbyAlertRow(alert: SosAlert, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SosColors.surface)
            .border(0.5.dp, SosColors.divider, RoundedCornerShape(10.dp))
            .clickable {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(alert.getMapsUrl())
                ).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(intent)
            }
            .padding(10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint     = SosColors.sosRed,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                alert.deviceName,
                color      = SosColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                fontFamily = PlusJakartaSans
            )
            Text(
                "${alert.getDistanceText()} · ${alert.getFormattedTime()}",
                color      = SosColors.textHint,
                fontSize   = 11.sp,
                fontFamily = PlusJakartaSans
            )
        }
        Text(
            "Open Maps",
            color      = SosColors.blue,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSans
        )
    }
}

// ── Warning banner ────────────────────────────────────────────────────────────
@Composable
fun SosWarningBanner(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    text   : String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SosColors.warnAmberBg)
            .border(0.5.dp, SosColors.warnAmberBdr, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = SosColors.warnAmber, modifier = Modifier.size(16.dp))
        Text(text, color = SosColors.warnAmberText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
    }
}

// ── Quick action button ───────────────────────────────────────────────────────
@Composable
fun SosQuickButton(
    modifier  : Modifier = Modifier,
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    label     : String,
    iconColor : Color,
    iconBg    : Color,
    onClick   : () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SosColors.surface)
            .border(0.5.dp, SosColors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color      = SosColors.textPrimary,
            fontSize   = 11.sp,
            textAlign  = TextAlign.Center,
            fontFamily = PlusJakartaSans
        )
    }
}

// ── SOS info card ─────────────────────────────────────────────────────────────
@Composable
fun SosInfoCard(
    icon      : androidx.compose.ui.graphics.vector.ImageVector,
    iconColor : Color,
    iconBg    : Color,
    title     : String,
    body      : String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SosColors.surface)
            .border(0.5.dp, SosColors.divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                color      = SosColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                fontFamily = PlusJakartaSans
            )
            Text(
                body,
                color      = SosColors.textSecondary,
                fontSize   = 12.sp,
                lineHeight = 17.sp,
                fontFamily = PlusJakartaSans
            )
        }
    }
}

// ── SOS trigger (unchanged) ───────────────────────────────────────────────────
fun triggerSOS(
    context  : Context,
    contacts : List<EmergencyContactEntity>,
    onResult : (Boolean, String) -> Unit
) {
    SosManager.sendSosMessages(context, contacts, onResult)
}