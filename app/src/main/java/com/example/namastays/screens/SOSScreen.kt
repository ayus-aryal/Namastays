package com.example.namastays.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.SosManager
import com.example.namastays.viewmodel.SafetyViewModel
import kotlinx.coroutines.delay

@Composable
fun SOSScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()

    var sosActivated by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(5) }
    var counting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Tap the button to start SOS countdown") }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Track permissions
    var hasSmsPermission by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Permission launchers
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasSmsPermission = granted }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocationPermission = granted }

    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermission = permissions[Manifest.permission.SEND_SMS] == true
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    // Request permissions on first load
    LaunchedEffect(Unit) {
        multiplePermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Countdown coroutine
    LaunchedEffect(counting) {
        if (counting) {
            while (countdown > 0) {
                delay(1000L)
                countdown--
            }
            sosActivated = true
            counting = false
            statusMessage = "🚨 Sending SOS messages..."
            triggerSOS(context, contacts) { success, message ->
                statusMessage = message
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (sosActivated) Color(0xFF1A0000) else Color(0xFF1A1A2E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(24.dp)
        ) {
            // Back button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "Emergency SOS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            // ── Permission warning banner ─────────────────────────────────
            if (!hasSmsPermission) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable {
                            multiplePermissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                )
                            )
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(18.dp))
                    Text(
                        "SMS permission needed to send alerts. Tap to grant.",
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp
                    )
                }
            }

            // ── No contacts warning ───────────────────────────────────────
            if (contacts.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF5252).copy(alpha = 0.1f))
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .clickable { navController.navigate(SafetyRoutes.CONTACTS) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                    Text(
                        "No emergency contacts saved. Tap to add.",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── SOS Circle Button ─────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                if (counting || sosActivated) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F).copy(alpha = 0.2f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0xFFFF1744), Color(0xFFD32F2F))
                            )
                        )
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                        .clickable(enabled = !sosActivated) {
                            if (!counting) {
                                counting = true
                                countdown = 5
                            } else {
                                counting = false
                                countdown = 5
                                statusMessage = "Tap the button to start SOS countdown"
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (counting) {
                            Text(
                                text = "$countdown",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 56.sp
                            )
                            Text("Tap to cancel", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        } else if (sosActivated) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("SOS SENT", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("SOS", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                            Text("Tap to activate", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                    }
                }
            }

            // ── Status text ───────────────────────────────────────────────
            Text(
                text = statusMessage,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // ── Quick Action Buttons ──────────────────────────────────────
            Text(
                "Quick Actions",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Call,
                    label = "Call 112",
                    color = Color(0xFF4CAF50),
                    onClick = { dialNumber(context, "112") }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalHospital,
                    label = "Ambulance",
                    color = Color(0xFF2196F3),
                    onClick = { dialNumber(context, "108") }
                )
                QuickActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Shield,
                    label = "Police",
                    color = Color(0xFF9C27B0),
                    onClick = { dialNumber(context, "100") }
                )
            }

            SOSInfoCard(
                icon = Icons.Default.LocationOn,
                title = "Location Sharing",
                body = "Your GPS coordinates will be shared with emergency contacts when SOS is activated."
            )
            SOSInfoCard(
                icon = Icons.Default.Message,
                title = "Auto SMS",
                body = "An emergency SMS with your location is sent to all ${contacts.size} saved emergency contact(s)."
            )
        }
    }
}

// ── SOS trigger ───────────────────────────────────────────────────────────────
fun triggerSOS(
    context: Context,
    contacts: List<com.example.namastays.data.EmergencyContactEntity>,
    onResult: (Boolean, String) -> Unit
) {
    SosManager.sendSosMessages(context, contacts, onResult)
}



@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun SOSInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16213E))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(body, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}