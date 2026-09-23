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
import com.example.namastays.ui.theme.PlusJakartaSans

// ─── Palette ──────────────────────────────────────────────────────────────────
// Private to this file — colours are specific to the permission sheet UI.
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

/**
 * Bottom sheet that walks the user through granting every permission and
 * enabling every service required by the SOS feature.
 *
 * The flow has two distinct phases:
 *
 * **Phase 1 — Permissions:** Shown until all four runtime permissions
 * (SMS, Location, Bluetooth, Nearby Devices) are granted. If the user
 * denies after a first attempt, the sheet switches to a "settings path"
 * that deep-links to the app's system settings page.
 *
 * **Phase 2 — Services:** Shown once permissions are granted. Prompts the
 * user to enable Location and Bluetooth if either is currently off, and
 * optionally prompts for Do Not Disturb bypass access (CHANGE: new, not
 * required for navigation — see [SosPermissionStatus.allGranted]).
 *
 * Navigation to [SOSScreen] happens automatically via [onAllGranted] once
 * [SosPermissionStatus.allGranted] becomes true AND the user has taken at
 * least one action (the `userAttemptedGrant` guard prevents auto-navigation
 * on initial composition if permissions happen to already be granted — that
 * case is handled upstream in [SafetyHomeScreen] and never shows this sheet).
 *
 * @param permissionStatus current snapshot of permission + service state,
 *   updated by calling [onRefresh].
 * @param onAllGranted called when all permissions and services are confirmed
 *   ready; the caller should navigate to [SOSScreen].
 * @param onDismiss called when the user taps Cancel or dismisses the sheet.
 * @param onRefresh called to re-read current permission state from the system;
 *   typically delegates to [SafetyViewModel.refreshPermissions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosPermissionSheet(
    permissionStatus: SosPermissionStatus,
    onAllGranted    : () -> Unit,
    onDismiss       : () -> Unit,
    onRefresh       : () -> Unit,
) {
    val context = LocalContext.current

    // Tracks whether the user has taken any action this sheet session.
    // Prevents auto-navigation to SOS on first composition if permissions
    // were somehow already granted before the sheet opened.
    var userAttemptedGrant  by remember { mutableStateOf(false) }
    // True if at least one permission was denied after a launch attempt.
    var anyDeniedAfterGrant by remember { mutableStateOf(false) }
    // Switches to the Settings deep-link path after a denial.
    var showSettingsPath    by remember { mutableStateOf(false) }

    // Standard runtime permission launcher.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        userAttemptedGrant  = true
        anyDeniedAfterGrant = results.any { (_, granted) -> !granted }
        showSettingsPath    = anyDeniedAfterGrant
        onRefresh()
    }

    // System BT enable dialog launcher (ACTION_REQUEST_ENABLE).
    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check after user dismisses the BT dialog — they may have
        // enabled it, or they may have dismissed without enabling.
        onRefresh()
    }

    // CHANGE: launcher for the DND (Do Not Disturb) policy access settings
    // screen. There's no result code returned by the system for this intent,
    // so we simply re-check permission state via onRefresh() on return.
    val dndSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onRefresh()
    }

    // Auto-navigate once everything is ready, but only after the user has
    // taken an action — avoids racing with the initial permission refresh.
    LaunchedEffect(permissionStatus, userAttemptedGrant) {
        if (userAttemptedGrant && permissionStatus.allGranted) {
            onAllGranted()
        }
    }

    // Derived booleans for cleaner branch logic below.
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

            // ── Title row ──────────────────────────────────────────────────────
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

            // ── Phase 1: Permission rows ───────────────────────────────────────
            // Shown until all runtime permissions are granted.
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

            // ── Phase 2: Service state rows ────────────────────────────────────
            // Shown once all permissions are granted — checks Location + BT on/off,
            // plus (CHANGE) optional DND bypass access.
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
                    // CHANGE: optional DND-bypass status row. Purely
                    // informational here — the CTA to grant it lives further
                    // down in the action buttons section. Not required for
                    // allGranted, so the sheet never blocks on this row.
                    HorizontalDivider(color = SheetBorder, thickness = 0.5.dp)
                    ServiceRow(
                        label   = "Bypass Do Not Disturb",
                        detail  = "Optional — lets SOS alerts vibrate even in DND",
                        icon    = Icons.Outlined.NotificationsActive,
                        enabled = permissionStatus.hasNotificationPolicyAccess,
                    )
                }
            }

            // ── Contextual warning rows ────────────────────────────────────────
            if (userAttemptedGrant && anyDeniedAfterGrant) {
                ServiceWarningRow(
                    icon = Icons.Outlined.Info,
                    text = "Some permissions were denied. You may need to open Settings and grant them manually.",
                )
            }
            if (locationOff) {
                ServiceWarningRow(
                    icon = Icons.Outlined.LocationOff,
                    text = "Location is turned off. Open Settings → Location and enable it.",
                )
            }
            if (bluetoothOff) {
                ServiceWarningRow(
                    icon = Icons.Outlined.BluetoothDisabled,
                    text = "Bluetooth is turned off. Tap 'Enable Bluetooth' below to turn it on.",
                )
            }

            // ── Action buttons ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Case A: permissions not yet granted
                if (!allPermissionsGranted) {
                    if (showSettingsPath) {
                        // After a denial — deep-link to system app settings.
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

                // Case B: permissions granted but a service is off
                if (allPermissionsGranted &&
                    (!permissionStatus.isLocationEnabled || !permissionStatus.isBluetoothEnabled)) {

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
                            // Use grey when Bluetooth is also off so the
                            // primary red CTA always targets Bluetooth first.
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

                    SecondaryButton(label = "I've enabled them — check again") {
                        userAttemptedGrant = true
                        onRefresh()
                    }
                }

                // CHANGE: optional DND-access CTA. Deliberately its own `if`
                // block (independent of Case B) since DND bypass is not part
                // of Location/Bluetooth service state and not required for
                // allGranted — shown whenever permissions are granted but
                // DND access hasn't been given yet.
                if (allPermissionsGranted && !permissionStatus.hasNotificationPolicyAccess) {
                    SecondaryButton(label = "Allow SOS to bypass Do Not Disturb") {
                        userAttemptedGrant = true
                        dndSettingsLauncher.launch(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                    }
                }

                // Dismiss / cancel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Cancel",
                        fontFamily = PlusJakartaSans,
                        fontSize   = 13.sp,
                        color      = SheetHint
                    )
                }
            }
        }
    }
}

// ─── Permission Row ───────────────────────────────────────────────────────────

/**
 * A single row in the permission checklist (Phase 1).
 * Shows granted/not-granted state visually via icon and tick/circle.
 */
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
            Icon(
                icon, null,
                tint     = if (granted) SheetGreen else SheetGray,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                type.label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = SheetText
            )
            Text(
                type.rationale,
                fontFamily = PlusJakartaSans,
                fontSize   = 11.sp,
                color      = SheetHint,
                lineHeight = 15.sp
            )
        }
        Icon(
            imageVector        = if (granted) Icons.Outlined.CheckCircle
            else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint               = if (granted) SheetGreen else SheetHint,
            modifier           = Modifier.size(20.dp),
        )
    }
}

// ─── Service State Row ────────────────────────────────────────────────────────

/**
 * A single row in the service state checklist (Phase 2).
 * Shows whether Location, Bluetooth, or DND access is currently on/off.
 */
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
            Icon(
                icon, null,
                tint     = if (enabled) SheetGreen else SheetAmber,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 14.sp,
                color      = SheetText
            )
            Text(
                text       = if (enabled) "On" else detail,
                fontFamily = PlusJakartaSans,
                fontSize   = 11.sp,
                color      = if (enabled) SheetGreen else SheetAmber,
                lineHeight = 15.sp,
            )
        }
        Icon(
            imageVector        = if (enabled) Icons.Outlined.CheckCircle
            else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint               = if (enabled) SheetGreen else SheetAmber,
            modifier           = Modifier.size(20.dp),
        )
    }
}

// ─── Warning Row ─────────────────────────────────────────────────────────────

/** An amber inline warning strip shown for contextual issues (denied, service off). */
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
        Text(
            text,
            fontFamily = PlusJakartaSans,
            fontSize   = 12.sp,
            color      = Color(0xFF92400E),
            lineHeight = 17.sp
        )
    }
}

// ─── Primary Action Button ────────────────────────────────────────────────────

/** A full-width solid primary CTA button. */
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
            Text(
                label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = Color.White
            )
        }
    }
}

// ─── Secondary Action Button ──────────────────────────────────────────────────

/** A full-width outlined secondary button (grey fill, no colour emphasis). */
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
        Text(
            label,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 14.sp,
            color      = SheetText
        )
    }
}