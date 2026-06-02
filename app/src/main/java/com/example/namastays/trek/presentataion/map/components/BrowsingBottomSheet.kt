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
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.util.ElevationPoint

@Composable
fun BrowsingBottomSheet(
    trek: TrekItem?,
    elevationPoints: List<ElevationPoint>,
    isDownloaded: Boolean,
    onStart: () -> Unit,
    onLocateMe: () -> Unit,
    onBack: () -> Unit,
    onZoomToRoute: () -> Unit,
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Drag handle — tapping expands/collapses
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

            // Trek name row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trek?.name ?: "Trek",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Stats — always visible
            trek?.let { t ->
                val elevGain = if (elevationPoints.size > 1) {
                    elevationPoints.zipWithNext()
                        .sumOf { (a, b) -> (b.elevationM - a.elevationM).coerceAtLeast(0.0) }
                        .toInt()
                } else 0
                val estHours = (t.distanceKm / 3.5).toInt()
                val estMins = ((t.distanceKm / 3.5 - estHours) * 60).toInt()
                val estTime = if (estHours > 0) "${estHours}h ${estMins}min"
                else "${estMins}min"

                Text(
                    text = "${t.distanceKm} km  ·  ${elevGain}m gain  ·  Est. $estTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (elevationPoints.isNotEmpty()) {
                        ElevationProfileChart(
                            points = elevationPoints,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF4CAF50),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons — always visible
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color(0xFF2A2A2A),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isDownloaded) Icons.Filled.OfflinePin
                                else Icons.Filled.Download,
                                contentDescription = null,
                                tint = if (isDownloaded) Color(0xFF4CAF50)
                                else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isDownloaded) "Downloaded" else "Download",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isDownloaded) Color(0xFF4CAF50)
                                else Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { onStart() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Start",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF1A1A1A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}