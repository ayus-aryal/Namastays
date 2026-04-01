package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Color Palette ───────────────────────────────────────────────────────────
val BackgroundColor   = Color(0xFFF2F2F7)
val CardWhite         = Color(0xFFFFFFFF)
val PrimaryText       = Color(0xFF1C1C2E)
val SecondaryText     = Color(0xFF8E8EA8)
val AccentBlue        = Color(0xFF4A90D9)
val BottomNavSelected = Color(0xFF1C1C2E)
val BottomNavUnsel    = Color(0xFFAAAAAA)
val OnlineGreen       = Color(0xFF34C759)

// ─── Category Icon Colors ─────────────────────────────────────────────────────
val HotelIconBg     = Color(0xFFE8EEFF)
val HotelIconColor  = Color(0xFF6B7FE3)
val HomeIconBg      = Color(0xFFE6F7F0)
val HomeIconColor   = Color(0xFF34C77A)
val ToursIconBg     = Color(0xFFFFF4E0)
val ToursIconColor  = Color(0xFFF5A623)
val GuidesIconBg    = Color(0xFFF0EBFF)
val GuidesIconColor = Color(0xFF9B6FE3)
val PickupIconBg    = Color(0xFFFFEBEB)
val PickupIconColor = Color(0xFFE34F4F)
val HelpIconBg      = Color(0xFFE0F7F7)
val HelpIconColor   = Color(0xFF3ABCBC)

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
    Destination("Bali, Indonesia",    "Tropical paradise awaits",   listOf(Color(0xFF2D6A4F), Color(0xFF1B4332))),
    Destination("Santorini, Greece",  "Whitewashed cliffside magic", listOf(Color(0xFF1D3557), Color(0xFF457B9D))),
    Destination("Kyoto, Japan",       "Ancient temples & cherry blossoms", listOf(Color(0xFF6D2B3D), Color(0xFFB5838D))),
    Destination("Patagonia, Chile",   "Wild landscapes, pure freedom", listOf(Color(0xFF264653), Color(0xFF2A9D8F)))
)

val sampleCategories = listOf(
    Category("Hotels",    Icons.Outlined.Apartment,      HotelIconBg,  HotelIconColor),
    Category("Homestays", Icons.Outlined.Home,           HomeIconBg,   HomeIconColor),
    Category("Tours",     Icons.Outlined.Explore,        ToursIconBg,  ToursIconColor),
    Category("Guides",    Icons.Outlined.MenuBook,       GuidesIconBg, GuidesIconColor),
    Category("Pickup",    Icons.Outlined.DirectionsCar,  PickupIconBg, PickupIconColor),
    Category("Help",      Icons.Outlined.Headset,        HelpIconBg,   HelpIconColor)
)

val bottomNavItems = listOf(
    BottomNavItem("Home",     Icons.Filled.Home,           Icons.Outlined.Home),
    BottomNavItem("Explore",  Icons.Filled.Search,         Icons.Outlined.Search),
    BottomNavItem("Bookings", Icons.Filled.CalendarMonth,  Icons.Outlined.CalendarMonth),
    BottomNavItem("Wishlist", Icons.Filled.Favorite,       Icons.Outlined.FavoriteBorder),
    BottomNavItem("Profile",  Icons.Filled.Person,         Icons.Outlined.Person)
)

// ─── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun HomeScreen() {
    var selectedTab       by remember { mutableStateOf(0) }
    var selectedDotIndex  by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = BackgroundColor,
        bottomBar = {
            TravelerBottomNavBar(
                items       = bottomNavItems,
                selectedIdx = selectedTab,
                onItemClick = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            TopBar()

            Spacer(Modifier.height(16.dp))

            // ── Search Bar ───────────────────────────────────────────────────
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(24.dp))

            // ── Featured Destinations ────────────────────────────────────────
            SectionHeader(
                title      = "Featured Destinations",
                actionText = "See All",
                modifier   = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(12.dp))

            FeaturedDestinationsRow(
                destinations   = sampleDestinations,
                selectedDot    = selectedDotIndex,
                onDotSelected  = { selectedDotIndex = it }
            )

            Spacer(Modifier.height(24.dp))

            // ── Categories ───────────────────────────────────────────────────
            SectionHeader(
                title    = "Categories",
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(12.dp))

            CategoriesGrid(
                categories = sampleCategories,
                modifier   = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
fun TopBar() {
    Row(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text       = "Traveler",
            fontSize   = 26.sp,
            fontWeight = FontWeight.Bold,
            color      = PrimaryText
        )

        // Avatar with online indicator
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF8E8EA8))
            ) {
                Icon(
                    imageVector        = Icons.Filled.Person,
                    contentDescription = "Profile",
                    tint               = Color.White,
                    modifier           = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
            // Green online dot
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

// ─── Search Bar ──────────────────────────────────────────────────────────────
@Composable
fun SearchBar(modifier: Modifier = Modifier) {
    Row(
        modifier          = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(CardWhite)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Outlined.Search,
            contentDescription = "Search",
            tint               = SecondaryText,
            modifier           = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text     = "Where are you heading?",
            fontSize = 15.sp,
            color    = SecondaryText
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
            text       = title,
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = PrimaryText
        )
        if (actionText != null) {
            Text(
                text       = actionText,
                fontSize   = 14.sp,
                color      = SecondaryText,
                modifier   = Modifier.clickable { }
            )
        }
    }
}

// ─── Featured Destinations Row ────────────────────────────────────────────────
@Composable
fun FeaturedDestinationsRow(
    destinations  : List<Destination>,
    selectedDot   : Int,
    onDotSelected : (Int) -> Unit
) {
    Column {
        LazyRow(
            contentPadding        = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(destinations.size) { index ->
                DestinationCard(
                    destination = destinations[index],
                    isLarge     = index == 0
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Dot indicators
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { i, _ ->
                val isSelected = i == selectedDot
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (isSelected) 20.dp else 7.dp, 7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) PrimaryText else Color(0xFFCCCCCC))
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
    isLarge     : Boolean = false
) {
    val cardWidth  = if (isLarge) 280.dp else 160.dp
    val cardHeight = 200.dp

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(destination.gradientColors)
            )
            .clickable { }
    ) {
        // Bottom gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x99000000))
                    )
                )
        )

        // Destination info at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text       = destination.name,
                fontSize   = if (isLarge) 18.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (isLarge) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint               = Color.White.copy(alpha = 0.85f),
                        modifier           = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text     = destination.tagline,
                        fontSize = 12.sp,
                        color    = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── Categories Grid ─────────────────────────────────────────────────────────
@Composable
fun CategoriesGrid(
    categories : List<Category>,
    modifier   : Modifier = Modifier
) {
    // 2 rows × 3 columns
    val rows = categories.chunked(3)
    Column(
        modifier              = modifier,
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { category ->
                    CategoryCard(
                        category = category,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining slots if row is incomplete
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─── Category Card ────────────────────────────────────────────────────────────
@Composable
fun CategoryCard(
    category : Category,
    modifier : Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable { }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier        = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(category.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = category.icon,
                contentDescription = category.label,
                tint               = category.iconColor,
                modifier           = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text       = category.label,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
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
        tonalElevation = 0.dp
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
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
        HomeScreen()
    }
}