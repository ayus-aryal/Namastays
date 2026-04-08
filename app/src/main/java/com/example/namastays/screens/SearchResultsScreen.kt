package com.example.namastays.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.dto.PropertySearchResponse
import com.example.namastays.viewmodel.SearchResultsViewModel
import kotlin.math.roundToInt

// ── Color tokens ─────────────────────────────────────────────────────────────
private val Background     = Color(0xFFF5F5F5)
private val ChipSelected   = Color(0xFF1A1A2E)
private val ChipUnselected = Color(0xFFFFFFFF)
private val ChipTextSelected = Color(0xFFFFFFFF)
private val ChipTextDefault  = Color(0xFF374151)
private val BorderColor    = Color(0xFFE5E7EB)
private val AmenityBg      = Color(0xFFF3F4F6)

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
    FilterChip("Sort",  isDropdown = true),
)

// ── Screen ────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    city: String,
    navController: NavController,
    viewModel: SearchResultsViewModel = viewModel()
) {
    // currentCity drives fetches — starts with the nav arg, updated on in-screen searches
    var currentCity by remember { mutableStateOf(city) }
    LaunchedEffect(currentCity) { viewModel.fetchProperties(currentCity) }

    val stays        by remember { viewModel.stays }
    val isLoading    by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }

    // ── Filter / sort state ───────────────────────────────────────────────────
    var selectedFilter    by remember { mutableStateOf("All") }
    var selectedSort      by remember { mutableStateOf(SortOption.RECOMMENDED) }
    var priceRangeMin     by remember { mutableFloatStateOf(0f) }
    var priceRangeMax     by remember { mutableFloatStateOf(50_000f) }
    var activePriceFilter by remember { mutableStateOf(false) }

    // Bottom-sheet visibility
    var showPriceSheet by remember { mutableStateOf(false) }
    var showSortSheet  by remember { mutableStateOf(false) }

    // Temp state while sheet is open
    var tempMin by remember { mutableFloatStateOf(priceRangeMin) }
    var tempMax by remember { mutableFloatStateOf(priceRangeMax) }

    // ── Dynamic price bounds from data ────────────────────────────────────────
    val maxPriceInData = remember(stays) {
        stays.maxOfOrNull { it.startingPrice?.toFloatOrNull() ?: 0f } ?: 50_000f
    }

    // ── Filtered + sorted list ────────────────────────────────────────────────
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
                    else                  -> compareBy { 0 } // keep original order
                }
            )
    }

    // ── Price sheet ───────────────────────────────────────────────────────────
    if (showPriceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPriceSheet = false },
            containerColor   = CardWhite
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

    // ── Sort sheet ────────────────────────────────────────────────────────────
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor   = CardWhite
        ) {
            SortSheetContent(
                selected  = selectedSort,
                onSelect  = { option ->
                    selectedSort   = option
                    showSortSheet  = false
                    selectedFilter = "Sort"
                }
            )
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(containerColor = Background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchTopBar(
                city     = currentCity,
                onBack   = { navController.popBackStack() },
                onSearch = { newCity ->
                    currentCity       = newCity
                    selectedFilter    = "All"
                    activePriceFilter = false
                }
            )

            FilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { label ->
                    when (label) {
                        "Price" -> {
                            tempMin        = priceRangeMin
                            tempMax        = priceRangeMax
                            showPriceSheet = true
                        }
                        "Sort"  -> showSortSheet = true
                        else    -> {
                            selectedFilter    = label
                            // Clear price filter when switching to type chips
                            if (label != "Price") activePriceFilter = false
                        }
                    }
                }
            )

            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentBlue)
                    }
                }

                errorMessage != null -> {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(errorMessage ?: "Something went wrong", color = Color.Red)
                    }
                }

                filteredStays.isEmpty() -> {
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No stays found in $currentCity", color = SecondaryText, fontSize = 16.sp)
                    }
                }

                else -> {
                    // Active filter summary line
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredStays.size} stays found in $currentCity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedSort != SortOption.RECOMMENDED) {
                            Text(
                                text = "· ${selectedSort.label}",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
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

// ── Search top bar ────────────────────────────────────────────────────────────
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isEditing) { query = city; isEditing = false } else onBack()
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = if (isEditing) "Cancel" else "Back",
                tint = PrimaryText
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(AmenityBg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (isEditing) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val trimmed = query.trim()
                            if (trimmed.isNotEmpty()) { isEditing = false; onSearch(trimmed) }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) {
                                    Text("Search city…", fontSize = 15.sp, color = SecondaryText)
                                }
                                innerTextField()
                            }
                            if (query.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Outlined.Cancel,
                                    contentDescription = "Clear",
                                    tint = SecondaryText,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { query = "" }
                                )
                            }
                        }
                    }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isEditing = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$city, Nepal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit search",
                        tint = SecondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            imageVector = Icons.Outlined.Map,
            contentDescription = "Map",
            tint = PrimaryText,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ── Filter row ────────────────────────────────────────────────────────────────
@Composable
private fun FilterRow(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, BorderColor, CircleShape)
                .background(CardWhite),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Filters",
                tint = PrimaryText,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            Modifier
                .width(1.dp)
                .height(28.dp)
                .background(BorderColor)
        )

        filterChips.forEach { chip ->
            val isSelected = selectedFilter == chip.label
            val bgColor by animateColorAsState(
                if (isSelected) ChipSelected else ChipUnselected, label = ""
            )
            val textColor by animateColorAsState(
                if (isSelected) ChipTextSelected else ChipTextDefault, label = ""
            )
            val borderMod = if (!isSelected)
                Modifier.border(1.dp, BorderColor, RoundedCornerShape(50))
            else Modifier

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bgColor)
                    .then(borderMod)
                    .clickable { onFilterSelected(chip.label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chip.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
                if (chip.isDropdown) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ── Price range bottom sheet ──────────────────────────────────────────────────
@Composable
private fun PriceRangeSheetContent(
    min: Float,
    max: Float,
    upperBound: Float,
    onMinChange: (Float) -> Unit,
    onMaxChange: (Float) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Text(
            text = "Price Range",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )

        Spacer(Modifier.height(20.dp))

        // Price labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceBox(label = "Min", value = "NPR ${min.roundToInt()}")
            PriceBox(label = "Max", value = "NPR ${max.roundToInt()}")
        }

        Spacer(Modifier.height(20.dp))

        // Min slider
        Text(
            text = "Minimum price",
            fontSize = 13.sp,
            color = SecondaryText,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = min,
            onValueChange = { newMin ->
                if (newMin <= max - 500f) onMinChange(newMin)
            },
            valueRange = 0f..upperBound,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor       = ChipSelected,
                activeTrackColor = ChipSelected
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Max slider
        Text(
            text = "Maximum price",
            fontSize = 13.sp,
            color = SecondaryText,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Slider(
            value = max,
            onValueChange = { newMax ->
                if (newMax >= min + 500f) onMaxChange(newMax)
            },
            valueRange = 0f..upperBound,
            steps = 0,
            colors = SliderDefaults.colors(
                thumbColor       = ChipSelected,
                activeTrackColor = ChipSelected
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    width = 1.dp
                )
            ) {
                Text("Clear", color = PrimaryText, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ChipSelected)
            ) {
                Text("Apply", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PriceBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = SecondaryText)
        Spacer(Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PrimaryText)
    }
}

// ── Sort bottom sheet ─────────────────────────────────────────────────────────
@Composable
private fun SortSheetContent(
    selected: SortOption,
    onSelect: (SortOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Text(
            text = "Sort By",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryText
        )

        Spacer(Modifier.height(16.dp))

        SortOption.entries.forEach { option ->
            val isSelected = selected == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AmenityBg else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.label,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) PrimaryText else SecondaryText
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = ChipSelected,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (option != SortOption.entries.last()) {
                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
            }
        }
    }
}

// ── Stay card ─────────────────────────────────────────────────────────────────
@Composable
fun StayCard(
    stay: PropertySearchResponse,
    onClick: () -> Unit
) {
    val photoCount = stay.imageUrls.size
    val firstImage = stay.imageUrls.firstOrNull()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        onClick = onClick
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                if (!firstImage.isNullOrBlank()) {
                    AsyncImage(
                        model = firstImage,
                        contentDescription = stay.propertyName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(AmenityBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No Image Available", color = SecondaryText)
                    }
                }

                if (photoCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "1/$photoCount",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stay.propertyType.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecondaryText,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = stay.propertyName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                Spacer(Modifier.height(5.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = SecondaryText,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${stay.city}, ${stay.state}",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (stay.amenities.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        stay.amenities.take(3).forEach { amenity ->
                            AmenityChip(label = amenity)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                HorizontalDivider(color = BorderColor, thickness = 0.8.dp)
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NPR ${stay.startingPrice}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryText
                        )
                        Text(
                            text = "per night, taxes incl.",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }

                    Button(
                        onClick = onClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChipSelected),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "View Details",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ── Amenity chip ──────────────────────────────────────────────────────────────
@Composable
fun AmenityChip(label: String) {
    val icon = when {
        label.contains("WiFi",      ignoreCase = true) -> Icons.Outlined.Wifi
        label.contains("Breakfast", ignoreCase = true) -> Icons.Outlined.FreeBreakfast
        label.contains("Parking",   ignoreCase = true) -> Icons.Outlined.LocalParking
        label.contains("Pool",      ignoreCase = true) -> Icons.Outlined.Pool
        label.contains("AC",        ignoreCase = true) -> Icons.Outlined.AcUnit
        else                                           -> Icons.Outlined.CheckCircle
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AmenityBg)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SecondaryText,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = SecondaryText,
            fontWeight = FontWeight.Medium
        )
    }
}