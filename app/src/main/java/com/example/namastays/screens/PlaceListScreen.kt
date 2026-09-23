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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.namastays.NamastaysApp
import com.example.namastays.components.AppEmptyState
import com.example.namastays.components.AppErrorState
import com.example.namastays.data.CityPreferences
import com.example.namastays.dto.CityPlacesResponse
import com.example.namastays.dto.PlaceResponse
import com.example.namastays.trek.util.toCloudinaryThumbnail
import com.example.namastays.ui.theme.AccentBlue
import com.example.namastays.ui.theme.BackgroundColor
import com.example.namastays.ui.theme.CardWhite
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PrimaryText
import com.example.namastays.ui.theme.SecondaryText
import com.example.namastays.ui.theme.SubtleText
import com.example.namastays.viewmodel.PlaceUiState
import com.example.namastays.viewmodel.PlaceViewModel
import kotlinx.coroutines.launch

// FIX #2/#3 (audit): ActivePill/PillText now alias the shared design tokens
// instead of being a third independently-hardcoded indigo definition. Kept
// as local vals (rather than replacing every call site inline) so the diff
// against the original file stays readable, but they now point at the same
// AccentBlue used by every other screen, instead of #6366F1 which — while
// close — was a different literal than AccentBlue's #4F46E5.
private val ActivePill = AccentBlue
private val PillText   = SecondaryText

// FIX #9 (audit, partial): this hardcoded guess list is a known limitation,
// not a full fix. There is no backend endpoint that returns the canonical
// set of categories for a city, so the screen still has to declare *some*
// fixed list of filter pills to show. What IS fixed here:
//   1. Matching against place.categories is now case-insensitive and
//      trims whitespace (see categoryMatches below), instead of relying on
//      exact-string equality against these exact labels.
//   2. PlaceCard now renders ALL of a place's categories as chips, not
//      just categories.firstOrNull() — a place with multiple categories no
//      longer has the rest silently dropped from the UI.
// A real fix for "this list might not match what the backend actually
// returns" requires a /cities/{slug}/categories-style endpoint — flagging
// this rather than inventing one, since that's a backend decision.
val categories = listOf("All", "Stays", "Food", "Viewpoints", "Adventure", "Parks", "Nightlife")

private fun categoryMatches(placeCategory: String, filter: String): Boolean =
    placeCategory.trim().equals(filter.trim(), ignoreCase = true)

// ── Hero state ──────────────────────────────────────────────────────────────
// A single source of truth for what PlaceListHero should render, computed
// once by the caller from uiState + heroCity, instead of the hero receiving
// two loosely-related signals (a nullable city + a derived boolean) that it
// then has to reconcile itself. Collapses what used to be an implicit,
// partially-undefined (city, isLoadingCity) matrix into three exhaustive,
// independently previewable/testable cases.
sealed interface HeroState {
    data object Loading : HeroState                                  // city/places fetch itself is in flight — no city data yet at all
    data class HasImage(val city: CityPlacesResponse) : HeroState     // city known, has a non-blank imageUrl
    data class NoImage(val city: CityPlacesResponse?) : HeroState     // city known but has no image, OR fetch failed with nothing to show
}

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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // FIX #8 (audit): the hero section previously read city/places/etc via
    // `(uiState as? PlaceUiState.Success)?.x` casts living OUTSIDE the
    // `when (uiState)` block that drove the rest of the screen. That meant
    // the hero could render its fallback (or stale leftover values) on a
    // recomposition pass where the body section was deciding something
    // entirely different from the same uiState read, because the two were
    // derived independently rather than from one shared branch.
    //
    // Fix: lastKnownCity is updated ONLY on a successful load, and is
    // explicitly what the hero renders from — including during a Loading
    // state caused by tapping a different category filter. This is a
    // deliberate "keep showing the city header you already had while a
    // re-filter is in flight" choice, not a side effect of stale-cast
    // leftovers. Flagging this as a design decision: previously, tapping a
    // category pill made the ENTIRE hero flicker to its empty-gradient
    // fallback for the duration of the reload, which looked broken — this
    // keeps the hero stable across filter changes instead.
    var lastKnownCity by remember { mutableStateOf<CityPlacesResponse?>(null) }
    LaunchedEffect(uiState) {
        if (uiState is PlaceUiState.Success) {
            lastKnownCity = (uiState as PlaceUiState.Success).city
        }
    }

    // FIX #10 (audit): selectedCategoryLocal is the source of truth for
    // which pill is highlighted and is updated the instant a pill is
    // tapped (optimistic UI), rather than waiting for uiState to resolve.
    // Combined with isLoadingNewFilter below, this lets pills show
    // immediate visual feedback on tap while also being disabled for the
    // duration of that specific load, preventing rapid-tap races where
    // multiple loadCityWithPlaces calls could land out of order against a
    // single uiState (the original screen had no tap guard at all).
    var selectedCategoryLocal by rememberSaveable { mutableStateOf<String?>(null) }
    val isLoadingNewFilter = uiState is PlaceUiState.Loading

    // FIX (this pass): reset lastKnownCity the INSTANT citySlug changes,
    // synchronously ahead of loadCityWithPlaces — not reliant on the
    // ViewModel emitting Loading fast enough. Without this, switching
    // Kathmandu -> Butwal could briefly render Butwal's slug/back-stack
    // with Kathmandu's stale hero image and name still showing, because
    // lastKnownCity previously only cleared on the NEXT Success emission
    // for whichever city that turned out to be.
    LaunchedEffect(citySlug) {
        lastKnownCity = null
        viewModel.loadCityWithPlaces(citySlug)
    }

    PlaceListContent(
        citySlug             = citySlug,
        heroCity             = lastKnownCity,
        uiState              = uiState,
        selectedCategory     = selectedCategoryLocal,
        isLoadingNewFilter   = isLoadingNewFilter,
        onBack               = { navController.popBackStack() },
        onChangeCity         = {
            scope.launch { cityPreferences.clearCity() }
            // FIX #22 (audit): was popUpTo("explore", inclusive = false).
            // After the companion fix in CityListScreen (popUpTo("explore",
            // inclusive = true) on city selection), "explore" is no longer
            // ever present in the back stack by the time the user reaches
            // PlaceListScreen — so that popUpTo target silently matched
            // nothing and did nothing (Navigation-Compose no-ops a popUpTo
            // whose target isn't currently in the stack, rather than
            // erroring). The actual goal here — discard the current
            // (now-stale) place list, and anything pushed on top of it
            // like place_detail, so Back from the city picker returns to
            // the tab root instead of bouncing into the city you just
            // abandoned — is achieved by popping the places/{citySlug}
            // route pattern itself, inclusive. Back stack after this is
            // [tabRoot, cities].
            navController.navigate("cities") {
                popUpTo("places/{citySlug}") { inclusive = true }
            }
        },
        onCategorySelected   = { category ->
            selectedCategoryLocal = category
            val filter = if (category == "All") null else category
            viewModel.loadCityWithPlaces(citySlug, filter)
        },
        onRetry              = { viewModel.loadCityWithPlaces(citySlug, selectedCategoryLocal) },
        onPlaceClick         = { place ->
            navController.navigate("place_detail/$citySlug/${place.slug}")
        }
    )
}

/**
 * FIX #21 (audit): full screen body extracted into a stateless composable
 * so @Preview can call it with hand-built sample state. PlaceListScreen
 * above is now just state collection + navigation wiring.
 */
@Composable
private fun PlaceListContent(
    citySlug: String,
    heroCity: CityPlacesResponse?,
    uiState: PlaceUiState,
    selectedCategory: String?,
    isLoadingNewFilter: Boolean,
    onBack: () -> Unit,
    onChangeCity: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onRetry: () -> Unit,
    onPlaceClick: (PlaceResponse) -> Unit
) {
    // Computed once, here — the single place that reconciles heroCity +
    // uiState into what the hero should actually show. PlaceListHero itself
    // no longer has to reason about uiState at all.
    val heroState: HeroState = when {
        heroCity != null && !heroCity.imageUrl.isNullOrBlank() -> HeroState.HasImage(heroCity)
        heroCity != null -> HeroState.NoImage(heroCity)
        uiState is PlaceUiState.Loading || uiState is PlaceUiState.Idle -> HeroState.Loading
        else -> HeroState.NoImage(null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor) // FIX #2/#3 — was a locally hardcoded #F2F2F7
    ) {
        LazyColumn {

            // ── Hero ─────────────────────────────────────────────────────────
            item {
                PlaceListHero(
                    citySlug     = citySlug,
                    heroState    = heroState,
                    onBack       = onBack,
                    onChangeCity = onChangeCity
                )
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

                        val backgroundColor by animateColorAsState(
                            targetValue = if (isSelected) ActivePill else CardWhite,
                            label       = "pillBackground"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) Color.White else PillText,
                            label       = "pillTextColor"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 1f,
                            animationSpec = spring(dampingRatio = 0.55f),
                            label = "pillScale"
                        )

                        // FIX #10 (audit): pill is disabled (non-clickable,
                        // dimmed) while a filter load is in flight, instead
                        // of being tappable at any time with no guard.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .alpha(if (isLoadingNewFilter) 0.6f else 1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    color = backgroundColor,
                                    shape = RoundedCornerShape(50)
                                )
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = if (isSelected) Color.Transparent else com.example.namastays.ui.theme.BorderColor,
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable(enabled = !isLoadingNewFilter) {
                                    onCategorySelected(category)
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
            // FIX #8 (audit): this when-block is now the ONLY place that
            // reads uiState for body content, and the hero above is driven
            // by heroState (derived above), not by re-casting uiState a
            // second time — eliminating the prior split-derivation bug.
            when (uiState) {
                is PlaceUiState.Idle,
                is PlaceUiState.Loading -> item { PlaceListSkeleton() }

                // FIX #17 (audit): typed AppError instead of a raw String;
                // FIX #18 — shared AppErrorState instead of a screen-local
                // PlaceErrorState composable.
                is PlaceUiState.Error -> item {
                    AppErrorState(error = uiState.error, onRetry = onRetry)
                }

                is PlaceUiState.Success -> {
                    val places = uiState.places
                    if (places.isEmpty()) {
                        item {
                            AppEmptyState(
                                icon     = Icons.Outlined.Explore,
                                title    = "No places found",
                                subtitle = if (selectedCategory != null)
                                    "No $selectedCategory spots in ${uiState.city.name} yet"
                                else
                                    "Nothing listed in ${uiState.city.name} yet"
                            )
                        }
                    } else {
                        items(places, key = { it.slug }) { place ->
                            PlaceCard(
                                place    = place,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            ) { onPlaceClick(place) }
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

// ── Hero ──────────────────────────────────────────────────────────────────────

@Composable
private fun PlaceListHero(
    citySlug: String,
    heroState: HeroState,
    onBack: () -> Unit,
    onChangeCity: () -> Unit
) {
    // Only used for the name/count text below — HasImage/NoImage carry a
    // city; Loading has none yet, so the title falls back to the slug.
    val city = when (heroState) {
        is HeroState.HasImage -> heroState.city
        is HeroState.NoImage  -> heroState.city
        HeroState.Loading     -> null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        when (heroState) {
            is HeroState.HasImage -> {
                // FIX #16 (audit): SubcomposeAsyncImage with an explicit error
                // branch — a failed hero load now falls back to the
                // no-image placeholder instead of rendering blank or
                // reusing the loading skeleton (which would misleadingly
                // imply a retry is already in progress).
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(heroState.city.imageUrl?.toCloudinaryThumbnail(width = 800))
                        .crossfade(300)
                        .build(),
                    contentDescription = heroState.city.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                    loading = { PlaceListHeroSkeleton() },
                    error   = { PlaceListHeroNoImage() },
                    success = { SubcomposeAsyncImageContent() }
                )
            }
            HeroState.Loading -> {
                // City/places fetch itself is still in flight (e.g. just
                // tapped "Change city") — shimmer, not the compass
                // placeholder, since content IS coming, we just don't know
                // what yet.
                PlaceListHeroSkeleton()
            }
            is HeroState.NoImage -> {
                // City resolved (or fetch failed with nothing to show) and
                // there is genuinely no image to wait for.
                PlaceListHeroNoImage()
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
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = PrimaryText,
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
                .clickable { onChangeCity() }
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

@Composable
private fun PlaceListHeroSkeleton() {
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFFD9DEE8), Color(0xFFEAEDF3), Color(0xFFD9DEE8))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shimmer)
    )
}

@Composable
private fun PlaceListHeroNoImage() {
    Box(
        modifier         = Modifier
            .fillMaxSize()
            .background(Color(0xFFE5E7EB)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = Icons.Outlined.Explore,
            contentDescription = null,
            tint               = Color(0xFF9CA3AF),
            modifier           = Modifier.size(56.dp)
        )
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
            .background(CardWhite)
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
                    // FIX #16 (audit): explicit error branch instead of a
                    // blank tile on load failure.
                    SubcomposeAsyncImage(
                        model              = place.image?.toCloudinaryThumbnail(width = 200),
                        contentDescription = place.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        loading = { PlaceCardImageSkeleton() },
                        error   = { PlaceCardImageError() },
                        success = { SubcomposeAsyncImageContent() }
                    )
                } else {
                    PlaceCardImageError()
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = place.name,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color    = PrimaryText,
                    lineHeight = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // FIX #9 (audit): previously only categories.firstOrNull()
                // was ever shown — any additional categories on a place
                // were silently invisible in the UI. Now renders a chip
                // for every category the place actually has.
                if (place.categories.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        place.categories.forEach { cat ->
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
                    }
                }

                if (!place.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text       = place.description,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        fontSize   = 12.sp,
                        color      = SecondaryText,
                        lineHeight = 17.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SubtleText, modifier = Modifier.size(20.dp))
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
                    SubcomposeAsyncImage(
                        model              = rec.logoUrl,
                        contentDescription = rec.displayName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(36.dp).clip(CircleShape),
                        loading = { RecommenderLogoFallback(rec.name) },
                        error   = { RecommenderLogoFallback(rec.name) },
                        success = { SubcomposeAsyncImageContent() }
                    )
                } else {
                    RecommenderLogoFallback(rec.name)
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

@Composable
private fun PlaceCardImageSkeleton() {
    val shimmer = Brush.linearGradient(
        listOf(Color(0xFFE8E8E8), Color(0xFFF5F5F5), Color(0xFFE8E8E8))
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(shimmer)
    )
}

@Composable
private fun PlaceCardImageError() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector        = Icons.Outlined.Explore,
            contentDescription = null,
            tint               = Color(0xFFBBBBBB),
            modifier           = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun RecommenderLogoFallback(name: String) {
    Box(
        modifier         = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .background(Color.White.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name.first().uppercaseChar().toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// ── Skeleton ──────────────────────────────────────────────────────────────────
// FIX #18 (audit): kept screen-local, same reasoning as CityGridSkeleton —
// this skeleton's shape (image + 2 text lines + pill) is specific to
// PlaceCard's layout and isn't shared with any other screen's skeleton.

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
                    .background(CardWhite)
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
            PlaceResponse(id = java.util.UUID.randomUUID(), slug = "phewa",     name = "Phewa Lake",       image = "https://images.unsplash.com/photo-1544735716-392fe2489ffa?w=400", categories = listOf("Adventure", "Viewpoints"), description = "Nepal's second largest lake with stunning Annapurna reflections."),
            PlaceResponse(id = java.util.UUID.randomUUID(), slug = "davis",     name = "Davis Falls",      image = null, categories = listOf("Adventure"), description = null),
        )
    )
    MaterialTheme {
        // FIX #21 (audit) — calls the real PlaceListContent with hand-built
        // sample state instead of a hand-duplicated layout.
        PlaceListContent(
            citySlug           = "pokhara",
            heroCity           = sampleCity,
            uiState            = PlaceUiState.Success(sampleCity, sampleCity.places, null),
            selectedCategory   = null,
            isLoadingNewFilter = false,
            onBack             = {},
            onChangeCity       = {},
            onCategorySelected = {},
            onRetry            = {},
            onPlaceClick       = {}
        )
    }
}