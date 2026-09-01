package com.safelink.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.safelink.app.ui.home.HomeScreen
import com.safelink.app.ui.home.HomeViewModel
import com.safelink.app.ui.navigation.Screen
import com.safelink.app.ui.navigation.bottomNavItems
import com.safelink.app.ui.scenes.ScenesScreen
import com.safelink.app.ui.settings.SettingsScreen
import com.safelink.app.ui.stats.StatsScreen
import com.safelink.app.ui.theme.MintGreen
import com.safelink.app.ui.theme.TealAccent
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.safelink.app.SafeLinkApplication
import com.safelink.app.ui.settings.SettingsViewModel
import com.safelink.app.ui.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(updateViewModel: UpdateViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val homeViewModel: HomeViewModel = viewModel()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()



    val application = LocalContext.current.applicationContext as SafeLinkApplication
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.provideFactory(application.settingsRepository)
    )
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    // Auto-start scanning once settingsState is available
    LaunchedEffect(settingsState.pairingKey) {
        homeViewModel.startAutoScan(settingsState.pairingKey)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val currentTitle = when {
        currentRoute?.startsWith("device/") == true -> "Device Details"
        else -> bottomNavItems
            .firstOrNull { screen -> currentDestination?.hierarchy?.any { it.route == screen.route } == true }
            ?.label ?: "SafeLink"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SafeLinkDrawer(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        if (currentRoute?.startsWith("device/") == true) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Drawer",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    },
                    actions = {
                        when {
                            currentRoute?.startsWith("device/") == true -> {
                                IconButton(onClick = { /* Device Menu */ }) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "More",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            currentRoute == Screen.Home.route -> {
                                IconButton(onClick = { homeViewModel.discoverDevices(settingsState.pairingKey) }) {
                                    if (uiState.isDiscovering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = TealAccent,
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(TealAccent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = "Scan / Add Device",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                IconButton(onClick = { /* Notifications */ }) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealAccent,
                                selectedTextColor = TealAccent,
                                indicatorColor = TealAccent.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(paddingValues),
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(250)
                    )
                },
                exitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(250)
                    )
                },
                popEnterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(250)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(250)
                    )
                }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        uiState = uiState,
                        onDeviceClick = { deviceId ->
                            navController.navigate(Screen.DeviceDetail.createRoute(deviceId))
                        },
                        onRelayClick = { device, relay -> homeViewModel.toggleRelay(device, relay) },
                        onRelayLongClick = { device, relay -> homeViewModel.renameRelay(device, relay.id, relay.name) },
                        onRefresh = { homeViewModel.discoverDevices(settingsState.pairingKey) }
                    )
                }
                composable(Screen.Scenes.route) {
                    ScenesScreen()
                }
                composable(Screen.Stats.route) {
                    StatsScreen(devices = uiState.devices)
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(updateViewModel = updateViewModel)
                }
                composable(Screen.DeviceDetail.route) { backStackEntry ->
                    val deviceId = backStackEntry.arguments?.getString("deviceId")
                    val device = uiState.devices.find { it.ip == deviceId }
                    if (device != null) {
                        com.safelink.app.ui.device.DeviceDetailScreen(
                            device = device,
                            onRelayToggle = { relay -> homeViewModel.toggleRelay(device, relay) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SafeLinkDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    ModalDrawerSheet(
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(300.dp)
    ) {
        // Gradient header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(listOf(TealAccent, MintGreen))
                )
                .padding(24.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.safelink.app.R.drawable.ic_app_logo),
                        contentDescription = "SafeLink Logo",
                        modifier = Modifier.size(52.dp).clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SafeLink",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = "Smart Home Control",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Primary nav items
        val primaryItems = listOf(
            Triple(Screen.Home.route, Icons.Default.Home, "Home"),
            Triple(Screen.Scenes.route, Icons.Default.AutoAwesome, "Scenes"),
            Triple(Screen.Stats.route, Icons.Default.BarChart, "Statistics"),
            Triple(Screen.Settings.route, Icons.Default.Settings, "Settings"),
        )

        primaryItems.forEach { (route, icon, label) ->
            val isSelected = currentRoute == route
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                selected = isSelected,
                onClick = { onNavigate(route) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = TealAccent.copy(alpha = 0.15f),
                    selectedIconColor = TealAccent,
                    selectedTextColor = TealAccent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )

        // Secondary items
        DrawerSecondaryItem(
            icon = Icons.AutoMirrored.Filled.Help,
            label = "Help & Support",
            onClick = {}
        )
        DrawerSecondaryItem(
            icon = Icons.Default.Info,
            label = "About SafeLink",
            onClick = {}
        )
        DrawerSecondaryItem(
            icon = Icons.Default.BugReport,
            label = "Send Feedback",
            onClick = {}
        )

        Spacer(modifier = Modifier.weight(1f))

        // Footer
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
        Text(
            text = "SafeLink v1.0.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(24.dp)
                .align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun DrawerSecondaryItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
