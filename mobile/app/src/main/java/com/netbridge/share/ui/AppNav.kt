package com.netbridge.share.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netbridge.share.R
import com.netbridge.share.ui.screens.GuideScreen
import com.netbridge.share.ui.screens.HomeScreen
import com.netbridge.share.ui.screens.SettingsScreen
import com.netbridge.share.ui.screens.ShareScreen
import com.netbridge.share.ui.theme.Bg1
import com.netbridge.share.ui.theme.Bg2
import com.netbridge.share.ui.theme.Brand
import com.netbridge.share.ui.theme.TextSecondary

private data class Tab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconOut: ImageVector
)

private val tabs = listOf(
    Tab("home", R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Tab("share", R.string.tab_share, Icons.Filled.Share, Icons.Outlined.Share),
    Tab("guide", R.string.tab_guide, Icons.Filled.Info, Icons.Outlined.Info),
    Tab("settings", R.string.tab_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
)

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = Bg1,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { tab ->
                    val label = stringResource(tab.labelRes)
                    val selected = current?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.icon else tab.iconOut,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                textAlign = TextAlign.Center,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brand,
                            selectedTextColor = Brand,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Bg2
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") { HomeScreen() }
                composable("share") { ShareScreen() }
                composable("guide") { GuideScreen() }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
