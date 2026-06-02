package com.example.namastays.trek.presentataion.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.repository.TrekRepository
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.TrekTheme
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.trek.util.MBTilesLoader
import com.example.namastays.viewmodel.TreksViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekListScreen(navController: NavController) {
    val context = LocalContext.current
    val db = remember { TrekDatabase.getInstance(context) }
    val viewModel: TreksViewModel = viewModel(
        factory = TreksViewModel.Factory(TrekRepository(db.trekCacheDao()))
    )

    val treks by viewModel.treks.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Downloaded", "Easy", "Moderate", "Hard")

    val filteredTreks = remember(treks, selectedFilter) {
        when (selectedFilter) {
            "Downloaded" -> treks.filter { MBTilesLoader.isDownloaded(context, it.id) }
            "All"        -> treks
            else         -> treks.filter {
                it.difficulty.equals(selectedFilter, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Explore Treks",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TrekTheme.TextPrimary
                        )
                        Text(
                            "Nepal's finest trails",
                            style = MaterialTheme.typography.bodySmall,
                            color = TrekTheme.TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TrekTheme.Background
                )
            )
        },
        containerColor = TrekTheme.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Filter chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    items(filters) { filter ->
                        val selected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (selected) TrekTheme.PrimaryGreen
                            else TrekTheme.Surface,
                            border = if (!selected) ButtonDefaults.outlinedButtonBorder
                            else null,
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White
                                else TrekTheme.TextPrimary,
                                fontWeight = if (selected) FontWeight.SemiBold
                                else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Trek cards
            items(filteredTreks) { trek ->
                val isDownloaded = MBTilesLoader.isDownloaded(context, trek.id)
                TrekCard(
                    trek = trek.copy(isDownloaded = isDownloaded),
                    onClick = { navController.navigate("trek_detail/${trek.id}") },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TrekCard(
    trek: TrekItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TrekTheme.SurfaceElevated),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column {
            // Hero image placeholder with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                TrekTheme.PrimaryGreen,
                                TrekTheme.AccentGreen
                            )
                        )
                    )
            ) {
                // Trek name overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = trek.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = trek.region,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                // Difficulty badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = getDifficultyColor(trek.difficulty).copy(alpha = 0.9f)
                ) {
                    Text(
                        text = trek.difficulty,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Stats row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TrekStat(
                    icon = Icons.Filled.Route,
                    value = "${trek.distanceKm}km"
                )
                TrekStat(
                    icon = Icons.Filled.Schedule,
                    value = "${trek.durationDays}d"
                )
                TrekStat(
                    icon = Icons.Filled.Landscape,
                    value = "${trek.maxElevation}m"
                )

                // Download status
                if (trek.isDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.OfflinePin,
                            contentDescription = null,
                            tint = TrekTheme.SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrekTheme.SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = TrekTheme.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${trek.fileSizeMb}MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TrekTheme.TextSecondary
                        )
                    }
                }
            }

            // Description
            Text(
                text = trek.description,
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TrekTheme.TextSecondary,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun TrekStat(
    icon: ImageVector,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TrekTheme.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TrekTheme.TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

fun getDifficultyColor(difficulty: String): Color {
    return when (difficulty.lowercase()) {
        "easy"     -> TrekTheme.DifficultyEasy
        "moderate" -> TrekTheme.DifficultyModerate
        "hard"     -> TrekTheme.DifficultyHard
        else       -> Color.Gray
    }
}