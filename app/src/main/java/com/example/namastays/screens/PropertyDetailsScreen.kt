package com.example.namastays.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.namastays.R
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.dto.RoomResponse
import com.example.namastays.viewmodel.PropertyDetailsViewModel

// ── Colour tokens ────────────────────────────────────────────────────────────
private val NavyDark       = Color(0xFF1A2340)
private val BlueAccent     = Color(0xFF2563EB)
private val GreyText       = Color(0xFF6B7280)
private val LightGreyText  = Color(0xFF9CA3AF)
private val PageBg         = Color(0xFFF7F3EE)
private val CardBg         = Color(0xFFFFFFFF)
private val BorderGrey     = Color(0xFFE5E7EB)
private val GreenAllow     = Color(0xFF16A34A)
private val GreenBg        = Color(0xFFDCFCE7)
private val RedDeny        = Color(0xFFDC2626)
private val RedBg          = Color(0xFFFEE2E2)
private val SelectedBorder = Color(0xFF2563EB)
private val SelectedBg     = Color(0xFFEFF6FF)
private val TagBg          = Color(0xFFF3F4F6)

@RequiresApi(Build.VERSION_CODES.Q)
val playFairFontFamily = FontFamily(Font(R.font.playfair, FontWeight.Normal))

@RequiresApi(Build.VERSION_CODES.Q)
val interFontFamily = FontFamily(Font(R.font.interlight, FontWeight.Normal))

// ── Screen ───────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    propertyId: String,
    navController: NavController,
    viewModel: PropertyDetailsViewModel = viewModel()
) {
    LaunchedEffect(propertyId) { viewModel.fetchPropertyDetails(propertyId) }

    val property by remember { viewModel.property }
    val isLoading by remember { viewModel.isLoading }
    val errorMessage by remember { viewModel.errorMessage }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val heroHeight = when {
        screenWidthDp >= 600 -> 340.dp
        screenWidthDp >= 400 -> 280.dp
        else -> 240.dp
    }
    val hPad = if (screenWidthDp >= 600) 28.dp else 18.dp

    var selectedRoomId by remember(property?.id) { mutableStateOf<String?>(null) }

    val selectedRoom = property?.rooms?.firstOrNull { it.id == selectedRoomId }
        ?: property?.rooms?.firstOrNull()

    LaunchedEffect(property?.id, property?.rooms) {
        if (selectedRoomId == null && !property?.rooms.isNullOrEmpty()) {
            selectedRoomId = property?.rooms?.firstOrNull()?.id
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = BlueAccent)
            }

            errorMessage != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                Alignment.Center
            ) {
                Text(
                    errorMessage ?: "Something went wrong",
                    color = RedDeny,
                    fontSize = 15.sp
                )
            }

            property == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Property not found", color = GreyText, fontSize = 15.sp)
            }

            else -> {
                val p = property!!
                var descExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(bottom = 170.dp)
                ) {

                    // ── HERO IMAGE ────────────────────────────────────────
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
                                    .clip(RoundedCornerShape(22.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroHeight)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0xFFE8ECF2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No Images Available", color = GreyText, fontSize = 14.sp)
                            }
                        }

                        // Back button
                        Box(
                            modifier = Modifier
                                .padding(14.dp)
                                .align(Alignment.TopStart)
                                .size(40.dp)
                                .background(Color.White.copy(alpha = 0.92f), CircleShape),
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
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Image counter badge
                        if (p.imageUrls.size > 1) {
                            Box(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .align(Alignment.BottomEnd)
                                    .background(Color(0xBB1A2340), RoundedCornerShape(50.dp))
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("1/${p.imageUrls.size}", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // ── PROPERTY HEADER ───────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50.dp),
                                color = TagBg
                            ) {
                                Text(
                                    text = p.propertyType.uppercase(),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    color = NavyDark,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp,
                                    fontFamily = interFontFamily
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            text = p.propertyName,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            lineHeight = 36.sp,
                            fontFamily = playFairFontFamily
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                null,
                                tint = GreyText,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${p.city}, ${p.state}",
                                color = GreyText,
                                fontSize = 14.sp,
                                fontFamily = interFontFamily
                            )
                        }

                        Spacer(Modifier.height(28.dp))

                        // ── ABOUT ─────────────────────────────────────────
                        Text(
                            text = "About This Property",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            lineHeight = 26.sp,
                            fontFamily = playFairFontFamily
                        )

                        Spacer(Modifier.height(10.dp))

                        val desc = p.propertyDescription ?: "No description available."
                        val truncatable = desc.length > 130

                        Text(
                            text = if (!descExpanded && truncatable) "${desc.take(130)}…" else desc,
                            color = GreyText,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            fontFamily = interFontFamily
                        )

                        if (truncatable) {
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (descExpanded) "Read less" else "Read more",
                                    color = BlueAccent,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // ── PROPERTY DETAILS CARD ─────────────────────────
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    "Property Details",
                                    fontSize = 20.sp,
                                    lineHeight = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontFamily = playFairFontFamily
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(Icons.Outlined.CalendarMonth, "Year Established", p.yearEstablished.ifBlank { "-" }, Modifier.weight(1f))
                                    DetailCell(Icons.Outlined.Home, "Property Type", p.propertyType, Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(Icons.Outlined.LocationOn, "City", p.city, Modifier.weight(1f))
                                    DetailCell(Icons.Outlined.Map, "Address", p.address.substringBefore(",").trim().ifBlank { p.address }, Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(Icons.Outlined.Schedule, "Check-in", p.checkInTime ?: "-", Modifier.weight(1f))
                                    DetailCell(Icons.Outlined.Logout, "Check-out", p.checkOutTime ?: "-", Modifier.weight(1f))
                                }
                            }
                        }

                        // ── AVAILABLE ROOMS ───────────────────────────────
                        if (p.rooms.isNotEmpty()) {
                            Spacer(Modifier.height(28.dp))

                            Text(
                                "Available Rooms",
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontFamily = playFairFontFamily
                            )

                            Spacer(Modifier.height(14.dp))

                            p.rooms.forEach { room ->
                                RoomCard(
                                    room = room,
                                    isSelected = room.id == selectedRoom?.id,
                                    onSelect = { selectedRoomId = room.id }
                                )
                                Spacer(Modifier.height(14.dp))
                            }
                        }

                        // ── AMENITIES ─────────────────────────────────────
                        if (p.amenities.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))

                            Text(
                                "Amenities",
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontFamily = playFairFontFamily
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
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // ── GUEST POLICIES ────────────────────────────────
                        Spacer(Modifier.height(28.dp))

                        Text(
                            "Guest Policies",
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontFamily = playFairFontFamily
                        )

                        Spacer(Modifier.height(12.dp))

                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                                PolicyRow(Icons.Outlined.SmokeFree, "Smoking", p.smokingAllowed == true, "Allowed", "Not Allowed")
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                PolicyRow(Icons.Outlined.ChildCare, "Children", p.childrenAllowed == true, "Allowed", "Not Allowed")
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                PolicyRow(Icons.Outlined.Pets, "Pets", p.petsAllowed == true, "Allowed", "Not Allowed")
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                PolicyRow(Icons.Outlined.BreakfastDining, "Breakfast", p.breakfastIncluded == true, "Included", "Not Included")
                            }
                        }

                        // ── CANCELLATION POLICY ───────────────────────────
                        if (!p.cancellationPolicy.isNullOrBlank()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                "Cancellation Policy",
                                fontSize = 20.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontFamily = playFairFontFamily
                            )

                            Spacer(Modifier.height(10.dp))

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                elevation = CardDefaults.cardElevation(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(18.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Outlined.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        p.cancellationPolicy,
                                        color = Color(0xFF92400E),
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── STICKY BOTTOM BAR ─────────────────────────────────────
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 18.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = hPad, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "TOTAL PRICE",
                                fontSize = 11.sp,
                                color = LightGreyText,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp,
                                fontFamily = interFontFamily
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = selectedRoom?.pricePerNight?.let { "₹$it" } ?: "N/A",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontFamily = playFairFontFamily
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "/ night",
                                    fontSize = 13.sp,
                                    color = GreyText,
                                    modifier = Modifier.padding(bottom = 3.dp),
                                    fontFamily = interFontFamily
                                )
                            }
                            selectedRoom?.let {
                                Text(
                                    text = it.category,
                                    fontSize = 11.sp,
                                    color = BlueAccent,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = interFontFamily
                                )
                            }
                        }

                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                            modifier = Modifier
                                .height(56.dp)
                                .widthIn(min = 150.dp)
                        ) {
                            Text(
                                text = "Book Now",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Detail Cell ──────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun DetailCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = BlueAccent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                label,
                fontSize = 11.sp,
                color = GreyText,
                fontFamily = interFontFamily,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                fontSize = 14.sp,
                color = NavyDark,
                fontFamily = interFontFamily
            )
        }
    }
}

// ── Room Card ────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun RoomCard(
    room: RoomResponse,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) SelectedBorder else BorderGrey,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) SelectedBg else CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.KingBed,
                    null,
                    tint = GreyText,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = room.category,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        modifier = Modifier.weight(1f),
                        fontFamily = playFairFontFamily
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(BlueAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Check,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.KingBed, null, tint = GreyText, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(room.bedType, fontSize = 13.sp, color = GreyText, fontFamily = interFontFamily)
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Person, null, tint = GreyText, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${room.maxGuests} Guests", fontSize = 13.sp, color = GreyText, fontFamily = interFontFamily)
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoomTag("${room.totalRooms} Available")
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "₹${room.pricePerNight}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontFamily = interFontFamily
                        )
                        Text(
                            " /night",
                            fontSize = 12.sp,
                            color = GreyText,
                            modifier = Modifier.padding(bottom = 2.dp),
                            fontFamily = interFontFamily
                        )
                    }

                    if (isSelected) {
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = NavyDark
                        ) {
                            Text(
                                "Selected",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = interFontFamily
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelect() },
                            shape = RoundedCornerShape(50.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "Select Room",
                                fontSize = 12.sp,
                                color = NavyDark,
                                fontFamily = interFontFamily
                            )
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun RoomTag(label: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = TagBg) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            color = GreyText,
            fontFamily = interFontFamily
        )
    }
}

// ── Amenity Cell ─────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun AmenityCell(name: String, modifier: Modifier = Modifier) {
    fun iconFor(n: String): ImageVector = when {
        n.contains("wifi", ignoreCase = true) -> Icons.Outlined.Wifi
        n.contains("park", ignoreCase = true) -> Icons.Outlined.DirectionsCar
        n.contains("airport", ignoreCase = true) || n.contains("pickup", ignoreCase = true) -> Icons.Outlined.Flight
        n.contains("restaurant", ignoreCase = true) -> Icons.Outlined.Restaurant
        n.contains("pool", ignoreCase = true) -> Icons.Outlined.Pool
        n.contains("spa", ignoreCase = true) -> Icons.Outlined.Spa
        n.contains("gym", ignoreCase = true) -> Icons.Outlined.FitnessCenter
        n.contains("laundry", ignoreCase = true) -> Icons.Outlined.LocalLaundryService
        n.contains("room service", ignoreCase = true) || n.contains("room svc", ignoreCase = true) -> Icons.Outlined.RoomService
        n.contains("breakfast", ignoreCase = true) -> Icons.Outlined.BreakfastDining
        n.contains("bar", ignoreCase = true) -> Icons.Outlined.LocalBar
        n.contains("tv", ignoreCase = true) -> Icons.Outlined.Tv
        n.contains("hot water", ignoreCase = true) -> Icons.Outlined.HotTub
        n.contains("ac", ignoreCase = true) || n.contains("air", ignoreCase = true) -> Icons.Outlined.AcUnit
        n.contains("garden", ignoreCase = true) -> Icons.Outlined.Park
        else -> Icons.Outlined.CheckCircle
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .border(1.dp, BorderGrey, RoundedCornerShape(14.dp))
            .background(CardBg, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(iconFor(name), name, tint = NavyDark, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                color = NavyDark,
                maxLines = 2,
                textAlign = TextAlign.Center,
                fontFamily = interFontFamily
            )
        }
    }
}

// ── Policy Row ───────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.Q)
@Composable
private fun PolicyRow(
    icon: ImageVector,
    label: String,
    allowed: Boolean,
    allowedLabel: String = "Allowed",
    deniedLabel: String = "Not Allowed"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = GreyText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                fontSize = 15.sp,
                color = NavyDark,
                fontWeight = FontWeight.Medium,
                fontFamily = interFontFamily
            )
        }

        Surface(
            shape = RoundedCornerShape(50.dp),
            color = if (allowed) GreenBg else RedBg
        ) {
            Text(
                text = if (allowed) allowedLabel else deniedLabel,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (allowed) GreenAllow else RedDeny,
                fontFamily = interFontFamily
            )
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@RequiresApi(Build.VERSION_CODES.Q)
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun PropertyDetailsScreenPreview() {
    val fakeProperty = PropertyDetailsResponse(
        id = "1",
        propertyName = "The Grand Himalayan Resort",
        propertyType = "Hotel",
        propertyDescription = "Nestled in the foothills of the Annapurna range, this resort offers breathtaking mountain views and world-class amenities. Each room is thoughtfully designed with local craftsmanship and modern comforts.",
        city = "Pokhara",
        state = "Gandaki Province",
        country = "Nepal",
        address = "Lakeside Road, Pokhara",
        yearEstablished = "2008",
        checkInTime = "2:00 PM",
        checkOutTime = "11:00 AM",
        postalCode = "33700",
        smokingAllowed = false,
        childrenAllowed = true,
        petsAllowed = false,
        breakfastIncluded = true,
        cancellationPolicy = "Free cancellation up to 48 hours before check-in. After that, the first night's charge applies.",
        imageUrls = emptyList(),
        amenities = listOf("WiFi", "Pool", "Gym", "Spa", "Restaurant", "Breakfast"),
        rooms = listOf(
            RoomResponse(
                id = "r1",
                category = "Deluxe Mountain View",
                bedType = "King Bed",
                maxGuests = 2,
                totalRooms = 3,
                pricePerNight = 4500,
            ),
            RoomResponse(
                id = "r2",
                category = "Suite with Balcony",
                bedType = "Queen Bed",
                maxGuests = 3,
                totalRooms = 1,
                pricePerNight = 7200,
            )
        ),
        extraGuestPrice = "500",
    )

    val viewModel = PropertyDetailsViewModel().apply {
        property.value = fakeProperty
        isLoading.value = false
        errorMessage.value = null
    }

    MaterialTheme {
        PropertyDetailsScreen(
            propertyId = "1",
            navController = rememberNavController(),
            viewModel = viewModel
        )
    }
}