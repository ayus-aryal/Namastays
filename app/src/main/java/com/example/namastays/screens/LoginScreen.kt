package com.example.namastays.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.NamastaysApp
import com.example.namastays.R
import com.example.namastays.auth.AuthViewModel
import com.example.namastays.auth.LoginUiState

// Brand palette pulled from the mockup. Move these into your Theme.kt
// if you reuse them elsewhere (recommended) — kept local here for now.
private val NamastaysCream = Color(0xFFFCF3E3)
private val NamastaysMaroon = Color(0xFF7A1F2B)
private val NamastaysTerracotta = Color(0xFFD8714B)
private val NamastaysTextBrown = Color(0xFF3D2B23)
private val NamastaysSubtextBrown = Color(0xFF6B5A50)

// Vertical offset to clear the prayer-flag band in login_screen_bg.
// Tune this single value if the banner overlaps the flags on your device.
private val PRAYER_FLAG_CLEARANCE = 64.dp

// ---- Real entry point — wires up ViewModel/nav, delegates UI to LoginScreenContent ----
@Composable
fun LoginScreen(navController: NavController) {

    val context = LocalContext.current

    val application = LocalContext.current.applicationContext as NamastaysApp
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.Factory(application.deps.authRepository)
    )

    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
            authViewModel.resetState()
        }
    }

    LoginScreenContent(
        uiState = uiState,
        onGoogleSignInClick = { authViewModel.signInWithGoogle(context) }
    )
}

// ---- Stateless UI — previewable without a ViewModel/NamastaysApp context ----
@Composable
private fun LoginScreenContent(
    uiState: LoginUiState,
    onGoogleSignInClick: () -> Unit
) {
    // Match the status bar's solid background color to this screen only —
    // restored to whatever it was before, on leaving this screen, so other
    // screens aren't affected after navigating away (e.g. to "home").
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        val originalStatusBarColor = window.statusBarColor

        window.statusBarColor = AndroidColor.parseColor("#FCF3E3")
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true

        onDispose {
            window.statusBarColor = originalStatusBarColor
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ---- Background hero image ----
        // Crop + TopCenter keeps the mountains/stupa composition framed
        // consistently whether the screen is a small phone or a tall tablet.
        Image(
            painter = painterResource(id = R.drawable.login_screen_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )

        Column(modifier = Modifier.fillMaxSize()) {

            // Header logo sits over the image, positioned below the prayer-flag
            // band of the background art. PRAYER_FLAG_CLEARANCE is the one knob
            // to tune if the banner overlaps the flags on a given device/screen size.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.18f)
                    .padding(top = PRAYER_FLAG_CLEARANCE, start = 24.dp, end = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.namastays_login_screen_banner),
                    contentDescription = "Namastays",
                    modifier = Modifier.fillMaxWidth(0.65f),
                    contentScale = ContentScale.FillWidth
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- Bottom sheet card ----
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                color = NamastaysCream,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp)
                        .padding(top = 36.dp, bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to Namastays",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = NamastaysTextBrown,
                        textAlign = TextAlign.Center,
                        fontFamily = PlusJakartaSansBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Discover the warmth of Nepal.",
                        fontSize = 16.sp,
                        color = NamastaysSubtextBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        fontFamily = PlusJakartaSans
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    if (uiState is LoginUiState.Error) {
                        Text(
                            text = uiState.message,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp),
                            fontFamily = PlusJakartaSans
                        )
                    }

                    GoogleSignInButton(
                        isLoading = uiState is LoginUiState.Loading,
                        onClick = onGoogleSignInClick
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "By continuing, you agree to our Terms & Privacy Policy",
                        fontSize = 12.sp,
                        color = NamastaysSubtextBrown,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        fontFamily = PlusJakartaSans
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = NamastaysTextBrown
        ),
        border = BorderStroke(1.dp, Color(0xFFE3CFC2))
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = NamastaysTerracotta
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.google_logo),
                contentDescription = null,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = PlusJakartaSans
            )
        }
    }
}

// ---- Previews ----
@Preview(showBackground = true, name = "Idle")
@Composable
private fun LoginScreenPreview_Idle() {
    MaterialTheme {
        LoginScreenContent(
            uiState = LoginUiState.Idle,
            onGoogleSignInClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Loading")
@Composable
private fun LoginScreenPreview_Loading() {
    MaterialTheme {
        LoginScreenContent(
            uiState = LoginUiState.Loading,
            onGoogleSignInClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Error")
@Composable
private fun LoginScreenPreview_Error() {
    MaterialTheme {
        LoginScreenContent(
            uiState = LoginUiState.Error("No internet connection. Please try again."),
            onGoogleSignInClick = {}
        )
    }
}