package com.example.namastays.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

// ─── Data Models ──────────────────────────────────────────────────────────────

data class PackingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val note: String = "",
    val isChecked: Boolean = false,
    val isCustom: Boolean = false,
    val isEssential: Boolean = false
)

data class PackingCategory(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconColor: Color,
    val items: List<PackingItem>
)

enum class ChecklistFilter { ALL, UNPACKED, PACKED, ESSENTIALS }

private val allFilters = ChecklistFilter.values().toList()

// ─── Default Data ─────────────────────────────────────────────────────────────

private val defaultCategories: List<PackingCategory> by lazy {
    listOf(
        PackingCategory(
            id        = "essentials",
            name      = "Essentials",
            icon      = Icons.Outlined.Star,
            iconBg    = Color(0xFFFFFBEB),
            iconColor = Color(0xFFF59E0B),
            items     = listOf(
                PackingItem(name = "Passport and visa", isEssential = true),
                PackingItem(name = "Travel insurance docs", isEssential = true),
                PackingItem(name = "Trekking permit (TIMS card)", isEssential = true),
                PackingItem(name = "ACAP / NATT permit", isEssential = true),
                PackingItem(name = "Cash (NPR — ATMs scarce on trail)", isEssential = true),
                PackingItem(name = "Emergency contact card", isEssential = true),
            )
        ),
        PackingCategory(
            id        = "clothing",
            name      = "Clothing",
            icon      = Icons.Outlined.Checkroom,
            iconBg    = Color(0xFFF0FDF4),
            iconColor = Color(0xFF16A34A),
            items     = listOf(
                PackingItem(name = "Moisture-wicking base layer"),
                PackingItem(name = "Fleece jacket"),
                PackingItem(name = "Down jacket"),
                PackingItem(name = "Waterproof shell / rain jacket"),
                PackingItem(name = "Trekking pants", note = "×2"),
                PackingItem(name = "Thermal leggings"),
                PackingItem(name = "Wool hiking socks", note = "×4 pairs"),
                PackingItem(name = "Liner socks", note = "×3 pairs"),
                PackingItem(name = "Sun hat / wide-brim hat"),
                PackingItem(name = "Warm beanie"),
                PackingItem(name = "Gloves (liner + warm outer)"),
                PackingItem(name = "Buff / neck gaiter"),
            )
        ),
        PackingCategory(
            id        = "footwear",
            name      = "Footwear",
            icon      = Icons.Outlined.DirectionsWalk,
            iconBg    = Color(0xFFEFF6FF),
            iconColor = Color(0xFF3B82F6),
            items     = listOf(
                PackingItem(name = "Waterproof trekking boots (broken in)"),
                PackingItem(name = "Camp sandals / flip flops"),
                PackingItem(name = "Gaiters"),
                PackingItem(name = "Custom insoles"),
            )
        ),
        PackingCategory(
            id        = "camping",
            name      = "Camping",
            icon      = Icons.Outlined.Landscape,
            iconBg    = Color(0xFFF0FDF4),
            iconColor = Color(0xFF059669),
            items     = listOf(
                PackingItem(name = "Sleeping bag (−10 °C rated)"),
                PackingItem(name = "Sleeping bag liner"),
                PackingItem(name = "Trekking poles", note = "×2"),
                PackingItem(name = "Headlamp", note = "with spare batteries"),
                PackingItem(name = "Lighter / waterproof matches"),
                PackingItem(name = "Whistle"),
                PackingItem(name = "Knife / multi-tool"),
                PackingItem(name = "Tent (if camping route)"),
            )
        ),
        PackingCategory(
            id        = "electronics",
            name      = "Electronics",
            icon      = Icons.Outlined.BatteryChargingFull,
            iconBg    = Color(0xFFF0F9FF),
            iconColor = Color(0xFF0EA5E9),
            items     = listOf(
                PackingItem(name = "Phone + charger"),
                PackingItem(name = "Power bank", note = "20 000 mAh+"),
                PackingItem(name = "Camera", note = "with extra SD cards"),
                PackingItem(name = "USB-C cable", note = "×2"),
                PackingItem(name = "Solar charger"),
                PackingItem(name = "Walkie-talkie / satellite communicator"),
            )
        ),
        PackingCategory(
            id        = "safety",
            name      = "Safety",
            icon      = Icons.Outlined.HealthAndSafety,
            iconBg    = Color(0xFFFFF1F2),
            iconColor = Color(0xFFEF4444),
            items     = listOf(
                PackingItem(name = "First aid kit", isEssential = true),
                PackingItem(name = "Altitude sickness pills (Diamox)", isEssential = true),
                PackingItem(name = "Blister pads / moleskin"),
                PackingItem(name = "Emergency blanket / bivy"),
                PackingItem(name = "Sunscreen SPF 50+"),
                PackingItem(name = "Lip balm with SPF"),
                PackingItem(name = "Insect repellent"),
                PackingItem(name = "Personal prescription medicines"),
                PackingItem(name = "Pulse oximeter"),
            )
        ),
        PackingCategory(
            id        = "hydration_food",
            name      = "Hydration & Food",
            icon      = Icons.Outlined.WaterDrop,
            iconBg    = Color(0xFFEEF2FF),
            iconColor = Color(0xFF6366F1),
            items     = listOf(
                PackingItem(name = "Water bottles", note = "×2 (1 L each)"),
                PackingItem(name = "Hydration bladder", note = "2 L"),
                PackingItem(name = "Water purification tablets"),
                PackingItem(name = "Portable water filter (Sawyer/LifeStraw)"),
                PackingItem(name = "Energy bars / granola bars", note = "×10"),
                PackingItem(name = "Trail mix / nuts", note = "×500 g"),
                PackingItem(name = "Instant noodles / porridge packets"),
                PackingItem(name = "Electrolyte sachets", note = "×10"),
                PackingItem(name = "Chocolate / emergency snacks"),
            )
        ),
        PackingCategory(
            id        = "personal",
            name      = "Personal",
            icon      = Icons.Outlined.Person,
            iconBg    = Color(0xFFFDF4FF),
            iconColor = Color(0xFF9333EA),
            items     = listOf(
                PackingItem(name = "Toothbrush & toothpaste"),
                PackingItem(name = "Microfiber towel"),
                PackingItem(name = "Hand sanitiser", note = "×2"),
                PackingItem(name = "Biodegradable soap / shampoo"),
                PackingItem(name = "Toilet paper + waste bags"),
                PackingItem(name = "Wet wipes"),
                PackingItem(name = "Earplugs"),
                PackingItem(name = "Sunglasses (UV400)"),
                PackingItem(name = "Trekking journal / pen"),
            )
        ),
    )
}

// ─── UI State ─────────────────────────────────────────────────────────────────

data class PackingUiState(
    val filteredCategories : List<PackingCategory> = emptyList(),
    val totalItems         : Int                   = 0,
    val packedItems        : Int                   = 0,
    val expandedIds        : Set<String>           = emptySet(),
    val isReady            : Boolean               = false
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class)
class PackingChecklistViewModel : ViewModel() {

    private val _categories  = MutableStateFlow<List<PackingCategory>>(emptyList())
    private val _filter      = MutableStateFlow(ChecklistFilter.ALL)
    private val _search      = MutableStateFlow("")
    private val _expandedIds = MutableStateFlow<Set<String>>(emptySet())

    val search: StateFlow<String>          = _search.asStateFlow()
    val filter: StateFlow<ChecklistFilter> = _filter.asStateFlow()

    val uiState: StateFlow<PackingUiState> = combine(
        _categories,
        _filter,
        _search.debounce { query -> if (query.isEmpty()) 0L else 120L },
        _expandedIds
    ) { categories, filter, search, expandedIds ->

        val filtered = categories.mapNotNull { cat ->
            val matchedItems = cat.items.filter { item ->
                val matchesSearch = search.isBlank() ||
                        item.name.contains(search, ignoreCase = true)
                val matchesFilter = when (filter) {
                    ChecklistFilter.ALL        -> true
                    ChecklistFilter.PACKED     -> item.isChecked
                    ChecklistFilter.UNPACKED   -> !item.isChecked
                    ChecklistFilter.ESSENTIALS -> item.isEssential
                }
                matchesSearch && matchesFilter
            }
            if (matchedItems.isEmpty()) null else cat.copy(items = matchedItems)
        }

        val total  = categories.sumOf { it.items.size }
        val packed = categories.sumOf { it.items.count { i -> i.isChecked } }

        PackingUiState(
            filteredCategories = filtered,
            totalItems         = total,
            packedItems        = packed,
            expandedIds        = expandedIds,
            isReady            = true
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = PackingUiState()
        )

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val cats = defaultCategories
            _expandedIds.value = setOf(cats.first().id)
            _categories.value  = cats
        }
    }

    fun toggleItem(categoryId: String, itemId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val cats    = _categories.value
            val catIdx  = cats.indexOfFirst { it.id == categoryId }
            if (catIdx == -1) return@launch
            val cat     = cats[catIdx]
            val itemIdx = cat.items.indexOfFirst { it.id == itemId }
            if (itemIdx == -1) return@launch
            val newItems = cat.items.toMutableList()
            newItems[itemIdx] = newItems[itemIdx].copy(isChecked = !newItems[itemIdx].isChecked)
            val newCats = cats.toMutableList()
            newCats[catIdx] = cat.copy(items = newItems)
            _categories.value = newCats
        }
    }

    fun toggleExpand(categoryId: String) {
        _expandedIds.value = _expandedIds.value.let { ids ->
            if (categoryId in ids) ids - categoryId else ids + categoryId
        }
    }

    fun setFilter(f: ChecklistFilter) { _filter.value = f }
    fun setSearch(q: String)          { _search.value = q }

    fun addCustomItem(categoryId: String, name: String, note: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val cats   = _categories.value
            val catIdx = cats.indexOfFirst { it.id == categoryId }
            if (catIdx == -1) return@launch
            val newItem = PackingItem(name = name, note = note, isCustom = true)
            val newCats = cats.toMutableList()
            newCats[catIdx] = cats[catIdx].copy(items = cats[catIdx].items + newItem)
            _categories.value = newCats
        }
    }

    fun deleteItem(categoryId: String, itemId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val cats   = _categories.value
            val catIdx = cats.indexOfFirst { it.id == categoryId }
            if (catIdx == -1) return@launch
            val newCats = cats.toMutableList()
            newCats[catIdx] = cats[catIdx].copy(
                items = cats[catIdx].items.filter { it.id != itemId }
            )
            _categories.value = newCats
        }
    }

    fun resetAll() {
        viewModelScope.launch(Dispatchers.Default) {
            _categories.value = _categories.value.map { cat ->
                cat.copy(items = cat.items.map { it.copy(isChecked = false) })
            }
        }
    }
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingChecklistScreen(
    navController : NavController,
    vm            : PackingChecklistViewModel = viewModel()   // fallback for Preview
) {
    val uiState        by vm.uiState.collectAsState()
    val searchText     by vm.search.collectAsState()
    val selectedFilter by vm.filter.collectAsState()

    var showResetDialog  by remember { mutableStateOf(false) }
    var showAddItemSheet by remember { mutableStateOf(false) }

    // Plain (non-animated) progress values.
    val progress = remember(uiState.packedItems, uiState.totalItems) {
        if (uiState.totalItems == 0) 0f else uiState.packedItems.toFloat() / uiState.totalItems
    }
    val displayPercent = (progress * 100).toInt()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor      = BackgroundColor,
        topBar = {
            Surface(color = CardWhite, shadowElevation = 4.dp) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentBlue)
                    }
                    Text(
                        text       = "Packing Checklist",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSans,
                        color      = AccentBlue,
                        modifier   = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showResetDialog = true }) {
                        Text(
                            "Reset",
                            color      = AccentBlue,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = PlusJakartaSans,
                            fontSize   = 15.sp
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB is always shown — no entrance animation.
            FloatingActionButton(
                onClick        = { showAddItemSheet = true },
                containerColor = AccentBlue,
                contentColor   = Color.White,
                shape          = CircleShape,
                modifier       = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add custom item", modifier = Modifier.size(26.dp))
            }
        }
    ) { innerPadding ->

        ChecklistContent(
            uiState         = uiState,
            searchText      = searchText,
            selectedFilter  = selectedFilter,
            progress        = progress,
            displayPercent  = displayPercent,
            innerPadding    = innerPadding,
            onSearchChange  = vm::setSearch,
            onFilterChange  = vm::setFilter,
            onToggleItem    = vm::toggleItem,
            onToggleExpand  = vm::toggleExpand,
            onDeleteItem    = vm::deleteItem,
        )
    }

    if (showResetDialog) {
        ResetConfirmDialog(
            onConfirm = { vm.resetAll(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showAddItemSheet) {
        val categoryOptions = remember(uiState.filteredCategories) {
            uiState.filteredCategories.map { it.id to it.name }
        }
        AddCustomItemSheet(
            categoryOptions = categoryOptions,
            onAdd           = { categoryId, name, note ->
                vm.addCustomItem(categoryId, name, note)
                showAddItemSheet = false
            },
            onDismiss = { showAddItemSheet = false }
        )
    }
}

// ─── Checklist Content ────────────────────────────────────────────────────────

@Composable
private fun ChecklistContent(
    uiState        : PackingUiState,
    searchText     : String,
    selectedFilter : ChecklistFilter,
    progress       : Float,
    displayPercent : Int,
    innerPadding   : PaddingValues,
    onSearchChange : (String) -> Unit,
    onFilterChange : (ChecklistFilter) -> Unit,
    onToggleItem   : (String, String) -> Unit,
    onToggleExpand : (String) -> Unit,
    onDeleteItem   : (String, String) -> Unit,
) {
    LazyColumn(
        modifier            = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding      = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "search") {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value         = searchText,
                onValueChange = onSearchChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder   = {
                    Text("Search items...", fontFamily = PlusJakartaSans, color = SubtleText, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchText.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SecondaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine      = true,
                shape           = RoundedCornerShape(12.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = AccentBlue,
                    unfocusedBorderColor    = BorderColor,
                    focusedContainerColor   = CardWhite,
                    unfocusedContainerColor = CardWhite,
                    cursorColor             = AccentBlue
                ),
                textStyle       = androidx.compose.ui.text.TextStyle(
                    fontFamily = PlusJakartaSans, fontSize = 14.sp, color = PrimaryText
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Spacer(Modifier.height(12.dp))
        }

        item(key = "filters") {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allFilters, key = { it.name }) { f ->
                    val selected = selectedFilter == f
                    val label = when (f) {
                        ChecklistFilter.ALL        -> "All"
                        ChecklistFilter.UNPACKED   -> "Unpacked"
                        ChecklistFilter.PACKED     -> "Packed"
                        ChecklistFilter.ESSENTIALS -> "Essentials"
                    }
                    FilterChip(
                        selected = selected,
                        onClick  = { onFilterChange(f) },
                        label    = {
                            Text(
                                text       = label,
                                fontFamily = PlusJakartaSans,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize   = 13.sp
                            )
                        },
                        shape    = RoundedCornerShape(20.dp),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentBlue,
                            selectedLabelColor     = Color.White,
                            containerColor         = CardWhite,
                            labelColor             = SecondaryText,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled             = true,
                            selected            = selected,
                            borderColor         = BorderColor,
                            selectedBorderColor = AccentBlue,
                            borderWidth         = 1.dp,
                            selectedBorderWidth = 0.dp
                        )
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        item(key = "progress") {
            Card(
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Overall Readiness",
                                fontWeight = FontWeight.Bold,
                                fontFamily = PlusJakartaSans,
                                fontSize   = 15.sp,
                                color      = PrimaryText
                            )
                            Text(
                                "${uiState.packedItems} / ${uiState.totalItems} items packed",
                                fontFamily = PlusJakartaSans,
                                fontSize   = 12.sp,
                                color      = SecondaryText
                            )
                        }
                        Text(
                            "$displayPercent%",
                            fontWeight = FontWeight.Bold,
                            fontFamily = PlusJakartaSans,
                            fontSize   = 22.sp,
                            color      = AccentBlue
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress  = { progress },
                        modifier  = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color      = AccentBlue,
                        trackColor = Color(0xFFE0E7FF),
                        strokeCap  = StrokeCap.Round
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        items(
            items = uiState.filteredCategories,
            key   = { it.id }
        ) { category ->
            PackingCategorySection(
                category    = category,
                isExpanded  = category.id in uiState.expandedIds,
                onToggle    = { onToggleExpand(category.id) },
                onItemCheck = { itemId -> onToggleItem(category.id, itemId) },
                onDelete    = { itemId -> onDeleteItem(category.id, itemId) }
            )
            Spacer(Modifier.height(10.dp))
        }

        if (uiState.filteredCategories.isEmpty() && uiState.isReady) {
            item(key = "empty") {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint               = SubtleText,
                            modifier           = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No items found",
                            fontFamily = PlusJakartaSans,
                            color      = SecondaryText,
                            fontSize   = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// ─── Category Section ─────────────────────────────────────────────────────────

@Composable
fun PackingCategorySection(
    category    : PackingCategory,
    isExpanded  : Boolean,
    onToggle    : () -> Unit,
    onItemCheck : (String) -> Unit,
    onDelete    : (String) -> Unit
) {
    val packedCount = remember(category.items) { category.items.count { it.isChecked } }
    val totalCount  = category.items.size
    val allPacked   = packedCount == totalCount && totalCount > 0

    // Static rotation — no animation when expanding/collapsing.
    val arrowRotation = if (isExpanded) 90f else 0f

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onToggle
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(category.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint               = category.iconColor,
                    modifier           = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text       = category.name,
                fontWeight = FontWeight.SemiBold,
                fontFamily = PlusJakartaSans,
                fontSize   = 15.sp,
                color      = PrimaryText,
                modifier   = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (allPacked) Color(0xFFDCFCE7) else Color(0xFFEEF2FF))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    "$packedCount/$totalCount",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = PlusJakartaSans,
                    color      = if (allPacked) Color(0xFF16A34A) else AccentBlue
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint               = SecondaryText,
                modifier           = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = arrowRotation }
            )
        }

        // No animateContentSize — expand/collapse happens instantly.
        if (isExpanded) {
            Column {
                HorizontalDivider(color = BorderColor, thickness = 0.5.dp)
                category.items.forEachIndexed { idx, item ->
                    key(item.id) {
                        PackingItemRow(
                            item     = item,
                            onCheck  = { onItemCheck(item.id) },
                            onDelete = if (item.isCustom) ({ onDelete(item.id) }) else null
                        )
                    }
                    if (idx < category.items.lastIndex) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 56.dp),
                            color     = BorderColor,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

// ─── Item Row ─────────────────────────────────────────────────────────────────

@Composable
fun PackingItemRow(
    item     : PackingItem,
    onCheck  : () -> Unit,
    onDelete : (() -> Unit)?
) {
    // Static checkbox/text colors — no animateColorAsState / animateFloatAsState.
    val checkboxColor = if (item.isChecked) AccentGreen else Color.Transparent
    val textColor     = if (item.isChecked) SubtleText else PrimaryText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onCheck
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(checkboxColor),
            contentAlignment = Alignment.Center
        ) {
            if (item.isChecked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(14.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape    = RoundedCornerShape(6.dp),
                    color    = Color.Transparent,
                    border   = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor)
                ) {}
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text           = item.name,
                fontFamily     = PlusJakartaSans,
                fontSize       = 14.sp,
                color          = textColor,
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                fontWeight     = FontWeight.Normal,
                maxLines       = 1,
                overflow       = TextOverflow.Ellipsis
            )
            if (item.note.isNotBlank()) {
                Text(item.note, fontFamily = PlusJakartaSans, fontSize = 12.sp, color = SubtleText)
            }
        }

        if (item.isCustom) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFFEF3C7))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    "Custom",
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = PlusJakartaSans,
                    color      = Color(0xFF92400E)
                )
            }
        }

        if (item.isEssential && !item.isCustom) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Star,
                contentDescription = "Essential",
                tint               = Color(0xFFF59E0B),
                modifier           = Modifier.size(14.dp)
            )
        }

        // No AnimatedVisibility — checkmark icon shown/hidden instantly.
        if (item.isChecked) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint               = AccentGreen,
                modifier           = Modifier.size(18.dp)
            )
        }

        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint               = DestructiveRed.copy(alpha = 0.7f),
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─── Reset Dialog ─────────────────────────────────────────────────────────────

@Composable
fun ResetConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape     = RoundedCornerShape(24.dp),
            colors    = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier         = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFE4E6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = DestructiveRed, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("Reset all items?", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans, fontSize = 18.sp, color = PrimaryText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "This will uncheck everything in your current packing list. You cannot undo this action.",
                    fontFamily = PlusJakartaSans, fontSize = 14.sp, color = SecondaryText,
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick  = onConfirm,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = DestructiveRed)
                ) {
                    Text("Reset", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryText),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Text("Cancel", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 16.sp)
                }
            }
        }
    }
}

// ─── Add Custom Item Bottom Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomItemSheet(
    categoryOptions : List<Pair<String, String>>,
    onAdd           : (categoryId: String, name: String, note: String) -> Unit,
    onDismiss       : () -> Unit
) {
    var itemName     by remember { mutableStateOf("") }
    var itemNote     by remember { mutableStateOf("") }
    var selectedIdx  by remember { mutableStateOf(0) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var nameError    by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = CardWhite,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BorderColor)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Text("Add Custom Item", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans, fontSize = 18.sp, color = PrimaryText)
            Spacer(Modifier.height(20.dp))

            Text("Item name *", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                color = if (nameError) DestructiveRed else PrimaryText)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = itemName,
                onValueChange = { itemName = it; nameError = false },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("e.g. Hiking Boots", fontFamily = PlusJakartaSans, color = SubtleText, fontSize = 14.sp) },
                singleLine      = true,
                isError         = nameError,
                shape           = RoundedCornerShape(12.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Color(0xFFF8F9FF), unfocusedContainerColor = Color(0xFFF8F9FF), cursorColor = AccentBlue
                ),
                textStyle       = androidx.compose.ui.text.TextStyle(fontFamily = PlusJakartaSans, fontSize = 14.sp, color = PrimaryText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(14.dp))

            Text("Note / quantity (optional)", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PrimaryText)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = itemNote,
                onValueChange = { itemNote = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("e.g. 2 pairs, blue ones", fontFamily = PlusJakartaSans, color = SubtleText, fontSize = 14.sp) },
                singleLine      = true,
                shape           = RoundedCornerShape(12.dp),
                colors          = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor,
                    focusedContainerColor = Color(0xFFF8F9FF), unfocusedContainerColor = Color(0xFFF8F9FF), cursorColor = AccentBlue
                ),
                textStyle       = androidx.compose.ui.text.TextStyle(fontFamily = PlusJakartaSans, fontSize = 14.sp, color = PrimaryText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(Modifier.height(14.dp))

            Text("Category", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PrimaryText)
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = dropdownOpen, onExpandedChange = { dropdownOpen = it }) {
                OutlinedTextField(
                    value         = categoryOptions.getOrNull(selectedIdx)?.second ?: "",
                    onValueChange = {},
                    readOnly      = true,
                    modifier      = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon  = {
                        Icon(if (dropdownOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = SecondaryText)
                    },
                    shape  = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderColor,
                        focusedContainerColor = Color(0xFFF8F9FF), unfocusedContainerColor = Color(0xFFF8F9FF)
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = PlusJakartaSans, fontSize = 14.sp, color = PrimaryText)
                )
                ExposedDropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false },
                    modifier = Modifier.background(CardWhite)) {
                    categoryOptions.forEachIndexed { idx, (_, name) ->
                        DropdownMenuItem(
                            text    = { Text(name, fontFamily = PlusJakartaSans, fontSize = 14.sp, color = PrimaryText) },
                            onClick = { selectedIdx = idx; dropdownOpen = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    if (itemName.isBlank()) { nameError = true; return@Button }
                    val catId = categoryOptions.getOrNull(selectedIdx)?.first ?: return@Button
                    onAdd(catId, itemName.trim(), itemNote.trim())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Add Item", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = AccentBlue)
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview(showBackground = true, showSystemUi = true, device = "id:pixel_7")
@Composable
fun PackingChecklistScreenPreview() {
    MaterialTheme {
        PackingChecklistScreen(navController = androidx.navigation.compose.rememberNavController())
    }
}