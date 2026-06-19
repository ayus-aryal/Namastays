package com.example.namastays.trek.presentataion.map.components

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.namastays.trek.util.NavigationState
import com.example.namastays.trek.util.NavigationStatus
import com.example.namastays.trek.util.quality

@Composable
fun NavigationBottomSheet(
    navigationState: NavigationState?,
    elapsedSeconds: Long,
    onStop: () -> Unit,
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 12.dp)
                    .clickable { expanded = !expanded }
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                ) {}
            }

            // Main stats — always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time
                Column {
                    Text(
                        text = formatTime(elapsedSeconds),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "elapsed",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }

                // Distance
                navigationState?.let { state ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatDist(state.distanceCovered),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "covered",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }
                }

                // Expand chevron
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandMore
                        else Icons.Filled.ExpandLess,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            // Expanded content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                navigationState?.let { state ->
                    Column(
                        modifier = Modifier.padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress bar
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                                Text(
                                    "${state.progressPercent.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFF4CAF50),
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )
                        }

                        // Secondary stats
                        // Secondary stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            NavMiniStat("Remaining", formatDist(state.distanceRemaining))
                            NavMiniStat("ETA", state.eta.ifEmpty { "--" })
                            NavMiniStat(
                                "Elevation",
                                if (state.currentElevation > 0) "${state.currentElevation}m" else "--"
                            )
                            NavMiniStat(
                                "GPS",
                                state.currentLocation.quality().name
                                    .lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                color = when (state.currentLocation.quality()) {
                                    com.example.namastays.trek.util.LocationQuality.EXCELLENT,
                                    com.example.namastays.trek.util.LocationQuality.GOOD -> Color(0xFF4CAF50)
                                    com.example.namastays.trek.util.LocationQuality.POOR -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
                        }

                        // Speed + zoom indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current speed
                            val speedKmh = (state.currentLocation.speed * 3.6f)
                            NavMiniStat(
                                label = "Speed",
                                value = if (speedKmh < 0.5f) "Stopped"
                                else "${"%.1f".format(speedKmh)} km/h"
                            )

                            // Zoom level indicator
                            NavMiniStat(
                                label = "Pace",
                                value = when {
                                    state.currentLocation.speed < 0.3f -> "Stopped"
                                    state.currentLocation.speed < 1.0f -> "Slow"
                                    state.currentLocation.speed < 2.0f -> "Walking"
                                    state.currentLocation.speed < 3.5f -> "Fast walk"
                                    else                               -> "Running"
                                },
                                color = when {
                                    state.currentLocation.speed < 0.3f -> Color(0xFF9E9E9E)
                                    state.currentLocation.speed < 1.0f -> Color(0xFF4CAF50)
                                    state.currentLocation.speed < 2.0f -> Color(0xFF4285F4)
                                    else                               -> Color(0xFFFF9800)
                                }
                            )
                        }

                        // Status
                        val statusText = when (state.status) {
                            NavigationStatus.ON_TRAIL           -> "On trail"
                            NavigationStatus.OFF_TRAIL_WARNING  -> "Slightly off trail"
                            NavigationStatus.OFF_TRAIL_CRITICAL -> "Off trail — return to route"
                            NavigationStatus.WRONG_DIRECTION    -> "Wrong direction"
                            NavigationStatus.IN_VEHICLE         -> "Navigation paused"
                            NavigationStatus.POOR_GPS           -> "Weak GPS signal"
                            NavigationStatus.COMPLETED          -> "Trek completed"
                            else                                -> ""
                        }
                        if (statusText.isNotEmpty()) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (state.status) {
                                    NavigationStatus.ON_TRAIL   -> Color(0xFF4CAF50)
                                    NavigationStatus.COMPLETED  -> Color(0xFF4CAF50)
                                    else                        -> Color(0xFFFF9800)
                                }
                            )
                        }

                        // Stop button
                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(
                                    Color.White.copy(alpha = 0.2f)
                                )
                            )
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Stop Trail",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavMiniStat(
    label: String,
    value: String,
    color: Color = Color.White.copy(alpha = 0.8f)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f)
        )
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}:${"%02d".format(m)}"
    else "${"%02d".format(m)}:${"%02d".format(s)}"
}

private fun formatDist(meters: Float): String {
    return if (meters < 1000) "${meters.toInt()}m"
    else "${"%.2f".format(meters / 1000)}km"
}