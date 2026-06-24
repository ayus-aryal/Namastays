package com.example.namastays.screens

import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.viewmodel.PlaceDetailUiState
import com.example.namastays.viewmodel.PlaceDetailViewModel
import java.util.UUID
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Water
import com.example.namastays.NamastaysApp

private fun iconForTag(tag: String): ImageVector = when {
    tag.contains("photo", ignoreCase = true) || tag.contains("camera", ignoreCase = true)                -> Icons.Outlined.CameraAlt
    tag.contains("view", ignoreCase = true) || tag.contains("sunset", ignoreCase = true) || tag.contains("sunrise", ignoreCase = true) -> Icons.Filled.WbSunny
    tag.contains("forest", ignoreCase = true) || tag.contains("jungle", ignoreCase = true) || tag.contains("nature", ignoreCase = true) -> Icons.Filled.Park
    tag.contains("mountain", ignoreCase = true) || tag.contains("hill", ignoreCase = true) || tag.contains("trek", ignoreCase = true)   -> Icons.Filled.Landscape
    tag.contains("river", ignoreCase = true) || tag.contains("lake", ignoreCase = true) || tag.contains("waterfall", ignoreCase = true) -> Icons.Filled.Water
    tag.contains("cafe", ignoreCase = true) || tag.contains("coffee", ignoreCase = true)                -> Icons.Filled.LocalCafe
    tag.contains("restaurant", ignoreCase = true) || tag.contains("food", ignoreCase = true) || tag.contains("eat", ignoreCase = true)  -> Icons.Filled.Restaurant
    tag.contains("bar", ignoreCase = true) || tag.contains("drinks", ignoreCase = true)                 -> Icons.Filled.LocalBar
    tag.contains("wifi", ignoreCase = true) || tag.contains("internet", ignoreCase = true)              -> Icons.Filled.Wifi
    tag.contains("parking", ignoreCase = true)                                                          -> Icons.Filled.LocalParking
    tag.contains("atm", ignoreCase = true) || tag.contains("bank", ignoreCase = true)                   -> Icons.Filled.AccountBalance
    tag.contains("hospital", ignoreCase = true) || tag.contains("medical", ignoreCase = true)           -> Icons.Filled.LocalHospital
    tag.contains("pharmacy", ignoreCase = true)                                                         -> Icons.Filled.MedicalServices
    tag.contains("toilet", ignoreCase = true) || tag.contains("restroom", ignoreCase = true)            -> Icons.Filled.Wc
    tag.contains("temple", ignoreCase = true) || tag.contains("monastery", ignoreCase = true) || tag.contains("stupa", ignoreCase = true) -> Icons.Filled.AccountBalance
    tag.contains("museum", ignoreCase = true) || tag.contains("heritage", ignoreCase = true) || tag.contains("historic", ignoreCase = true) -> Icons.Filled.Museum
    tag.contains("market", ignoreCase = true) || tag.contains("shopping", ignoreCase = true)            -> Icons.Filled.ShoppingBag
    tag.contains("bus", ignoreCase = true)                                                              -> Icons.Filled.DirectionsBus
    tag.contains("taxi", ignoreCase = true) || tag.contains("cab", ignoreCase = true)                   -> Icons.Filled.LocalTaxi
    tag.contains("airport", ignoreCase = true)                                                          -> Icons.Filled.Flight
    tag.contains("hotel", ignoreCase = true) || tag.contains("lodge", ignoreCase = true) || tag.contains("teahouse", ignoreCase = true) -> Icons.Filled.Hotel
    tag.contains("camp", ignoreCase = true)                                                             -> Icons.Filled.NightShelter
    tag.contains("hiking", ignoreCase = true) || tag.contains("walking", ignoreCase = true)             -> Icons.Filled.DirectionsWalk
    tag.contains("cycling", ignoreCase = true) || tag.contains("bike", ignoreCase = true)               -> Icons.Filled.DirectionsBike
    tag.contains("swimming", ignoreCase = true)                                                         -> Icons.Filled.Pool
    tag.contains("yoga", ignoreCase = true)                                                             -> Icons.Filled.SelfImprovement
    tag.contains("family", ignoreCase = true)                                                           -> Icons.Filled.FamilyRestroom
    tag.contains("pet", ignoreCase = true)                                                              -> Icons.Filled.Pets
    tag.contains("free", ignoreCase = true)                                                             -> Icons.Filled.CardGiftcard
    tag.contains("paid", ignoreCase = true) || tag.contains("entry", ignoreCase = true)                 -> Icons.Filled.ConfirmationNumber
    tag.contains("open", ignoreCase = true) || tag.contains("hour", ignoreCase = true)                  -> Icons.Filled.Schedule
    tag.contains("popular", ignoreCase = true) || tag.contains("famous", ignoreCase = true)             -> Icons.Filled.Star
    else                                                                                                -> Icons.Filled.Place
}

@Composable
fun PlaceDetailScreen(
    citySlug: String,
    placeSlug: String,
    navController: NavController,
    viewModel: PlaceDetailViewModel = run {
        val app = LocalContext.current.applicationContext as NamastaysApp
        viewModel(factory = PlaceDetailViewModel.Factory(app.deps.placeRepository))
    }) {
    // Collect sealed UiState — replaces the three separate mutableStateOf reads
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shareUrl  = "https://namastays.app/places/$citySlug/$placeSlug"

    LaunchedEffect(citySlug, placeSlug) { viewModel.loadPlace(citySlug, placeSlug) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F2F7))
    ) {
        when (val state = uiState) {
            is PlaceDetailUiState.Idle,
            is PlaceDetailUiState.Loading -> CircularProgressIndicator(
                color    = Color(0xFF4A80F0),
                modifier = Modifier.align(Alignment.Center)
            )

            is PlaceDetailUiState.Error -> Text(
                text     = "Couldn't load place",
                fontFamily = PlusJakartaSans,
                fontSize = 15.sp,
                color    = Color(0xFF888888),
                modifier = Modifier.align(Alignment.Center)
            )

            is PlaceDetailUiState.Success -> PlaceDetailContent(
                place      = state.place,
                shareUrl   = shareUrl,
                onBack     = { navController.popBackStack() },
                onOpenMaps = {
                    val place = state.place
                    val uri   = "geo:${place.lat},${place.lng}?q=${place.lat},${place.lng}".toUri()
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    context.startActivity(intent)
                },
                onShare = { clipboard.setText(AnnotatedString(shareUrl)) }
            )
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: PlaceDetailResponse,
    shareUrl: String,
    onBack: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit
) {
    var currentImage by remember { mutableStateOf(0) }
    var expanded     by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(Color(0xFFF2F2F7))
    ) {
        // ── Hero image ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            if (place.images.isNotEmpty()) {
                AsyncImage(
                    model              = place.images[currentImage],
                    contentDescription = place.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFCCCCCC)))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f))))
            )

            // Back button
            Box(
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .align(Alignment.TopStart)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF111111), modifier = Modifier.size(18.dp))
            }

            // Share button
            Box(
                modifier = Modifier
                    .padding(top = 48.dp, end = 16.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .align(Alignment.TopEnd)
                    .clickable { onShare() },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share", tint = Color(0xFF111111), modifier = Modifier.size(18.dp))
            }

            // Carousel dots
            if (place.images.size > 1) {
                Row(
                    modifier              = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    place.images.forEachIndexed { index, _ ->
                        val isSelected = index == currentImage
                        val size by animateDpAsState(targetValue = if (isSelected) 8.dp else 5.dp, label = "dot_size")
                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
                                .clickable { currentImage = index }
                        )
                    }
                }
            }
        }

        // ── White content card ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(elevation = 0.dp, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            Text(text = place.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, color = Color(0xFF0D0D0D))

            Spacer(Modifier.height(28.dp))
            Text(text = "ABOUT", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = Color(0xFFAAAAAA))
            Spacer(Modifier.height(10.dp))
            Text(
                text      = place.description ?: "No description available.",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize  = 15.sp,
                lineHeight = 24.sp,
                color     = Color(0xFF6B7280),
                maxLines  = if (expanded) Int.MAX_VALUE else 3,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.animateContentSize()
            )
            if ((place.description?.length ?: 0) > 120) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = if (expanded) "Show less" else "Read more",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF4A80F0),
                    modifier   = Modifier.clickable { expanded = !expanded }
                )
            }

            // Highlights
            if (place.tags.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(text = "HIGHLIGHTS", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = Color(0xFFAAAAAA))
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    place.tags.take(3).forEach { tag ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .shadow(1.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(vertical = 18.dp, horizontal = 8.dp)
                        ) {
                            Icon(imageVector = iconForTag(tag), contentDescription = tag, tint = Color(0xFF4A80F0), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(text = tag, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF374151), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Location
            Spacer(Modifier.height(32.dp))
            Text(text = "LOCATION", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = Color(0xFFAAAAAA))
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .shadow(1.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEEF0FF))
                    .clickable { onOpenMaps() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(52.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF4A80F0), modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Open in Maps", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF111827))
                }
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "Place Detail Screen")
@Composable
fun PlaceDetailScreenPreview() {
    val sample = PlaceDetailResponse(
        id          = UUID.randomUUID(),
        name        = "Boudhanath Stupa",
        description = "Boudhanath is a stupa in Kathmandu, Nepal. Located about 11 km from the center and northeastern outskirts of Kathmandu, the stupa's massive mandala makes it one of the largest spherical stupas in Nepal and the world.",
        lat         = 27.7215,
        lng         = 85.3620,
        images      = listOf(
            "https://images.unsplash.com/photo-1544735716-392fe2489ffa?w=800",
            "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800"
        ),
        tags = listOf("Photography", "Cafes Nearby", "Free Wifi")
    )
    MaterialTheme {
        PlaceDetailContent(
            place    = sample,
            shareUrl = "https://namastays.app/places/kathmandu/boudhanath-stupa",
            onBack   = {},
            onOpenMaps = {},
            onShare  = {}
        )
    }
}