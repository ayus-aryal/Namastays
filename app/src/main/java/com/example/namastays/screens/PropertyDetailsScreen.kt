package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.namastays.viewmodel.PropertyDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    propertyId: String,
    navController: NavController,
    viewModel: PropertyDetailsViewModel = viewModel()
) {
    LaunchedEffect(propertyId) {
        viewModel.fetchPropertyDetails(propertyId)
    }

    val property by remember { viewModel.property }
    val isLoading by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Property Details")
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
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        color = Color.Red,
                        fontSize = 16.sp
                    )
                }
            }

            property == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Property not found",
                        color = SecondaryText,
                        fontSize = 16.sp
                    )
                }
            }

            else -> {
                val p = property!!

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .background(Color(0xFFE0E0E0))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = p.propertyName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = SecondaryText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${p.city}, ${p.state}, ${p.country}",
                            color = SecondaryText,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = p.propertyType,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentBlue
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Established: ${p.yearEstablished}",
                        fontSize = 14.sp,
                        color = SecondaryText
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Description",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = p.propertyDescription ?: "No description available",
                        color = SecondaryText,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Address",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${p.address}, ${p.city}, ${p.state}, ${p.postalCode}, ${p.country}",
                        color = SecondaryText,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    ) {
                        Text("Book Now")
                    }
                }
            }
        }
    }
}