package com.example.namastays.data

// ─── SOS State machine ────────────────────────────────────────────────────────
sealed class SosState {
    object Idle : SosState()
    data class Counting(val secondsLeft: Int) : SosState()
    data class Active(val sentAt: Long) : SosState()
    data class Failed(val reason: String) : SosState()
}

// ─── Permission + Service status ─────────────────────────────────────────────
data class SosPermissionStatus(
    // Permissions granted by user
    val hasSmsPermission          : Boolean = false,
    val hasLocationPermission     : Boolean = false,
    val hasBluetoothPermission    : Boolean = false,
    val hasNearbyDevicesPermission: Boolean = false,

    // Hardware/service actually ON
    val isLocationEnabled  : Boolean = false,
    val isBluetoothEnabled : Boolean = false,
) {
    // All permissions granted
    val allPermissionsGranted: Boolean
        get() = hasSmsPermission &&
                hasLocationPermission &&
                hasBluetoothPermission &&
                hasNearbyDevicesPermission

    // Everything ready — permissions + services on
    val allGranted: Boolean
        get() = allPermissionsGranted &&
                isLocationEnabled &&
                isBluetoothEnabled

    val missingPermissions: List<SosPermissionType>
        get() = buildList {
            if (!hasSmsPermission)           add(SosPermissionType.SMS)
            if (!hasLocationPermission)      add(SosPermissionType.LOCATION)
            if (!hasBluetoothPermission)     add(SosPermissionType.BLUETOOTH)
            if (!hasNearbyDevicesPermission) add(SosPermissionType.NEARBY_DEVICES)
        }
}

enum class SosPermissionType(val label: String, val rationale: String) {
    SMS(
        label     = "Send SMS",
        rationale = "Required to send your emergency location to saved contacts.",
    ),
    LOCATION(
        label     = "Location",
        rationale = "Required to include your GPS coordinates in the SOS message.",
    ),
    BLUETOOTH(
        label     = "Bluetooth",
        rationale = "Required to broadcast SOS signal to nearby Namastays users.",
    ),
    NEARBY_DEVICES(
        label     = "Nearby Devices",
        rationale = "Required on Android 12+ to scan and advertise via Bluetooth.",
    ),
}