package com.example.namastays

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.example.namastays.api.ApiClient
import com.example.namastays.auth.TokenManager
import com.example.namastays.utilities.TrekEngine

class NamastaysApp : Application() {

    /**
     * FIX #22 — single DI container; all repositories are created here with
     * their injected dependencies. Call sites use [deps] instead of
     * constructing repositories or accessing Retrofit singletons directly.
     */
    val deps: AppDependencies by lazy { AppDependencies(this) }

    val trekEngine: TrekEngine by lazy {
        TrekEngine(applicationContext).also {
            Log.d("APP", "TrekEngine initialized (lazy)")
        }
    }

    val isDebugBuild = true

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(TokenManager(applicationContext))


        if (isDebugBuild) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
    }
}