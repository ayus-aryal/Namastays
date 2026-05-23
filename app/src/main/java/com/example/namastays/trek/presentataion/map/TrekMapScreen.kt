package com.example.namastays.trek.presentataion.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.namastays.trek.presentataion.map.components.TrekMapView

@Composable
fun TrekMapScreen(
    trekId: String = "ghorepani-poonhill",
    navController: NavController? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Map takes full screen
        TrekMapView(
            modifier = Modifier.fillMaxSize(),
            onMapReady = { map ->
                // Map is ready — we'll add route + markers here in later phases
            }
        )

        // Temporary label to confirm map loaded
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            Text(
                text = "Ghorepani Poon Hill",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}