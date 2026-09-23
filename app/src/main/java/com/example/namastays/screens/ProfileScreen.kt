package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.namastays.viewmodel.LogOutUiState
import com.example.namastays.viewmodel.ProfileUiState
import com.example.namastays.viewmodel.ProfileViewModel

/**
 * Simple menu entry shown in the grouped card below the profile header.
 */
private data class ProfileMenuItem(
    val label: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconColor: Color,
    val route: String
)

private val profileMenuItems = listOf(
    ProfileMenuItem(
        label = "Your Bookings",
        icon = Icons.Outlined.ConfirmationNumber,
        iconBg = HotelIconBg,
        iconColor = HotelIconColor,
        route = "your_bookings"
    ),
    ProfileMenuItem(
        label = "Terms & Conditions",
        icon = Icons.Outlined.Description,
        iconBg = HomeIconBg,
        iconColor = HomeIconColor,
        route = "terms_and_conditions"
    ),
    ProfileMenuItem(
        label = "Privacy Policy",
        icon = Icons.Outlined.Shield,
        iconBg = ToursIconBg,
        iconColor = ToursIconColor,
        route = "privacy_policy"
    )
)

/**
 * Derives up to two uppercase initials from a display name, e.g.
 * "Anish Shrestha" -> "AS", "Anish" -> "A". Falls back to "?" for blank input.
 *
 * Pulled out as a standalone function so it can be reused wherever we need
 * an avatar fallback (e.g. booking list, reviews) once real user data exists.
 */
private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/**
 * Profile screen shown when the user taps their avatar on [HomeScreen].
 *
 * Backed by [ProfileViewModel], which fetches the real user via
 * GET /app-auth/me. [profileMenuItems] (Bookings/Terms/Privacy) remain
 * static since they're just navigation entries, not user data.
 *
 * @param navController used to navigate to sub-screens (bookings, terms,
 * privacy).
 * @param viewModel owns profile-fetch and log-out state; construct via
 * [ProfileViewModel.Factory] with the app's UserRepository + AuthRepository.
 * @param onLogOutConfirmed invoked once local session tokens are cleared
 * ([LogOutUiState.Done]). The caller owns navigating to the auth flow —
 * this screen doesn't know what that destination is.
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel,
    onLogOutConfirmed: () -> Unit = {}
) {
    var showLogOutDialog by remember { mutableStateOf(false) }

    val profileState by viewModel.profileState.collectAsState()
    val logOutState by viewModel.logOutState.collectAsState()

    // Navigate away exactly once when logout completes, regardless of
    // whether the network call to the backend succeeded — AuthRepository
    // guarantees local tokens are already cleared by the time this fires.
    LaunchedEffect(logOutState) {
        if (logOutState is LogOutUiState.Done) {
            onLogOutConfirmed()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScrollPadding()
    ) {
        Spacer(Modifier.height(40.dp))

        when (val state = profileState) {
            is ProfileUiState.Loading -> {
                ProfileLoadingState()
            }
            is ProfileUiState.Error -> {
                ProfileErrorState(
                    message = state.message,
                    onRetry = { viewModel.loadProfile() }
                )
            }
            is ProfileUiState.Success -> {
                ProfileHeader(
                    name = state.profile.displayName ?: "Namastays Traveler",
                    email = state.profile.email ?: ""
                )

                Spacer(Modifier.height(24.dp))

                MemberSinceBadge(
                    year = state.profile.memberSinceYear,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        ProfileMenuCard(
            items = profileMenuItems,
            onItemClick = { item ->
                navController.navigate(item.route) { launchSingleTop = true }
            },
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(20.dp))

        LogOutButton(
            enabled = logOutState !is LogOutUiState.InProgress,
            onClick = { showLogOutDialog = true },
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }

    if (showLogOutDialog) {
        LogOutConfirmationDialog(
            onConfirm = {
                showLogOutDialog = false
                viewModel.logOut()
            },
            onDismiss = { showLogOutDialog = false }
        )
    }
}

/**
 * Shown while the initial profile fetch is in flight. Reuses the same
 * vertical rhythm as the loaded state (avatar-sized circle + two text-line
 * placeholders) so the screen doesn't visibly jump once data arrives.
 */
@Composable
private fun ProfileLoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(shimmerBrush())
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush())
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush())
        )
    }
}

/**
 * Shown when the profile fetch fails (session expired, no connectivity,
 * server error). Gives the user a way to retry without leaving the screen.
 */
@Composable
private fun ProfileErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 14.sp,
            fontFamily = PlusJakartaSans,
            color = SecondaryText
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tap to retry",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSans,
            color = AccentBlue,
            modifier = Modifier.clickable { onRetry() }
        )
    }
}

/**
 * Placeholder modifier hook — intentionally a no-op passthrough. Kept as a
 * named seam in case this screen needs to become scrollable once bookings
 * previews or additional sections are added below the menu card.
 */
private fun Modifier.verticalScrollPadding(): Modifier = this

// ─── Profile Header (avatar + name + email) ───────────────────────────────────
@Composable
private fun ProfileHeader(name: String, email: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(elevation = 8.dp, shape = CircleShape, spotColor = Color(0x33000000))
                .clip(CircleShape)
                .background(AccentBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(name),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansBold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSansBold,
            color = PrimaryText
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = email,
            fontSize = 14.sp,
            fontFamily = PlusJakartaSans,
            color = SecondaryText
        )
    }
}

// ─── Member Since Badge ────────────────────────────────────────────────────────
@Composable
private fun MemberSinceBadge(year: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Member since $year",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSans,
            color = PrimaryText
        )
    }
}

// ─── Grouped Menu Card ──────────────────────────────────────────────────────────
@Composable
private fun ProfileMenuCard(
    items: List<ProfileMenuItem>,
    onItemClick: (ProfileMenuItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
    ) {
        items.forEachIndexed { index, item ->
            ProfileMenuRow(
                item = item,
                onClick = { onItemClick(item) }
            )
            if (index != items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    thickness = 1.dp,
                    color = BorderColor
                )
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    item: ProfileMenuItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = item.iconColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = item.label,
            fontSize = 16.sp,
            fontFamily = PlusJakartaSans,
            color = PrimaryText,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = SubtleText,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── Log Out Button ─────────────────────────────────────────────────────────────
@Composable
private fun LogOutButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (enabled) DestructiveRed else DestructiveRed.copy(alpha = 0.4f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(
                width = 1.5.dp,
                color = contentColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (enabled) {
            Icon(
                imageVector = Icons.Filled.Logout,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Log Out",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PlusJakartaSans,
                color = contentColor
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
        }
    }
}

// ─── Log Out Confirmation Dialog ────────────────────────────────────────────────
@Composable
private fun LogOutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Log out?",
                fontFamily = PlusJakartaSansBold,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )
        },
        text = {
            Text(
                text = "You'll need to sign in again to access your bookings and trek data.",
                fontFamily = PlusJakartaSans,
                color = SecondaryText
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Log Out",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    color = DestructiveRed
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    fontFamily = PlusJakartaSans,
                    color = SecondaryText
                )
            }
        },
        containerColor = CardWhite
    )
}

// Preview removed — ProfileScreen now requires a real ProfileViewModel
// (backed by UserRepository + AuthRepository), which isn't worth mocking
// just for a Compose preview. Test via the running app instead.