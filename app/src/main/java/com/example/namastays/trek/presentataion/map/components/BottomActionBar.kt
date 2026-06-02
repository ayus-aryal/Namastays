package com.example.namastays.trek.presentataion.map.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.namastays.trek.TrekTheme

@Composable
fun BottomActionBar(
    isNavigating: Boolean,
    isTrailView: Boolean,
    cameraFollowMode: Boolean,
    onLocateMe: () -> Unit,
    onStartTrail: () -> Unit,
    onStopTrail: () -> Unit,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isNavigating) Color(0xFF1A1A1A) else TrekTheme.Background

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = bgColor,
        shadowElevation = if (isNavigating) 0.dp else 12.dp,
        shape = if (isNavigating) RoundedCornerShape(0.dp)
        else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Locate Me
            ActionButton(
                icon = if (cameraFollowMode) Icons.Filled.GpsFixed
                else Icons.Filled.GpsNotFixed,
                label = if (cameraFollowMode) "Centered" else "Locate Me",
                color = if (cameraFollowMode) Color(0xFF4CAF50)
                else if (isNavigating) Color.White.copy(alpha = 0.6f)
                else TrekTheme.TextSecondary,
                onClick = onLocateMe
            )

            // Main CTA
            if (isNavigating) {
                OutlinedButton(
                    onClick = onStopTrail,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(
                            Color.White.copy(alpha = 0.3f)
                        )
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 28.dp,
                        vertical = 14.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Stop",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onStartTrail,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrekTheme.PrimaryGreen
                    ),
                    contentPadding = PaddingValues(
                        horizontal = 28.dp,
                        vertical = 14.dp
                    )
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Start Trail",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Map/Trail toggle
            ActionButton(
                icon = if (isTrailView) Icons.Filled.Terrain
                else Icons.Filled.Map,
                label = if (isTrailView) "Trail" else "Map",
                color = if (isTrailView) Color(0xFF4CAF50)
                else if (isNavigating) Color.White.copy(alpha = 0.6f)
                else TrekTheme.TextSecondary,
                onClick = onToggleView
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(72.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}