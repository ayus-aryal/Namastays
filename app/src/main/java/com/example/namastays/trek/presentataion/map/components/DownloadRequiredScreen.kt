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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.namastays.trek.domain.TrekItem

@Composable
fun DownloadRequiredScreen(
    trek: TrekItem?,
    onDownloadClick: () -> Unit
) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Map icon
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF5F0E8),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF1B4332)
                    )
                }
            }

            Text(
                text = "Offline Map Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = trek?.let {
                    "Download the ${it.name} map pack (${it.fileSizeMb}MB) " +
                            "to navigate offline on the trail."
                } ?: "Download the map pack to navigate offline on the trail.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // What's included
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF5F0E8)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IncludeRow(
                        icon = Icons.Filled.Map,
                        text = "Full offline trail map"
                    )
                    IncludeRow(
                        icon = Icons.Filled.Timeline,
                        text = "GPX route with elevation"
                    )
                    IncludeRow(
                        icon = Icons.Filled.Place,
                        text = "Teahouses, water & viewpoints"
                    )
                    IncludeRow(
                        icon = Icons.Filled.Navigation,
                        text = "Turn-by-turn trail navigation"
                    )
                }
            }

            Button(
                onClick = onDownloadClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1B4332)
                )
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Go to Download")
            }
        }
    }
}

@Composable
private fun IncludeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF1B4332),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}