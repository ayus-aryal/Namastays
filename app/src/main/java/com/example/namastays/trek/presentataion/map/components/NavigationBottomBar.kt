package com.example.namastays.trek.presentataion.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namastays.trek.domain.TrekNavigationSession
import com.example.namastays.trek.util.LocationQuality
import com.example.namastays.trek.util.NavigationState
import com.example.namastays.trek.util.NavigationStatus
import com.example.namastays.trek.util.quality

@Composable
fun NavigationBottomBar(
    state: NavigationState,
    elapsedTimeSeconds: Long,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                ) {}
            }

            // Main stats row — always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time elapsed
                Column {
                    Text(
                        text = formatElapsedTime(elapsedTimeSeconds),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 32.sp
                    )
                    Text(
                        text = "elapsed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // Distance covered
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDistanceClean(state.distanceCovered),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 32.sp
                    )
                    Text(
                        text = "covered",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // Expand button
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandMore
                        else Icons.Filled.ExpandLess,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Expanded details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Progress bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "${state.progressPercent.toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }

                    // Secondary stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SecondaryNavStat(
                            label = "Remaining",
                            value = formatDistanceClean(state.distanceRemaining)
                        )
                        SecondaryNavStat(
                            label = "ETA",
                            value = state.eta.ifEmpty { "--" }
                        )
                        SecondaryNavStat(
                            label = "GPS",
                            value = state.currentLocation.quality().name
                                .lowercase()
                                .replaceFirstChar { it.uppercase() },
                            valueColor = getGpsColor(state.currentLocation.quality())
                        )
                    }

                    // Status
                    Text(
                        text = getStatusCaption(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = getStatusColor(state.status)
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationWarningBanner(
    state: NavigationState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state.warningMessage != null,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        state.warningMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = getWarningBgColor(state.status)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getWarningIcon(state.status),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        if (state.status == NavigationStatus.OFF_TRAIL_WARNING ||
                            state.status == NavigationStatus.OFF_TRAIL_CRITICAL) {
                            Text(
                                text = "Nearest trail: ${state.distanceToTrail.toInt()}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                    if (state.bearingToTrail > 0f &&
                        (state.status == NavigationStatus.OFF_TRAIL_WARNING ||
                                state.status == NavigationStatus.OFF_TRAIL_CRITICAL)) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = "Direction to trail",
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = state.bearingToTrail }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GpsAcquiringBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A1A)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF4CAF50),
                strokeWidth = 2.dp
            )
            Column {
                Text(
                    text = "Acquiring GPS signal...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = "Move to open sky for better accuracy",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun WrongLocationDialog(
    message: String,
    onStartAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFE8622A),
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Far from Trailhead", fontWeight = FontWeight.Bold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onStartAnyway,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A3C2E)
                ),
                shape = RoundedCornerShape(100.dp)
            ) { Text("Start Anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TrekCompletedDialog(
    trekName: String = "the trek",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Trek Completed!", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Congratulations! You have successfully completed $trekName.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A3C2E)
                ),
                shape = RoundedCornerShape(100.dp)
            ) { Text("Done") }
        }
    )
}

@Composable
fun ResumeNavigationDialog(
    session: TrekNavigationSession,
    onResume: () -> Unit,
    onStartFresh: () -> Unit
) {
    val distanceKm = "%.1f".format(session.distanceCovered / 1000)
    val progress = session.progressPercent.toInt()
    val hoursAgo = ((System.currentTimeMillis() - session.updatedAt) / 3_600_000).toInt()
    val timeAgo = if (hoursAgo == 0) "just now"
    else "$hoursAgo hour${if (hoursAgo > 1) "s" else ""} ago"

    AlertDialog(
        onDismissRequest = onStartFresh,
        icon = {
            Icon(
                Icons.Filled.Navigation,
                contentDescription = null,
                tint = Color(0xFF1A3C2E),
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Resume Trek?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "You have a saved trek session from $timeAgo.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Distance covered: ${distanceKm}km ($progress%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1A3C2E),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A3C2E)
                ),
                shape = RoundedCornerShape(100.dp)
            ) { Text("Resume") }
        },
        dismissButton = {
            TextButton(onClick = onStartFresh) { Text("Start Fresh") }
        }
    )
}

@Composable
private fun SecondaryNavStat(
    label: String,
    value: String,
    valueColor: Color = Color.White.copy(alpha = 0.8f)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

private fun formatElapsedTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}:${"%02d".format(m)}"
    else "${m}:${"%02d".format(seconds % 60)}"
}

private fun formatDistanceClean(meters: Float): String {
    return if (meters < 1000) "${meters.toInt()}m"
    else "${"%.2f".format(meters / 1000)}km"
}

private fun getGpsColor(quality: LocationQuality): Color {
    return when (quality) {
        LocationQuality.EXCELLENT -> Color(0xFF4CAF50)
        LocationQuality.GOOD      -> Color(0xFF8BC34A)
        LocationQuality.POOR      -> Color(0xFFFF9800)
        LocationQuality.UNUSABLE  -> Color(0xFFF44336)
        LocationQuality.ACQUIRING -> Color(0xFF9E9E9E)
    }
}

private fun getStatusCaption(state: NavigationState): String {
    return when (state.status) {
        NavigationStatus.ON_TRAIL           -> "On trail · GPS active"
        NavigationStatus.OFF_TRAIL_WARNING  -> "Slightly off trail"
        NavigationStatus.OFF_TRAIL_CRITICAL -> "Off trail"
        NavigationStatus.WRONG_DIRECTION    -> "Wrong direction"
        NavigationStatus.WRONG_LOCATION     -> "Far from trailhead"
        NavigationStatus.IN_VEHICLE         -> "Navigation paused"
        NavigationStatus.POOR_GPS           -> "Weak GPS signal"
        NavigationStatus.COMPLETED          -> "Trek completed"
        NavigationStatus.ACQUIRING          -> "Acquiring GPS..."
    }
}

private fun getStatusColor(status: NavigationStatus): Color {
    return when (status) {
        NavigationStatus.ON_TRAIL           -> Color(0xFF4CAF50)
        NavigationStatus.OFF_TRAIL_WARNING  -> Color(0xFFFF9800)
        NavigationStatus.OFF_TRAIL_CRITICAL -> Color(0xFFF44336)
        NavigationStatus.WRONG_DIRECTION    -> Color(0xFFFF9800)
        NavigationStatus.WRONG_LOCATION     -> Color(0xFFFF9800)
        NavigationStatus.IN_VEHICLE         -> Color(0xFF9E9E9E)
        NavigationStatus.POOR_GPS           -> Color(0xFFFF9800)
        NavigationStatus.COMPLETED          -> Color(0xFF4CAF50)
        NavigationStatus.ACQUIRING          -> Color(0xFF9E9E9E)
    }
}

private fun getWarningBgColor(status: NavigationStatus): Color {
    return when (status) {
        NavigationStatus.OFF_TRAIL_CRITICAL -> Color(0xFFC62828)
        NavigationStatus.WRONG_LOCATION     -> Color(0xFFE65100)
        NavigationStatus.IN_VEHICLE         -> Color(0xFF546E7A)
        NavigationStatus.POOR_GPS           -> Color(0xFFE65100)
        NavigationStatus.COMPLETED          -> Color(0xFF2E7D32)
        else                                -> Color(0xFFE65100)
    }
}

private fun getWarningIcon(
    status: NavigationStatus
): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        NavigationStatus.COMPLETED  -> Icons.Filled.CheckCircle
        NavigationStatus.IN_VEHICLE -> Icons.Filled.DirectionsCar
        NavigationStatus.POOR_GPS   -> Icons.Filled.GpsOff
        else                        -> Icons.Filled.Warning
    }
}