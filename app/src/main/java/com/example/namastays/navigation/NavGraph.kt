package com.example.namastays.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.example.namastays.screens.MainScreen

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun AppNavGraph(startDestination: String) {
    MainScreen(startDestination = startDestination)
}