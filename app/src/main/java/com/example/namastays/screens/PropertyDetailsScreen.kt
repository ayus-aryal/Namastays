package com.example.namastays.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.NamastaysApp
import com.example.namastays.dto.RoomResponse
import com.example.namastays.ui.theme.AccentGreen
import com.example.namastays.ui.theme.CardWhite
import com.example.namastays.ui.theme.ErrorColor
import com.example.namastays.ui.theme.ErrorContainer
import com.example.namastays.ui.theme.NavyDark
import com.example.namastays.ui.theme.OnErrorContainer
import com.example.namastays.ui.theme.OnSecContainer
import com.example.namastays.ui.theme.OnSurface
import com.example.namastays.ui.theme.OnSurfaceVariant
import com.example.namastays.ui.theme.OutlineVariant
import com.example.namastays.ui.theme.PageBg
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PlusJakartaSansBold
import com.example.namastays.ui.theme.Primary
import com.example.namastays.ui.theme.SecContainer
import com.example.namastays.ui.theme.SelectedRoomBg
import com.example.namastays.ui.theme.SurfaceContainer
import com.example.namastays.ui.theme.SurfaceLow
import com.example.namastays.viewmodel.PropertyDetailsUiState
import com.example.namastays.viewmodel.PropertyDetailsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// NOTE: SkeletonHigh was declared in this file but never referenced anywhere
// below — dead code. It now lives in ui.theme.Color.kt but is intentionally
// not imported here since nothing in this file uses it.
//
// Bug fix: a SUCCESS color token did not previously exist in this file's
// local palette (everything was either Primary/indigo or Error/red). The
// room availability badge used ErrorContainer/OnErrorContainer for the
// "available" state, which read as a red "sold out"-style badge for rooms
// that were actually available. AccentGreen (imported from ui.theme.Color)
// is now used for the available state; ErrorContainer is reserved for the
// genuinely sold-out state. See RoomCard below.
// ─────────────────────────────────────────────────────────────────────────────

private val AvailableBg   = Color(0xFFDCFCE7) // light green container, matches AccentGreen family
private val OnAvailableBg = Color(0xFF166534) // dark green text for contrast on AvailableBg

// ── Skeleton ───────────────────────────────────────────────────────────────────
@Composable
private fun PropertyDetailsSkeleton(hPad: androidx.compose.ui.unit.Dp) {
    val brush = shimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = hPad, vertical = 16.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(brush)
        )
        Column(modifier = Modifier.padding(horizontal = hPad)) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(70.dp).height(26.dp).clip(RoundedCornerShape(50.dp)).background(brush))
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth(0.8f).height(32.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.5f).height(18.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(18.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(20.dp)).background(brush))
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth(0.45f).height(22.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(Modifier.height(12.dp))
            repeat(3) {
                Box(Modifier.fillMaxWidth(if (it == 2) 0.65f else 1f).height(16.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Error state ────────────────────────────────────────────────────────────────
@Composable
private fun PropertyDetailsError(message: String, onBack: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(ErrorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.WifiOff, null, tint = ErrorColor, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Couldn't load property", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
            Text("Go Back", fontWeight = FontWeight.SemiBold, color = OnSurface, fontFamily = PlusJakartaSans)
        }
    }
}

// ── Not found state ────────────────────────────────────────────────────────────
@Composable
private fun PropertyNotFound(onBack: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(SurfaceLow), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.SearchOff, null, tint = OnSurfaceVariant, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Property not found", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(8.dp))
        Text("This property may no longer be available\nor the link may be incorrect.", fontSize = 14.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = NavyDark)) {
            Text("Browse Other Stays", fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = PlusJakartaSans)
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

/**
 * Full property details screen: hero image, identity, room picker,
 * amenities, guest policies, and a sticky "Book Now" CTA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDetailsScreen(
    propertyId: String,
    navController: NavController,
) {
    // Composable-safe ViewModel creation — see SearchResultsScreen.kt for the
    // same fix and rationale (the previous `run {}` default-parameter
    // pattern re-evaluated the factory lambda inline in the signature).
    val context = LocalContext.current
    val app = LocalContext.current.applicationContext as NamastaysApp
    val viewModel: PropertyDetailsViewModel = viewModel(
        factory = PropertyDetailsViewModel.Factory(app.deps.propertyRepository)
    )

    LaunchedEffect(propertyId) { viewModel.fetchPropertyDetails(propertyId) }

    // ── Collect sealed UiState — replaces the broken remember{} delegation ────
    val uiState      by viewModel.uiState.collectAsStateWithLifecycle()
    val property     = (uiState as? PropertyDetailsUiState.Success)?.property
    val isLoading    = uiState is PropertyDetailsUiState.Loading
    val errorMessage = (uiState as? PropertyDetailsUiState.Error)?.message

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
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
            isLoading            -> PropertyDetailsSkeleton(hPad = hPad)
            errorMessage != null -> PropertyDetailsError(
                message = errorMessage,
                onBack  = { navController.popBackStack() }
            )
            property == null     -> PropertyNotFound(onBack = { navController.popBackStack() })
            else -> {
                val p = property
                var descExpanded     by remember { mutableStateOf(false) }
                var showAllAmenities by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 90.dp)
                ) {
                    // ── HERO ──────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        if (p.imageUrls.isNotEmpty()) {
                            AsyncImage(
                                model              = p.imageUrls.first(),
                                contentDescription = p.propertyName,
                                modifier           = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)),
                                contentScale       = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier         = Modifier.fillMaxSize().clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)).background(SurfaceLow),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Image, null, tint = OnSurfaceVariant, modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No photos available", color = OnSurfaceVariant, fontSize = 14.sp, fontFamily = PlusJakartaSans)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)))
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(start = hPad, top = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                                .clickable { navController.popBackStack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = OnSurface, modifier = Modifier.size(20.dp))
                        }

                        if (p.imageUrls.size > 1) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("1 / ${p.imageUrls.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                                }
                            }
                        }
                    }

                    // ── PROPERTY IDENTITY ─────────────────────────────────
                    Column(Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                        Spacer(Modifier.height(16.dp))
                        Box(modifier = Modifier.clip(CircleShape).background(SecContainer).padding(horizontal = 14.dp, vertical = 5.dp)) {
                            Text(p.propertyType.uppercase(), color = OnSecContainer, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, fontFamily = PlusJakartaSans)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(p.propertyName, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = OnSurface, lineHeight = 36.sp, letterSpacing = (-0.3).sp, fontFamily = PlusJakartaSansBold)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, null, tint = Primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${p.city}, ${p.state}", color = OnSurfaceVariant, fontSize = 14.sp, fontFamily = PlusJakartaSans)
                        }
                        Spacer(Modifier.height(8.dp))

                        Text("View on map", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans, modifier = Modifier.clickable { openLocationInMaps(context, p) })
                        Spacer(Modifier.height(20.dp))

                        // Quick stats card
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardWhite).padding(vertical = 18.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                QuickStatCol("CHECK-IN",    p.checkInTime  ?: "—")
                                Box(Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.3f)))
                                QuickStatCol("CHECK-OUT",   p.checkOutTime ?: "—")
                                Box(Modifier.width(1.dp).height(36.dp).background(OutlineVariant.copy(alpha = 0.3f)))
                                QuickStatCol("ESTABLISHED", p.yearEstablished.ifBlank { "—" })
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // About
                        Text("About the ${p.propertyType.replaceFirstChar { it.uppercase() }}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, lineHeight = 30.sp, fontFamily = PlusJakartaSansBold)
                        Spacer(Modifier.height(10.dp))
                        val desc = p.propertyDescription?.takeIf { it.isNotBlank() } ?: "No description has been provided for this property yet."
                        val truncatable = desc.length > 150
                        Text(
                            text       = if (!descExpanded && truncatable) "${desc.take(150)}…" else desc,
                            color      = OnSurfaceVariant,
                            fontSize   = 16.sp,
                            lineHeight = 26.sp,
                            fontFamily = PlusJakartaSans
                        )
                        if (truncatable) {
                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.clickable { descExpanded = !descExpanded }, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (descExpanded) "Read less" else "Read more", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans)
                                Spacer(Modifier.width(2.dp))
                                Icon(if (descExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ArrowForward, null, tint = Primary, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(Modifier.height(28.dp))
                        if (p.rooms.isNotEmpty()) {
                            Text("Available Rooms", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSansBold)
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    // ── ROOMS ──────────────────────────────────────────────
                    if (p.rooms.isNotEmpty()) {
                        Row(
                            modifier              = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = hPad),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            p.rooms.forEach { room ->
                                RoomCard(
                                    room       = room,
                                    isSelected = room.id == selectedRoom?.id,
                                    onSelect   = { selectedRoomId = room.id }
                                )
                            }
                        }
                    } else {
                        Column(Modifier.padding(horizontal = hPad)) {
                            Text("Available Rooms", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSansBold)
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier         = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, OutlineVariant, RoundedCornerShape(18.dp)).padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.KingBed, null, tint = OnSurfaceVariant, modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text("No rooms listed yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, fontFamily = PlusJakartaSans)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Contact the property directly\nfor availability and rates.", fontSize = 13.sp, color = OnSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 20.sp, fontFamily = PlusJakartaSans)
                                }
                            }
                        }
                    }

                    Column(Modifier.fillMaxWidth().padding(horizontal = hPad)) {
                        // Amenities
                        if (p.amenities.isNotEmpty()) {
                            Spacer(Modifier.height(32.dp))
                            Text("Amenities", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSansBold)
                            Spacer(Modifier.height(16.dp))
                            val visibleAmenities = if (showAllAmenities) p.amenities else p.amenities.take(6)
                            visibleAmenities.chunked(3).forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { AmenityCell(name = it, modifier = Modifier.weight(1f)) }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            if (p.amenities.size > 6) {
                                Spacer(Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick  = { showAllAmenities = !showAllAmenities },
                                    shape    = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    border   = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariant)
                                ) {
                                    Text(
                                        if (showAllAmenities) "Show less" else "Show all ${p.amenities.size} amenities",
                                        color = OnSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans
                                    )
                                }
                            }
                        }

                        // Guest policies
                        Spacer(Modifier.height(32.dp))
                        Text("Guest Policies", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSansBold)
                        Spacer(Modifier.height(14.dp))
                        PolicyRow(Icons.Outlined.SmokeFree,       "Smoking",   p.smokingAllowed    == true, "Allowed",  "Not Allowed")
                        Spacer(Modifier.height(10.dp))
                        PolicyRow(Icons.Outlined.ChildCare,       "Children",  p.childrenAllowed   == true, "Allowed",  "Not Allowed")
                        Spacer(Modifier.height(10.dp))
                        PolicyRow(Icons.Outlined.Pets,            "Pets",      p.petsAllowed       == true, "Allowed",  "Not Allowed")
                        Spacer(Modifier.height(10.dp))
                        PolicyRow(Icons.Outlined.BreakfastDining, "Breakfast", p.breakfastIncluded == true, "Included", "Not Included")

                        // Cancellation policy
                        if (!p.cancellationPolicy.isNullOrBlank()) {
                            Spacer(Modifier.height(28.dp))
                            Text("Cancellation Policy", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurface, fontFamily = PlusJakartaSansBold)
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(18.dp))
                                    .background(Color(0xFFFFFBEB))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Outlined.Info, null, tint = Color(0xFFB45309), modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(p.cancellationPolicy, color = Color(0xFF92400E), fontSize = 14.sp, lineHeight = 22.sp, fontFamily = PlusJakartaSans)
                                }
                            }
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }

                // Sticky bottom bar
                Surface(
                    modifier        = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    color           = CardWhite,
                    shadowElevation = 12.dp,
                    shape           = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

                ) {
                    Row(
                        modifier              = Modifier
                            .navigationBarsPadding()
                            .fillMaxWidth().padding(horizontal = hPad, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Starting from", fontSize = 12.sp, color = OnSurfaceVariant, fontWeight = FontWeight.Medium, fontFamily = PlusJakartaSans)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    selectedRoom?.pricePerNight?.let { "NPR $it" } ?: "N/A",
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Primary, letterSpacing = (-0.3).sp, fontFamily = PlusJakartaSansBold
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("/night", fontSize = 12.sp, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp), fontFamily = PlusJakartaSans)
                            }
                        }
                        Button(
                            onClick        = { if (selectedRoom != null && p.rooms.isNotEmpty()) navController.navigate("confirm_booking/${p.id}/${selectedRoom.id}") },
                            enabled        = selectedRoom != null && p.rooms.isNotEmpty(),
                            shape          = RoundedCornerShape(12.dp),
                            colors         = ButtonDefaults.buttonColors(containerColor = NavyDark, disabledContainerColor = OutlineVariant),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text(if (p.rooms.isEmpty()) "Unavailable" else "Book Now", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = PlusJakartaSans)
                        }
                    }
                }
            }
        }
    }
}

// ── Quick Stat Column ─────────────────────────────────────────────────────────
@Composable
private fun QuickStatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = OnSurfaceVariant, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp, fontFamily = PlusJakartaSans)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Primary, fontFamily = PlusJakartaSansBold)
    }
}

// ── Room Card ─────────────────────────────────────────────────────────────────

/**
 * A single selectable room card.
 *
 * Bug fix: the "rooms left" badge previously used the same red
 * ErrorContainer/OnErrorContainer pair for BOTH the available and sold-out
 * states, which made available rooms read visually as "sold out". It now
 * uses AvailableBg/OnAvailableBg (green) when rooms are available and
 * ErrorContainer/OnErrorContainer (red) only when genuinely sold out.
 */
@Composable
private fun RoomCard(room: RoomResponse, isSelected: Boolean, onSelect: () -> Unit) {
    val isAvailable = room.totalRooms > 0
    Box(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) SelectedRoomBg else CardWhite)
            .border(width = if (isSelected) 2.dp else 0.dp, color = if (isSelected) Primary else Color.Transparent, shape = RoundedCornerShape(20.dp))
            .clickable { onSelect() }
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(SurfaceLow)
            ) {
                Icon(Icons.Outlined.KingBed, null, tint = OnSurfaceVariant, modifier = Modifier.size(36.dp).align(Alignment.Center))
                if (isSelected) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(CircleShape).background(Primary).padding(horizontal = 10.dp, vertical = 3.dp)) {
                        Text("Selected", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 0.3.sp, fontFamily = PlusJakartaSans)
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(room.category, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = OnSurface, fontFamily = PlusJakartaSans)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("NPR ${room.pricePerNight}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Primary else OnSurface, fontFamily = PlusJakartaSansBold)
                    Text(" / night", fontSize = 12.sp, color = OnSurfaceVariant, modifier = Modifier.padding(bottom = 1.dp), fontFamily = PlusJakartaSans)
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isAvailable) AvailableBg else SurfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text       = if (isAvailable) "${room.totalRooms} room${if (room.totalRooms > 1) "s" else ""} left" else "Sold out",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (isAvailable) OnAvailableBg else OnSurfaceVariant,
                            fontFamily = PlusJakartaSans
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Primary, modifier = Modifier.size(24.dp))
                    } else if (isAvailable) {
                        Text("Select", color = Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans, modifier = Modifier.clickable { onSelect() })
                    }
                }
            }
        }
    }
}

// ── Amenity Cell ──────────────────────────────────────────────────────────────

/**
 * Maps an amenity's name to a representative icon.
 *
 * Hoisted to a top-level private function (was previously a local `fun`
 * declared inside the @Composable AmenityCell, meaning it was re-declared —
 * though not re-allocated — on every recomposition; moving it out makes the
 * separation between "pure lookup logic" and "composable UI" explicit and
 * lets it be reused/tested independently of AmenityCell).
 */
private fun iconForAmenity(name: String): ImageVector = when {
    name.contains("wifi",         ignoreCase = true) -> Icons.Outlined.Wifi
    name.contains("park",         ignoreCase = true) -> Icons.Outlined.DirectionsCar
    name.contains("airport",      ignoreCase = true) || name.contains("pickup", ignoreCase = true) -> Icons.Outlined.Flight
    name.contains("restaurant",   ignoreCase = true) -> Icons.Outlined.Restaurant
    name.contains("pool",         ignoreCase = true) -> Icons.Outlined.Pool
    name.contains("spa",          ignoreCase = true) -> Icons.Outlined.Spa
    name.contains("gym",          ignoreCase = true) -> Icons.Outlined.FitnessCenter
    name.contains("laundry",      ignoreCase = true) -> Icons.Outlined.LocalLaundryService
    name.contains("room service", ignoreCase = true) -> Icons.Outlined.RoomService
    name.contains("breakfast",    ignoreCase = true) -> Icons.Outlined.BreakfastDining
    name.contains("bar",          ignoreCase = true) -> Icons.Outlined.LocalBar
    name.contains("tv",           ignoreCase = true) -> Icons.Outlined.Tv
    name.contains("hot water",    ignoreCase = true) -> Icons.Outlined.HotTub
    name.contains("ac",           ignoreCase = true) || name.contains("air", ignoreCase = true) -> Icons.Outlined.AcUnit
    name.contains("garden",       ignoreCase = true) -> Icons.Outlined.Park
    else                                              -> Icons.Outlined.CheckCircle
}

@Composable
private fun AmenityCell(name: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(CardWhite, CircleShape), contentAlignment = Alignment.Center) {
            Icon(iconForAmenity(name), contentDescription = name, tint = Primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(name, fontSize = 11.sp, color = OnSurfaceVariant, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, fontFamily = PlusJakartaSans)
    }
}

// ── Policy Row ────────────────────────────────────────────────────────────────
@Composable
private fun PolicyRow(
    icon: ImageVector, label: String, allowed: Boolean,
    allowedLabel: String = "Allowed", deniedLabel: String = "Not Allowed"
) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardWhite).padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = if (allowed) Primary else ErrorColor, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(label, fontSize = 16.sp, color = OnSurface, fontWeight = FontWeight.Normal, fontFamily = PlusJakartaSans)
            }
            Box(modifier = Modifier.clip(CircleShape).background(if (allowed) Primary.copy(alpha = 0.12f) else ErrorContainer).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(if (allowed) allowedLabel else deniedLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = if (allowed) Primary else OnErrorContainer, fontFamily = PlusJakartaSans)
            }
        }
    }
}



private fun openLocationInMaps(context: android.content.Context, property: com.example.namastays.dto.PropertyDetailsResponse) {
    val gmmIntentUri = if (property.latitude != null && property.longitude != null) {
        Uri.parse("geo:${property.latitude},${property.longitude}?q=${property.latitude},${property.longitude}(${Uri.encode(property.propertyName)})")
    } else {
        val fullAddress = listOf(property.address, property.city, property.state, property.postalCode, property.country)
            .filter { it.isNotBlank() }.joinToString(", ")
        Uri.parse("geo:0,0?q=${Uri.encode(fullAddress)}")
    }
    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply { setPackage("com.google.android.apps.maps") }
    if (mapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapIntent)
    } else {
        val fallbackQuery = if (property.latitude != null && property.longitude != null)
            "${property.latitude},${property.longitude}"
        else Uri.encode(listOf(property.address, property.city, property.state, property.postalCode, property.country).filter { it.isNotBlank() }.joinToString(", "))
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$fallbackQuery")))
    }
}