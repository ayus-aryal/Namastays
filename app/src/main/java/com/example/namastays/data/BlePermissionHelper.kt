package com.example.namastays.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BlePermissionHelper {

    // Check if device supports BLE at all
    fun isBleSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    // Check if Bluetooth is enabled
    fun isBluetoothEnabled(context: Context): Boolean {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter?.isEnabled == true
    }

    // Get the list of permissions we need to request based on Android version
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            // Android 11 and below
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    // Check if all required BLE permissions are granted
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    // Check specifically if we can advertise (send SOS)
    fun canAdvertise(context: Context): Boolean {
        if (!isBleSupported(context)) return false
        if (!isBluetoothEnabled(context)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Check specifically if we can scan (receive SOS)
    fun canScan(context: Context): Boolean {
        if (!isBleSupported(context)) return false
        if (!isBluetoothEnabled(context)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Human readable status for UI
    fun getStatusMessage(context: Context): String {
        if (!isBleSupported(context)) return "BLE not supported on this device"
        if (!isBluetoothEnabled(context)) return "Bluetooth is turned off"
        if (!hasAllPermissions(context)) return "BLE permissions not granted"
        return "BLE ready"
    }
}