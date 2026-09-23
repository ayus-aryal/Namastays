package com.example.namastays.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.namastays.components.AppErrorState
import com.example.namastays.components.AppLoadingIndicator
import com.example.namastays.dto.PlaceDetailResponse
import com.example.namastays.ui.theme.AccentBlue
import com.example.namastays.ui.theme.BackgroundColor
import com.example.namastays.ui.theme.CardWhite
import com.example.namastays.ui.theme.PlusJakartaSans
import com.example.namastays.ui.theme.PrimaryText
import com.example.namastays.ui.theme.SecondaryText
import com.example.namastays.ui.theme.SubtleText
import com.example.namastays.viewmodel.PlaceDetailUiState
import com.example.namastays.viewmodel.PlaceDetailViewModel
import kotlinx.coroutines.launch
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
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val context   = LocalContext.current
    val shareUrl  = "https://namastays.app/places/$citySlug/$placeSlug"

    // FIX #11 (audit): snackbar host so onOpenMaps has somewhere to report
    // failure if EVERY fallback below also fails, instead of crashing.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(citySlug, placeSlug) { viewModel.loadPlace(citySlug, placeSlug) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundColor
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundColor)
        ) {
            when (val state = uiState) {
                is PlaceDetailUiState.Idle,
                is PlaceDetailUiState.Loading -> AppLoadingIndicator()

                // FIX #17/#18 (audit): typed AppError through the shared
                // AppErrorState component (with a working retry), instead
                // of a static "Couldn't load place" Text with no retry
                // action at all.
                is PlaceDetailUiState.Error -> AppErrorState(
                    error   = state.error,
                    onRetry = { viewModel.retry(citySlug, placeSlug) }
                )

                is PlaceDetailUiState.Success -> PlaceDetailContent(
                    place      = state.place,
                    onBack     = { navController.popBackStack() },
                    onOpenMaps = {
                        openInMaps(
                            context  = context,
                            lat      = state.place.lat,
                            lng      = state.place.lng,
                            onAllFailed = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Couldn't open a maps app")
                                }
                            }
                        )
                    },
                    onShare = {
                        // FIX #12 (audit): real Android share sheet
                        // (ACTION_SEND) instead of silently copying the URL
                        // to the clipboard with no user-visible feedback.
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out ${state.place.name} on NamaStays: $shareUrl")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share ${state.place.name}"))
                    }
                )
            }
        }
    }
}

/**
 * FIX #11 (audit): the original onOpenMaps set
 *   setPackage("com.google.android.apps.maps")
 * with no try/catch. If Google Maps isn't installed, startActivity throws
 * ActivityNotFoundException — a straight crash, not a graceful failure.
 *
 * Fallback chain, each step only attempted if the previous one fails:
 *   1. Explicitly open in Google Maps (best experience if installed).
 *   2. Open a generic geo: intent with no package set, letting Android
 *      offer whatever maps app(s) the user actually has installed.
 *   3. Open Google Maps' web URL in a browser — virtually guaranteed to
 *      have a handler on any Android device.
 *   4. If even that throws, report failure via the snackbar instead of
 *      crashing.
 */
private fun openInMaps(
    context: android.content.Context,
    lat: Double,
    lng: Double,
    onAllFailed: () -> Unit
) {
    val geoUri = "geo:$lat,$lng?q=$lat,$lng".toUri()

    fun tryGoogleMapsApp(): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, geoUri).apply { setPackage("com.google.android.apps.maps") }
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun tryAnyMapsApp(): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    fun tryBrowserFallback(): Boolean = try {
        val webUri = "https://www.google.com/maps/search/?api=1&query=$lat,$lng".toUri()
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    val opened = tryGoogleMapsApp() || tryAnyMapsApp() || tryBrowserFallback()
    if (!opened) onAllFailed()
}

@Composable
private fun PlaceDetailContent(
    place: PlaceDetailResponse,
    onBack: () -> Unit,
    onOpenMaps: () -> Unit,
    onShare: () -> Unit
) {
    // FIX #15 (audit): both were plain remember { mutableStateOf(...) } —
    // rotating the device or process death reset which photo was selected
    // and whether the description was expanded. Now rememberSaveable.
    var currentImage by rememberSaveable { mutableStateOf(0) }
    var expanded     by rememberSaveable { mutableStateOf(false) }

    // FIX #13 (audit): the original "Read more" toggle used
    // (description?.length ?: 0) > 120 as a proxy for "does this text
    // overflow 3 lines" — a character-count guess that ignores font
    // metrics and screen width, so it both falsely showed the toggle when
    // text fit fine and falsely hid it when text genuinely overflowed.
    // hasOverflow is now set from the real TextLayoutResult via
    // onTextLayout, reflecting actual visual overflow at the current
    // screen width/font size.
    var hasOverflow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero image carousel ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            if (place.images.isNotEmpty()) {
                // FIX #14 (audit): the original carousel only supported
                // tapping the dot indicators — no swipe gesture, which is
                // the interaction every user instinctively tries first on
                // a photo carousel. HorizontalPager adds real swipe
                // support; the dots remain tappable and stay in sync via
                // pagerState.currentPage.
                val pagerState = rememberPagerState(
                    initialPage = currentImage.coerceIn(0, place.images.lastIndex),
                    pageCount   = { place.images.size }
                )
                val scope = rememberCoroutineScope()

                LaunchedEffect(pagerState.currentPage) {
                    currentImage = pagerState.currentPage
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    // FIX #16 (audit): explicit error branch — a failed
                    // image load now falls back to a neutral placeholder
                    // instead of rendering blank.
                    SubcomposeAsyncImage(
                        model              = place.images[page],
                        contentDescription = place.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                        loading = { Box(Modifier.fillMaxSize().background(Color(0xFFCCCCCC))) },
                        error   = { Box(Modifier.fillMaxSize().background(Color(0xFFCCCCCC))) },
                        success = { SubcomposeAsyncImageContent() }

                    )
                }

                // Carousel dots — now also reflect/drive pagerState.
                if (place.images.size > 1) {
                    Row(
                        modifier              = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        place.images.forEachIndexed { index, _ ->
                            val isSelected = index == pagerState.currentPage
                            val size by animateDpAsState(targetValue = if (isSelected) 8.dp else 5.dp, label = "dot_size")
                            Box(
                                modifier = Modifier
                                    .size(size)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                            )
                        }
                    }
                }
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
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryText, modifier = Modifier.size(18.dp))
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
                Icon(imageVector = Icons.Outlined.Share, contentDescription = "Share", tint = PrimaryText, modifier = Modifier.size(18.dp))
            }
        }

        // ── White content card ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardWhite)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            Text(text = place.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp, color = PrimaryText)

            Spacer(Modifier.height(28.dp))
            Text(text = "ABOUT", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = SubtleText)
            Spacer(Modifier.height(10.dp))
            Text(
                text      = place.description ?: "No description available.",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Normal,
                fontSize  = 15.sp,
                lineHeight = 24.sp,
                color     = SecondaryText,
                maxLines  = if (expanded) Int.MAX_VALUE else 3,
                overflow  = TextOverflow.Ellipsis,
                // FIX #13 (audit): real overflow detection. When NOT
                // expanded, Compose lays the text out at the 3-line cap and
                // reports whether it had to clip anything — that result
                // (not a character count) decides whether "Read more" is
                // shown. Re-evaluated automatically whenever expanded
                // toggles since maxLines is part of this composable's
                // layout key.
                onTextLayout = { result -> hasOverflow = result.hasVisualOverflow },
                modifier  = Modifier.animateContentSize()
            )
            if (hasOverflow || expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = if (expanded) "Show less" else "Read more",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 14.sp,
                    color      = AccentBlue,
                    modifier   = Modifier.clickable { expanded = !expanded }
                )
            }

            // Highlights
            if (place.tags.isNotEmpty()) {
                Spacer(Modifier.height(32.dp))
                Text(text = "HIGHLIGHTS", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = SubtleText)
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    place.tags.take(3).forEach { tag ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .shadow(1.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardWhite)
                                .padding(vertical = 18.dp, horizontal = 8.dp)
                        ) {
                            Icon(imageVector = iconForTag(tag), contentDescription = tag, tint = AccentBlue, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(text = tag, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PrimaryText, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Location
            Spacer(Modifier.height(32.dp))
            Text(text = "LOCATION", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp, color = SubtleText)
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
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(text = "Open in Maps", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PrimaryText)
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
            onBack   = {},
            onOpenMaps = {},
            onShare  = {}
        )
    }
}