package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.R
import com.example.namastays.viewmodel.PropertyDetailsViewModel

// ── Font family — map each .ttf weight correctly ──────────────────────────────
// Drop arial.ttf, arial_medium.ttf, arial_semibold.ttf, arial_bold.ttf
// into res/font/ and this will pick up the right face automatically.
private val ArialFamily = FontFamily(
    Font(R.font.inter,          FontWeight.Normal),
    Font(R.font.inter_medium,   FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold,     FontWeight.Bold)
)

// ── Type-hierarchy helpers — use these instead of raw FontWeight everywhere ────
//
//   DISPLAY  →  property name, price              Bold       (700)
//   HEADING  →  section titles, card headers      SemiBold   (600)
//   LABEL    →  chip, button, detail value        Medium     (500)
//   BODY     →  description, rule label, address  Normal     (400)
//   CAPTION  →  grey sub-labels, review count     Normal     (400)  + smaller size
//
private val WeightDisplay  = FontWeight.Bold
private val WeightHeading  = FontWeight.SemiBold
private val WeightLabel    = FontWeight.Normal
private val WeightBody     = FontWeight.Normal
private val WeightCaption  = FontWeight.Normal

// ── Color tokens ──────────────────────────────────────────────────────────────
private val NavyDark   = Color(0xFF0D1B3E)
private val BlueAccent = Color(0xFF2563EB)
private val ChipBg     = Color(0xFFEEF4FF)
private val GreyText   = Color(0xFF6B7280)
private val PageBg     = Color(0xFFF5F0EB)   // warm off-white
private val CardBg     = Color(0xFFFFFFFF)
private val BorderGrey = Color(0xFFE5E7EB)
private val StarYellow = Color(0xFFFACC15)
private val GreenAllow = Color(0xFF16A34A)
private val RedDeny    = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    propertyId: String,
    navController: NavController,
    viewModel: PropertyDetailsViewModel = viewModel()
) {
    LaunchedEffect(propertyId) { viewModel.fetchPropertyDetails(propertyId) }

    val property     by remember { viewModel.property }
    val isLoading    by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val heroHeight = when {
        screenWidthDp >= 600 -> 340.dp
        screenWidthDp >= 400 -> 260.dp
        else                 -> 220.dp
    }
    val hPad = if (screenWidthDp >= 600) 28.dp else 18.dp

    Box(modifier = Modifier.fillMaxSize().background(PageBg)) {
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = BlueAccent)
                }
            }

            errorMessage != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        text = errorMessage ?: "Something went wrong",
                        color = RedDeny,
                        fontSize = 15.sp,
                        fontWeight = WeightBody,       // plain error message
                        fontFamily = ArialFamily
                    )
                }
            }

            property == null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = "Property not found",
                        color = GreyText,
                        fontSize = 15.sp,
                        fontWeight = WeightBody,
                        fontFamily = ArialFamily
                    )
                }
            }

            else -> {
                val p = property!!
                var descExpanded by remember { mutableStateOf(false) }

                // ── SCROLLABLE BODY ────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(bottom = 88.dp)
                ) {

                    // ── HERO IMAGE ─────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 14.dp)
                    ) {
                        if (p.imageUrls.isNotEmpty()) {
                            AsyncImage(
                                model = p.imageUrls.first(),
                                contentDescription = p.propertyName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight)
                                    .clip(RoundedCornerShape(20.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFE8ECF2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Images Available",
                                    color = GreyText,
                                    fontSize = 14.sp,
                                    fontWeight = WeightBody,
                                    fontFamily = ArialFamily
                                )
                            }
                        }

                        // Back button
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .align(Alignment.TopStart)
                                .size(38.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NavyDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Image counter badge
                        if (p.imageUrls.size > 1) {
                            Box(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xBB0D1B3E), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "1/${p.imageUrls.size}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = WeightLabel,  // medium — visible but not heavy
                                    fontFamily = ArialFamily
                                )
                            }
                        }
                    }

                    // ── CONTENT ────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad)
                    ) {

                        // Property name — largest, most prominent element on screen
                        Text(
                            text = p.propertyName,
                            fontSize = 26.sp,
                            fontWeight = WeightDisplay,   // Bold
                            color = NavyDark,
                            lineHeight = 32.sp,
                            fontFamily = ArialFamily
                        )

                        Spacer(Modifier.height(10.dp))

                        // Property type pill
                        Surface(shape = RoundedCornerShape(50.dp), color = ChipBg) {
                            Text(
                                text = p.propertyType,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = BlueAccent,
                                fontSize = 13.sp,
                                fontWeight = WeightLabel,  // Medium — just a tag, not a title
                                fontFamily = ArialFamily
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Location
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, tint = GreyText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${p.city}, ${p.state}",
                                color = GreyText,
                                fontSize = 14.sp,
                                fontWeight = WeightCaption, // Normal — supporting info
                                fontFamily = ArialFamily
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Star rating
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = StarYellow, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "4.8",
                                fontSize = 15.sp,
                                fontWeight = WeightHeading,  // SemiBold — rating is important
                                color = NavyDark,
                                fontFamily = ArialFamily
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "(324 reviews)",
                                fontSize = 13.sp,
                                fontWeight = WeightCaption,  // Normal — supporting count
                                color = GreyText,
                                fontFamily = ArialFamily
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        // ── About This Property ────────────────────────────────
                        Text(
                            text = "About This Property",
                            fontSize = 17.sp,
                            fontWeight = WeightHeading,  // SemiBold — section title
                            color = NavyDark,
                            fontFamily = ArialFamily
                        )

                        Spacer(Modifier.height(8.dp))

                        val desc = p.propertyDescription ?: "No description available."
                        val truncatable = desc.length > 130

                        Text(
                            text = if (!descExpanded && truncatable) "${desc.take(130)}…" else desc,
                            color = GreyText,
                            fontSize = 14.sp,
                            fontWeight = WeightBody,     // Normal — long-form body copy
                            lineHeight = 22.sp,
                            fontFamily = ArialFamily
                        )

                        if (truncatable) {
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (descExpanded) "Read less" else "Read more",
                                    color = BlueAccent,
                                    fontSize = 14.sp,
                                    fontWeight = WeightBody,  // Normal — inline link, not a CTA
                                    fontFamily = ArialFamily
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── Property Details Card ──────────────────────────────
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Property Details",
                                    fontSize = 17.sp,
                                    fontWeight = WeightHeading, // SemiBold — card header
                                    color = NavyDark,
                                    fontFamily = ArialFamily
                                )
                                Spacer(Modifier.height(16.dp))

                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(
                                        icon = Icons.Outlined.CalendarMonth,
                                        label = "Year Established",
                                        value = p.yearEstablished?.toString() ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                    DetailCell(
                                        icon = Icons.Outlined.Home,
                                        label = "Property Type",
                                        value = p.propertyType,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(
                                        icon = Icons.Outlined.LocationOn,
                                        label = "City",
                                        value = p.city,
                                        modifier = Modifier.weight(1f)
                                    )
                                    DetailCell(
                                        icon = Icons.Outlined.Map,
                                        label = "Address",
                                        value = p.address.substringBefore(",").trim().ifBlank { p.address },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(18.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(
                                        icon = Icons.Outlined.Schedule,
                                        label = "Check-in",
                                        value = p.checkInTime ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                    DetailCell(
                                        icon = Icons.Outlined.Logout,
                                        label = "Check-out",
                                        value = p.checkOutTime ?: "-",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // ── Amenities ──────────────────────────────────────────
                        if (p.amenities.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Amenities",
                                fontSize = 17.sp,
                                fontWeight = WeightHeading, // SemiBold
                                color = NavyDark,
                                fontFamily = ArialFamily
                            )

                            Spacer(Modifier.height(14.dp))

                            p.amenities.chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    row.forEach { amenity ->
                                        AmenityCell(name = amenity, modifier = Modifier.weight(1f))
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }

                        // ── Property Rules ─────────────────────────────────────
                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Property Rules",
                            fontSize = 17.sp,
                            fontWeight = WeightHeading, // SemiBold
                            color = NavyDark,
                            fontFamily = ArialFamily
                        )

                        Spacer(Modifier.height(12.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                                RuleRow(Icons.Outlined.SmokeFree,   Icons.Outlined.SmokingRooms,   "Smoking",            p.smokingAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.ChildCare,   Icons.Outlined.ChildCare,       "Children",           p.childrenAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.Pets,        Icons.Outlined.Pets,            "Pets",               p.petsAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.NoMeals,     Icons.Outlined.BreakfastDining, "Breakfast Included", p.breakfastIncluded == true)
                            }
                        }

                        // ── Cancellation Policy ────────────────────────────────
                        if (!p.cancellationPolicy.isNullOrBlank()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Cancellation Policy",
                                fontSize = 17.sp,
                                fontWeight = WeightHeading, // SemiBold
                                color = NavyDark,
                                fontFamily = ArialFamily
                            )

                            Spacer(Modifier.height(10.dp))

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                elevation = CardDefaults.cardElevation(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = p.cancellationPolicy,
                                        color = Color(0xFF92400E),
                                        fontSize = 14.sp,
                                        fontWeight = WeightBody,  // Normal — policy copy
                                        lineHeight = 22.sp,
                                        fontFamily = ArialFamily
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── STICKY BOTTOM BAR ──────────────────────────────────────────
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "₹4,500",
                                fontSize = 24.sp,
                                fontWeight = WeightDisplay,  // Bold — the price is hero text
                                color = NavyDark,
                                fontFamily = ArialFamily
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "/night",
                                fontSize = 13.sp,
                                fontWeight = WeightCaption,  // Normal — unit label
                                color = GreyText,
                                modifier = Modifier.padding(bottom = 2.dp),
                                fontFamily = ArialFamily
                            )
                        }

                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                            modifier = Modifier.height(50.dp).widthIn(min = 150.dp)
                        ) {
                            Text(
                                text = "Reserve Stay",
                                fontSize = 15.sp,
                                fontWeight = WeightHeading,  // SemiBold — primary CTA
                                color = Color.White,
                                fontFamily = ArialFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Detail cell: caption label + semibold value ───────────────────────────────
@Composable
private fun DetailCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = BlueAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = WeightCaption,  // Normal — tiny helper label
                color = GreyText,
                fontFamily = ArialFamily
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = WeightHeading,  // SemiBold — the actual value needs to stand out
                color = NavyDark,
                fontFamily = ArialFamily
            )
        }
    }
}

// ── Amenity cell: icon + caption label ───────────────────────────────────────
@Composable
private fun AmenityCell(name: String, modifier: Modifier = Modifier) {
    fun iconFor(n: String): ImageVector = when {
        n.contains("wifi",         ignoreCase = true) -> Icons.Outlined.Wifi
        n.contains("park",         ignoreCase = true) -> Icons.Outlined.DirectionsCar
        n.contains("airport",      ignoreCase = true) -> Icons.Outlined.Flight
        n.contains("restaurant",   ignoreCase = true) -> Icons.Outlined.Restaurant
        n.contains("pool",         ignoreCase = true) -> Icons.Outlined.Pool
        n.contains("spa",          ignoreCase = true) -> Icons.Outlined.Spa
        n.contains("gym",          ignoreCase = true) -> Icons.Outlined.FitnessCenter
        n.contains("laundry",      ignoreCase = true) -> Icons.Outlined.LocalLaundryService
        n.contains("room service", ignoreCase = true) -> Icons.Outlined.RoomService
        n.contains("breakfast",    ignoreCase = true) -> Icons.Outlined.BreakfastDining
        n.contains("bar",          ignoreCase = true) -> Icons.Outlined.LocalBar
        n.contains("tv",           ignoreCase = true) -> Icons.Outlined.Tv
        else                                          -> Icons.Outlined.CheckCircle
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
            .background(CardBg, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(imageVector = iconFor(name), contentDescription = name, tint = BlueAccent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = WeightCaption,  // Normal — tiny label under icon
                color = GreyText,
                maxLines = 2,
                textAlign = TextAlign.Center,
                fontFamily = ArialFamily
            )
        }
    }
}

// ── Rule row: body label + semibold badge ─────────────────────────────────────
@Composable
private fun RuleRow(
    icon: ImageVector,
    activeIcon: ImageVector,
    label: String,
    allowed: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (allowed) activeIcon else icon,
                contentDescription = null,
                tint = if (allowed) GreenAllow else RedDeny,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = WeightBody,    // Normal — rule label is readable body text
                color = NavyDark,
                fontFamily = ArialFamily
            )
        }

        Surface(
            shape = RoundedCornerShape(50.dp),
            color = if (allowed) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
        ) {
            Text(
                text = if (allowed) "Allowed" else "Not Allowed",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                fontSize = 12.sp,
                fontWeight = WeightHeading, // SemiBold — status badge needs to pop
                color = if (allowed) GreenAllow else RedDeny,
                fontFamily = ArialFamily
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────
@Preview(
    name = "Property Details – Phone",
    showBackground = true,
    showSystemUi = true,
    device = "spec:width=390dp,height=844dp,dpi=430"
)
@Composable
private fun PropertyDetailsScreenPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(bottom = 88.dp)
            ) {
                // Hero placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFD0C8BC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📷  Property Image",
                            color = Color(0xFF8A7F75),
                            fontSize = 15.sp,
                            fontWeight = WeightBody,
                            fontFamily = ArialFamily
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.TopStart)
                            .size(38.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = NavyDark, modifier = Modifier.size(18.dp))
                    }
                    Box(
                        modifier = Modifier
                            .padding(12.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color(0xBB0D1B3E), RoundedCornerShape(50.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text("1/8", color = Color.White, fontSize = 12.sp, fontWeight = WeightLabel, fontFamily = ArialFamily)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                ) {
                    // Property name — Display/Bold
                    Text(
                        text = "The Horizon Retreat",
                        fontSize = 26.sp,
                        fontWeight = WeightDisplay,
                        color = NavyDark,
                        lineHeight = 32.sp,
                        fontFamily = ArialFamily
                    )
                    Spacer(Modifier.height(10.dp))

                    // Type chip — Label/Medium
                    Surface(shape = RoundedCornerShape(50.dp), color = ChipBg) {
                        Text(
                            text = "Boutique Hotel",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            color = BlueAccent,
                            fontSize = 13.sp,
                            fontWeight = WeightLabel,
                            fontFamily = ArialFamily
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    // Location — Caption/Normal
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, null, tint = GreyText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Udaipur, Rajasthan", color = GreyText, fontSize = 14.sp, fontWeight = WeightCaption, fontFamily = ArialFamily)
                    }
                    Spacer(Modifier.height(10.dp))

                    // Rating — Heading/SemiBold for number, Caption/Normal for count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = StarYellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("4.8", fontSize = 15.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                        Spacer(Modifier.width(5.dp))
                        Text("(324 reviews)", fontSize = 13.sp, fontWeight = WeightCaption, color = GreyText, fontFamily = ArialFamily)
                    }
                    Spacer(Modifier.height(22.dp))

                    // Section heading — Heading/SemiBold
                    Text("About This Property", fontSize = 17.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                    Spacer(Modifier.height(8.dp))

                    // Body copy — Body/Normal
                    Text(
                        text = "Nestled on the serene shores of Lake Pichola, The Horizon Retreat offers an unparalleled blend of traditional Rajasthani architecture and modern luxury.",
                        color = GreyText,
                        fontSize = 14.sp,
                        fontWeight = WeightBody,
                        lineHeight = 22.sp,
                        fontFamily = ArialFamily
                    )
                    TextButton(onClick = {}, contentPadding = PaddingValues(0.dp)) {
                        Text("Read more", color = BlueAccent, fontSize = 14.sp, fontWeight = WeightBody, fontFamily = ArialFamily)
                    }
                    Spacer(Modifier.height(20.dp))

                    // Property Details card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text("Property Details", fontSize = 17.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth()) {
                                DetailCell(Icons.Outlined.CalendarMonth, "Year Established", "2018",         Modifier.weight(1f))
                                DetailCell(Icons.Outlined.Home,          "Property Type",    "Boutique Hotel",Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth()) {
                                DetailCell(Icons.Outlined.LocationOn, "City",    "Udaipur",         Modifier.weight(1f))
                                DetailCell(Icons.Outlined.Map,        "Address", "Lake Pichola Road",Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth()) {
                                DetailCell(Icons.Outlined.Schedule, "Check-in",  "2:00 PM",  Modifier.weight(1f))
                                DetailCell(Icons.Outlined.Logout,   "Check-out", "11:00 AM", Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Amenities", fontSize = 17.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                    Spacer(Modifier.height(14.dp))
                    listOf("WiFi", "Parking", "Airport Pickup", "Restaurant", "Pool", "Spa", "Gym", "Laundry", "Room Service")
                        .chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { AmenityCell(it, Modifier.weight(1f)) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                    Spacer(Modifier.height(14.dp))
                    Text("Property Rules", fontSize = 17.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                    Spacer(Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                            RuleRow(Icons.Outlined.SmokeFree,   Icons.Outlined.SmokingRooms,   "Smoking",            false)
                            HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                            RuleRow(Icons.Outlined.ChildCare,   Icons.Outlined.ChildCare,       "Children",           true)
                            HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                            RuleRow(Icons.Outlined.Pets,        Icons.Outlined.Pets,            "Pets",               false)
                            HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                            RuleRow(Icons.Outlined.NoMeals,     Icons.Outlined.BreakfastDining, "Breakfast Included", true)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Cancellation Policy", fontSize = 17.sp, fontWeight = WeightHeading, color = NavyDark, fontFamily = ArialFamily)
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Free cancellation up to 48 hours before check-in. After that, the first night is non-refundable.",
                                color = Color(0xFF92400E),
                                fontSize = 14.sp,
                                fontWeight = WeightBody,
                                lineHeight = 22.sp,
                                fontFamily = ArialFamily
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Sticky bar
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("₹4,500", fontSize = 24.sp, fontWeight = WeightDisplay, color = NavyDark, fontFamily = ArialFamily)
                        Spacer(Modifier.width(4.dp))
                        Text("/night", fontSize = 13.sp, fontWeight = WeightCaption, color = GreyText, modifier = Modifier.padding(bottom = 2.dp), fontFamily = ArialFamily)
                    }
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                        modifier = Modifier.height(50.dp).widthIn(min = 150.dp)
                    ) {
                        Text("Reserve Stay", fontSize = 15.sp, fontWeight = WeightHeading, color = Color.White, fontFamily = ArialFamily)
                    }
                }
            }
        }
    }
}