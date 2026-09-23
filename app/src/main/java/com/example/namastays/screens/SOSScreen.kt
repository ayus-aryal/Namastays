package com.example.namastays.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.SosState
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.viewmodel.SafetyViewModel

// ─── Palette ──────────────────────────────────────────────────────────────────
// Private to this file — SOS-specific colour values not shared elsewhere.
private object SosColors {
    val background    = Color(0xFFF7F8FA)
    val surface       = Color(0xFFFFFFFF)
    val surfaceAlt    = Color(0xFFF3F4F6)
    val divider       = Color(0xFFE5E7EB)
    val textPrimary   = Color(0xFF111827)
    val textSecondary = Color(0xFF6B7280)
    val textHint      = Color(0xFF9CA3AF)
    val sosRed        = Color(0xFFDC2626)
    val sosRedLight   = Color(0xFFFEE2E2)
    val green         = Color(0xFF16A34A)
    val greenBg       = Color(0xFFDCFCE7)
    val blue          = Color(0xFF2563EB)
    val blueBg        = Color(0xFFDBEAFE)
    val purple        = Color(0xFF7C3AED)
    val purpleBg      = Color(0xFFEDE9FE)
    val cyanIcon      = Color(0xFF0891B2)
    val cyanBg        = Color(0xFFCFFAFE)
}

/**
 * Emergency SOS screen.
 *
 * The user can:
 * - Tap the central button to start a countdown, then cancel it.
 * - See the SOS-sent confirmation once the service fires.
 * - Directly dial 112 (Police), 108 (Ambulance), or 100 (Nepal Police).
 *
 * State machine lifecycle (all transitions driven by [SafetyViewModel]):
 *   Idle → Counting (startSos) → Active (service sends SMS + BLE broadcast)
 *                             ↘ Idle (cancelSos)
 *   Active → Idle (resetSosState)
 *   Counting/Active → Failed (service error)
 *
 * The pulse animation only runs when the state is Counting or Active,
 * so no animation work happens in the Idle/Failed states.
 */
@Composable
fun SOSScreen(
    navController: NavController,
    vm           : SafetyViewModel = viewModel(),
) {
    val context  = LocalContext.current
    val sosState by vm.sosState.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()

    val isCountingOrActive = sosState is SosState.Counting || sosState is SosState.Active

    // Pulse ring animation — only active (and therefore only consuming CPU/GPU)
    // when SOS is in a Counting or Active state. The targetValue collapses back
    // to 1f when idle/failed, so the ring stays still without a separate
    // animation cancellation path.
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (isCountingOrActive) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SosColors.background),
    ) {
        // Propagate PlusJakartaSans as the default font family for all Text
        // composables within this screen, avoiding per-Text fontFamily params
        // on the many small status/label texts.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = PlusJakartaSans)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 32.dp),
            ) {

                // ── Header ─────────────────────────────────────────────────────
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SosColors.surfaceAlt)
                            .border(1.dp, SosColors.divider, CircleShape)
                            .clickable {
                                // Cancel a running countdown before going back
                                // so the timer doesn't fire while the user
                                // is on another screen.
                                if (sosState is SosState.Counting) vm.cancelSos()
                                navController.popBackStack()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint     = SosColors.textPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Emergency SOS",
                        color      = SosColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                    )
                }

                // ── No-contacts warning ────────────────────────────────────────
                // Shown when contacts list is empty so users understand that
                // SOS will not send any SMS — prompts them to add a contact
                // before triggering.
                if (contacts.isEmpty()) {
                    SosWarningBanner(
                        icon    = Icons.Default.PersonOff,
                        text    = "No emergency contacts saved. SOS SMS won't be sent. Tap to add.",
                        onClick = { navController.navigate(SafetyRoutes.CONTACTS) },
                    )
                }

                // ── Failure state banner ───────────────────────────────────────
                if (sosState is SosState.Failed) {
                    SosWarningBanner(
                        icon    = Icons.Default.Warning,
                        text    = "SOS failed: ${(sosState as SosState.Failed).reason}. Tap to retry.",
                        onClick = { vm.startSos() },
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── SOS pulse button ───────────────────────────────────────────
                Box(contentAlignment = Alignment.Center) {

                    // Animated outer ring — only rendered when counting/active
                    // to avoid a persistent invisible composable in idle state.
                    if (isCountingOrActive) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(SosColors.sosRed.copy(alpha = 0.07f)),
                        )
                        Box(
                            modifier = Modifier
                                .size(168.dp)
                                .clip(CircleShape)
                                .background(SosColors.sosRed.copy(alpha = 0.11f)),
                        )
                    } else {
                        // Placeholder box keeps the outer Box the same size so
                        // the core button doesn't jump position on state change.
                        Box(modifier = Modifier.size(200.dp))
                    }

                    // Core SOS button
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(
                                when (sosState) {
                                    is SosState.Active  -> Color(0xFFB91C1C) // darker red when sent
                                    is SosState.Failed  -> Color(0xFF6B7280) // grey when failed
                                    else                -> SosColors.sosRed
                                }
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            // Disabled once Active — there is nothing to do until
                            // the user explicitly resets via the Reset button.
                            .clickable(enabled = sosState !is SosState.Active) {
                                when (sosState) {
                                    is SosState.Idle, is SosState.Failed -> vm.startSos()
                                    is SosState.Counting                 -> vm.cancelSos()
                                    else                                 -> Unit
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when (val state = sosState) {
                                is SosState.Counting -> {
                                    Text(
                                        "${state.secondsLeft}",
                                        color      = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize   = 52.sp,
                                        lineHeight = 52.sp,
                                    )
                                    Text(
                                        "TAP TO CANCEL",
                                        color         = Color.White.copy(alpha = 0.8f),
                                        fontSize      = 10.sp,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                is SosState.Active -> {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    Text(
                                        "SOS SENT",
                                        color         = Color.White,
                                        fontWeight    = FontWeight.ExtraBold,
                                        fontSize      = 18.sp,
                                        letterSpacing = 1.sp,
                                    )
                                }
                                is SosState.Failed -> {
                                    Icon(
                                        Icons.Outlined.Refresh, null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        "RETRY",
                                        color      = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize   = 16.sp,
                                        letterSpacing = 2.sp
                                    )
                                }
                                else -> {
                                    Icon(
                                        Icons.Outlined.Warning, null,
                                        tint     = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        "SOS",
                                        color         = Color.White,
                                        fontWeight    = FontWeight.ExtraBold,
                                        fontSize      = 24.sp,
                                        letterSpacing = 4.sp
                                    )
                                    Text(
                                        "TAP TO ACTIVATE",
                                        color    = Color.White.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Status pill ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SosColors.surface)
                        .border(1.dp, SosColors.divider, RoundedCornerShape(20.dp))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    val (statusText, statusColor) = when (val state = sosState) {
                        is SosState.Idle     -> "Tap the button to start the SOS countdown" to SosColors.textSecondary
                        is SosState.Counting -> "Activating in ${state.secondsLeft}s — tap to cancel" to SosColors.sosRed
                        is SosState.Active   -> "SOS sent — help is on the way" to SosColors.green
                        is SosState.Failed   -> "Failed — tap button to retry" to Color(0xFFD97706)
                    }
                    Text(
                        statusText,
                        color      = statusColor,
                        fontSize   = 12.sp,
                        textAlign  = TextAlign.Center,
                        fontWeight = if (sosState is SosState.Active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }

                // Reset button — only shown in Active state so user can
                // clear the "SOS SENT" confirmation and return to Idle.
                if (sosState is SosState.Active) {
                    TextButton(onClick = { vm.resetSosState() }) {
                        Icon(
                            Icons.Outlined.Refresh, null,
                            tint     = SosColors.textHint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Reset", color = SosColors.textHint, fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = SosColors.divider, thickness = 0.5.dp)

                // ── Quick dial actions ─────────────────────────────────────────
                Text(
                    "QUICK ACTIONS",
                    color         = SosColors.textHint,
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier      = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.Call,
                        label     = "Call 112",
                        iconColor = SosColors.green,
                        iconBg    = SosColors.greenBg,
                        onClick   = { dialNumber(context, "112") },
                    )
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.LocalHospital,
                        label     = "Ambulance",
                        iconColor = SosColors.blue,
                        iconBg    = SosColors.blueBg,
                        onClick   = { dialNumber(context, "108") },
                    )
                    SosQuickButton(
                        modifier  = Modifier.weight(1f),
                        icon      = Icons.Default.Shield,
                        label     = "Police",
                        iconColor = SosColors.purple,
                        iconBg    = SosColors.purpleBg,
                        onClick   = { dialNumber(context, "100") },
                    )
                }

                HorizontalDivider(color = SosColors.divider, thickness = 0.5.dp)

                Text(
                    "WHAT HAPPENS WHEN ACTIVATED",
                    color         = SosColors.textHint,
                    fontSize      = 10.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier      = Modifier.fillMaxWidth(),
                )

                SosInfoCard(
                    icon      = Icons.Default.Message,
                    iconColor = SosColors.sosRed,
                    iconBg    = SosColors.sosRedLight,
                    title     = "Auto SMS with Location",
                    body      = "An emergency SMS with your GPS coordinates is sent to ${
                        if (contacts.isEmpty()) "no contacts saved yet"
                        else "${contacts.size} saved contact${if (contacts.size > 1) "s" else ""}"
                    }.",
                )
                SosInfoCard(
                    icon      = Icons.Default.Bluetooth,
                    iconColor = SosColors.cyanIcon,
                    iconBg    = SosColors.cyanBg,
                    title     = "BLE Broadcast",
                    body      = "An SOS signal is broadcast via Bluetooth to nearby Namastays users within ~80 metres.",
                )
            }
        }
    }
}

// ─── Warning Banner ───────────────────────────────────────────────────────────

/**
 * An amber warning banner with an icon and tappable text.
 * Used to surface no-contacts and failure states without blocking the SOS button.
 */
@Composable
fun SosWarningBanner(
    icon   : androidx.compose.ui.graphics.vector.ImageVector,
    text   : String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFBEB))
            .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
        Text(
            text,
            color      = Color(0xFF92400E),
            fontSize   = 12.sp,
            modifier   = Modifier.weight(1f),
            lineHeight = 17.sp
        )
        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
    }
}

// ─── Quick Action Button ──────────────────────────────────────────────────────

/** A small dial-action tile used in the three-button quick-actions row. */
@Composable
fun SosQuickButton(
    modifier : Modifier = Modifier,
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    label    : String,
    iconColor: Color,
    iconBg   : Color,
    onClick  : () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SosColors.surface)
            .border(1.dp, SosColors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color      = SosColors.textPrimary,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center
        )
    }
}

// ─── Info Card ────────────────────────────────────────────────────────────────

/** Explains one consequence of triggering SOS (SMS and BLE broadcast). */
@Composable
fun SosInfoCard(
    icon     : androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg   : Color,
    title    : String,
    body     : String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SosColors.surface)
            .border(1.dp, SosColors.divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = SosColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(body, color = SosColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}