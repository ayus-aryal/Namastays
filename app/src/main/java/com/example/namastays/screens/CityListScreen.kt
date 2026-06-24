// ── CityListScreen.kt ────────────────────────────────────────────────────────

package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.NamastaysApp
import com.example.namastays.data.CityPreferences
import com.example.namastays.dto.CityResponse
import com.example.namastays.viewmodel.CityUiState
import com.example.namastays.viewmodel.CityViewModel
import kotlinx.coroutines.launch

private val RedAccent   = Color(0xFFE53935)
private val CardOverlay = listOf(Color.Transparent, Color(0x33000000), Color(0xDD000000))

@Composable
fun CityListScreen(
    navController: NavController,
    viewModel: CityViewModel = run {
        val app = LocalContext.current.applicationContext as NamastaysApp
        viewModel(factory = CityViewModel.Factory(app.deps.cityRepository))
    }) {
    val context         = LocalContext.current
    val cityPreferences = remember { CityPreferences(context) }
    val scope           = rememberCoroutineScope()

    // Collect the sealed UiState — single source of truth
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }

    // Derive the city list from the current state for filtering
    val cities = when (val s = uiState) {
        is CityUiState.Success -> s.cities
        else                   -> emptyList()
    }

    val filteredCities = remember(cities, searchQuery.text) {
        if (searchQuery.text.isBlank()) cities
        else cities.filter {
            it.name.contains(searchQuery.text, ignoreCase = true) ||
                    it.state?.contains(searchQuery.text, ignoreCase = true) == true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Column(
            modifier = Modifier.padding(
                start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(RedAccent, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text         = "DISCOVER",
                    fontFamily   = PlusJakartaSans,
                    fontWeight   = FontWeight.Bold,
                    fontSize     = 11.sp,
                    letterSpacing = 2.sp,
                    color        = RedAccent
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text         = "Explore Nepal",
                fontFamily   = PlusJakartaSans,
                fontWeight   = FontWeight.ExtraBold,
                fontSize     = 32.sp,
                lineHeight   = 38.sp,
                letterSpacing = (-0.5).sp,
                color        = Color(0xFF0D0D0D)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = "Choose your destination",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize   = 14.sp,
                lineHeight = 20.sp,
                color      = Color(0xFF888888)
            )
        }

        // ── Search bar ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = Color(0xFFAAAAAA),
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    textStyle     = androidx.compose.ui.text.TextStyle(
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 14.sp,
                        color      = Color(0xFF111111)
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.text.isEmpty()) {
                            Text(
                                text       = "Search cities...",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Normal,
                                fontSize   = 14.sp,
                                color      = Color(0xFFBBBBBB)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Body ─────────────────────────────────────────────────────────────
        when (val state = uiState) {
            is CityUiState.Loading -> CityGridSkeleton()

            is CityUiState.Error -> CityErrorState(
                message = state.message,
                onRetry = { viewModel.retry() }
            )

            is CityUiState.Success -> when {
                state.cities.isEmpty() -> CityEmptyState()

                filteredCities.isEmpty() -> CityNoResultsState(query = searchQuery.text)

                else -> {
                    LazyVerticalGrid(
                        columns             = GridCells.Fixed(2),
                        contentPadding      = PaddingValues(
                            start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredCities, key = { it.slug }) { city ->
                            CityGridCard(city = city) {
                                scope.launch {
                                    cityPreferences.saveCity(
                                        slug = city.slug,
                                        name = city.name
                                    )
                                }
                                navController.navigate("places/${city.slug}") {
                                    popUpTo("cities") { inclusive = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── City grid card ────────────────────────────────────────────────────────────

@Composable
fun CityGridCard(city: CityResponse, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        if (!city.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model              = city.imageUrl,
                contentDescription = city.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFDDDDDD)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.LocationCity,
                    contentDescription = null,
                    tint               = Color(0xFFAAAAAA),
                    modifier           = Modifier.size(40.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = CardOverlay))
        )

        if (!city.state.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text       = city.state,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 10.sp,
                    color      = Color.White
                )
            }
        }

        Text(
            text       = city.name,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            fontSize   = 16.sp,
            lineHeight = 21.sp,
            color      = Color.White,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
        )
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────────

@Composable
private fun CityGridSkeleton() {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        contentPadding        = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled     = false
    ) {
        items(6) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE8E8E8), Color(0xFFF5F5F5), Color(0xFFE8E8E8))
                        )
                    )
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun CityEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.LocationCity,
                    contentDescription = null,
                    tint               = Color(0xFFAAAAAA),
                    modifier           = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("No cities available", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF333333))
            Spacer(Modifier.height(6.dp))
            Text("Check back later", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
        }
    }
}

// ── No results state ──────────────────────────────────────────────────────────

@Composable
private fun CityNoResultsState(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No results found", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF333333))
            Spacer(Modifier.height(6.dp))
            Text("No cities match \"$query\"", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
        }
    }
}

// ── Error state ───────────────────────────────────────────────────────────────

@Composable
private fun CityErrorState(message: String, onRetry: () -> Unit) {
    val isNetworkError = listOf(
        "unable to resolve", "failed to connect", "timeout", "network", "no address"
    ).any { message.contains(it, ignoreCase = true) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.padding(horizontal = 40.dp)
        ) {
            Box(modifier = Modifier.size(72.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("⚠️", fontSize = 32.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text       = if (isNetworkError) "No internet connection" else "Something went wrong",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 17.sp,
                color      = Color(0xFF111111),
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = if (isNetworkError) "Check your connection and try again" else "We couldn't load the city list",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize   = 14.sp,
                color      = Color(0xFF888888),
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(12.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Try again", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "City List Screen")
@Composable
fun CityListScreenPreview() {
    val sampleCities = listOf(
        CityResponse(slug = "pokhara",   name = "Pokhara",       state = "Hills",     imageUrl = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400"),
        CityResponse(slug = "kathmandu", name = "Kathmandu",     state = "Heritage",  imageUrl = "https://images.unsplash.com/photo-1544735716-392fe2489ffa?w=400"),
        CityResponse(slug = "namche",    name = "Namche Bazaar", state = "Mountains", imageUrl = "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=400"),
        CityResponse(slug = "chitwan",   name = "Chitwan",       state = "Terai",     imageUrl = "https://images.unsplash.com/photo-1549366021-9f761d450615?w=400"),
        CityResponse(slug = "mustang",   name = "Mustang",       state = "Mountains", imageUrl = "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=400"),
        CityResponse(slug = "bandipur",  name = "Bandipur",      state = "Hills",     imageUrl = "https://images.unsplash.com/photo-1455156218388-5e61b526818b?w=400"),
    )
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(3.dp).height(16.dp).background(RedAccent, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DISCOVER", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = RedAccent)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Explore Nepal", fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp, color = Color(0xFF0D0D0D))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Choose your destination", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = Color(0xFF888888))
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Search cities...", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = Color(0xFFBBBBBB))
                }
            }
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns               = GridCells.Fixed(2),
                contentPadding        = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sampleCities) { city -> CityGridCard(city = city) {} }
            }
        }
    }
}