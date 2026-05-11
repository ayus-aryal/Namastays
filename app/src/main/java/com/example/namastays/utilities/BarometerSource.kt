package com.example.namastays.utilities

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BarometerSource(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val pressureSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private var listener: SensorEventListener? = null

    fun isAvailable() = pressureSensor != null

    fun start(onAltitude: (Double) -> Unit) {
        if (pressureSensor == null) {
            Log.e("BARO", "No pressure sensor available")
            return
        }

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pressure = event.values.firstOrNull() ?: return
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure
                )
                onAltitude(altitude.toDouble())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        listener = l
        sensorManager.registerListener(l, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        listener?.let { sensorManager.unregisterListener(it) }
        listener = null
    }
}