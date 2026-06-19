package com.example.namastays.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.viewmodel.SearchResultsUiState
import com.example.namastays.viewmodel.SearchResultsViewModel
import kotlin.math.roundToInt

// ── Color tokens (matching HTML design exactly) ───────────────────────────────
private val PageBackground      = Color(0xFFF7F8FA)   // body bg
//private val CardWhite           = Color(0xFFFFFFFF)
private val PrimaryIndigo       = Color(0xFF4648D4)   // --primary: #4648d4
private val PrimaryContainer    = Color(0xFF6063EE)   // --primary-container
private val SurfaceContainer    = Color(0xFFEFECF8)   // --surface-container (amenity chip bg)
private val SurfaceVariant      = Color(0xFFE4E1ED)   // --surface-variant
private val OutlineVariant      = Color(0xFFC7C4D7)   // --outline-variant (border)
private val OnSurface           = Color(0xFF1B1B23)   // --on-surface
private val OnSurfaceVariant    = Color(0xFF464554)   // --on-surface-variant
private val OutlineColor        = Color(0xFF767586)   // --outline
private val StarAmber           = Color(0xFF703700)   // --on-tertiary-fixed-variant
private val FilterBg            = Color(0xFFF3F4F6)   // chip unselected / icon button bg
private val FilterBorder        = Color(0xFFE5E7EB)   // chip border
private val ShimmerBase         = Color(0xFFF0F0F0)
private val ShimmerHighlight    = Color(0xFFF8F8F8)

// Aliases for readability
//private val PrimaryText    = OnSurface
//private val SecondaryText  = OnSurfaceVariant
//private val SubtleText     = OutlineColor
//private val DestructiveRed = Color(0xFFBA1A1A)

// ── Font family ───────────────────────────────────────────────────────────────
// Replace with your actual PlusJakartaSans font family reference
// import com.example.namastays.ui.theme.PlusJakartaSans
// ── Sort options ──────────────────────────────────────────────────────────────
private enum class SortOption(val label: String) {
    RECOMMENDED("Recommended"),
    PRICE_LOW("Price: Low to High"),
    PRICE_HIGH("Price: High to Low"),
    NAME_AZ("Name: A → Z"),
    NAME_ZA("Name: Z → A"),
}

// ── Filter chip data ──────────────────────────────────────────────────────────
private data class FilterChip(val label: String, val isDropdown: Boolean = false)

private val filterChips = listOf(
    FilterChip("All"),
    FilterChip("Hotel"),
    FilterChip("Homestay"),
    FilterChip("Price", isDropdown = true),
)

// ── Shimmer brush ─────────────────────────────────────────────────────────────
//@Composable
//private fun shimmerBrush(): Brush {
//    val transition = rememberInfiniteTransition(label = "shimmer")
//    val translateAnim by transition.animateFloat(
//        initialValue   = 0f,
//        targetValue    = 1000f,
//        animationSpec  = infiniteRepeatable(tween(1200, easing = LinearEasing)),
//        label          = "shimmerTranslate"
//    )
//    return Brush.linearGradient(
//        colors     = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
//        start      = Offset(translateAnim - 300f, 0f),
//        end        = Offset(translateAnim, 0f)
//    )
//}

// ── Skeleton Card ─────────────────────────────────────────────────────────────
@Composable
private fun SkeletonStayCard() {
    val brush = shimmerBrush()
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Image placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)   // h-48
                    .background(brush)
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(Modifier.width(50.dp).height(14.dp).clip(RoundedCornerShape(50.dp)).background(brush))
                    Box(Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(50.dp)).background(brush))
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(0.75f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(0.5f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) {
                        Box(Modifier.width(56.dp).height(24.dp).clip(RoundedCornerShape(8.dp)).background(brush))
                    }
                }
            }
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyStateView(city: String) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape).background(FilterBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.SearchOff, null, tint = SubtleText, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No stays found in $city", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText)
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "Try adjusting your filters or searching\na different city in Nepal.",
            fontSize  = 14.sp,
            color     = SecondaryText,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// ── Error State ───────────────────────────────────────────────────────────────
@Composable
private fun ErrorStateView(message: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFFEF2F2)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.WifiOff, null, tint = DestructiveRed, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Something went wrong", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = SecondaryText, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick        = onRetry,
            shape          = RoundedCornerShape(12.dp),
            colors         = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp), tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Try Again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = PlusJakartaSans)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    city: String,
    navController: NavController,
    viewModel: SearchResultsViewModel = viewModel()
) {
    var currentCity by remember { mutableStateOf(city) }
    LaunchedEffect(currentCity) { viewModel.fetchProperties(currentCity) }

    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val stays        = (uiState as? SearchResultsUiState.Success)?.stays ?: emptyList()
    val isLoading    = uiState is SearchResultsUiState.Loading
    val errorMessage = (uiState as? SearchResultsUiState.Error)?.message

    var selectedFilter    by remember { mutableStateOf("All") }
    var selectedSort      by remember { mutableStateOf(SortOption.RECOMMENDED) }
    var priceRangeMin     by remember { mutableFloatStateOf(0f) }
    var priceRangeMax     by remember { mutableFloatStateOf(50_000f) }
    var activePriceFilter by remember { mutableStateOf(false) }
    var showPriceSheet    by remember { mutableStateOf(false) }
    var showSortSheet     by remember { mutableStateOf(false) }
    var tempMin           by remember { mutableFloatStateOf(priceRangeMin) }
    var tempMax           by remember { mutableFloatStateOf(priceRangeMax) }

    val maxPriceInData = remember(stays) {
        stays.maxOfOrNull { it.startingPrice?.toFloatOrNull() ?: 0f } ?: 50_000f
    }

    val filteredStays = remember(stays, selectedFilter, selectedSort, activePriceFilter, priceRangeMin, priceRangeMax) {
        stays
            .filter { stay ->
                when (selectedFilter) {
                    "Hotel"    -> stay.propertyType.equals("Hotel",    ignoreCase = true)
                    "Homestay" -> stay.propertyType.equals("Homestay", ignoreCase = true)
                    else       -> true
                }
            }
            .filter { stay ->
                if (!activePriceFilter) return@filter true
                val price = stay.startingPrice?.toFloatOrNull() ?: return@filter true
                price in priceRangeMin..priceRangeMax
            }
            .sortedWith(
                when (selectedSort) {
                    SortOption.PRICE_LOW  -> compareBy { it.startingPrice?.toFloatOrNull() ?: Float.MAX_VALUE }
                    SortOption.PRICE_HIGH -> compareByDescending { it.startingPrice?.toFloatOrNull() ?: 0f }
                    SortOption.NAME_AZ    -> compareBy { it.propertyName }
                    SortOption.NAME_ZA    -> compareByDescending { it.propertyName }
                    else                  -> compareBy { 0 }
                }
            )
    }

    // Price bottom sheet
    if (showPriceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPriceSheet = false },
            containerColor   = CardWhite,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            PriceRangeSheetContent(
                min        = tempMin,
                max        = tempMax,
                upperBound = maxPriceInData.coerceAtLeast(50_000f),
                onMinChange = { tempMin = it },
                onMaxChange = { tempMax = it },
                onApply = {
                    priceRangeMin     = tempMin
                    priceRangeMax     = tempMax
                    activePriceFilter = true
                    showPriceSheet    = false
                    selectedFilter    = "Price"
                },
                onClear = {
                    priceRangeMin     = 0f
                    priceRangeMax     = maxPriceInData.coerceAtLeast(50_000f)
                    activePriceFilter = false
                    showPriceSheet    = false
                    if (selectedFilter == "Price") selectedFilter = "All"
                }
            )
        }
    }

    // Sort bottom sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor   = CardWhite,
            shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            SortSheetContent(
                selected = selectedSort,
                onSelect = { option ->
                    selectedSort  = option
                    showSortSheet = false
                }
            )
        }
    }

    Scaffold(
        containerColor = PageBackground,
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets(0, 0, 0, 0))
        ) {
            // ── Top AppBar: white card with back + search pill + map icon ──
            SearchTopBar(
                city     = currentCity,
                onBack   = { navController.popBackStack() },
                onSearch = { newCity ->
                    currentCity       = newCity
                    selectedFilter    = "All"
                    activePriceFilter = false
                }
            )

            // ── Filter Row ─────────────────────────────────────────────────
            FilterRow(
                selectedFilter   = selectedFilter,
                onFilterSelected = { label ->
                    when (label) {
                        "Price" -> {
                            tempMin        = priceRangeMin
                            tempMax        = priceRangeMax
                            showPriceSheet = true
                        }
                        "Sort" -> {
                            showSortSheet = true
                        }
                        else -> {
                            selectedFilter    = label
                            activePriceFilter = false
                        }
                    }
                }
            )

            // ── Content area ───────────────────────────────────────────────
            when {
                isLoading -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding      = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        items(3) { SkeletonStayCard() }
                    }
                }

                errorMessage != null -> {
                    ErrorStateView(
                        message = errorMessage ?: "We couldn't load stays. Please check your connection.",
                        onRetry = { viewModel.fetchProperties(currentCity) }
                    )
                }

                filteredStays.isEmpty() && !isLoading -> {
                    EmptyStateView(city = currentCity)
                }

                else -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding      = PaddingValues(top = 12.dp, bottom = 24.dp)
                    ) {
                        items(filteredStays) { stay ->
                            StayCard(
                                stay    = stay,
                                onClick = {
                                    stay.id?.let { navController.navigate("property_details/$it") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


// ── Search Top Bar ────────────────────────────────────────────────────────────
@Composable
private fun SearchTopBar(
    city: String,
    onBack: () -> Unit,
    onSearch: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var query     by remember(city) { mutableStateOf(city) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = CardWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Back button — circle, grey fill ───────────────────────────
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FilterBg)
                    .clickable {
                        if (isEditing) { query = city; isEditing = false } else onBack()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (isEditing) "Cancel" else "Back",
                    tint               = OnSurface,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // ── Search pill — flex-1, grey fill, rounded-full ─────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(FilterBg)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isEditing) {
                    BasicTextField(
                        value           = query,
                        onValueChange   = { query = it },
                        singleLine      = true,
                        textStyle       = androidx.compose.ui.text.TextStyle(
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = OnSurface,
                            fontFamily = PlusJakartaSans
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val trimmed = query.trim()
                                if (trimmed.isNotEmpty()) { isEditing = false; onSearch(trimmed) }
                            }
                        ),
                        modifier      = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Search, null, tint = OutlineColor, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text("Search city in Nepal…", fontSize = 15.sp, color = SubtleText,                                             fontFamily = PlusJakartaSans)
                                    }
                                    innerTextField()
                                }
                                if (query.isNotEmpty()) {
                                    Icon(
                                        imageVector        = Icons.Outlined.Cancel,
                                        contentDescription = "Clear",
                                        tint               = SecondaryText,
                                        modifier           = Modifier.size(18.dp).clickable { query = "" }
                                    )
                                }
                            }
                        }
                    )
                } else {
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { isEditing = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = OutlineColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text       = "$city, Nepal",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = OnSurface,
                            modifier   = Modifier.weight(1f),
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Filter Row ────────────────────────────────────────────────────────────────
@Composable
private fun FilterRow(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(top = 8.dp),
        color    = PageBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .height(52.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Tune (filter settings) icon button + right divider ────────
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, OutlineVariant, CircleShape)
                        .background(CardWhite)
                        .clickable { /* open advanced filters */ },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Tune, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(OutlineVariant.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(4.dp))
            }

            // ── Filter chips ──────────────────────────────────────────────
            filterChips.forEach { chip ->
                val isSelected = selectedFilter == chip.label
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) PrimaryIndigo else CardWhite,
                    label       = "chipBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else OnSurfaceVariant,
                    label       = "chipText"
                )

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bgColor)
                        .then(
                            if (!isSelected) Modifier.border(1.dp, OutlineVariant, CircleShape)
                            else Modifier
                        )
                        .clickable { onFilterSelected(chip.label) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = chip.label,
                        fontSize   = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = textColor,
                        letterSpacing = 0.01.sp,
                        fontFamily = PlusJakartaSans
                    )
                    if (chip.isDropdown) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector        = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint               = textColor,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Sort chip (separate, with dropdown arrow) ─────────────────
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CardWhite)
                    .border(1.dp, OutlineVariant, CircleShape)
                    .clickable { onFilterSelected("Sort") }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sort", fontSize = 12.sp, color = OnSurfaceVariant, letterSpacing = 0.01.sp, fontFamily = PlusJakartaSans)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── Price Range Sheet ─────────────────────────────────────────────────────────
@Composable
private fun PriceRangeSheetContent(
    min: Float, max: Float, upperBound: Float,
    onMinChange: (Float) -> Unit, onMaxChange: (Float) -> Unit,
    onApply: () -> Unit, onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 36.dp)
    ) {
        Text("Filter by Price", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PriceBox(label = "Min", value = "NPR ${min.roundToInt()}")
            PriceBox(label = "Max", value = "NPR ${max.roundToInt()}")
        }
        Spacer(Modifier.height(24.dp))
        Text("Minimum price", fontSize = 13.sp, color = SecondaryText, modifier = Modifier.padding(bottom = 4.dp), fontFamily = PlusJakartaSans)
        Slider(
            value         = min,
            onValueChange = { if (it <= max - 500f) onMinChange(it) },
            valueRange    = 0f..upperBound,
            colors        = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo),
            modifier      = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text("Maximum price", fontSize = 13.sp, color = SecondaryText, modifier = Modifier.padding(bottom = 4.dp), fontFamily = PlusJakartaSans)
        Slider(
            value         = max,
            onValueChange = { if (it >= min + 500f) onMaxChange(it) },
            valueRange    = 0f..upperBound,
            colors        = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo),
            modifier      = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick  = onClear,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(14.dp)
            ) {
                Text("Clear", color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
            }
            Button(
                onClick  = onApply,
                modifier = Modifier.weight(1f),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
            ) {
                Text("Apply", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, fontFamily = PlusJakartaSans)
            }
        }
    }
}

@Composable
private fun PriceBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, FilterBorder, RoundedCornerShape(12.dp))
            .background(Color(0xFFF9FAFB))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = SecondaryText, fontWeight = FontWeight.Medium, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryText, fontFamily = PlusJakartaSans)
    }
}

// ── Sort Sheet ────────────────────────────────────────────────────────────────
@Composable
private fun SortSheetContent(selected: SortOption, onSelect: (SortOption) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 36.dp)
    ) {
        Text("Sort By", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryText, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(16.dp))
        SortOption.entries.forEach { option ->
            val isSelected = selected == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) FilterBg else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = option.label,
                    fontSize   = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isSelected) PrimaryText else SecondaryText,
                    fontFamily = PlusJakartaSans
                )
                if (isSelected) {
                    Box(
                        modifier         = Modifier.size(22.dp).clip(CircleShape).background(PrimaryIndigo),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
            if (option != SortOption.entries.last()) {
                HorizontalDivider(color = FilterBorder, thickness = 0.5.dp)
            }
        }
    }
}

// ── Stay Card ─────────────────────────────────────────────────────────────────
@Composable
fun StayCard(stay: PropertySearchResponse, onClick: () -> Unit) {
    val photoCount = stay.imageUrls.size
    val firstImage = stay.imageUrls.firstOrNull()

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick   = onClick
    ) {
        Column {
            // ── Hero Image: h-56 = 224dp ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                if (!firstImage.isNullOrBlank()) {
                    AsyncImage(
                        model              = firstImage,
                        contentDescription = stay.propertyName,
                        modifier           = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        contentScale       = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(FilterBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Image, null, tint = SubtleText, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("No photos yet", color = SubtleText, fontSize = 12.sp, fontFamily = PlusJakartaSans)
                        }
                    }
                }

                // ── Photo counter badge — bottom-right ────────────────────
                if (photoCount > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text       = "1 / $photoCount",
                            color      = Color.White,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }

            // ── Card Body ──────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

                // ── Type label + Rating row ────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text          = stay.propertyType.replaceFirstChar { it.uppercase() },
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.Medium,
                        color         = OnSurfaceVariant,
                        letterSpacing = 0.sp,
                        fontFamily = PlusJakartaSans
                    )

                }

                Spacer(Modifier.height(4.dp))

                // ── Property name ─────────────────────────────────────────
                Text(
                    text       = stay.propertyName,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = PrimaryText,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    fontFamily = PlusJakartaSansBold
                )

                Spacer(Modifier.height(4.dp))

                // ── Location ──────────────────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text     = "${stay.city}, ${stay.state}",
                        fontSize = 14.sp,
                        color    = OnSurfaceVariant,
                        fontFamily = PlusJakartaSans
                    )
                }

                // ── Amenity chips ─────────────────────────────────────────
                if (stay.amenities.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        stay.amenities.take(3).forEach { amenity ->
                            AmenityChip(label = amenity)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Price + Book Now ──────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.Bottom
                ) {
                    Column {
                        if (!stay.startingPrice.isNullOrBlank()) {
                            Text(
                                text     = "Starting from",
                                fontSize = 11.sp,
                                color    = SubtleText,
                                fontFamily = PlusJakartaSans
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            fontSize   = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = PrimaryIndigo,
                                            fontFamily = PlusJakartaSansBold
                                        )
                                    ) {
                                        append("NPR ${stay.startingPrice}")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            fontSize   = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color      = SubtleText,
                                            fontFamily = PlusJakartaSans
                                        )
                                    ) {
                                        append(" /night")
                                    }
                                }
                            )
                        } else {
                            Text("Price on request", fontSize = 14.sp, color = SecondaryText, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Book Now — indigo pill button
                    Button(
                        onClick        = onClick,
                        shape          = RoundedCornerShape(12.dp),
                        colors         = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        elevation      = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text       = "Book Now",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White,
                            fontFamily = PlusJakartaSans
                        )
                    }
                }
            }
        }
    }
}

// ── Amenity Chip — plain text label, surface-container bg, rounded-lg ─────────
@Composable
fun AmenityChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text       = label,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = OnSurfaceVariant,
            fontFamily = PlusJakartaSans
        )
    }
}