package com.example.namastays.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object SosManager {

    // FIX S2 — was a plain var Boolean; AtomicBoolean is safe across threads.
    private val _isSosActive = AtomicBoolean(false)
    val isSosActive: Boolean get() = _isSosActive.get()

    // FIX S3 — was a plain var List; AtomicReference gives a safe
    // happens-before between the Main-thread write and the IO-thread read.
    private val pendingContactsRef = AtomicReference<List<EmergencyContactEntity>>(emptyList())

    // Tracks the in-flight location CancellationTokenSource so cancelSos()
    // can abort a running getCurrentLocation call. FIX S4.
    private val locationCtsRef = AtomicReference<CancellationTokenSource?>(null)

    fun setPendingContacts(contacts: List<EmergencyContactEntity>) {
        // FIX S3 — AtomicReference.set() provides full happens-before.
        pendingContactsRef.set(contacts)
    }

    fun cancelSos(context: Context) {
        _isSosActive.set(false)
        // FIX S4 — cancel any in-flight location request.
        locationCtsRef.getAndSet(null)?.cancel()
        BleManager.stopAdvertising()
        Log.d("SOS_BLE", "SOS cancelled, BLE advertising stopped")
    }

    @SuppressLint("MissingPermission")
    fun sendSosMessages(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        // FIX S1 — always use applicationContext to prevent an Activity leak
        // if a caller ever passes an Activity context.
        val appContext = context.applicationContext

        _isSosActive.set(true)

        val effectiveContacts = contacts.ifEmpty { pendingContactsRef.get() }

        // FIX S4 — create a CancellationTokenSource so cancelSos() can abort
        // a HIGH_ACCURACY location request that is still in flight.
        val cts = CancellationTokenSource()
        locationCtsRef.set(cts)

        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { location ->
                // Clear the CTS reference — this request is done.
                locationCtsRef.compareAndSet(cts, null)

                val locationText = if (location != null)
                    "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                else "Location unavailable"

                sendSmsToAll(appContext, effectiveContacts, locationText, onResult)

                if (BlePermissionHelper.canAdvertise(appContext)) {
                    BleManager.startAdvertising(
                        context   = appContext,
                        latitude  = location?.latitude ?: 0.0,
                        longitude = location?.longitude ?: 0.0
                    ) { _, bleMessage -> Log.d("SOS_BLE", bleMessage) }
                }
            }
            .addOnFailureListener {
                locationCtsRef.compareAndSet(cts, null)

                // Only send if SOS is still active — cancelSos() may have been
                // called while the location request was in flight.
                if (!_isSosActive.get()) return@addOnFailureListener

                sendSmsToAll(appContext, effectiveContacts, "Location unavailable", onResult)
                if (BlePermissionHelper.canAdvertise(appContext)) {
                    BleManager.startAdvertising(appContext, 0.0, 0.0) { _, _ -> }
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
        val backgroundSuccess = tryBackgroundSms(context, contacts, message)

        if (backgroundSuccess) {
            onResult(true, "SOS sent to ${contacts.size} contact(s)")
        } else {
            // FIX S6 — distinguish between "sent" and "SMS app opened".
            // The caller (SosForegroundService) uses onResult(false, ...) to
            // transition to SosState.Failed if background SMS fails AND the
            // intent fallback is the only option. Changed to false so the UI
            // accurately reflects that the user must still tap Send.
            Log.w("SOS_DEBUG", "Background SMS failed, falling back to SMS Intent")
            openSmsIntent(context, contacts, message)
            onResult(false, "SMS app opened — tap Send to alert your contacts")
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
            Log.w("SOS_DEBUG", "SEND_SMS not granted, skipping background send")
            return false
        }

        return try {
            val smsManager = getSmsManager(context)
            contacts.forEach { contact ->
                val normalized = if (contact.phone.startsWith("+")) contact.phone
                else "+977${contact.phone}"
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(normalized, null, parts, null, null)
                Log.d("SOS_DEBUG", "Background SMS dispatched to $normalized")
            }
            true
        } catch (e: Exception) {
            Log.e("SOS_DEBUG", "Background SMS exception: ${e.message}")
            false
        }
    }

    private fun openSmsIntent(
        context: Context,
        contacts: List<EmergencyContactEntity>,
        message: String
    ) {
        val numbers = contacts.joinToString(";") { contact ->
            if (contact.phone.startsWith("+")) contact.phone else "+977${contact.phone}"
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data  = Uri.parse("smsto:$numbers")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        Log.d("SOS_DEBUG", "Opening SMS app for numbers: $numbers")
        context.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    private fun getSmsManager(context: Context): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            Log.d("SOS_DEBUG", "Using subscriptionId: $subId")
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }
}