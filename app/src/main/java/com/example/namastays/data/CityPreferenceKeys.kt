package com.example.namastays.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.cityDataStore: DataStore<Preferences> by preferencesDataStore(name = "city_prefs")

object CityPreferenceKeys {
    val CITY_SLUG = stringPreferencesKey("city_slug")
    val CITY_NAME = stringPreferencesKey("city_name")
}

data class SavedCity(val slug: String, val name: String)

class CityPreferences(private val context: Context) {

    val savedCity: Flow<SavedCity?> = context.cityDataStore.data.map { prefs ->
        val slug = prefs[CityPreferenceKeys.CITY_SLUG]
        val name = prefs[CityPreferenceKeys.CITY_NAME]
        if (!slug.isNullOrBlank() && !name.isNullOrBlank()) SavedCity(slug, name) else null
    }

    suspend fun saveCity(slug: String, name: String) {
        context.cityDataStore.edit { prefs ->
            prefs[CityPreferenceKeys.CITY_SLUG] = slug
            prefs[CityPreferenceKeys.CITY_NAME] = name
        }
    }

    suspend fun clearCity() {
        context.cityDataStore.edit { prefs ->
            prefs.remove(CityPreferenceKeys.CITY_SLUG)
            prefs.remove(CityPreferenceKeys.CITY_NAME)
        }
    }
}