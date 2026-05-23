package com.example.namastays.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer

class SosBroadcastReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SOS_SCAN_RESULT = "com.example.namastays.SOS_SCAN_RESULT"
        private const val TAG = "BLE_SOS_RECEIVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SOS_SCAN_RESULT) return

        val results: List<ScanResult> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java
            ) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT
            ) ?: emptyList()
        }

        Log.d(TAG, "Hardware scan delivery: ${results.size} result(s)")

        results.forEach { result ->
            parseAndHandle(context, result)
        }
    }

    private fun parseAndHandle(context: Context, result: ScanResult) {
        val sosUuid = java.util.UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")

        // ── Debug: log all service UUIDs seen in this packet ─────────────
        val allUuids = result.scanRecord?.serviceUuids
        Log.d(TAG, "Service UUIDs in packet: $allUuids")

        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(sosUuid))

        // ── Debug: log raw bytes or null ──────────────────────────────────
        if (serviceData != null) {
            Log.d(TAG, "Service data size: ${serviceData.size} bytes")
            Log.d(TAG, "Raw bytes (first 5): ${serviceData.take(5).map { it.toInt() and 0xFF }}")
            Log.d(TAG, "Expected bytes: [78, 83, 1, ...]") // N=78, S=83, SOS_TYPE=1
        } else {
            Log.d(TAG, "Service data is NULL for UUID $sosUuid")
            Log.d(TAG, "Full scan record: ${result.scanRecord}")
            return
        }

        // ── Validate Namastays packet ─────────────────────────────────────
        if (serviceData.size < 19) {
            Log.w(TAG, "Packet too small: ${serviceData.size} bytes, need 19")
            return
        }
        if (serviceData[0] != 'N'.code.toByte()) {
            Log.w(TAG, "Byte[0] mismatch: got ${serviceData[0].toInt() and 0xFF}, expected 78 ('N')")
            return
        }
        if (serviceData[1] != 'S'.code.toByte()) {
            Log.w(TAG, "Byte[1] mismatch: got ${serviceData[1].toInt() and 0xFF}, expected 83 ('S')")
            return
        }
        if (serviceData[2] != 0x01.toByte()) {
            Log.w(TAG, "Byte[2] mismatch: got ${serviceData[2].toInt() and 0xFF}, expected 1 (SOS type)")
            return
        }

        // ── Parse payload ─────────────────────────────────────────────────
        val buffer = ByteBuffer.wrap(serviceData)
        buffer.position(3)

        val lat = buffer.float
        val lon = buffer.float
        val timestamp = buffer.long

        val deviceName = result.scanRecord?.deviceName ?: "Unknown Device"
        val rssi = result.rssi
        val estimatedDistance = estimateDistance(rssi)

        Log.d(TAG, "SOS parsed successfully from '$deviceName' lat=$lat lon=$lon distance=~${estimatedDistance}m")

        val alert = SosAlert(
            deviceName = deviceName,
            latitude = lat.toDouble(),
            longitude = lon.toDouble(),
            timestamp = timestamp,
            estimatedDistanceMeters = estimatedDistance,
            rssi = rssi
        )

        SosAlertReceiver.handleAlert(context, alert) { }
    }

    private fun estimateDistance(rssi: Int): Int {
        return when {
            rssi >= -60 -> 5
            rssi >= -70 -> 15
            rssi >= -80 -> 40
            rssi >= -90 -> 70
            else        -> 100
        }
    }
}