package com.example.namastays.utilities

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.namastays.dto.TrekState
import com.example.namastays.screens.AltitudeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TrekEngine(private val context: Context) {

    private val gps = GpsDataSource(context)
    private val barometer = BarometerSource(context)
    private val kalman = KalmanFilter()

    private var lastLocation: Location? = null
    private var lastAltitude: Double? = null   // always Kalman-filtered — fixes Bug 3

    private var totalGain = 0.0
    private var totalLoss = 0.0
    private var totalDistance = 0.0

    private val _state = MutableStateFlow(TrekState())
    val state: StateFlow<TrekState> = _state

    @Volatile private var latestBaro: Double? = null
    private var started = false

    fun start() {
        if (started) {
            Log.d("ENGINE", "already started — ignoring")
            return
        }
        started = true
        Log.d("ENGINE", "starting")

        barometer.start { pressureAlt ->
            latestBaro = pressureAlt
        }

        gps.start { location ->
            val gpsAlt  = location.altitude
            val baroAlt = latestBaro

            val fusedAlt = when {
                baroAlt != null -> (gpsAlt * 0.3) + (baroAlt * 0.7)
                gpsAlt != 0.0   -> gpsAlt
                else            -> 0.0
            }

            val altitude = kalman.update(fusedAlt)

            // Bug 3 fix: compare filtered altitude against the previous filtered altitude
            lastAltitude?.let { prevAlt ->
                val diff = altitude - prevAlt
                if (diff >  2.0) totalGain += diff
                if (diff < -2.0) totalLoss += kotlin.math.abs(diff)
            }

            lastLocation?.let { prev ->
                totalDistance += prev.distanceTo(location)
            }

            lastAltitude = altitude
            lastLocation = location

            _state.value = TrekState(
                altitude         = altitude,
                latitude         = location.latitude,
                longitude        = location.longitude,
                speedKmh         = location.speed * 3.6,
                distanceKm       = totalDistance / 1000.0,
                gainMeters       = totalGain,
                lossMeters       = totalLoss,
                altitudeZone     = zone(altitude),
                accuracy         = location.accuracy,
                barometerAvailable = baroAlt != null
            )
        }
    }

    fun stop() {
        if (!started) return
        gps.stop()
        barometer.stop()
        started = false
        Log.d("ENGINE", "stopped")
    }

    private fun zone(alt: Double) = when {
        alt < 2500 -> AltitudeZone.NORMAL
        alt < 3500 -> AltitudeZone.ACCLIMATIZATION
        alt < 5000 -> AltitudeZone.HIGH_RISK
        else       -> AltitudeZone.EXTREME
    }
}