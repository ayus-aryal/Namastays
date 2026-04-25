package com.example.namastays.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.namastays.screens.ExploreScreen
import com.example.namastays.screens.HomeScreen
import com.example.namastays.screens.SearchResultsScreen
import com.example.namastays.screens.PropertyDetailsScreen

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {

        // Home Screen
        composable("home") {
            HomeScreen(navController = navController)
        }

        // Search Results Screen
        composable(
            route = "search_results/{city}",
            arguments = listOf(
                navArgument("city") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val city = backStackEntry.arguments?.getString("city") ?: ""

            SearchResultsScreen(
                city = city,
                navController = navController
            )
        }

        // Property Details Screen
        composable(
            route = "property_details/{propertyId}",
            arguments = listOf(
                navArgument("propertyId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: ""

            PropertyDetailsScreen(
                propertyId = propertyId,
                navController = navController
            )
        }

        composable("explore"){
            ExploreScreen(navController)
        }
    }
}