package com.example.namastays

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.example.namastays.utilities.TrekEngine
class NamastaysApp : Application() {

    // Lazy initialization: TrekEngine is created on first access rather than
    // in onCreate(). This keeps Application.onCreate() off the critical path
    // and avoids any disk/sensor work that TrekEngine's constructor may do
    // from landing on the main thread before the first frame is drawn.
    //
    // Any code that needs trekEngine (i.e. TrekViewModel) runs after the
    // Activity/ViewModel layer is up, so the lazy is always resolved off the
    // onCreate() hot path.
    val trekEngine: TrekEngine by lazy {
        TrekEngine(applicationContext).also {
            Log.d("APP", "TrekEngine initialized (lazy)")
        }
    }

    val isDebugBuild = true

    override fun onCreate() {
        super.onCreate()

        if (isDebugBuild) {
            // Use penaltyDeath during active development to surface ALL
            // StrictMode violations immediately as hard crashes with a full
            // stack trace. Switch back to penaltyLog before sharing builds
            // with testers — penaltyDeath will crash on any violation,
            // including ones in third-party libraries you can't control.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()   // ← swap to .penaltyDeath() to find all violators fast
                    .build()
            )
        }

        // MapLibre.getInstance() reads native libraries from disk on the main
        // thread and will trip StrictMode detectDiskReads(). Initialize it
        // lazily or move to a background thread if re-enabling.
        // MapLibre.getInstance(this)
    }
}