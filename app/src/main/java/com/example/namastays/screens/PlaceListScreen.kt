package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.viewmodel.PlaceViewModel

val categories = listOf("All", "Food", "Hikes", "Parks", "Nightlife", "Adventure")

@Composable
fun PlaceListScreen(
    navController: NavController,
    citySlug: String,
    cityDisplayName: String = citySlug.replaceFirstChar { it.uppercase() },
    viewModel: PlaceViewModel = viewModel()
) {
    val places = viewModel.places
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    val selectedCategory = viewModel.selectedCategory

    LaunchedEffect(citySlug) {
        viewModel.loadPlaces(citySlug)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // ── HEADER ──────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier
                    .size(24.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = cityDisplayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)

        // ── CATEGORY PILLS ───────────────────────────────────────
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category ||
                        (selectedCategory == null && category == "All")

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(
                            color = if (isSelected) Color(0xFF4A80F0) else Color.Transparent,
                            shape = RoundedCornerShape(50)
                        )
                        .then(
                            if (!isSelected) Modifier.border(
                                width = 1.dp,
                                color = Color(0xFFDDDDDD),
                                shape = RoundedCornerShape(50)
                            ) else Modifier
                        )
                        .clickable {
                            val filter = if (category == "All") null else category
                            viewModel.loadPlaces(citySlug, filter)
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.White else Color.DarkGray,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // ── CONTENT ──────────────────────────────────────────────
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = Color.Red)
                }
            }
            places.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No places found in this city")
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(places) { place ->
                        PlaceCard(place) {
                            navController.navigate("place_detail/$citySlug/${place.slug}")                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceCard(
    place: PlaceResponse,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        // ── IMAGE WITH BADGE ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFDDDDDD))
        ) {
            if (!place.image.isNullOrBlank()) {
                AsyncImage(
                    model = place.image,
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Category badge (top-left)
            place.categories.firstOrNull()?.let { cat ->
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopStart)
                        .background(Color.White, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ── TITLE ────────────────────────────────────────────────
        Text(
            text = place.name,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}