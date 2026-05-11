package com.example.namastays.utilities

import android.location.GnssMeasurement

class KalmanFilter {


    private var x = 0.0
    private var p = 1.0
    private var initialized = false

    fun update(measurement: Double): Double{

        if(!initialized){
            x = measurement
            initialized = true
            return x
        }

        val q = 0.1
        val r = 4.0

        p += q

        val k = p / (p + r)

        x += k * (measurement - x)
        p *= (1 - k)

        return x
    }
}