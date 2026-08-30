package com.safelink.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Scenes : Screen("scenes", "Scenes", Icons.Default.AutoAwesome)
    data object Stats : Screen("stats", "Stats", Icons.Default.BarChart)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    data object DeviceDetail : Screen("device/{deviceId}", "Device", Icons.Default.DevicesOther) {
        fun createRoute(deviceId: String) = "device/$deviceId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Scenes,
    Screen.Stats,
    Screen.Settings
)
