package com.example.namastays.trek.presentataion.map.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.namastays.trek.TrekTheme
import com.example.namastays.trek.domain.Waypoint
import com.example.namastays.trek.domain.WaypointType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaypointBottomSheet(
    waypoint: Waypoint,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = TrekTheme.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = getWaypointColor(waypoint.type),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getWaypointIcon(waypoint.type),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TrekTheme.TextPrimary
                    )
                    Text(
                        text = waypoint.type.name
                            .lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = TrekTheme.TextSecondary
                    )
                }
            }

            // Elevation chip
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = TrekTheme.Surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TrekTheme.TextSecondary
                    )
                    Text(
                        text = "${waypoint.elevation}m elevation",
                        style = MaterialTheme.typography.labelMedium,
                        color = TrekTheme.TextSecondary
                    )
                }
            }

            // Description
            if (waypoint.description.isNotEmpty()) {
                Text(
                    text = waypoint.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TrekTheme.TextSecondary,
                    lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.6
                )
            }

            // Amenities
            if (waypoint.amenities.isNotEmpty()) {
                Text(
                    text = "Amenities",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TrekTheme.TextPrimary
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    waypoint.amenities.forEach { amenity ->
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = TrekTheme.PrimaryGreen.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = amenity,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = TrekTheme.PrimaryGreen,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getWaypointColor(type: WaypointType): androidx.compose.ui.graphics.Color {
    return when (type) {
        WaypointType.TEAHOUSE          -> TrekTheme.PrimaryGreen
        WaypointType.WATER             -> TrekTheme.WaterBlue
        WaypointType.VIEWPOINT         -> TrekTheme.TrailOrange
        WaypointType.CAMPSITE          -> androidx.compose.ui.graphics.Color(0xFF6A1B9A)
        WaypointType.TRAILHEAD         -> TrekTheme.AccentGreen
        WaypointType.CHECKPOINT        -> TrekTheme.WarningOrange
        WaypointType.VILLAGE           -> androidx.compose.ui.graphics.Color(0xFF4E342E)
        WaypointType.EMERGENCY         -> TrekTheme.ErrorRed
        WaypointType.PASS              -> androidx.compose.ui.graphics.Color(0xFF546E7A)
        WaypointType.SUSPENSION_BRIDGE -> androidx.compose.ui.graphics.Color(0xFF795548)
    }
}

fun getWaypointIcon(type: WaypointType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        WaypointType.TEAHOUSE          -> androidx.compose.material.icons.Icons.Filled.Coffee
        WaypointType.WATER             -> androidx.compose.material.icons.Icons.Filled.Water
        WaypointType.VIEWPOINT         -> androidx.compose.material.icons.Icons.Filled.Visibility
        WaypointType.CAMPSITE          -> androidx.compose.material.icons.Icons.Filled.NightShelter
        WaypointType.TRAILHEAD         -> androidx.compose.material.icons.Icons.Filled.Hiking
        WaypointType.CHECKPOINT        -> androidx.compose.material.icons.Icons.Filled.Flag
        WaypointType.VILLAGE           -> androidx.compose.material.icons.Icons.Filled.LocationCity
        WaypointType.EMERGENCY         -> androidx.compose.material.icons.Icons.Filled.LocalHospital
        WaypointType.PASS              -> androidx.compose.material.icons.Icons.Filled.Landscape
        WaypointType.SUSPENSION_BRIDGE -> androidx.compose.material.icons.Icons.Filled.LinearScale
    }
}