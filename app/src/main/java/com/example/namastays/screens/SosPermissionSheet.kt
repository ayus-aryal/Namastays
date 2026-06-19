package com.example.namastays.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namastays.data.SosPermissionHelper
import com.example.namastays.data.SosPermissionStatus
import com.example.namastays.data.SosPermissionType

private val SheetRed     = Color(0xFFDC2626)
private val SheetRedBg   = Color(0xFFFEE2E2)
private val SheetGreen   = Color(0xFF16A34A)
private val SheetGreenBg = Color(0xFFDCFCE7)
private val SheetAmber   = Color(0xFFD97706)
private val SheetAmberBg = Color(0xFFFFFBEB)
private val SheetGray    = Color(0xFF6B7280)
private val SheetGrayBg  = Color(0xFFF3F4F6)
private val SheetText    = Color(0xFF111827)
private val SheetHint    = Color(0xFF9CA3AF)
private val SheetBorder  = Color(0xFFE5E7EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosPermissionSheet(
    permissionStatus: SosPermissionStatus,
    onAllGranted    : () -> Unit,
    onDismiss       : () -> Unit,
    onRefresh       : () -> Unit,
) {
    val context = LocalContext.current

    var userAttemptedGrant  by remember { mutableStateOf(false) }
    var anyDeniedAfterGrant by remember { mutableStateOf(false) }
    var showSettingsPath    by remember { mutableStateOf(false) }

    // Launcher for requesting permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        userAttemptedGrant  = true
        anyDeniedAfterGrant = results.any { (_, granted) -> !granted }
        showSettingsPath    = anyDeniedAfterGrant
        onRefresh()
    }

    // Launcher for enabling Bluetooth (shows system BT enable dialog)
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After user dismisses BT enable dialog, re-check everything
        onRefresh()
    }

    // Only auto-navigate after user acted AND everything is truly ready
    LaunchedEffect(permissionStatus, userAttemptedGrant) {
        if (userAttemptedGrant && permissionStatus.allGranted) {
            onAllGranted()
        }
    }

    // Derive what phase we're in
    val allPermissionsGranted = permissionStatus.allPermissionsGranted
    val locationOff           = allPermissionsGranted && !permissionStatus.isLocationEnabled
    val bluetoothOff          = allPermissionsGranted && !permissionStatus.isBluetoothEnabled

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = Color.White,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Title ─────────────────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SheetRedBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Warning, null, tint = SheetRed, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text(
                        text       = if (allPermissionsGranted) "Enable Required Services"
                        else "SOS Requires Access",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                        color      = SheetText,
                    )
                    Text(
                        text       = if (allPermissionsGranted) "Turn on Location and Bluetooth to proceed"
                        else "Grant the following to enable SOS",
                        fontFamily = PlusJakartaSans,
                        fontSize   = 13.sp,
                        color      = SheetHint,
                    )
                }
            }

            // ── Phase 1: Permission rows (shown until all permissions granted) ─
            if (!allPermissionsGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, SheetBorder, RoundedCornerShape(16.dp)),
                ) {
                    PermissionRow(
                        type    = SosPermissionType.SMS,
                        granted = permissionStatus.hasSmsPermission,
                        icon    = Icons.Outlined.Sms,
                    )
                    HorizontalDivider(color = SheetBorder, thickness = 0.5.dp)
                    PermissionRow(
                        type    = SosPermissionType.LOCATION,
                        granted = permissionStatus.hasLocationPermission,
                        icon    = Icons.Outlined.LocationOn,
                    )
                    HorizontalDivider(color = SheetBorder, thickness = 0.5.dp)
                    PermissionRow(
                        type    = SosPermissionType.BLUETOOTH,
                        granted = permissionStatus.hasBluetoothPermission,
                        icon    = Icons.Outlined.Bluetooth,
                    )
                    HorizontalDivider(color = SheetBorder, thickness = 0.5.dp)
                    PermissionRow(
                        type    = SosPermissionType.NEARBY_DEVICES,
                        granted = permissionStatus.hasNearbyDevicesPermission,
                        icon    = Icons.Outlined.DevicesOther,
                    )
                }
            }

            // ── Phase 2: Service state rows (shown once permissions are granted) ─
            if (allPermissionsGranted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, SheetBorder, RoundedCornerShape(16.dp)),
                ) {
                    ServiceRow(
                        label   = "Location",
                        detail  = "GPS must be turned on",
                        icon    = Icons.Outlined.LocationOn,
                        enabled = permissionStatus.isLocationEnabled,
                    )
                    HorizontalDivider(color = SheetBorder, thickness = 0.5.dp)
                    ServiceRow(
                        label   = "Bluetooth",
                        detail  = "Bluetooth must be turned on",
                        icon    = Icons.Outlined.Bluetooth,
                        enabled = permissionStatus.isBluetoothEnabled,
                    )
                }
            }

            // ── Denied after grant warning ────────────────────────────────────
            if (userAttemptedGrant && anyDeniedAfterGrant) {
                ServiceWarningRow(
                    icon = Icons.Outlined.Info,
                    text = "Some permissions were denied. You may need to open Settings and grant them manually.",
                )
            }

            // ── Location off warning + shortcut ───────────────────────────────
            if (locationOff) {
                ServiceWarningRow(
                    icon = Icons.Outlined.LocationOff,
                    text = "Location is turned off. Open Settings → Location and enable it.",
                )
            }

            // ── Bluetooth off warning + enable prompt ─────────────────────────
            if (bluetoothOff) {
                ServiceWarningRow(
                    icon = Icons.Outlined.BluetoothDisabled,
                    text = "Bluetooth is turned off. Tap 'Enable Bluetooth' below to turn it on.",
                )
            }

            // ── Action buttons ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ── Case A: Permissions not yet granted ───────────────────────
                if (!allPermissionsGranted) {
                    if (showSettingsPath) {
                        // Permanently denied — send to app settings
                        ActionButton(
                            label  = "Open App Settings",
                            icon   = Icons.Outlined.OpenInNew,
                            color  = SheetText,
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data  = Uri.fromParts("package", context.packageName, null)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                        )
                        SecondaryButton(label = "Try Granting Again") {
                            showSettingsPath    = false
                            anyDeniedAfterGrant = false
                            userAttemptedGrant  = false
                            val toRequest = SosPermissionHelper.getPermissionsToRequest(context)
                            if (toRequest.isNotEmpty()) permissionLauncher.launch(toRequest)
                        }
                    } else {
                        // Normal grant flow
                        ActionButton(
                            label  = "Grant Permissions",
                            icon   = Icons.Outlined.Shield,
                            color  = SheetRed,
                            onClick = {
                                val toRequest = SosPermissionHelper.getPermissionsToRequest(context)
                                if (toRequest.isEmpty()) {
                                    userAttemptedGrant = true
                                    onRefresh()
                                } else {
                                    permissionLauncher.launch(toRequest)
                                }
                            }
                        )
                    }
                }

                // ── Case B: Permissions granted, but services off ─────────────
                if (allPermissionsGranted && (!permissionStatus.isLocationEnabled || !permissionStatus.isBluetoothEnabled)) {

                    if (bluetoothOff) {
                        ActionButton(
                            label  = "Enable Bluetooth",
                            icon   = Icons.Outlined.Bluetooth,
                            color  = SheetRed,
                            onClick = {
                                userAttemptedGrant = true
                                enableBtLauncher.launch(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                )
                            }
                        )
                    }

                    if (locationOff) {
                        ActionButton(
                            label  = "Open Location Settings",
                            icon   = Icons.Outlined.LocationOn,
                            color  = if (bluetoothOff) SheetGray else SheetRed,
                            onClick = {
                                userAttemptedGrant = true
                                context.startActivity(
                                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                )
                            }
                        )
                    }

                    // Re-check button after user manually enables services
                    SecondaryButton(label = "I've enabled them — check again") {
                        userAttemptedGrant = true
                        onRefresh()
                    }
                }

                // Dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Cancel", fontFamily = PlusJakartaSans, fontSize = 13.sp, color = SheetHint)
                }
            }
        }
    }
}

// ─── Permission row ───────────────────────────────────────────────────────────
@Composable
private fun PermissionRow(
    type   : SosPermissionType,
    granted: Boolean,
    icon   : ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (granted) SheetGreenBg else SheetGrayBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (granted) SheetGreen else SheetGray, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(type.label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SheetText)
            Text(type.rationale, fontFamily = PlusJakartaSans, fontSize = 11.sp, color = SheetHint, lineHeight = 15.sp)
        }
        Icon(
            imageVector        = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint               = if (granted) SheetGreen else SheetHint,
            modifier           = Modifier.size(20.dp),
        )
    }
}

// ─── Service state row ────────────────────────────────────────────────────────
@Composable
private fun ServiceRow(
    label  : String,
    detail : String,
    icon   : ImageVector,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (enabled) SheetGreenBg else SheetAmberBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (enabled) SheetGreen else SheetAmber, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SheetText)
            Text(
                text       = if (enabled) "On" else detail,
                fontFamily = PlusJakartaSans,
                fontSize   = 11.sp,
                color      = if (enabled) SheetGreen else SheetAmber,
                lineHeight = 15.sp,
            )
        }
        Icon(
            imageVector        = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint               = if (enabled) SheetGreen else SheetAmber,
            modifier           = Modifier.size(20.dp),
        )
    }
}

// ─── Warning row ─────────────────────────────────────────────────────────────
@Composable
private fun ServiceWarningRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetAmberBg)
            .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Icon(icon, null, tint = SheetAmber, modifier = Modifier.size(16.dp))
        Text(text, fontFamily = PlusJakartaSans, fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 17.sp)
    }
}

// ─── Primary action button ────────────────────────────────────────────────────
@Composable
private fun ActionButton(
    label  : String,
    icon   : ImageVector,
    color  : Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
        }
    }
}

// ─── Secondary action button ──────────────────────────────────────────────────
@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SheetGrayBg)
            .border(1.dp, SheetBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = SheetText)
    }
}