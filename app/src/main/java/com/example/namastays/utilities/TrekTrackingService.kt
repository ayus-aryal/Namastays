package com.example.namastays.utilities

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.namastays.NamastaysApp
import com.example.namastays.R
import com.example.namastays.dto.TrekState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Foreground service that owns the TrekEngine lifecycle.
 *
 * ── Responsibilities ─────────────────────────────────────────────────────────
 *  1. Start TrekEngine on onCreate(); stop it on onDestroy().
 *  2. Maintain a live foreground notification showing altitude + zone.
 *  3. Expose a "Stop Trek" notification action via [ACTION_STOP] broadcast.
 *  4. Handle START_STICKY restart: on re-delivery of a null intent (system
 *     restart), close any orphaned session left open by the killed process,
 *     then start a fresh session — engine.start() already calls
 *     closeOrphanedSessions() internally, so no extra work here.
 *
 * ── Session ownership ────────────────────────────────────────────────────────
 *  The session lifecycle is owned entirely by TrekEngine:
 *    • engine.start()  → inserts a TrekSession row (isActive = true)
 *    • engine.stop()   → closes that row (isActive = false, writes final stats)
 *  This service never touches Room directly. It only drives the engine.
 *
 * ── Notification updates ─────────────────────────────────────────────────────
 *  We observe only fields that appear in the notification
 *  (altitude, altitudeZone, inBatterySaver, distanceKm) via
 *  distinctUntilChanged so we never spam NotificationManager on every GPS fix.
 *  Updates are additionally throttled to [NOTIFICATION_THROTTLE_MS].
 *
 * ── Stop action ──────────────────────────────────────────────────────────────
 *  The "Stop Trek" button in the notification sends [ACTION_STOP] as a
 *  broadcast. A local BroadcastReceiver catches it and calls stopSelf(),
 *  which triggers onDestroy() → engine.stop() → session closed cleanly.
 *  Using a local receiver (not exported) avoids the security risk of an
 *  exported receiver or a PendingIntent that could be replayed.
 */
class TrekTrackingService : Service() {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "TrekTrackingService"

        private const val CHANNEL_ID             = "trek_tracking"
        private const val NOTIFICATION_ID        = 1001
        private const val NOTIFICATION_THROTTLE_MS = 5_000L

        /**
         * Broadcast action for the "Stop Trek" notification button.
         * Scoped to this package so it cannot be triggered by other apps.
         */
        const val ACTION_STOP = "com.example.namastays.TREK_STOP"

        /**
         * PendingIntent request code for the Stop action.
         * Must be unique across all PendingIntents in the app.
         */
        private const val PI_STOP_REQUEST_CODE = 9001
    }

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val engine by lazy { (application as NamastaysApp).trekEngine }

    // ── Coroutine scope ───────────────────────────────────────────────────────

    /**
     * Scope lives exactly as long as the service.
     * SupervisorJob: one failed child does not cancel notification updates.
     * Dispatchers.Main: notification API must be called on main thread.
     */
    private var serviceScope: CoroutineScope? = null

    // ── Notification throttle ────────────────────────────────────────────────

    private var lastNotificationUpdateMs = 0L

    // ── Stop broadcast receiver ───────────────────────────────────────────────

    /**
     * Local receiver for ACTION_STOP. Registered/unregistered in
     * onCreate/onDestroy so it lives exactly as long as the service.
     *
     * Why local and not in the manifest?
     *   An exported manifest receiver can be triggered by any app.
     *   A local receiver is only visible within this process — safer.
     */
    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_STOP) {
                Log.d(TAG, "Stop action received from notification")
                // stopSelf() triggers onDestroy() → engine.stop() → session close
                stopSelf()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        // Create the notification channel before startForeground() —
        // required on API 26+, safe no-op on lower APIs (already guarded).
        createNotificationChannel()

        // Post an immediate placeholder notification. startForeground() must
        // be called within 5 seconds of onCreate() to avoid ANR/ForegroundServiceDidNotStartInTimeException.
        startForeground(NOTIFICATION_ID, buildNotification(TrekState()))

        // Register our local stop receiver before starting the engine
        // so the button is live the moment the notification appears.
        // ContextCompat.registerReceiver handles the API-level branching
        // internally and always passes an explicit exported/not-exported
        // flag — this broadcast is internal to our own process, so
        // RECEIVER_NOT_EXPORTED is correct.
        ContextCompat.registerReceiver(
            this,
            stopReceiver,
            IntentFilter(ACTION_STOP),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Log.d(TAG, "onCreate — starting engine")

        // Start engine. engine.start() is non-blocking (fire-and-forget
        // coroutine inside TrekEngine that acquires startStopMutex).
        try {
            engine.start()
        } catch (e: Exception) {
            // engine.start() is a suspend-free launcher — this catch covers
            // unexpected synchronous failures (e.g. lazy DB init crash).
            Log.e(TAG, "engine.start() threw synchronously: ${e.message}")
            stopSelf()
            return
        }

        // Start service scope and observe state for notification updates.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        serviceScope = scope

        scope.launch {
            engine.state
                .map { s ->
                    // Notify only when these visible fields change.
                    // Data class equality handles the comparison.
                    NotificationFields(
                        altitudeInt    = s.altitude.toInt(),
                        zoneName       = s.altitudeZone.label,
                        distanceKm     = s.distanceKm,
                        inBatterySaver = s.inBatterySaver
                    )
                }
                .distinctUntilChanged()
                .collect {
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdateMs >= NOTIFICATION_THROTTLE_MS) {
                        lastNotificationUpdateMs = now
                        updateNotification(engine.state.value)
                    }
                }
        }
    }

    /**
     * Called by the system when startForegroundService() is invoked.
     *
     * START_STICKY semantics:
     *   If the system kills this service (OOM), it will be restarted with
     *   a null intent. engine.start() handles this gracefully — it calls
     *   closeOrphanedSessions() before opening a new one, ensuring the
     *   previous dangling isActive=1 row is closed with a best-effort
     *   endMs before the new session begins.
     *
     * Re-delivery with non-null intent:
     *   No extra handling needed; engine.start() is idempotent (guarded by
     *   startStopMutex — a second call while already running is a no-op).
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ACTION_STOP arriving here means the PendingIntent used getService
        // instead of getBroadcast. That path is unused in our implementation
        // (we use a broadcast receiver), but guard it defensively.
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Stop action via onStartCommand — stopping service")
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy — stopping engine")

        // Unregister receiver first so no further stop signals can arrive
        // during teardown.
        runCatching { unregisterReceiver(stopReceiver) }
            .onFailure { Log.w(TAG, "stopReceiver already unregistered: ${it.message}") }

        // Cancel notification observer.
        serviceScope?.cancel()
        serviceScope = null

        // engine.stop() is a fire-and-forget coroutine that acquires
        // startStopMutex, snapshots accumulators, and writes the close
        // record to Room within SESSION_CLOSE_TIMEOUT_MS. The engine's
        // internal CoroutineScope is independent of the service scope,
        // so the close write will complete even after onDestroy() returns.
        engine.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trek Tracking",
                // IMPORTANCE_LOW: no sound, no heads-up; shows in shade with icon.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description  = "Live altitude and trek stats"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun updateNotification(state: TrekState) {
        // Guard: if service is being destroyed, skip the update.
        if (serviceScope == null) return
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: TrekState): Notification {
        // ── Content lines ──────────────────────────────────────────────────
        val altText = if (state.altitude > 0.0)
            "${state.altitude.toInt()} m  ·  ${state.altitudeZone.label}"
        else
            "Acquiring GPS fix…"

        val subText = buildString {
            if (state.distanceKm > 0.0) append("%.1f km".format(state.distanceKm))
            if (state.inBatterySaver) {
                if (isNotEmpty()) append("  ·  ")
                append("Battery saver")
            }
        }.ifEmpty { "Trek Mode active" }

        // ── Tap intent: opens app to the Trek Mode screen ─────────────────
        val tapIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else
            PendingIntent.FLAG_UPDATE_CURRENT
        val tapPending = PendingIntent.getActivity(this, 0, tapIntent, piFlags)

        // ── Stop action: sends ACTION_STOP broadcast to our local receiver ─
        val stopIntent  = Intent(ACTION_STOP).apply { `package` = packageName }
        val stopPending = PendingIntent.getBroadcast(
            this,
            PI_STOP_REQUEST_CODE,
            stopIntent,
            piFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(altText)
            .setContentText(subText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapPending)
            .addAction(
                // Icon 0 = no icon (avoids needing a dedicated drawable resource).
                // The label is the only thing visible in compact notification view.
                0,
                "Stop Trek",
                stopPending
            )
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }

    // ── Internal data class for distinctUntilChanged ──────────────────────────

    /**
     * Tiny value class so distinctUntilChanged() does structural equality
     * on exactly the fields we render in the notification, nothing more.
     */
    private data class NotificationFields(
        val altitudeInt    : Int,
        val zoneName       : String,
        val distanceKm     : Double,
        val inBatterySaver : Boolean
    )
}