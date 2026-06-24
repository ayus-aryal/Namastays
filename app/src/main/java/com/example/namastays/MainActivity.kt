package com.example.namastays

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.namastays.auth.SplashAuthState
import com.example.namastays.auth.SplashViewModel
import com.example.namastays.navigation.AppNavGraph
import com.example.namastays.ui.theme.NamastaysTheme

class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels {
        SplashViewModel.Factory((application as NamastaysApp).deps.authRepository)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE super.onCreate() — this is the system
        // splash screen API's requirement, not a style choice.
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition {
            splashViewModel.authState.value is SplashAuthState.Loading
        }

        setContent {
            NamastaysTheme {

                val authState by splashViewModel.authState.collectAsStateWithLifecycle()

                when (authState) {
                    SplashAuthState.Loading -> {
                        // System splash is still covering the screen via
                        // setKeepOnScreenCondition above — render nothing
                        // here to avoid any content flashing underneath it.
                    }
                    SplashAuthState.Authenticated -> {
                        AppNavGraph(startDestination = "home")
                    }
                    SplashAuthState.Unauthenticated -> {
                        AppNavGraph(startDestination = "login")
                    }
                }
            }
        }
    }
}