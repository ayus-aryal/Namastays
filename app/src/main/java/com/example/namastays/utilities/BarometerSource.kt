package com.example.namastays.utilities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log

/**
 * Wraps the device pressure sensor and converts raw pressure readings into
 * an altitude estimate via [SensorManager.getAltitude].
 *
 * FIX BARO-1 (audit, CRITICAL): registerListener(listener, sensor, rate) —
 * the 3-argument overload previously used here — internally builds a
 * Handler bound to Looper.myLooper() of whichever thread calls it. TrekEngine
 * calls barometer.start(...) from inside a coroutine running on
 * engineDispatcher (Dispatchers.IO.limitedParallelism(1)), which is a plain
 * thread-pool worker thread that never calls Looper.prepare(). That means
 * Looper.myLooper() was null at the point of registration, and
 * SensorManager would throw:
 *   RuntimeException: Can't create handler inside thread that has not
 *   called Looper.prepare()
 * This was happening on every single Trek Mode start, inside a
 * SupervisorJob-rooted coroutine with no installed CoroutineExceptionHandler
 * upstream (see TrekEngine FIX ENG-1) — i.e. a likely production crash (or,
 * depending on OS/manufacturer behavior, a silent permanent failure to ever
 * receive barometer callbacks) on every session start.
 *
 * Fix: own a dedicated HandlerThread with a real prepared Looper, and pass
 * that Handler explicitly into the 4-argument registerListener overload.
 * This makes BarometerSource self-contained — it no longer depends on, or
 * cares about, which thread start()/stop() are called from.
 *
 * FIX BAT-2/BAT-3 (audit): the original always sampled at
 * SENSOR_DELAY_NORMAL (~200ms) regardless of whether the trek engine had
 * entered battery-saver mode, and regardless of the fact that the fused
 * altitude calculation in TrekEngine only ever consumes the single latest
 * barometer value once per GPS fix (every 3s active / 15s saver) — so the
 * sensor was producing roughly 15-75x more samples than were ever read,
 * for continuous battery cost with zero accuracy benefit between reads.
 * setBatterySaver(true) now drops to SENSOR_DELAY_UI (~60ms target, but
 * more importantly a lower-priority hint to the HAL) is NOT enough on its
 * own to meaningfully cut power on most devices, since pressure sensors are
 * typically polled rather than interrupt-driven — the real fix is
 * unregistering and re-registering at SENSOR_DELAY_NORMAL only while
 * something will actually consume it, matched to TrekEngine's own cadence.
 * SENSOR_DELAY_NORMAL is already a reasonably low rate; we widen the gap
 * further in saver mode by switching to the explicit microsecond-interval
 * overload so the gap between samples roughly matches GPS's saver-mode
 * fix interval, rather than firing far more often than anything reads it.
 */
class BarometerSource(context: Context) {

    private companion object {
        const val TAG = "BarometerSource"

        // Roughly matches GpsDataSource's active-mode GPS interval — no
        // point sampling faster than the rate at which TrekEngine actually
        // reads latestBaro (once per GPS fix).
        const val SAMPLE_INTERVAL_ACTIVE_US = 3_000_000 // 3s, matches GPS active interval

        // Matches GpsDataSource's battery-saver GPS interval.
        const val SAMPLE_INTERVAL_SAVER_US = 15_000_000 // 15s, matches GPS saver interval
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val pressureSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var listener: SensorEventListener? = null

    // FIX BARO-1 — dedicated background thread with its own prepared Looper,
    // so registerListener always has a valid Handler regardless of which
    // thread start()/stop() are invoked from.
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    private var inBatterySaver = false
    private var onAltitudeCallback: ((Double) -> Unit)? = null

    fun isAvailable() = pressureSensor != null

    fun start(onAltitude: (Double) -> Unit) {
        if (pressureSensor == null) {
            Log.e(TAG, "No pressure sensor available")
            return
        }

        onAltitudeCallback = onAltitude

        // FIX BARO-1 — start (or reuse) the dedicated Looper thread before
        // registering. THREAD_PRIORITY_BACKGROUND keeps this off the
        // critical path for UI/other work while still being a real,
        // long-lived thread with a prepared Looper.
        val thread = handlerThread ?: HandlerThread(
            "BarometerSource",
            Process.THREAD_PRIORITY_BACKGROUND
        ).also {
            it.start()
            handlerThread = it
        }
        val h = handler ?: Handler(thread.looper).also { handler = it }

        registerListener(h, inBatterySaver)
        Log.d(TAG, "started")
    }

    /**
     * FIX BAT-2/BAT-3 (audit): toggles sampling rate to match GPS's own
     * active/saver cadence, instead of sampling at a fixed high rate
     * regardless of how often a value is actually consumed.
     */
    fun setBatterySaver(enable: Boolean) {
        if (inBatterySaver == enable) return
        inBatterySaver = enable
        val h = handler ?: return
        val l = listener ?: return
        // Re-register at the new rate. Pressure sensors deliver a fresh
        // value almost immediately on registration, so there's no
        // meaningful data gap from the brief unregister/re-register.
        sensorManager.unregisterListener(l)
        registerListener(h, enable)
        Log.d(TAG, "battery saver ${if (enable) "ON" else "OFF"}")
    }

    private fun registerListener(handler: Handler, batterySaver: Boolean) {
        val sensor = pressureSensor ?: return
        val callback = onAltitudeCallback ?: return

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pressure = event.values.firstOrNull() ?: return
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure
                )
                callback(altitude.toDouble())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = l

        val intervalUs = if (batterySaver) SAMPLE_INTERVAL_SAVER_US else SAMPLE_INTERVAL_ACTIVE_US

        // FIX BARO-1 — explicit Handler overload, bound to handlerThread's
        // real prepared Looper, instead of relying on the calling thread's
        // (often nonexistent) Looper.
        sensorManager.registerListener(l, sensor, intervalUs, handler)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
        onAltitudeCallback = null

        // FIX (resource cleanup): quit the HandlerThread's Looper so the
        // thread actually terminates instead of living for the rest of the
        // process — this thread is recreated fresh on the next start().
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        Log.d(TAG, "stopped")
    }
}