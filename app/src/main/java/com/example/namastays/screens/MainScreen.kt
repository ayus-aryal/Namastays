package com.example.namastays.screens

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import com.example.namastays.auth.AuthEvents
import com.example.namastays.trek.presentataion.list.TrekListScreen
import com.example.namastays.trek.presentation.detail.TrekDetailScreen
import com.example.namastays.trek.presentataion.map.TrekMapScreen
import com.example.namastays.ui.theme.BackgroundColor
import com.example.namastays.viewmodel.TrekViewModel
import com.example.namastays.viewmodel.TrekViewModelFactory

private val routesWithoutBottomBar = setOf(
    "login",
    "packing_checklist",
    "cities",
    "safety/sos",
    "safety/contacts",
    "safety/add",
    "safety/compass",
    "safety/torch",
    "safety/ams",
    "safety/local_bodies"
)

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MainScreen(startDestination: String) {

    val navController = rememberNavController()
    val application   = LocalContext.current.applicationContext as Application

    val trekViewModel: TrekViewModel = viewModel(
        factory = TrekViewModelFactory(application)
    )

    val packingViewModel: PackingChecklistViewModel = viewModel()

    // Session died mid-use (background token refresh failed for real,
    // not just offline) — route to login using the exact same mechanism
    // as manual logout in ProfileScreen. Collected here, once, at the
    // NavController's own lifetime/scope.
    LaunchedEffect(Unit) {
        AuthEvents.sessionExpired.collect {
            navController.navigate("login") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute   = backStackEntry?.destination?.route

    val selectedTab = remember(currentRoute) {
        when {
            currentRoute == null -> 0

            currentRoute == "home"
                    || currentRoute.startsWith("search_results/")
                    || currentRoute.startsWith("property_details/")
                    || currentRoute.startsWith("confirm_booking/") -> 0

            currentRoute == "explore"
                    || currentRoute == "cities"
                    || currentRoute.startsWith("places/")
                    || currentRoute.startsWith("place_detail/") -> 1

            currentRoute == "maps"
                    || currentRoute.startsWith("trek_detail/")
                    || currentRoute.startsWith("trek_map/") -> 2

            currentRoute == "trek_mode" -> 3

            currentRoute == "safety"
                    || currentRoute.startsWith("safety/") -> 4

            else -> 0
        }
    }

    val hideBottomBar = currentRoute in routesWithoutBottomBar
            || currentRoute?.startsWith("trek_detail")       == true
            || currentRoute?.startsWith("place_detail")      == true
            || currentRoute?.startsWith("property_details")  == true
            || currentRoute?.startsWith("confirm_booking")   == true

    Scaffold(
        containerColor = BackgroundColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

        val contentPadding = when {
            currentRoute == "login" -> PaddingValues(0.dp)

            hideBottomBar -> PaddingValues(
                top    = 0.dp,
                start  = padding.calculateStartPadding(LocalLayoutDirection.current),
                end    = padding.calculateEndPadding(LocalLayoutDirection.current),
                bottom = 0.dp
            )

            else -> PaddingValues(
                top    = 0.dp,
                bottom = padding.calculateBottomPadding(), // keep bottom bar inset only
                start  = padding.calculateStartPadding(LocalLayoutDirection.current),
                end    = padding.calculateEndPadding(LocalLayoutDirection.current)
            )
        }

        NavHost(
            navController    = navController,
            startDestination = startDestination,
            modifier          = Modifier.padding(contentPadding)
        ) {
            addAuthFlow(navController)
            addMainTabs(navController, trekViewModel)
            addDetailFlows(navController, packingViewModel)
        }
    }
}

private fun NavGraphBuilder.addAuthFlow(navController: NavController) {
    composable("login") { com.example.namastays.screens.LoginScreen(navController) }
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

    composable("profile"){
        val app = LocalContext.current.applicationContext as com.example.namastays.NamastaysApp
        val profileViewModel: com.example.namastays.viewmodel.ProfileViewModel = viewModel(
            factory = com.example.namastays.viewmodel.ProfileViewModel.Factory(
                userRepository = app.deps.userRepository,
                authRepository = app.deps.authRepository
            )
        )
        ProfileScreen(
            navController = navController,
            viewModel = profileViewModel,
            onLogOutConfirmed = {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        )
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
    composable("safety/ams")          { LakeLouiseScreen(navController) }
    composable("safety/local_bodies") { EmergencySOSScreen(navController) }
}