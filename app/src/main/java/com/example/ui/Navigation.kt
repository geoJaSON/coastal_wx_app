package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StormDetailScreen
import com.example.ui.screens.TropicsScreen

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Screen("home", "Forecast", Icons.Filled.Home)
    data object Tropics : Screen("tropics", "Tropics", Icons.Filled.Cyclone)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun MainAppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val weatherState by viewModel.weatherState.collectAsState()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Dynamic temperature styling gradients shift based on conditions
    val themeGradient = remember(weatherState, isDark) {
        val colorStart = when (val state = weatherState) {
            is WeatherUIState.Success -> {
                when (state.weatherCode) {
                    1000, 1100 -> if (isDark) Color(0xFF16130F) else Color(0xFFFEF9F3) // Sunny warm alabaster
                    4000, 4001, 4200, 4201 -> if (isDark) Color(0xFF0F141C) else Color(0xFFF0F4F8) // Rain overcast blue (Sleek original)
                    8000 -> if (isDark) Color(0xFF11111B) else Color(0xFFF3F3FA) // Storm slate purple
                    else -> if (isDark) Color(0xFF0E1219) else Color(0xFFF1F5F9) // Overcast cozy gray
                }
            }
            else -> if (isDark) Color(0xFF0F141C) else Color(0xFFF0F4F8)
        }
        val colorEnd = if (isDark) Color(0xFF06090E) else Color(0xFFFFFFFF)
        Brush.verticalGradient(listOf(colorStart, colorEnd))
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            if (currentRoute in listOf(Screen.Home.route, Screen.Tropics.route, Screen.Settings.route)) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp
                    ) {
                        val screens = listOf(Screen.Home, Screen.Tropics, Screen.Settings)
                        screens.forEach { screen ->
                            NavigationBarItem(
                                selected = currentRoute == screen.route,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(themeGradient)
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.Tropics.route) {
                    TropicsScreen(
                        viewModel = viewModel,
                        onNavigateToStormDetail = { stormId -> navController.navigate("storm_detail/$stormId") },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(viewModel = viewModel)
                }
                composable("storm_detail/{stormId}") { backStackEntry ->
                    val stormId = backStackEntry.arguments?.getString("stormId") ?: ""
                    StormDetailScreen(
                        stormId = stormId,
                        viewModel = viewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
