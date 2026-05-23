package com.example.namastays.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

object SosManager {

    var isSosActive = false
        private set

    fun cancelSos(context: Context) {
        isSosActive = false
        BleManager.stopAdvertising()
        android.util.Log.d("SOS_BLE", "SOS cancelled, BLE advertising stopped")
    }

    @SuppressLint("MissingPermission")
    fun sendSosMessages(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        isSosActive = true

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val locationText = if (location != null)
                    "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                else "Location unavailable"

                // ── SMS ───────────────────────────────────────────────────────
                sendSmsToAll(context, contacts, locationText, onResult)

                // ── BLE broadcast ─────────────────────────────────────────────
                if (BlePermissionHelper.canAdvertise(context)) {
                    BleManager.startAdvertising(
                        context = context,
                        latitude = location?.latitude ?: 0.0,
                        longitude = location?.longitude ?: 0.0
                    ) { _, bleMessage ->
                        android.util.Log.d("SOS_BLE", bleMessage)
                    }
                }
            }
            .addOnFailureListener {
                sendSmsToAll(context, contacts, "Location unavailable", onResult)
                if (BlePermissionHelper.canAdvertise(context)) {
                    BleManager.startAdvertising(context, 0.0, 0.0) { _, _ -> }
                }
            }
    }

    private fun sendSmsToAll(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        locationText: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        if (contacts.isEmpty()) {
            onResult(false, "No emergency contacts saved")
            return
        }

        val message = "SOS EMERGENCY\nI need help!\nLocation: $locationText\nSent via Namastays"

        // Try background SmsManager first
        val backgroundSuccess = tryBackgroundSms(context, contacts, message)

        if (backgroundSuccess) {
            onResult(true, "SOS sent to ${contacts.size} contact(s)")
        } else {
            // Fallback: open SMS app with all contacts pre-filled
            android.util.Log.w("SOS_DEBUG", "Background SMS failed, falling back to SMS Intent")
            openSmsIntent(context, contacts, message)
            onResult(true, "SMS app opened — tap Send to alert your contacts")
        }
    }

    private fun tryBackgroundSms(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        message: String
    ): Boolean {
        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.SEND_SMS
        )
        if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.w("SOS_DEBUG", "SEND_SMS not granted, skipping background send")
            return false
        }

        return try {
            val smsManager = getSmsManager(context)
            contacts.forEach { contact ->
                val normalized = if (contact.phone.startsWith("+")) contact.phone
                else "+977${contact.phone}"
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(normalized, null, parts, null, null)
                android.util.Log.d("SOS_DEBUG", "Background SMS dispatched to $normalized")
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("SOS_DEBUG", "Background SMS exception: ${e.message}")
            false
        }
    }

    private fun openSmsIntent(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        message: String
    ) {
        // Build semicolon-separated number list for multi-recipient SMS
        val numbers = contacts.joinToString(";") { contact ->
            if (contact.phone.startsWith("+")) contact.phone else "+977${contact.phone}"
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$numbers")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        android.util.Log.d("SOS_DEBUG", "Opening SMS app for numbers: $numbers")
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun getSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            android.util.Log.d("SOS_DEBUG", "Using subscriptionId: $subId")
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}