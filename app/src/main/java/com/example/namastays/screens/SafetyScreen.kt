package com.example.namastays.screens


import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import androidx.navigation.compose.*

// ─── Navigation Routes ────────────────────────────────────────────────────────
object SafetyRoutes {
    const val HOME         = "safety_home"
    const val SOS          = "sos"
    const val AMS          = "ams_checker"
    const val LAKE_LOUISE  = "lake_louise"
    const val CONTACTS     = "emergency_contacts"
    const val COMPASS      = "compass"
    const val TORCH        = "torch"
    const val ADD_CONTACT  = "add_contact"
}


// ─── Safety Home Screen ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyHomeScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "⛰ Safety Center",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1A1A2E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Emergency SOS – full-width prominent button ──────────────────
            SOSButton(onClick = { navController.navigate(SafetyRoutes.SOS) })

            // ── Assessment tools ─────────────────────────────────────────────
            SectionHeader("🩺 Health Assessments")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.MonitorHeart,
                    title = "AMS Checker",
                    subtitle = "Altitude sickness",
                    color = Color(0xFF2196F3),
                    onClick = { navController.navigate(SafetyRoutes.AMS) }
                )
                FeatureCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Assessment,
                    title = "Lake Louise",
                    subtitle = "AMS score",
                    color = Color(0xFF9C27B0),
                    onClick = { navController.navigate(SafetyRoutes.LAKE_LOUISE) }
                )
            }

            // ── Quick Access ─────────────────────────────────────────────────
            SectionHeader("⚡ Quick Access")
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                item {
                    FeatureCard(
                        icon = Icons.Default.Contacts,
                        title = "Emergency\nContacts",
                        subtitle = "Your saved contacts",
                        color = Color(0xFFFF5722),
                        onClick = { navController.navigate(SafetyRoutes.CONTACTS) }
                    )
                }
                item {
                    FeatureCard(
                        icon = Icons.Default.LocalPhone,
                        title = "Local Bodies",
                        subtitle = "Rescue & services",
                        color = Color(0xFF4CAF50),
                        onClick = { /* local contacts bottom sheet */ }
                    )
                }
                item {
                    FeatureCard(
                        icon = Icons.Default.Explore,
                        title = "Compass",
                        subtitle = "Full immersive",
                        color = Color(0xFF00BCD4),
                        onClick = { navController.navigate(SafetyRoutes.COMPASS) }
                    )
                }
                item {
                    FeatureCard(
                        icon = Icons.Default.FlashlightOn,
                        title = "Torch",
                        subtitle = "Flashlight",
                        color = Color(0xFFFFEB3B),
                        onClick = { navController.navigate(SafetyRoutes.TORCH) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─── Reusable UI components ───────────────────────────────────────────────────

@Composable
fun SOSButton(onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFFD32F2F), Color(0xFFFF5252))
                )
            )
            .clickable {
                pressed = !pressed
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            Column {
                Text(
                    "EMERGENCY SOS",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp
                )
                Text(
                    "Tap to activate emergency protocol",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun FeatureCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF16213E))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}