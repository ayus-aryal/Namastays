package com.example.namastays.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.namastays.R
import kotlinx.coroutines.launch

val PlusJakartaSans = FontFamily(
    Font(R.font.plusjakartasans, FontWeight.Normal)
)

val PlusJakartaSansBold = FontFamily(
    Font(R.font.plusjakartasansbold, FontWeight.Bold)
)

// ─── Unified Color Palette ────────────────────────────────────────────────────
val BackgroundColor   = Color(0xFFF7F8FA)
val CardWhite         = Color(0xFFFFFFFF)
val PrimaryText       = Color(0xFF111827)
val SecondaryText     = Color(0xFF6B7280)
val SubtleText        = Color(0xFF9CA3AF)
val AccentBlue        = Color(0xFF4F46E5)
val AccentGreen       = Color(0xFF22C55E)
val DestructiveRed    = Color(0xFFEF4444)
val BorderColor       = Color(0xFFE5E7EB)
val SkeletonBase      = Color(0xFFE5E7EB)
val SkeletonHighlight = Color(0xFFF3F4F6)
val BottomNavSelected = Color(0xFF111827)
val BottomNavUnsel    = Color(0xFF9CA3AF)
val OnlineGreen       = Color(0xFF22C55E)

// ─── Category Icon Colors ─────────────────────────────────────────────────────
val HotelIconBg    = Color(0xFFEEF2FF)
val HotelIconColor = Color(0xFF4F46E5)
val HomeIconBg     = Color(0xFFDCFCE7)
val HomeIconColor  = Color(0xFF16A34A)
val ToursIconBg    = Color(0xFFFFF7ED)
val ToursIconColor = Color(0xFFEA580C)

// ─── Data Models ──────────────────────────────────────────────────────────────
data class Destination(
    val name: String,
    val tagline: String,
    val gradientColors: List<Color>
)

data class Category(
    val label: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconColor: Color
)

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

// ─── Sample Data ──────────────────────────────────────────────────────────────
val sampleDestinations = listOf(
    Destination("Pokhara, Nepal",   "Lakeside peace awaits",         listOf(Color(0xFF2D6A4F), Color(0xFF1B4332))),
    Destination("Mustang, Nepal",   "Adventure in the mountains",    listOf(Color(0xFF8D5A2B), Color(0xFF5A3E1B))),
    Destination("Kathmandu, Nepal", "Culture, heritage & city life", listOf(Color(0xFF6D2B3D), Color(0xFFB5838D))),
    Destination("Chitwan, Nepal",   "Wildlife and jungle escapes",   listOf(Color(0xFF264653), Color(0xFF2A9D8F)))
)

val sampleCategories = listOf(
    Category("Hotels",         Icons.Outlined.Apartment, HotelIconBg, HotelIconColor),
    Category("Homestays",      Icons.Outlined.Home,      HomeIconBg,  HomeIconColor),
    Category("Trip Checklist", Icons.Outlined.Checklist, ToursIconBg, ToursIconColor)
)

val bottomNavItems = listOf(
    BottomNavItem("Home",      Icons.Filled.Home,            Icons.Outlined.Home),
    BottomNavItem("Explore",   Icons.Filled.Search,          Icons.Outlined.Search),
    BottomNavItem("Maps",      Icons.Filled.Map,             Icons.Outlined.Map),
    BottomNavItem("Trek Mode", Icons.Filled.Terrain,         Icons.Outlined.Terrain),
    BottomNavItem("Safety",    Icons.Filled.HealthAndSafety, Icons.Outlined.HealthAndSafety)
)

// ─── Shimmer Brush (static, no animation) ─────────────────────────────────────
@Composable
fun shimmerBrush(): Brush {
    val shimmerColors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase)
    return Brush.linearGradient(
        colors = shimmerColors,
        start  = Offset(0f, 0f),
        end    = Offset(400f, 0f)
    )
}

// ─── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(navController: NavController) {
    val listState      = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val selectedDotIndex by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset       = listState.firstVisibleItemScrollOffset
            if (offset > 730) firstVisible + 1 else firstVisible
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        item(key = "topbar") {
            TopBar()
        }

        item(key = "search") {
            Spacer(Modifier.height(16.dp))
            SearchBar(
                navController = navController,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }

        item(key = "featured_header") {
            Spacer(Modifier.height(28.dp))
            SectionHeader(
                title      = "Featured Destinations",
                actionText = "See All",
                modifier   = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }

        item(key = "featured_row") {
            FeaturedDestinationsRow(
                destinations  = sampleDestinations,
                listState     = listState,
                selectedDot   = selectedDotIndex,
                onDotSelected = { index ->
                    coroutineScope.launch { listState.animateScrollToItem(index) }
                },
                navController = navController
            )
        }

        item(key = "categories_header") {
            Spacer(Modifier.height(28.dp))
            SectionHeader(
                title    = "Browse by Category",
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))
        }

        item(key = "categories_row") {
            CategoriesRow(
                categories    = sampleCategories,
                navController = navController,
                modifier      = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
fun TopBar() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text          = "Namastays",
                fontSize      = 26.sp,
                fontWeight    = FontWeight.ExtraBold,
                fontFamily    = PlusJakartaSans,
                color         = PrimaryText,
                letterSpacing = (-0.5).sp
            )
            Text(
                text       = "Discover Nepal's finest stays",
                fontSize   = 13.sp,
                fontFamily = PlusJakartaSans,
                color      = SecondaryText,
                fontWeight = FontWeight.Normal
            )
        }

        Box {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE0E7FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint               = AccentBlue,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Box(
                modifier         = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CardWhite)
                    .align(Alignment.BottomEnd),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen)
                )
            }
        }
    }
}

// ─── Search Bar ──────────────────────────────────────────────────────────────
@Composable
fun SearchBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
    ) {
        OutlinedTextField(
            value         = searchQuery,
            onValueChange = { searchQuery = it },
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = {
                Text(
                    text       = "Where are you heading?",
                    color      = SubtleText,
                    fontSize   = 15.sp,
                    fontFamily = PlusJakartaSans
                )
            },
            leadingIcon = {
                Icon(
                    imageVector        = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint               = SecondaryText,
                    modifier           = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val city = Uri.encode(searchQuery.trim())
                            navController.navigate("search_results/$city") { launchSingleTop = true }
                        }
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AccentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.ArrowForward,
                                contentDescription = "Search",
                                tint               = Color.White,
                                modifier           = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = PlusJakartaSans,
                fontSize   = 15.sp,
                color      = PrimaryText
            ),
            singleLine      = true,
            shape           = RoundedCornerShape(16.dp),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = Color.Transparent,
                unfocusedBorderColor    = Color.Transparent,
                focusedContainerColor   = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor             = AccentBlue
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    if (searchQuery.isNotBlank()) {
                        val city = Uri.encode(searchQuery.trim())
                        navController.navigate("search_results/$city")
                    }
                }
            )
        )
    }
}

// ─── Section Header ───────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title      : String,
    actionText : String? = null,
    modifier   : Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text          = title,
            fontSize      = 19.sp,
            fontWeight    = FontWeight.Bold,
            fontFamily    = PlusJakartaSans,
            color         = PrimaryText,
            letterSpacing = (-0.3).sp
        )
        if (actionText != null) {
            Text(
                text       = actionText,
                fontSize   = 14.sp,
                fontFamily = PlusJakartaSans,
                color      = AccentBlue,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.clickable { }
            )
        }
    }
}

// ─── Featured Destinations Row ────────────────────────────────────────────────
@Composable
fun FeaturedDestinationsRow(
    destinations  : List<Destination>,
    listState     : androidx.compose.foundation.lazy.LazyListState,
    selectedDot   : Int,
    onDotSelected : (Int) -> Unit,
    navController : NavController
) {
    Column {
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(destinations, key = { it.name }) { destination ->
                DestinationCard(
                    destination = destination,
                    isLarge     = destination == destinations.first(),
                    onClick     = {
                        val cityName = Uri.encode(destination.name.substringBefore(",").trim())
                        navController.navigate("search_results/$cityName") { launchSingleTop = true }
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { i, _ ->
                val isSelected = i == selectedDot
                // Static values — no dp/color animation on dot selection.
                val dotWidth = if (isSelected) 24.dp else 7.dp
                val dotColor = if (isSelected) AccentBlue else Color(0xFFD1D5DB)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(dotWidth, 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor)
                        .clickable { onDotSelected(i) }
                )
            }
        }
    }
}

// ─── Destination Card ─────────────────────────────────────────────────────────
@Composable
fun DestinationCard(
    destination : Destination,
    isLarge     : Boolean = false,
    onClick     : () -> Unit
) {
    val cardWidth  = if (isLarge) 288.dp else 170.dp
    val cardHeight = 210.dp

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .shadow(
                elevation = 8.dp,
                shape     = RoundedCornerShape(20.dp),
                spotColor = Color(0x26000000)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(destination.gradientColors))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text       = destination.name,
                fontSize   = if (isLarge) 18.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSans,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (isLarge) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.85f),
                        modifier           = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text       = destination.tagline,
                        fontSize   = 12.sp,
                        fontFamily = PlusJakartaSans,
                        color      = Color.White.copy(alpha = 0.85f),
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Categories Row ───────────────────────────────────────────────────────────
@Composable
fun CategoriesRow(
    categories    : List<Category>,
    navController : NavController,
    modifier      : Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { category ->
            val onClick: () -> Unit = when (category.label) {
                "Hotels"         -> {{ navController.navigate("hotels") { launchSingleTop = true } }}
                "Homestays"      -> {{ navController.navigate("homestays") { launchSingleTop = true } }}
                "Trip Checklist" -> {{
                    navController.navigate("packing_checklist") {
                        launchSingleTop = true
                        restoreState    = true
                        popUpTo("home") { saveState = true }
                    }
                }}                else             -> {{}}
            }
            CategoryCard(
                category = category,
                onClick  = onClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Category Card ────────────────────────────────────────────────────────────
@Composable
fun CategoryCard(
    category : Category,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(18.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(category.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = category.icon,
                contentDescription = category.label,
                tint               = category.iconColor,
                modifier           = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text       = category.label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlusJakartaSans,
            color      = PrimaryText
        )
    }
}

// ─── Bottom Navigation Bar ────────────────────────────────────────────────────
@Composable
fun TravelerBottomNavBar(
    items       : List<BottomNavItem>,
    selectedIdx : Int,
    onItemClick : (Int) -> Unit
) {
    NavigationBar(
        containerColor = CardWhite,
        tonalElevation = 0.dp,
        modifier       = Modifier.shadow(elevation = 12.dp, spotColor = Color(0x1A000000))
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIdx
            NavigationBarItem(
                selected = isSelected,
                onClick  = { onItemClick(index) },
                icon = {
                    Icon(
                        imageVector        = if (isSelected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        modifier           = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text       = item.label,
                        fontSize   = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = PlusJakartaSans
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = BottomNavSelected,
                    selectedTextColor   = BottomNavSelected,
                    unselectedIconColor = BottomNavUnsel,
                    unselectedTextColor = BottomNavUnsel,
                    indicatorColor      = Color.Transparent
                )
            )
        }
    }
}

// ─── Preview ─────────────────────────────────────────────────────────────────
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(navController = rememberNavController())
    }
}

@Preview(showBackground = true)
@Composable
fun BottomNavBarPreview() {
    var selectedIdx by remember { mutableStateOf(0) }
    MaterialTheme {
        TravelerBottomNavBar(
            items       = bottomNavItems,
            selectedIdx = selectedIdx,
            onItemClick = { selectedIdx = it }
        )
    }
}