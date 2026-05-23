package com.example.namastays.screens


import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.namastays.trek.presentataion.map.TrekMapScreen
import com.example.namastays.trek.presentation.list.TrekListScreen
import com.example.namastays.trek.presentation.detail.TrekDetailScreen

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = bottomNavItems

    // Map route → selected tab
    val selectedIndex = when (currentRoute) {
        "home" -> 0
        "explore" -> 1
        "maps" -> 2
        "trek_mode" -> 3
        "safety" -> 4
        else -> 0
    }

    Scaffold(
        bottomBar = {
            TravelerBottomNavBar(
                items = items,
                selectedIdx = selectedIndex,
                onItemClick = { index ->

                    val route = when (index) {
                        0 -> "home"
                        1 -> "explore"
                        2 -> "maps"
                        3 -> "trek_mode"
                        4 -> "safety"
                        else -> "home"
                    }

                    navController.navigate(route) {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                }
            )
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {

            composable("home") {
                HomeScreen(navController)
            }

            composable("explore") {
                ExploreScreen(navController)
            }

            composable("maps") {
                TrekListScreen(navController)
            }

            composable(
                route = "trek_detail/{trekId}",
                arguments = listOf(navArgument("trekId") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val trekId = backStackEntry.arguments?.getString("trekId") ?: ""
                TrekDetailScreen(trekId = trekId, navController = navController)
            }

            composable(
                route = "trek_map/{trekId}",
                arguments = listOf(navArgument("trekId") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val trekId = backStackEntry.arguments?.getString("trekId") ?: ""
                TrekMapScreen(trekId = trekId, navController = navController)
            }

            composable("trek_mode") {
                TrekModeScreen()
            }

            composable("safety") {
                SafetyHomeScreen(navController)
            }

            composable(
                route = "search_results/{city}",
                arguments = listOf(navArgument("city") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val city = backStackEntry.arguments?.getString("city") ?: ""
                SearchResultsScreen(city, navController)
            }

            composable(
                route = "property_details/{propertyId}",
                arguments = listOf(navArgument("propertyId") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->
                val propertyId = backStackEntry.arguments?.getString("propertyId") ?: ""
                PropertyDetailsScreen(propertyId, navController)
            }

            composable("cities"){
                CityListScreen(navController)
            }

            composable(
                route = "places/{citySlug}",
                arguments = listOf(navArgument("citySlug") {
                    type = NavType.StringType
                })
            ) { backStackEntry ->

                val citySlug = backStackEntry.arguments?.getString("citySlug") ?: ""

                PlaceListScreen(
                    navController = navController,
                    citySlug = citySlug
                )
            }

            composable(
                route = "place_detail/{citySlug}/{placeSlug}",
                arguments = listOf(
                    navArgument("citySlug") { type = NavType.StringType },
                    navArgument("placeSlug") { type = NavType.StringType }
                )
            ) { backStackEntry ->

                val citySlug = backStackEntry.arguments?.getString("citySlug") ?: ""
                val placeSlug = backStackEntry.arguments?.getString("placeSlug") ?: ""

                PlaceDetailScreen(
                    citySlug = citySlug,
                    placeSlug = placeSlug,
                    navController = navController
                )
            }

            composable(SafetyRoutes.SOS)          { SOSScreen(navController) }
            composable(SafetyRoutes.LAKE_LOUISE)  { LakeLouiseScreen(navController) }
            composable(SafetyRoutes.CONTACTS)     { EmergencyContactsScreen(navController) }
            composable(SafetyRoutes.ADD_CONTACT)  { AddContactScreen(navController) }
            composable(SafetyRoutes.COMPASS)      { CompassScreen(navController) }
            composable(SafetyRoutes.TORCH)        { TorchScreen(navController) }
            composable(SafetyRoutes.LOCAL_BODIES) { EmergencySOSScreen(navController) }
        }
    }
}