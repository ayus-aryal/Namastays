package com.example.namastays.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.dto.RoomResponse
import com.example.namastays.viewmodel.PropertyDetailsViewModel

private val NavyDark   = Color(0xFF0D1B3E)
private val BlueAccent = Color(0xFF2563EB)
private val ChipBg     = Color(0xFFEEF4FF)
private val GreyText   = Color(0xFF6B7280)
private val PageBg     = Color(0xFFF5F0EB)
private val CardBg     = Color(0xFFFFFFFF)
private val BorderGrey = Color(0xFFE5E7EB)
private val GreenAllow = Color(0xFF16A34A)
private val RedDeny    = Color(0xFFDC2626)
private val SelectedRoomBg = Color(0xFFEFF6FF)

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
        screenWidthDp >= 400 -> 260.dp
        else -> 220.dp
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
            isLoading -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = BlueAccent)
                }
            }

            errorMessage != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Something went wrong",
                        color = RedDeny,
                        fontSize = 15.sp
                    )
                }
            }

            property == null -> {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = "Property not found",
                        color = GreyText,
                        fontSize = 15.sp
                    )
                }
            }

            else -> {
                val p = property!!
                var descExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .padding(bottom = 88.dp)
                ) {

                    // HERO IMAGE
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
                                    fontSize = 14.sp
                                )
                            }
                        }

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
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = hPad)
                    ) {

                        Text(
                            text = p.propertyName,
                            fontSize = 26.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = NavyDark,
                            lineHeight = 32.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        Surface(shape = RoundedCornerShape(50.dp), color = ChipBg) {
                            Text(
                                text = p.propertyType,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = BlueAccent,
                                fontSize = 13.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, tint = GreyText, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${p.city}, ${p.state}",
                                color = GreyText,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(22.dp))

                        // ABOUT
                        Text(
                            text = "About This Property",
                            fontSize = 17.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = NavyDark
                        )

                        Spacer(Modifier.height(8.dp))

                        val desc = p.propertyDescription ?: "No description available."
                        val truncatable = desc.length > 130

                        Text(
                            text = if (!descExpanded && truncatable) "${desc.take(130)}…" else desc,
                            color = GreyText,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
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

                        Spacer(Modifier.height(20.dp))

                        // PROPERTY DETAILS CARD
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
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = NavyDark
                                )
                                Spacer(Modifier.height(16.dp))

                                Row(Modifier.fillMaxWidth()) {
                                    DetailCell(
                                        icon = Icons.Outlined.CalendarMonth,
                                        label = "Year Established",
                                        value = p.yearEstablished.ifBlank { "-" },
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

                        // ROOMS SECTION
                        if (p.rooms.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Choose Your Room",
                                fontSize = 17.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = NavyDark
                            )

                            Spacer(Modifier.height(14.dp))

                            p.rooms.forEach { room ->
                                RoomCard(
                                    room = room,
                                    isSelected = room.id == selectedRoom?.id,
                                    onSelect = {
                                        selectedRoomId = room.id
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // AMENITIES
                        if (p.amenities.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Amenities",
                                fontSize = 17.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = NavyDark
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

                        Spacer(Modifier.height(14.dp))

                        // PROPERTY RULES
                        Text(
                            text = "Property Rules",
                            fontSize = 17.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = NavyDark
                        )

                        Spacer(Modifier.height(12.dp))

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                                RuleRow(Icons.Outlined.SmokeFree, Icons.Outlined.SmokingRooms, "Smoking", p.smokingAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.ChildCare, Icons.Outlined.ChildCare, "Children", p.childrenAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.Pets, Icons.Outlined.Pets, "Pets", p.petsAllowed == true)
                                HorizontalDivider(color = BorderGrey, thickness = 0.8.dp)
                                RuleRow(Icons.Outlined.NoMeals, Icons.Outlined.BreakfastDining, "Breakfast Included", p.breakfastIncluded == true)
                            }
                        }

                        // CANCELLATION POLICY
                        if (!p.cancellationPolicy.isNullOrBlank()) {
                            Spacer(Modifier.height(24.dp))

                            Text(
                                text = "Cancellation Policy",
                                fontSize = 17.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = NavyDark
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
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(Icons.Outlined.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = p.cancellationPolicy,
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

                // STICKY BOTTOM BAR
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
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
                                text = selectedRoom?.pricePerNight?.let { "₹$it" } ?: "N/A",
                                fontSize = 24.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = NavyDark
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "/night",
                                fontSize = 13.sp,
                                color = GreyText,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        Button(
                            onClick = { },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDark),
                            modifier = Modifier
                                .height(50.dp)
                                .widthIn(min = 150.dp)
                        ) {
                            Text(
                                text = "Reserve Stay",
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BlueAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = GreyText
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = NavyDark
            )
        }
    }
}

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
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) BlueAccent else BorderGrey,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SelectedRoomBg else CardBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = room.category,
                        fontSize = 17.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = NavyDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = room.bedType,
                        fontSize = 14.sp,
                        color = GreyText
                    )
                }

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = BlueAccent
                    ) {
                        Text(
                            text = "Selected",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoomMiniInfo(
                    icon = Icons.Outlined.Person,
                    label = "Guests",
                    value = "${room.maxGuests}"
                )
                RoomMiniInfo(
                    icon = Icons.Outlined.MeetingRoom,
                    label = "Available",
                    value = "${room.totalRooms}"
                )
                RoomMiniInfo(
                    icon = Icons.Outlined.Payments,
                    label = "Price",
                    value = "₹${room.pricePerNight}"
                )
            }
        }
    }
}

@Composable
private fun RoomMiniInfo(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BlueAccent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = GreyText
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = NavyDark
        )
    }
}

@Composable
private fun AmenityCell(name: String, modifier: Modifier = Modifier) {
    fun iconFor(n: String): ImageVector = when {
        n.contains("wifi", ignoreCase = true) -> Icons.Outlined.Wifi
        n.contains("park", ignoreCase = true) -> Icons.Outlined.DirectionsCar
        n.contains("airport", ignoreCase = true) -> Icons.Outlined.Flight
        n.contains("restaurant", ignoreCase = true) -> Icons.Outlined.Restaurant
        n.contains("pool", ignoreCase = true) -> Icons.Outlined.Pool
        n.contains("spa", ignoreCase = true) -> Icons.Outlined.Spa
        n.contains("gym", ignoreCase = true) -> Icons.Outlined.FitnessCenter
        n.contains("laundry", ignoreCase = true) -> Icons.Outlined.LocalLaundryService
        n.contains("room service", ignoreCase = true) -> Icons.Outlined.RoomService
        n.contains("breakfast", ignoreCase = true) -> Icons.Outlined.BreakfastDining
        n.contains("bar", ignoreCase = true) -> Icons.Outlined.LocalBar
        n.contains("tv", ignoreCase = true) -> Icons.Outlined.Tv
        else -> Icons.Outlined.CheckCircle
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
            Icon(
                imageVector = iconFor(name),
                contentDescription = name,
                tint = BlueAccent,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                color = GreyText,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    activeIcon: ImageVector,
    label: String,
    allowed: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
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
                color = NavyDark
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (allowed) GreenAllow else RedDeny
            )
        }
    }
}