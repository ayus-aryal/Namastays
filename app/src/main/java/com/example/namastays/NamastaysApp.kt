package com.example.namastays

import android.app.Application
import android.os.StrictMode
import android.util.Log
import coil.Coil
import com.example.namastays.api.ApiClient
import com.example.namastays.auth.TokenManager
import com.example.namastays.data.AnchorPointRepository
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



    /**
     * Backtrack anchor point storage — DataStore-backed, persists across
     * restarts. Lives here (not in AppDependencies) because it's app-wide
     * device/local state, not a network-backed repository — same reasoning
     * as trekEngine above. Shared by TrekModeScreen (writes the anchor) and
     * the Compass screen (reads it + computes bearing).
     */
    val anchorPointRepository: AnchorPointRepository by lazy{
        AnchorPointRepository(applicationContext).also{
            Log.d("APP", "AnchorPointRepository initialized")
        }
    }


    val isDebugBuild = true

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(TokenManager(applicationContext))

        // FIX: register a shared, tuned Coil ImageLoader (memory + disk
        // cache) instead of every AsyncImage/SubcomposeAsyncImage falling
        // back to Coil's implicit default. Must happen before any screen
        // composes its first AsyncImage — onCreate is the right place.
        Coil.setImageLoader(deps.imageLoader)

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