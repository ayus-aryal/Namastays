package com.example.namastays.data

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object SosPermissionHelper {

    // ── Full status check ─────────────────────────────────────────────────────

    fun getStatus(context: Context): SosPermissionStatus {
        return SosPermissionStatus(
            // Permissions
            hasSmsPermission           = hasSmsPermission(context),
            hasLocationPermission      = hasLocationPermission(context),
            hasBluetoothPermission     = hasBluetoothHardware(context),
            hasNearbyDevicesPermission = hasNearbyDevicesPermission(context),

            // Services actually on
            isLocationEnabled  = isLocationEnabled(context),
            isBluetoothEnabled = isBluetoothEnabled(context),

            // CHANGE: populate the new DND-access field so the permission
            // sheet can show its state and prompt the user.
            hasNotificationPolicyAccess = hasNotificationPolicyAccess(context),
        )
    }

    // ── Permission checks ─────────────────────────────────────────────────────

    fun hasSmsPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.SEND_SMS)

    fun hasLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasNearbyDevicesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(context, Manifest.permission.BLUETOOTH) &&
                    hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    fun hasBluetoothHardware(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    // CHANGE: DND access is a special access grant, not a runtime permission —
    // there's no system permission dialog for it, only a settings screen.

    /**
     * Whether the user has granted "Do Not Disturb access" to this app.
     * Required for NotificationChannel.setBypassDnd(true) to actually work.
     */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    /**
     * Opens the system settings screen where the user can grant DND access.
     * No runtime permission dialog exists for this — call
     * hasNotificationPolicyAccess() afterward (e.g. onResume/onRefresh) to
     * confirm whether it was actually granted.
     */
    fun requestNotificationPolicyAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    // ── Service/hardware state checks ─────────────────────────────────────────

    fun isBluetoothEnabled(context: Context): Boolean {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return btManager?.adapter?.isEnabled == true
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    // ── What to request ───────────────────────────────────────────────────────

    fun getPermissionsToRequest(context: Context): Array<String> {
        return buildList {
            if (!hasSmsPermission(context)) {
                add(Manifest.permission.SEND_SMS)
            }
            if (!hasLocationPermission(context)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!hasPermission(context, Manifest.permission.BLUETOOTH_SCAN))
                    add(Manifest.permission.BLUETOOTH_SCAN)
                if (!hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE))
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                if (!hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT))
                    add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                if (!hasPermission(context, Manifest.permission.BLUETOOTH))
                    add(Manifest.permission.BLUETOOTH)
                if (!hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN))
                    add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }.toTypedArray()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
}