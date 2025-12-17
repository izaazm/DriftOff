package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.myapplication.ui.screens.AnalyticsScreen
import com.example.myapplication.ui.screens.DashboardScreen
import com.example.myapplication.ui.screens.SettingsScreen
import com.example.myapplication.ui.viewmodel.SleepViewModel

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Settings : Screen("settings")
    object Analytics : Screen("analytics")
}

/**
 * Main navigation host
 */
@Composable
@androidx.camera.core.ExperimentalGetImage
fun SleepNavHost(
    navController: NavHostController,
    viewModel: SleepViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = viewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAnalytics = {
                    viewModel.loadAnalytics()
                    navController.navigate(Screen.Analytics.route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Analytics.route) {
            val summary by viewModel.analyticsSummary.collectAsState()
            val sessions by viewModel.recentSessions.collectAsState()
            
            AnalyticsScreen(
                summary = summary,
                recentSessions = sessions,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRefreshData = {
                    viewModel.loadAnalytics()
                }
            )
        }
    }
}
