package com.example.namastays.trek.presentataion.list

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.namastays.api.ApiClient
import com.example.namastays.repository.NetworkResult
import com.example.namastays.repository.TrekRepository
import com.example.namastays.screens.PlusJakartaSans
import com.example.namastays.trek.TrekDatabase
import com.example.namastays.trek.domain.TrekItem
import com.example.namastays.viewmodel.TrekUiState
import com.example.namastays.viewmodel.TreksViewModel

// ── Palette ───────────────────────────────────────────────────────────────────

private object ListPalette {
    val Background    = Color(0xFFF2EDE8)
    val Surface       = Color(0xFFFFFFFF)
    val SurfaceDim    = Color(0xFFE8E2DB)
    val TextPrimary   = Color(0xFF1C1916)
    val TextSecondary = Color(0xFF7A6E66)
    val Accent        = Color(0xFFBF5B2A)
    val AccentLight   = Color(0xFFF4DDD1)
    val DiffEasy      = Color(0xFF4A9E6F)
    val DiffModerate  = Color(0xFFBF5B2A)
    val DiffHard      = Color(0xFFB83232)
    val SavedGreen    = Color(0xFF4A9E6F)
    val Divider       = Color(0xFFDDD7D0)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrekListScreen(navController: NavController) {
    val context = LocalContext.current
    val db      = remember { TrekDatabase.getInstance(context) }

    val viewModel: TreksViewModel = viewModel(
        factory = TreksViewModel.Factory(
            repository = TrekRepository(
                api           = ApiClient.trekApi,
                cacheDao      = db.trekCacheDao(),
                itineraryDao  = db.trekItineraryDao(),
                highlightDao  = db.trekHighlightDao(),
                downloadedDao = db.downloadedTrekDao()
            ),
            appContext = context.applicationContext
        )
    )

    val uiState       by viewModel.uiState.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()

    var searchQuery    by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters        = listOf("All", "Easy", "Moderate", "Hard")
    val focusManager   = LocalFocusManager.current

    val currentTreks = when (val s = uiState) {
        is TrekUiState.Loading -> emptyList()
        is TrekUiState.Success -> s.treks
        is TrekUiState.Error   -> s.treks
    }

    val filteredTreks = remember(currentTreks, selectedFilter, searchQuery, downloadedIds) {
        val afterFilter = when (selectedFilter) {
            "Downloaded" -> currentTreks.filter { it.id in downloadedIds }
            "All"        -> currentTreks
            else         -> currentTreks.filter {
                it.difficulty.equals(selectedFilter, ignoreCase = true)
            }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) afterFilter
        else afterFilter.filter { trek ->
            trek.name.contains(q, ignoreCase = true) ||
                    trek.region.contains(q, ignoreCase = true) ||
                    trek.description.contains(q, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text       = "Explore Trails",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 22.sp,
                            color      = ListPalette.TextPrimary
                        )
                        Text(
                            text       = "Nepal's finest treks",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Normal,
                            fontSize   = 12.sp,
                            color      = ListPalette.TextSecondary
                        )
                    }
                },
                actions = {
                    val isRefreshing = uiState is TrekUiState.Loading ||
                            (uiState is TrekUiState.Success &&
                                    (uiState as TrekUiState.Success).isRefreshing)
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier    = Modifier
                                .size(22.dp)
                                .padding(end = 4.dp),
                            color       = ListPalette.Accent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector        = Icons.Filled.Refresh,
                                contentDescription = "Refresh treks",
                                tint               = ListPalette.TextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ListPalette.Background
                )
            )
        },
        containerColor = ListPalette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {

            // ── Offline / error banner ────────────────────────────────────
            if (uiState is TrekUiState.Error) {
                item(key = "error_banner") {
                    val errorState = uiState as TrekUiState.Error
                    val message = when (errorState.networkResult) {
                        is NetworkResult.NoConnectivity ->
                            "No internet — showing cached trails"
                        is NetworkResult.Timeout        ->
                            "Connection timed out — showing cached trails"
                        is NetworkResult.ServerError    ->
                            "Server error — showing cached trails"
                        else -> return@item
                    }
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn() + slideInVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = ListPalette.AccentLight
                        ) {
                            Row(
                                modifier              = Modifier.padding(12.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector        = Icons.Filled.WifiOff,
                                    contentDescription = null,
                                    tint               = ListPalette.Accent,
                                    modifier           = Modifier.size(16.dp)
                                )
                                Text(
                                    text       = message,
                                    fontFamily = PlusJakartaSans,
                                    fontSize   = 12.sp,
                                    color      = ListPalette.Accent,
                                    modifier   = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick        = { viewModel.refresh() },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text       = "Retry",
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 12.sp,
                                        color      = ListPalette.Accent
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Search bar ────────────────────────────────────────────────
            item(key = "search_bar") {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = {
                        Text(
                            text       = "Search treks, regions…",
                            fontFamily = PlusJakartaSans,
                            fontSize   = 14.sp,
                            color      = ListPalette.TextSecondary
                        )
                    },
                    leadingIcon  = {
                        Icon(
                            imageVector        = Icons.Filled.Search,
                            contentDescription = null,
                            tint               = ListPalette.TextSecondary,
                            modifier           = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        // FIX: animate the clear button in/out instead of abrupt pop
                        AnimatedVisibility(
                            visible = searchQuery.isNotEmpty(),
                            enter   = fadeIn(tween(150)),
                            exit    = fadeOut(tween(150))
                        ) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector        = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint               = ListPalette.TextSecondary,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine      = true,
                    shape           = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    textStyle       = androidx.compose.ui.text.TextStyle(
                        fontFamily = PlusJakartaSans,
                        fontSize   = 14.sp,
                        color      = ListPalette.TextPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = ListPalette.Accent,
                        unfocusedBorderColor    = ListPalette.Divider,
                        focusedContainerColor   = ListPalette.Surface,
                        unfocusedContainerColor = ListPalette.Surface,
                        cursorColor             = ListPalette.Accent,
                        focusedTextColor        = ListPalette.TextPrimary,
                        unfocusedTextColor      = ListPalette.TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // ── Filter chips ──────────────────────────────────────────────
            item(key = "filter_chips") {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.padding(bottom = 12.dp)
                ) {
                    items(filters, key = { it }) { filter ->
                        FilterChipItem(
                            label    = filter,
                            selected = selectedFilter == filter,
                            onClick  = {
                                selectedFilter = filter
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }

            // ── Results count ─────────────────────────────────────────────
            if (searchQuery.isNotBlank() || selectedFilter != "All") {
                item(key = "results_count") {
                    val label = when {
                        filteredTreks.isEmpty() -> "No trails found"
                        filteredTreks.size == 1 -> "1 trail found"
                        else                    -> "${filteredTreks.size} trails found"
                    }
                    Text(
                        text       = label,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 11.sp,
                        color      = ListPalette.TextSecondary,
                        modifier   = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Skeleton loading ──────────────────────────────────────────
            if (uiState is TrekUiState.Loading) {
                items(count = 3, key = { "skeleton_$it" }) {
                    TrekCardSkeleton(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Trek cards ────────────────────────────────────────────────
            items(
                items = filteredTreks,
                key   = { it.id }
            ) { trek ->
                // FIX: slide + fade each card in as it enters the viewport
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(trek.id) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter   = fadeIn(tween(220)) + slideInVertically(
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                        initialOffsetY = { it / 6 }
                    )
                ) {
                    TrekCard(
                        trek         = trek,
                        isDownloaded = trek.id in downloadedIds,
                        // FIX: single click source — only Card handles navigation,
                        // inner "View Trek" button passes the same lambda rather than
                        // registering its own clickable on top.
                        onClick      = { navController.navigate("trek_detail/${trek.id}") },
                        modifier     = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Empty state ───────────────────────────────────────────────
            if (uiState !is TrekUiState.Loading && filteredTreks.isEmpty()) {
                item(key = "empty_state") {
                    TrekEmptyState(
                        isSearching = searchQuery.isNotBlank(),
                        onClear     = { searchQuery = ""; selectedFilter = "All" }
                    )
                }
            }
        }
    }
}

// ── Filter chip (extracted + animated) ───────────────────────────────────────

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    // Subtle scale pulse on selection
    val scale by animateFloatAsState(
        targetValue   = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label         = "chip_scale"
    )
    val bgColor by animateColorAsState(
        targetValue   = if (selected) ListPalette.Accent else ListPalette.Surface,
        animationSpec = tween(180),
        label         = "chip_bg"
    )
    val textColor by animateColorAsState(
        targetValue   = if (selected) Color.White else ListPalette.TextSecondary,
        animationSpec = tween(180),
        label         = "chip_text"
    )

    Surface(
        shape    = RoundedCornerShape(100.dp),
        color    = bgColor,
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,  // ripple would fight the scale anim
                onClick           = onClick
            )
    ) {
        Text(
            text       = label,
            fontFamily = PlusJakartaSans,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize   = 13.sp,
            color      = textColor,
            modifier   = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

// ── Skeleton card ─────────────────────────────────────────────────────────────

@Composable
private fun TrekCardSkeleton(modifier: Modifier = Modifier) {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmerTransition.animateFloat(
        initialValue  = -400f,
        targetValue   = 1200f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            ListPalette.SurfaceDim,
            ListPalette.Surface.copy(alpha = 0.9f),
            ListPalette.SurfaceDim
        ),
        start = Offset(shimmerX, 0f),
        end   = Offset(shimmerX + 400f, 200f)
    )

    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = ListPalette.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // FIX: hero shimmer shares the same card shape — no gap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(shimmerBrush)
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.32f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmerBrush)
                        .align(Alignment.End)
                )
            }
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun TrekEmptyState(isSearching: Boolean, onClear: () -> Unit) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector        = if (isSearching) Icons.Filled.SearchOff else Icons.Filled.Landscape,
            contentDescription = null,
            tint               = ListPalette.TextSecondary.copy(alpha = 0.35f),
            modifier           = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = if (isSearching) "No trails found" else "No trails available",
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 16.sp,
            color      = ListPalette.TextPrimary,
            textAlign  = TextAlign.Center
        )
        Text(
            text = if (isSearching)
                "Try adjusting your search or filters."
            else
                "Pull down to refresh or check your connection.",
            fontFamily = PlusJakartaSans,
            fontSize   = 13.sp,
            color      = ListPalette.TextSecondary,
            textAlign  = TextAlign.Center,
            lineHeight = 19.sp
        )
        if (isSearching) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick        = onClear,
                shape          = RoundedCornerShape(12.dp),
                colors         = ButtonDefaults.buttonColors(
                    containerColor = ListPalette.TextPrimary,
                    contentColor   = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    text       = "Reset Filters",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp
                )
            }
        }
    }
}

// ── Trek card ─────────────────────────────────────────────────────────────────

@Composable
fun TrekCard(
    trek:         TrekItem,
    isDownloaded: Boolean,
    onClick:      () -> Unit,
    modifier:     Modifier = Modifier
) {
    // Subtle press scale — gives tactile feedback without a competing ripple
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue   = if (isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "card_press_scale"
    )

    Card(
        modifier  = modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,   // FIX: we handle feedback via scale; no double ripple
                onClick           = onClick
            ),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = ListPalette.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {

            // ── Hero image ────────────────────────────────────────────────
            // FIX: removed the explicit .clip() on the Box — the Card's own
            // RoundedCornerShape(16dp) clips all children, so there is no
            // white gap between the bottom of the image and the card edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                // Gradient placeholder — always present, image layers on top
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B7355), Color(0xFF5C4A32))
                            )
                        )
                )

                if (!trek.thumbnailUrl.isNullOrBlank()) {
                    // FIX: track painter state correctly; Loading ≠ Empty
                    var painterState by remember {
                        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Loading(null))
                    }
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(trek.thumbnailUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = trek.name,
                        contentScale       = ContentScale.Crop,
                        onState            = { painterState = it },
                        modifier           = Modifier.fillMaxSize()
                    )
                    // Dim during load so the gradient placeholder shows smoothly,
                    // NOT when the image has successfully loaded.
                    if (painterState is AsyncImagePainter.State.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.12f))
                        )
                    }
                }

                // Bottom scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f  to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1.0f  to Color.Black.copy(alpha = 0.52f)
                                )
                            )
                        )
                )

                DifficultyBadge(
                    difficulty = trek.difficulty,
                    modifier   = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
            }

            // ── Text body ─────────────────────────────────────────────────
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {

                Text(
                    text       = trek.name,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = ListPalette.TextPrimary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint               = ListPalette.TextSecondary,
                        modifier           = Modifier.size(13.dp)
                    )
                    Text(
                        text       = trek.region,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = ListPalette.TextSecondary
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrekStat(icon = Icons.Filled.Route,    value = "${trek.distanceKm}km",       label = "Distance")
                    TrekStat(icon = Icons.Filled.Schedule, value = "${trek.durationDays} Days",  label = "Duration")
                    TrekStat(icon = Icons.Filled.Landscape,value = "${trek.maxElevation}m",      label = "Elevation")
                }

                Spacer(Modifier.height(12.dp))

                if (trek.description.isNotBlank()) {
                    Text(
                        text       = trek.description,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = ListPalette.TextSecondary,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // Bottom row
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloaded) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.OfflinePin,
                                contentDescription = null,
                                tint               = ListPalette.SavedGreen,
                                modifier           = Modifier.size(15.dp)
                            )
                            Text(
                                text       = "Saved offline",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 11.sp,
                                color      = ListPalette.SavedGreen
                            )
                        }
                    } else if (trek.fileSizeMb > 0) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Download,
                                contentDescription = null,
                                tint               = ListPalette.TextSecondary,
                                modifier           = Modifier.size(14.dp)
                            )
                            Text(
                                text       = "${trek.fileSizeMb} MB",
                                fontFamily = PlusJakartaSans,
                                fontSize   = 11.sp,
                                color      = ListPalette.TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // FIX: no separate clickable here — card's clickable covers the full
                    // surface, so this Surface is purely visual (no onClick registration).
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ListPalette.TextPrimary
                    ) {
                        Text(
                            text       = "View Trek",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 12.sp,
                            color      = Color.White,
                            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Difficulty badge ──────────────────────────────────────────────────────────

@Composable
private fun DifficultyBadge(difficulty: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape    = RoundedCornerShape(6.dp),
        color    = difficultyColor(difficulty)
    ) {
        Text(
            text       = difficulty.uppercase(),
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            fontSize   = 10.sp,
            color      = Color.White,
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

// ── Trek stat cell ────────────────────────────────────────────────────────────

@Composable
private fun TrekStat(icon: ImageVector, value: String, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = ListPalette.Accent,
            modifier           = Modifier.size(16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text       = value,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                color      = ListPalette.TextPrimary
            )
            Text(
                text       = label,
                fontFamily = PlusJakartaSans,
                fontSize   = 10.sp,
                color      = ListPalette.TextSecondary
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

fun difficultyColor(difficulty: String): Color = when (difficulty.lowercase()) {
    "easy"     -> ListPalette.DiffEasy
    "moderate" -> ListPalette.DiffModerate
    "hard"     -> ListPalette.DiffHard
    else       -> Color(0xFF888888)
}

fun getDifficultyColor(difficulty: String): Color = difficultyColor(difficulty)
