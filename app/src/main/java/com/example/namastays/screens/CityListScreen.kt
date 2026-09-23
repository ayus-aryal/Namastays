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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.namastays.NamastaysApp
import com.example.namastays.components.AppEmptyState
import com.example.namastays.components.AppErrorState
import com.example.namastays.data.CityPreferences
import com.example.namastays.dto.CityResponse
import com.example.namastays.ui.theme.AccentBlue
import com.example.namastays.ui.theme.BackgroundColor
import com.example.namastays.ui.theme.BorderColor
import com.example.namastays.ui.theme.CardWhite
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PrimaryText
import com.example.namastays.ui.theme.SecondaryText
import com.example.namastays.ui.theme.SubtleText
import com.example.namastays.viewmodel.CityUiState
import com.example.namastays.viewmodel.CityViewModel
import kotlinx.coroutines.launch

// FIX #2/#3 (audit): RedAccent kept as-is (it's a deliberate "DISCOVER"
// label accent, not a competing brand-blue), but the screen no longer
// declares its own blue — it now uses AccentBlue from the shared design
// tokens everywhere a blue was previously hardcoded locally.
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // FIX #15 (audit): was remember { mutableStateOf(...) } — search text was
    // lost on rotation/process death. TextFieldValue has a built-in Saver
    // registered with Compose, so rememberSaveable works directly without
    // a custom Saver implementation.
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }

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

    CityListContent(
        searchQuery       = searchQuery,
        onSearchChange     = { searchQuery = it },
        uiState            = uiState,
        filteredCities     = filteredCities,
        onRetry            = { viewModel.retry() },
        onCitySelected     = { city ->
            scope.launch {
                cityPreferences.saveCity(slug = city.slug, name = city.name)
            }
            // FIX #22 (audit): was popUpTo("cities", inclusive = true),
            // which only removed "cities" and left "explore" sitting
            // underneath it in the back stack. Pressing system back from
            // the place list then landed on ExploreScreen, whose
            // auto-redirect (ExploreViewModel already resolved to
            // CityChosen, since the city was just saved above) fired
            // immediately and navigated straight back to this exact
            // screen — turning the back button into a dead end.
            //
            // ExploreScreen is a redirect gate, never a destination a user
            // should be able to land back on once a city is resolved.
            // popUpTo("explore", inclusive = true) clears BOTH "cities"
            // and "explore" in one step (popUpTo removes everything above
            // its target route, inclusive of the target itself), matching
            // exactly what ExploreScreen's own auto-redirect already does.
            // Back stack after this is always [tabRoot, places/{slug}]
            // regardless of whether the user arrived via auto-redirect or
            // manual city selection.
            navController.navigate("places/${city.slug}") {
                popUpTo("explore") { inclusive = true }
            }
        }
    )
}

/**
 * FIX #21 (audit): the full screen body, extracted into a stateless
 * composable so the @Preview can call it directly with hand-built sample
 * state instead of re-typing the entire layout by hand. The real
 * CityListScreen above is now just state-collection + wiring.
 */
@Composable
private fun CityListContent(
    searchQuery: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    uiState: CityUiState,
    filteredCities: List<CityResponse>,
    onRetry: () -> Unit,
    onCitySelected: (CityResponse) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor) // FIX #2/#3 — was a locally hardcoded #F2F2F7
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
                color        = PrimaryText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text       = "Choose your destination",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize   = 14.sp,
                lineHeight = 20.sp,
                color      = SecondaryText
            )
        }

        // ── Search bar ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(CardWhite, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Default.Search,
                    contentDescription = null,
                    tint               = SubtleText,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value         = searchQuery,
                    onValueChange = onSearchChange,
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    textStyle     = androidx.compose.ui.text.TextStyle(
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 14.sp,
                        color      = PrimaryText
                    ),
                    decorationBox = { innerTextField ->
                        if (searchQuery.text.isEmpty()) {
                            Text(
                                text       = "Search cities...",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Normal,
                                fontSize   = 14.sp,
                                color      = SubtleText
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Body ─────────────────────────────────────────────────────────────
        when (uiState) {
            is CityUiState.Loading -> CityGridSkeleton()

            // FIX #17 (audit): branches on the typed AppError instead of a
            // raw String; FIX #18 — uses the shared AppErrorState composable
            // instead of a screen-local CityErrorState.
            is CityUiState.Error -> AppErrorState(
                error   = uiState.error,
                onRetry = onRetry
            )

            is CityUiState.Success -> when {
                uiState.cities.isEmpty() -> AppEmptyState(
                    icon     = Icons.Outlined.LocationCity,
                    title    = "No cities available",
                    subtitle = "Check back later"
                )

                filteredCities.isEmpty() -> AppEmptyState(
                    // FIX #19 (audit) — was already a real icon here
                    // (Icons.Default.Search), kept as-is; the emoji problem
                    // was specifically in the error state, now fixed via
                    // AppErrorState above.
                    icon     = Icons.Default.Search,
                    title    = "No results found",
                    subtitle = "No cities match \"${searchQuery.text}\""
                )

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
                            CityGridCard(city = city) { onCitySelected(city) }
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
        // FIX #16 (audit): previously, "no imageUrl provided" had a manual
        // fallback icon, but a *present-but-failed-to-load* image (bad URL,
        // network blip, 404, etc.) just rendered blank — no shimmer, no
        // fallback icon, since AsyncImage's placeholder/error params were
        // never supplied. SubcomposeAsyncImage now explicitly handles all
        // three states (loading / success / error) plus the original
        // no-URL case, all converging on the same fallback icon so the
        // user never sees a blank tile for any reason.
        if (!city.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model              = city.imageUrl,
                contentDescription = city.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFE8E8E8), Color(0xFFF5F5F5), Color(0xFFE8E8E8))
                                )
                            )
                    )
                },
                error = { CityImageFallback() },
                success = { SubcomposeAsyncImageContent() }
            )
        } else {
            CityImageFallback()
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

@Composable
private fun CityImageFallback() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color(0xFFDDDDDD)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.LocationCity,
            contentDescription = null,
            tint               = SubtleText,
            modifier           = Modifier.size(40.dp)
        )
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────────
// FIX #18 (audit): kept screen-local (not moved into StateComponents.kt)
// because, unlike Empty/Error, the skeleton's *shape* is genuinely specific
// to this screen's 2-column grid layout — PlaceListScreen's skeleton is a
// list of rows, not a grid, so there isn't a single shared shape to factor
// out without making either screen's skeleton look wrong.

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
        // FIX #21 (audit) — calls the real CityListContent composable with
        // hand-built sample state, instead of a hand-duplicated layout.
        CityListContent(
            searchQuery    = TextFieldValue(""),
            onSearchChange = {},
            uiState        = CityUiState.Success(sampleCities),
            filteredCities = sampleCities,
            onRetry        = {},
            onCitySelected = {}
        )
    }
}