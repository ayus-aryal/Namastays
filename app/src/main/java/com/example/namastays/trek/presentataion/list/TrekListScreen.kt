package com.example.namastays.trek.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

data class TrekItem(
    val id: String,
    val name: String,
    val region: String,
    val difficulty: String,
    val durationDays: Int,
    val maxElevation: Int,
    val distanceKm: Int,
    val description: String,
    val isDownloaded: Boolean = false
)

// Hardcoded for now — will come from API in later phase
val sampleTreks = listOf(
    TrekItem(
        id = "ghorepani-poonhill",
        name = "Ghorepani Poon Hill",
        region = "Gandaki Province",
        difficulty = "Easy",
        durationDays = 4,
        maxElevation = 3210,
        distanceKm = 42,
        description = "Classic short trek with stunning Annapurna sunrise views from Poon Hill. Perfect for first-time trekkers."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekListScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Trek Maps",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleTreks) { trek ->
                TrekCard(
                    trek = trek,
                    onClick = {
                        navController.navigate("trek_detail/${trek.id}")
                    }
                )
            }
        }
    }
}

@Composable
fun TrekCard(
    trek: TrekItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Trek name + difficulty badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trek.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                DifficultyBadge(difficulty = trek.difficulty)
            }

            // Region
            Text(
                text = trek.region,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatChip("${trek.durationDays} days")
                StatChip("${trek.distanceKm} km")
                StatChip("${trek.maxElevation}m")
            }

            // Description
            Text(
                text = trek.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            // Download status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (trek.isDownloaded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                } else {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Tap to view & download",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun DifficultyBadge(difficulty: String) {
    val color = when (difficulty.lowercase()) {
        "easy" -> Color(0xFF2E7D32)
        "moderate" -> Color(0xFFE65100)
        "hard" -> Color(0xFFC62828)
        else -> Color.Gray
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = difficulty,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun StatChip(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}