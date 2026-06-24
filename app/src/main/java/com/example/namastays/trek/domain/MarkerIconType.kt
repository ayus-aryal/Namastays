package com.example.namastays.trek.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Resolves the iconType String stored in CustomMarker to a display icon + label.
 *
 * Kept in the domain package (not moved to the UI layer) because five existing
 * screen files already import it from here and changing them would be a separate
 * refactor. The Compose dependency is acceptable at this layer for this project.
 *
 * The key change from the original: [key] is now a lowercase String that matches
 * what is stored in CustomMarker.iconType (previously used .name which is the
 * enum constant name in SCREAMING_SNAKE_CASE, which would break existing stored
 * rows if the enum was renamed).
 */
enum class MarkerIconType(val key: String, val label: String, val icon: ImageVector) {
    PIN("pin",       "Pin",     Icons.Filled.PushPin),
    CAMP("camp",     "Camp",    Icons.Filled.NightShelter),
    PHOTO("photo",   "Photo",   Icons.Filled.PhotoCamera),
    DANGER("danger", "Danger",  Icons.Filled.Warning),
    REST("rest",     "Rest",    Icons.Filled.Hotel),
    SUMMIT("summit", "Summit",  Icons.Filled.Landscape);

    companion object {
        /** Resolves a stored iconType string to its enum entry, defaulting to [PIN]. */
        fun fromString(key: String): MarkerIconType =
            entries.firstOrNull { it.key == key } ?: PIN
    }
}