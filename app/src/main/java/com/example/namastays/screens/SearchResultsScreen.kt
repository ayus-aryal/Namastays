package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.viewmodel.SearchResultsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    city: String,
    navController: NavController,
    viewModel: SearchResultsViewModel = viewModel()
) {
    LaunchedEffect(city) {
        viewModel.fetchProperties(city)
    }

    val stays by remember { viewModel.stays }
    val isLoading by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Stays in $city",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = BackgroundColor
    ) { innerPadding ->

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }

            stays.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No stays found in $city",
                        color = SecondaryText,
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(stays) { stay ->
                        StayCard(
                            stay = stay,
                            onClick = {
                                navController.navigate("property_details/${stay.id}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StayCard(
    stay: PropertySearchResponse,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Color(0xFFE0E0E0))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stay.propertyName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryText
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = SecondaryText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${stay.city}, ${stay.state}",
                    color = SecondaryText,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stay.propertyType,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AccentBlue
            )

            if (!stay.propertyDescription.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stay.propertyDescription,
                    color = SecondaryText,
                    fontSize = 14.sp,
                    maxLines = 2
                )
            }
        }
    }
}