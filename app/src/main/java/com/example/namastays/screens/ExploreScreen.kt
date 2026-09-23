// ── ExploreScreen.kt ─────────────────────────────────────────────────────────

package com.example.namastays.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.components.AppLoadingIndicator
import com.example.namastays.data.CityPreferences
import com.example.namastays.ui.theme.AccentBlue
import com.example.namastays.ui.theme.BackgroundColor
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PrimaryText
import com.example.namastays.ui.theme.SecondaryText
import com.example.namastays.viewmodel.ExploreUiState
import com.example.namastays.viewmodel.ExploreViewModel

/**
 * FIX #20 (audit): the original screen carried @RequiresApi(Build.VERSION_CODES.Q)
 * with nothing in its body actually requiring API 29 — removed. If a real
 * API-29+ dependency gets added later (e.g. a specific DataStore or system
 * API), re-add the annotation at that point with a comment explaining why.
 */
@Composable
fun ExploreScreen(navController: NavController) {
    val context = LocalContext.current
    // CityPreferences still needs a Context, so it's still created at the
    // composable level — but it's now handed to a real ViewModel (FIX #5)
    // instead of the screen reading the DataStore Flow and driving
    // navigation itself.
    val cityPreferences = remember { CityPreferences(context) }
    val viewModel: ExploreViewModel = viewModel(
        factory = ExploreViewModel.Factory(cityPreferences)
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // FIX #6 (audit): keyed on the resolved city slug (via the sealed
    // CityChosen state), not Unit. LaunchedEffect only restarts when its
    // key changes — combined with distinctUntilChanged in the ViewModel,
    // this means navigate() can only fire once per *distinct* resolved
    // city, eliminating the duplicate-back-stack-entry risk from a repeat
    // emission of the same saved city.
    val resolvedState = uiState
    LaunchedEffect(resolvedState) {
        if (resolvedState is ExploreUiState.CityChosen) {
            navController.navigate("places/${resolvedState.slug}") {
                popUpTo("explore") { inclusive = true }
            }
        }
    }

    when (resolvedState) {
        // FIX #7 (audit): was a blank white Box with no feedback. A slow
        // DataStore read previously looked like a frozen screen.
        is ExploreUiState.Loading -> AppLoadingIndicator(
            modifier = Modifier.background(BackgroundColor)
        )

        // Navigation is in flight — render nothing rather than flashing the
        // landing UI for a single frame before the LaunchedEffect above
        // navigates away.
        is ExploreUiState.CityChosen -> AppLoadingIndicator(
            modifier = Modifier.background(BackgroundColor)
        )

        is ExploreUiState.NoCityChosen -> ExploreLandingContent(
            onSelectCity = { navController.navigate("cities") }
        )
    }
}

/**
 * FIX #21 (audit): previously ExploreScreenPreview was a hand-duplicated
 * copy of this exact layout living in the same file as the real
 * composable — any change to the real landing UI had to be remembered and
 * re-applied to the preview by hand, or the preview silently drifted out of
 * sync. The landing UI is now its own stateless composable that both
 * ExploreScreen and the @Preview call, so there is exactly one source of
 * truth for what it looks like.
 *
 * FIX #2/#3 (audit): now uses the shared design tokens (BackgroundColor,
 * AccentBlue, PrimaryText, SecondaryText) instead of locally-declared
 * ExploreBlue (#2563EB) / ExploreLightBlue, which didn't match any other
 * screen's accent color.
 */
@Composable
private fun ExploreLandingContent(onSelectCity: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
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
                    .background(AccentBlue.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = AccentBlue,
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
                color = PrimaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Discover hidden gems, local favorites,\nand must-see spots across Nepal",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = SecondaryText,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onSelectCity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = androidx.compose.ui.graphics.Color.White
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ExploreScreenPreview() {
    // FIX #21 — calls the exact same composable the real screen renders,
    // instead of a hand-copied duplicate of its layout.
    ExploreLandingContent(onSelectCity = {})
}