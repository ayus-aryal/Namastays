// ── ExploreScreen.kt ─────────────────────────────────────────────────────────

package com.example.namastays.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.namastays.data.CityPreferences

private val ExploreBlue      = Color(0xFF2563EB)
private val ExploreLightBlue = Color(0xFFEFF6FF)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ExploreScreen(navController: NavController) {
    val context         = LocalContext.current
    val cityPreferences = remember { CityPreferences(context) }

    // null  = still reading prefs
    // true  = resolved, city found → navigate away
    // false = resolved, no city → show landing
    var isChecking by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        cityPreferences.savedCity.collect { saved ->
            if (saved != null) {
                navController.navigate("places/${saved.slug}") {
                    popUpTo("explore") { inclusive = true }
                }
            } else {
                isChecking = false
            }
        }
    }

    // Blank while reading — avoids flash of landing before nav fires
    if (isChecking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        )
        return
    }

    // ── Landing UI ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon bubble
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(ExploreLightBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = ExploreBlue,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Explore Nepal",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                letterSpacing = (-0.5).sp,
                color = Color(0xFF0D0D0D),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Discover hidden gems, local favorites,\nand must-see spots across Nepal",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { navController.navigate("cities") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExploreBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(
                    text = "Select a City",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ExploreScreenPreview() {
    // Preview shows landing UI directly — skip prefs check
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(Color(0xFFEFF6FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Language, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(46.dp))
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text("Explore Nepal", fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, letterSpacing = (-0.5).sp, color = Color(0xFF0D0D0D), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Discover hidden gems, local favorites,\nand must-see spots across Nepal", fontFamily = PlusJakartaSans, fontSize = 15.sp, lineHeight = 23.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White)) {
                Text("Select a City", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}