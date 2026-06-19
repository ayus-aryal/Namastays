package com.example.namastays.screens

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.namastays.trek.presentataion.list.TrekListScreen
import com.example.namastays.trek.presentation.detail.TrekDetailScreen
import com.example.namastays.trek.presentataion.map.TrekMapScreen
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory

// Routes where the bottom nav must be hidden.
// Top-level set — allocated once, not on every recomposition.
private val routesWithoutBottomBar = setOf(
    "packing_checklist",
    "cities",
    "safety/sos",
    "safety/contacts",
    "safety/add",
    "safety/compass",
    "safety/torch"
)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MainScreen() {

    val navController = rememberNavController()
    val application   = LocalContext.current.applicationContext as Application

    val trekViewModel = remember {
        TrekViewModelFactory(application).create(TrekViewModel::class.java)
    }

    // Pre-warm: VM is created here at MainScreen scope so it's fully initialised
    // before the user ever taps "Trip Checklist".
    val packingViewModel: PackingChecklistViewModel = viewModel()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    val selectedTab = remember(currentRoute) {
        when (currentRoute) {
            "home"      -> 0
            "explore"   -> 1
            "maps"      -> 2
            "trek_mode" -> 3
            "safety"    -> 4
            else        -> 0
        }
    }

    val hideBottomBar = currentRoute in routesWithoutBottomBar
            || currentRoute?.startsWith("trek_detail")       == true
            || currentRoute?.startsWith("place_detail")      == true
            || currentRoute?.startsWith("property_details")  == true
            || currentRoute?.startsWith("confirm_booking")   == true

    Scaffold(
        containerColor = BackgroundColor,
        bottomBar = {
            if (!hideBottomBar) {
                TravelerBottomNavBar(
                    items       = bottomNavItems,
                    selectedIdx = selectedTab,
                    onItemClick = { index ->
                        val route = when (index) {
                            0    -> "home"
                            1    -> "explore"
                            2    -> "maps"
                            3    -> "trek_mode"
                            4    -> "safety"
                            else -> "home"
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                )
            }
        }
    ) { padding ->

        // When the bottom bar is hidden, Scaffold still reserves the same
        // bottom system-bar inset in `padding` even though nothing is drawn
        // there anymore. Strip just the bottom component in that case —
        // screens with their own sticky bottom content (ConfirmBookingScreen,
        // PropertyDetailsScreen) consume the nav-bar inset themselves via
        // navigationBarsPadding() on that sticky element.
        val contentPadding = if (hideBottomBar) {
            PaddingValues(
                top    = padding.calculateTopPadding(),
                start  = padding.calculateStartPadding(LocalLayoutDirection.current),
                end    = padding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = 0.dp
            )
        } else {
            padding
        }

        // No enter/exit/pop transitions specified — navigation is instant,
        // with no animation in either direction.
        NavHost(
            navController    = navController,
            startDestination = "home",
            modifier          = Modifier.padding(contentPadding)
        ) {
            addMainTabs(navController, trekViewModel)
            addDetailFlows(navController, packingViewModel)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private fun NavGraphBuilder.addMainTabs(
    navController : NavController,
    trekViewModel : TrekViewModel
) {
    composable("home")    { HomeScreen(navController) }
    composable("explore") { ExploreScreen(navController) }
    composable("maps")    { TrekListScreen(navController) }
    composable("safety")  { SafetyHomeScreen(navController) }

    composable("trek_mode") {
        val selectedSession by trekViewModel.selectedSession
            .collectAsStateWithLifecycle()

        if (selectedSession == null) {
            TrekModeScreen(onSessionClick = { trekViewModel.selectSession(it) })
        } else {
            TrekSessionDetailScreen(
                session  = selectedSession!!,
                onBack   = { trekViewModel.selectSession(null) },
                onDelete = { trekViewModel.deleteSession(it) }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun NavGraphBuilder.addDetailFlows(
    navController    : NavController,
    packingViewModel : PackingChecklistViewModel
) {
    composable("packing_checklist") {
        PackingChecklistScreen(
            navController = navController,
            vm            = packingViewModel
        )
    }

    composable("cities") { CityListScreen(navController) }

    composable(
        route     = "trek_detail/{trekId}",
        arguments = listOf(navArgument("trekId") { type = NavType.StringType })
    ) { entry ->
        TrekDetailScreen(
            trekId        = entry.arguments?.getString("trekId").orEmpty(),
            navController = navController
        )
    }

    composable(
        route     = "trek_map/{trekId}",
        arguments = listOf(navArgument("trekId") { type = NavType.StringType })
    ) { entry ->
        TrekMapScreen(
            trekId        = entry.arguments?.getString("trekId").orEmpty(),
            navController = navController
        )
    }

    composable(
        route     = "search_results/{city}",
        arguments = listOf(navArgument("city") { type = NavType.StringType })
    ) { entry ->
        SearchResultsScreen(
            entry.arguments?.getString("city").orEmpty(),
            navController
        )
    }

    composable(
        route     = "property_details/{propertyId}",
        arguments = listOf(navArgument("propertyId") { type = NavType.StringType })
    ) { entry ->
        PropertyDetailsScreen(
            entry.arguments?.getString("propertyId").orEmpty(),
            navController
        )
    }

    composable(
        route     = "confirm_booking/{propertyId}/{roomId}",
        arguments = listOf(
            navArgument("propertyId") { type = NavType.StringType },
            navArgument("roomId")     { type = NavType.StringType }
        )
    ) { entry ->
        ConfirmBookingScreen(
            propertyId    = entry.arguments?.getString("propertyId").orEmpty(),
            roomId        = entry.arguments?.getString("roomId").orEmpty(),
            navController = navController
        )
    }

    composable(
        route     = "places/{citySlug}",
        arguments = listOf(navArgument("citySlug") { type = NavType.StringType })
    ) { entry ->
        PlaceListScreen(
            navController = navController,
            citySlug      = entry.arguments?.getString("citySlug").orEmpty()
        )
    }

    composable(
        route     = "place_detail/{citySlug}/{placeSlug}",
        arguments = listOf(
            navArgument("citySlug")  { type = NavType.StringType },
            navArgument("placeSlug") { type = NavType.StringType }
        )
    ) { entry ->
        PlaceDetailScreen(
            citySlug      = entry.arguments?.getString("citySlug").orEmpty(),
            placeSlug     = entry.arguments?.getString("placeSlug").orEmpty(),
            navController = navController
        )
    }

    composable("safety/sos")      { SOSScreen(navController) }
    composable("safety/contacts") { EmergencyContactsScreen(navController) }
    composable("safety/add")      { AddContactScreen(navController) }
    composable("safety/compass")  { CompassScreen(navController) }
    composable("safety/torch")    { TorchScreen(navController) }
}