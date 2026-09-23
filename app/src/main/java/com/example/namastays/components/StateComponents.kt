package com.example.namastays.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namastays.ui.theme.AccentBlue
import com.example.namastays.ui.theme.DestructiveRed
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PrimaryText
import com.example.namastays.ui.theme.SecondaryText
import com.example.namastays.viewmodel.AppError

/**
 * Shared state-display components for the Explore-by-City flow (and any
 * future screen that follows the same Loading / Success / Empty / Error
 * UiState shape).
 *
 * FIX #18 (audit): CityListScreen and PlaceListScreen each implemented their
 * own near-identical EmptyState / ErrorState / Skeleton composables —
 * same structure (icon bubble + title + subtitle [+ retry button]), just
 * copy-pasted per screen. Consolidated here into single parameterized
 * versions so future visual changes (spacing, icon style, copy tone) only
 * need to be made in one place.
 *
 * FIX #19 (audit): ErrorState previously rendered a raw "⚠️" emoji as its
 * icon. Emoji glyphs render inconsistently across device manufacturers/OS
 * versions, aren't part of the app's Material icon system, and are read
 * inconsistently by screen readers. Replaced with real Material icons,
 * chosen based on the typed [AppError] (FIX #17) rather than guessed from
 * message text.
 */

private val IconBubbleSize    = 72.dp
private val IconBubbleNeutral = Color(0xFFF3F4F6)
private val IconBubbleError   = Color(0xFFFEF2F2)

/** Generic "nothing to show" state — empty city/place lists, empty search results, etc. */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 40.dp, end = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(IconBubbleSize)
                    .background(IconBubbleNeutral, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = PrimaryText
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = SecondaryText,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Generic error state, driven by a typed [AppError] (FIX #17) instead of
 * string-matching a raw message. Icon, title, and subtitle are derived from
 * the error kind, so every screen reports network failures identically
 * rather than each screen inventing its own copy/heuristics.
 */
@Composable
fun AppErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, title, subtitle) = when (error) {
        is AppError.NoConnectivity -> Triple(
            Icons.Filled.CloudOff,
            "No internet connection",
            "Check your connection and try again"
        )
        is AppError.Timeout -> Triple(
            Icons.Filled.HourglassEmpty,
            "Request timed out",
            "The server took too long to respond. Try again."
        )
        is AppError.Server -> Triple(
            Icons.Filled.ErrorOutline,
            "Something went wrong",
            // Server-provided reason is shown as the subtitle — unlike
            // NoConnectivity/Timeout there's no single generic explanation
            // that's accurate for every server error.
            error.reason.ifBlank { "Please try again in a moment" }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 60.dp, start = 40.dp, end = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(IconBubbleSize)
                    .background(IconBubbleError, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DestructiveRed,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = PrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = SecondaryText,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Try again", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

/**
 * Full-screen / full-section centered spinner, for screens that need a bare
 * loading indicator with no skeleton shape available yet (e.g.
 * PlaceDetailScreen's single-item load, vs a list which has a known
 * repeating skeleton shape).
 *
 * FIX #7 (audit): ExploreScreen's redirect-check previously rendered a
 * blank white Box with zero feedback while resolving the saved-city
 * preference, which reads as a frozen screen on a slow read. Use this
 * instead of an empty Box.
 */
@Composable
fun AppLoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue)
    }
}