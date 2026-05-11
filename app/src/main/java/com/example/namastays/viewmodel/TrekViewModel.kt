package com.example.namastays.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.namastays.NamastaysApp
import com.example.namastays.dto.TrekState
import com.example.namastays.utilities.TrekEngine
import kotlinx.coroutines.flow.StateFlow

class TrekViewModel(application: Application) : AndroidViewModel(application) {

    private val engine =
        (application as NamastaysApp).trekEngine

    val trekState = engine.state

}