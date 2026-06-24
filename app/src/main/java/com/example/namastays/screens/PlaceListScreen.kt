// ── PlaceListScreen.kt ───────────────────────────────────────────────────────

package com.example.namastays.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.NamastaysApp
import com.example.namastays.data.CityPreferences
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.viewmodel.PlaceUiState
import com.example.namastays.viewmodel.PlaceViewModel
import kotlinx.coroutines.launch

// Brand accent — indigo, matching the rest of NamaStays' design system.
private val ActivePill = Color(0xFF6366F1)
private val PillText   = Color(0xFF4A4A4A)

val categories = listOf("All", "Stays", "Food", "Viewpoints", "Adventure", "Parks", "Nightlife")

@Composable
fun PlaceListScreen(
    navController: NavController,
    citySlug: String,
    viewModel: PlaceViewModel = run {
        val app = LocalContext.current.applicationContext as NamastaysApp
        viewModel(factory = PlaceViewModel.Factory(app.deps.placeRepository))
    }
) {
    val context         = LocalContext.current
    val cityPreferences = remember { CityPreferences(context) }
    val scope           = rememberCoroutineScope()

    // Single StateFlow — no more individual property reads
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Derive display values from state — avoids stale reads
    val city             = (uiState as? PlaceUiState.Success)?.city
    val places           = (uiState as? PlaceUiState.Success)?.places ?: emptyList()
    val selectedCategory = (uiState as? PlaceUiState.Success)?.selectedCategory
    val isLoading        = uiState is PlaceUiState.Loading
    val error            = (uiState as? PlaceUiState.Error)?.message

    LaunchedEffect(citySlug) {
        viewModel.loadCityWithPlaces(citySlug)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
    ) {
        LazyColumn {

            // ── Hero ─────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    if (!city?.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model              = city?.imageUrl,
                            contentDescription = city?.name,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier         = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF4A80F0), Color(0xFF1A3A6B)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.Explore,
                                contentDescription = null,
                                tint               = Color.White.copy(alpha = 0.4f),
                                modifier           = Modifier.size(64.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f  to Color.Black.copy(alpha = 0.25f),
                                    0.35f to Color.Transparent,
                                    1.0f  to Color.Black.copy(alpha = 0.72f)
                                )
                            )
                    )

                    // Back button
                    Box(
                        modifier = Modifier
                            .padding(top = 48.dp, start = 16.dp)
                            .size(38.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.TopStart)
                            .clickable { navController.popBackStack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = Color(0xFF111111),
                            modifier           = Modifier.size(18.dp)
                        )
                    }

                    // Change city button
                    Box(
                        modifier = Modifier
                            .padding(top = 48.dp, end = 16.dp)
                            .align(Alignment.TopEnd)
                            .shadow(6.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .clickable {
                                scope.launch { cityPreferences.clearCity() }
                                navController.navigate("cities") {
                                    popUpTo("explore") { inclusive = false }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment      = Alignment.CenterVertically,
                            horizontalArrangement  = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Outlined.SwapHoriz,
                                contentDescription = "Change city",
                                tint               = ActivePill,
                                modifier           = Modifier.size(15.dp)
                            )
                            Text(
                                text       = "Change city",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 12.sp,
                                color      = ActivePill
                            )
                        }
                    }

                    // City name + count
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 18.dp, end = 18.dp, bottom = 20.dp)
                    ) {
                        Text(
                            text       = city?.name ?: citySlug.replaceFirstChar { it.uppercase() },
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 32.sp,
                            letterSpacing = (-0.5).sp,
                            color      = Color.White,
                            lineHeight = 36.sp
                        )
                        city?.placesCount?.let { count ->
                            if (count > 0) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text       = "$count places to explore",
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Normal,
                                    fontSize   = 14.sp,
                                    color      = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Category pills ────────────────────────────────────────────────
            item {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory == category ||
                                (selectedCategory == null && category == "All")

                        // Animated background — fades between white and the indigo
                        // fill instead of snapping, so selection reads as a
                        // deliberate state change rather than a UI glitch.
                        val backgroundColor by animateColorAsState(
                            targetValue = if (isSelected) ActivePill else Color.White,
                            label       = "pillBackground"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else PillText,
                            label       = "pillTextColor"
                        )
                        // Tiny scale pop on selection — gives tactile feedback
                        // without being distracting.
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 1f,
                            animationSpec = spring(dampingRatio = 0.55f),
                            label = "pillScale"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clip(RoundedCornerShape(50))
                                .background(
                                    color = backgroundColor,
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = if (isSelected) Color.Transparent else Color(0xFFE0E0E0),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable {
                                    val filter = if (category == "All") null else category
                                    viewModel.loadCityWithPlaces(citySlug, filter)
                                }
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text       = category,
                                fontFamily = PlusJakartaSans,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp,
                                color      = textColor
                            )
                        }
                    }
                }
            }

            // ── Body ─────────────────────────────────────────────────────────
            when {
                isLoading -> item { PlaceListSkeleton() }

                error != null -> item {
                    PlaceErrorState(
                        message = error,
                        onRetry = { viewModel.loadCityWithPlaces(citySlug, selectedCategory) }
                    )
                }

                places.isEmpty() -> item {
                    PlaceEmptyState(
                        selectedCategory = selectedCategory,
                        cityName         = city?.name
                    )
                }

                else -> {
                    items(places, key = { it.slug }) { place ->
                        PlaceCard(
                            place    = place,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            navController.navigate("place_detail/$citySlug/${place.slug}")
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ── Place card ────────────────────────────────────────────────────────────────

@Composable
fun PlaceCard(
    place: PlaceResponse,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onClick() }
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEEEEEE))
            ) {
                if (!place.image.isNullOrBlank()) {
                    AsyncImage(
                        model              = place.image,
                        contentDescription = place.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Outlined.Explore, contentDescription = null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(28.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = place.name,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color    = Color(0xFF0D0D0D),
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                place.categories.firstOrNull()?.let { cat ->
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFEEF0FF), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text       = cat.replaceFirstChar { it.uppercase() },
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 11.sp,
                            color      = ActivePill
                        )
                    }
                }

                if (!place.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text       = place.description,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 12.sp,
                        color      = Color(0xFF888888),
                        lineHeight = 17.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
        }

        place.recommender?.let { rec ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(ActivePill)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!rec.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = rec.logoUrl,
                        contentDescription = rec.displayName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(36.dp).clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                            .background(Color.White.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = rec.name.first().uppercaseChar().toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Text(
                    text = buildAnnotatedString {
                        append("Recommended by ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(rec.displayName) }
                    },
                    fontFamily = PlusJakartaSans,
                    fontSize   = 12.sp,
                    color      = Color.White
                )
            }
        }
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────────

@Composable
private fun PlaceListSkeleton() {
    val shimmer = Brush.linearGradient(listOf(Color(0xFFE8E8E8), Color(0xFFF5F5F5), Color(0xFFE8E8E8)))
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier            = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        repeat(4) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(160.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(shimmer))
                    Box(Modifier.width(70.dp).height(20.dp).clip(RoundedCornerShape(50.dp)).background(Color(0xFFEEEEEE)))
                    Box(Modifier.width(200.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)))
                    Box(Modifier.width(140.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)))
                }
            }
        }
    }
}

// ── Empty / Error states ──────────────────────────────────────────────────────

@Composable
private fun PlaceEmptyState(selectedCategory: String?, cityName: String?) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 40.dp, end = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(72.dp).background(Color(0xFFF3F4F6), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Outlined.Explore, contentDescription = null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No places found", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF333333))
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (selectedCategory != null) "No $selectedCategory spots in ${cityName ?: "this city"} yet"
                else "Nothing listed in ${cityName ?: "this city"} yet",
                fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = Color(0xFF888888), textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlaceErrorState(message: String, onRetry: () -> Unit) {
    val isNetworkError = listOf("unable to resolve", "failed to connect", "timeout", "network", "no address").any { message.contains(it, ignoreCase = true) }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp, start = 40.dp, end = 40.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(72.dp).background(Color(0xFFFEF2F2), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("⚠️", fontSize = 32.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(if (isNetworkError) "No internet connection" else "Couldn't load places", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFF111111), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(if (isNetworkError) "Check your connection and try again" else "Something went wrong on our end", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = Color(0xFF888888), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ActivePill, contentColor = Color.White), modifier = Modifier.height(48.dp)) {
                Text("Try again", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Place List Screen")
@Composable
fun PlaceListScreenPreview() {
    val sampleCity = CityPlacesResponse(
        name = "Pokhara", slug = "pokhara",
        imageUrl = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800",
        placesCount = 24,
        places = listOf(
            PlaceResponse(id = java.util.UUID.randomUUID(), slug = "sarangkot", name = "Sarangkot Sunrise", image = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400", categories = listOf("Viewpoints"), description = "World-class panoramic sunrise over the Annapurna range."),
            PlaceResponse(id = java.util.UUID.randomUUID(), slug = "phewa",     name = "Phewa Lake",       image = "https://images.unsplash.com/photo-1544735716-392fe2489ffa?w=400", categories = listOf("Adventure"), description = "Nepal's second largest lake with stunning Annapurna reflections."),
            PlaceResponse(id = java.util.UUID.randomUUID(), slug = "davis",     name = "Davis Falls",      image = null, categories = listOf("Adventure"), description = null),
        )
    )
    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                AsyncImage(model = sampleCity.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color.Black.copy(0.25f), 0.35f to Color.Transparent, 1.0f to Color.Black.copy(0.72f))))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 20.dp)) {
                    Text(sampleCity.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.5).sp, color = Color.White)
                    Text("${sampleCity.placesCount} places to explore", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = Color.White.copy(0.85f))
                }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    val sel = cat == "All"
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.background(if (sel) ActivePill else Color.White, RoundedCornerShape(50)).then(if (!sel) Modifier.border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(50)) else Modifier).padding(horizontal = 18.dp, vertical = 9.dp)) {
                        Text(cat, fontFamily = PlusJakartaSans, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp, color = if (sel) Color.White else PillText)
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sampleCity.places) { place -> PlaceCard(place = place) {} }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}