package com.example.namastays.screens

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import kotlinx.coroutines.launch

// ─── Plus Jakarta Sans Font Family ────────────────────────────────────────────
// Add these font files to res/font/:
//   plus_jakarta_sans_regular.ttf
//   plus_jakarta_sans_medium.ttf
//   plus_jakarta_sans_semibold.ttf
//   plus_jakarta_sans_bold.ttf
//   plus_jakarta_sans_extrabold.ttf
//
// Then uncomment the block below and remove the fallback:
//
// val PlusJakartaSans = FontFamily(
//     Font(R.font.plus_jakarta_sans_regular,  FontWeight.Normal),
//     Font(R.font.plus_jakarta_sans_medium,   FontWeight.Medium),
//     Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
//     Font(R.font.plus_jakarta_sans_bold,     FontWeight.Bold),
//     Font(R.font.plus_jakarta_sans_extrabold,FontWeight.ExtraBold),
// )
//
// For now, using the system default until font files are added:
val PlusJakartaSans = FontFamily.Default

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
    Destination("Pokhara, Nepal",    "Lakeside peace awaits",        listOf(Color(0xFF2D6A4F), Color(0xFF1B4332))),
    Destination("Mustang, Nepal",    "Adventure in the mountains",   listOf(Color(0xFF8D5A2B), Color(0xFF5A3E1B))),
    Destination("Kathmandu, Nepal",  "Culture, heritage & city life",listOf(Color(0xFF6D2B3D), Color(0xFFB5838D))),
    Destination("Chitwan, Nepal",    "Wildlife and jungle escapes",  listOf(Color(0xFF264653), Color(0xFF2A9D8F)))
)

// ─── Only 3 categories (Guides, Pickup, Help removed) ────────────────────────
val sampleCategories = listOf(
    Category("Hotels",    Icons.Outlined.Apartment, HotelIconBg, HotelIconColor),
    Category("Homestays", Icons.Outlined.Home,      HomeIconBg,  HomeIconColor),
    Category("Tours",     Icons.Outlined.Explore,   ToursIconBg, ToursIconColor)
)

val bottomNavItems = listOf(
    BottomNavItem("Home",      Icons.Filled.Home,   Icons.Outlined.Home),
    BottomNavItem("Explore",   Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem("Maps",      Icons.Filled.Map,    Icons.Outlined.Map),
    BottomNavItem("Trek Mode", Icons.Filled.Hiking, Icons.Outlined.Hiking),
    BottomNavItem("Safety",    Icons.Filled.Shield, Icons.Outlined.Shield)
)

// ─── Main Screen ──────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(navController: NavController) {
    // Shared LazyListState so dots and row stay in sync
    val listState   = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Derive the "most visible" item index from scroll position
    val selectedDotIndex by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val offset       = listState.firstVisibleItemScrollOffset
            // Snap to next card when scrolled past halfway of a card (~146 dp)
            if (offset > 730) firstVisible + 1 else firstVisible
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(BackgroundColor)
    ) {
        TopBar()

        Spacer(Modifier.height(16.dp))

        SearchBar(
            navController = navController,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            title      = "Featured Destinations",
            actionText = "See All",
            modifier   = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        FeaturedDestinationsRow(
            destinations   = sampleDestinations,
            listState      = listState,
            selectedDot    = selectedDotIndex,
            onDotSelected  = { index ->
                coroutineScope.launch {
                    listState.animateScrollToItem(index)
                }
            },
            navController  = navController
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(
            title    = "Categories",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(12.dp))

        CategoriesRow(
            categories = sampleCategories,
            modifier   = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(Modifier.height(16.dp))
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────
@Composable
fun TopBar() {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text       = "Namastays",
            fontSize   = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = PlusJakartaSans,
            color      = PrimaryText
        )

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    OutlinedTextField(
        value         = searchQuery,
        onValueChange = { searchQuery = it },
        modifier      = modifier,
        placeholder   = {
            Text(
                text       = "Where are you heading?",
                color      = SecondaryText,
                fontSize   = 15.sp,
                fontFamily = PlusJakartaSans
            )
        },
        leadingIcon = {
            Icon(
                imageVector        = Icons.Outlined.Search,
                contentDescription = "Search",
                tint               = SecondaryText
            )
        },
        trailingIcon = {
            IconButton(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        val city = Uri.encode(searchQuery.trim())
                        navController.navigate("search_results/$city") { launchSingleTop = true }
                    }
                }
            ) {
                Icon(
                    imageVector        = Icons.Default.ArrowForward,
                    contentDescription = "Go",
                    tint               = PrimaryText
                )
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = PlusJakartaSans,
            fontSize   = 15.sp
        ),
        singleLine     = true,
        shape          = RoundedCornerShape(28.dp),
        colors         = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor   = CardWhite,
            unfocusedContainerColor = CardWhite,
            cursorColor          = PrimaryText
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
            fontFamily = PlusJakartaSans,
            color      = PrimaryText
        )
        if (actionText != null) {
            Text(
                text       = actionText,
                fontSize   = 14.sp,
                fontFamily = PlusJakartaSans,
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
    listState     : androidx.compose.foundation.lazy.LazyListState,
    selectedDot   : Int,
    onDotSelected : (Int) -> Unit,
    navController : NavController
) {
    Column {
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(destinations) { destination ->
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

        Spacer(Modifier.height(14.dp))

        // Dot indicators — tapping scrolls carousel to that item
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            destinations.forEachIndexed { i, _ ->
                val isSelected = i == selectedDot

                // Animate dot width with a spring
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 7.dp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "dotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) PrimaryText else Color(0xFFCCCCCC),
                    animationSpec = tween(250),
                    label = "dotColor"
                )

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
    val cardWidth  = if (isLarge) 280.dp else 160.dp
    val cardHeight = 200.dp

    // Press-to-scale animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label         = "cardScale"
    )

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(cardHeight)
            .scale(scale)
            .shadow(elevation = if (isPressed) 2.dp else 6.dp, shape = RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(destination.gradientColors))
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            )
    ) {
        // Gradient overlay for text legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xBB000000))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text       = destination.name,
                fontSize   = if (isLarge) 18.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSans,
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

// ─── Categories Row (3 items, single row) ─────────────────────────────────────
@Composable
fun CategoriesRow(
    categories : List<Category>,
    modifier   : Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { category ->
            CategoryCard(
                category = category,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─── Category Card (with press-scale animation) ───────────────────────────────
@Composable
fun CategoryCard(
    category : Category,
    modifier : Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label         = "categoryScale"
    )
    val elevation by animateDpAsState(
        targetValue   = if (isPressed) 1.dp else 3.dp,
        animationSpec = tween(150),
        label         = "categoryElevation"
    )

    Column(
        modifier            = modifier
            .scale(scale)
            .shadow(elevation = elevation, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = {}
            )
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier         = Modifier
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
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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