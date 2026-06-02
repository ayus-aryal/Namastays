package com.example.namastays.trek.presentation.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

@Composable
fun LocationPermissionHandler(
    onPermissionGranted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var gpsDisabled by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        Log.d("LocationPerm", "Permission granted: $granted")

        if (granted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val gpsOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            Log.d("LocationPerm", "GPS enabled: $gpsOn")

            if (gpsOn) {
                onPermissionGranted()
            } else {
                gpsDisabled = true
            }
        } else {
            permissionDenied = true
        }
    }

    // Auto-request on first show
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PermissionChecker.PERMISSION_GRANTED

        if (fineGranted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onPermissionGranted()
            } else {
                gpsDisabled = true
            }
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    when {
        gpsDisabled -> {
            LocationErrorCard(
                icon = Icons.Filled.GpsFixed,
                title = "GPS is turned off",
                message = "Navigation requires GPS to show your location on the trail. Please enable GPS in your device settings.",
                primaryButtonText = "Open GPS Settings",
                onPrimary = {
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                secondaryButtonText = "Browse Map Only",
                onSecondary = onDismiss
            )
        }
        permissionDenied -> {
            LocationErrorCard(
                icon = Icons.Filled.LocationOff,
                title = "Location permission denied",
                message = "Namastays needs your location to show where you are on the trail and warn you if you go off route.",
                primaryButtonText = "Open App Settings",
                onPrimary = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                        }
                    )
                },
                secondaryButtonText = "Browse Map Only",
                onSecondary = onDismiss
            )
        }
    }
}

@Composable
fun LocationErrorCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    primaryButtonText: String,
    onPrimary: () -> Unit,
    secondaryButtonText: String,
    onSecondary: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFFD4A017)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B4332)
                )
            ) {
                Text(primaryButtonText)
            }
            TextButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(secondaryButtonText)
            }
        }
    }
}