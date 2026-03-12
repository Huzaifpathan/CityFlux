package com.example.cityflux.ui.police

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.ui.notifications.NotificationsViewModel
import com.example.cityflux.ui.theme.*

// ─── Bottom nav tab definitions for Police ─────────────────────
enum class PoliceTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CONGESTION("Congestion", Icons.Filled.Map, Icons.Outlined.Map),
    REPORTS("Reports", Icons.Filled.Assignment, Icons.Outlined.Assignment),
    PARKING("Parking", Icons.Filled.LocalParking, Icons.Outlined.LocalParking),
    ACTIONS("Actions", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Single-activity police shell with persistent bottom navigation.
 * Hosts 6 tabs: Home, Congestion Map, Reports, Parking Control, Action Status, Profile.
 * Each tab's state is preserved across switches.
 */
@Composable
fun PoliceMainScreen(
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = PoliceTab.entries.toTypedArray()
    val colors = MaterialTheme.cityFluxColors
    val notificationsVm: NotificationsViewModel = viewModel()
    val unreadCount by notificationsVm.unreadCount.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.bottomNavBackground,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            if (tab == PoliceTab.REPORTS && unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AccentRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(if (selected) 24.dp else 22.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(if (selected) 24.dp else 22.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 12.sp
                                ),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryBlue,
                            selectedTextColor = PrimaryBlue,
                            unselectedIconColor = colors.textTertiary,
                            unselectedTextColor = colors.textTertiary,
                            indicatorColor = PrimaryBlue.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        // Crossfade keeps each tab alive and animates between them
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(250),
            label = "police_tab_crossfade",
            modifier = Modifier.padding(innerPadding)
        ) { tabIndex ->
            when (tabs[tabIndex]) {
                PoliceTab.HOME -> PoliceHomeScreen(
                    onNavigateToTab = { tab -> selectedTab = tabs.indexOf(tab) }
                )
                PoliceTab.CONGESTION -> CongestionMapScreen(
                    onBack = { selectedTab = 0 }
                )
                PoliceTab.REPORTS -> ReportsScreen(
                    onBack = { selectedTab = 0 }
                )
                PoliceTab.PARKING -> ParkingControlScreen(
                    onBack = { selectedTab = 0 }
                )
                PoliceTab.ACTIONS -> ActionStatusScreen(
                    onBack = { selectedTab = 0 }
                )
                PoliceTab.PROFILE -> com.example.cityflux.ui.profile.ProfileScreen(
                    onLogout = onLogout,
                    onNavigateToMap = { _, _ -> selectedTab = tabs.indexOf(PoliceTab.CONGESTION) }
                )
            }
        }
    }
}
