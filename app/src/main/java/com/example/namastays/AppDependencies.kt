package com.example.namastays

import android.content.Context
import com.example.namastays.api.ApiClient
import com.example.namastays.auth.AuthRepository
import com.example.namastays.auth.GoogleAuthManager
import com.example.namastays.auth.TokenManager
import com.example.namastays.repository.CityRepository
import com.example.namastays.repository.PlaceRepository
import com.example.namastays.repository.PropertyRepository
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.TrekDatabase

/**
 * Manual DI container — single place where all repositories are constructed
 * with their injected dependencies.
 *
 * FIX #22 — Previously each ViewModel constructed its own repository which
 * in turn pulled from a static singleton Retrofit client. Now:
 *   1. ApiClient holds one shared OkHttpClient and one Retrofit instance.
 *   2. Each repository receives its API service and DAO via constructor.
 *   3. ViewModels receive repositories via their Factory.
 *
 * Usage: obtain via NamastaysApp.deps, then pass into ViewModel Factories:
 *
 *   val vm: CityViewModel by viewModels {
 *       CityViewModel.Factory(NamastaysApp.deps.cityRepository)
 *   }
 *
 * If you adopt Hilt later, delete this file and annotate each repository
 * with @Singleton + @Inject constructor(...).
 */
class AppDependencies(context: Context) {

    private val appContext = context.applicationContext

    // ── Database ──────────────────────────────────────────────────────────────

    private val trekDb = TrekDatabase.getInstance(appContext)


    // Auth
    val tokenManager: TokenManager by lazy {
        TokenManager(appContext)
    }

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager()
    }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            googleAuthManager = googleAuthManager,
            appAuthApi = ApiClient.appAuthApi,
            tokenManager = tokenManager
        )
    }


    // ── Repositories ──────────────────────────────────────────────────────────

    val trekRepository: TrekRepository by lazy {
        TrekRepository(
            api           = ApiClient.trekApi,
            cacheDao      = trekDb.trekCacheDao(),
            itineraryDao  = trekDb.trekItineraryDao(),
            highlightDao  = trekDb.trekHighlightDao(),
            downloadedDao = trekDb.downloadedTrekDao()
        )
    }

    val cityRepository: CityRepository by lazy {
        CityRepository(api = ApiClient.cityApi)
    }

    val placeRepository: PlaceRepository by lazy {
        PlaceRepository(api = ApiClient.placeApi)
    }

    val propertyRepository: PropertyRepository by lazy {
        PropertyRepository(api = ApiClient.propertyApi)
    }
}