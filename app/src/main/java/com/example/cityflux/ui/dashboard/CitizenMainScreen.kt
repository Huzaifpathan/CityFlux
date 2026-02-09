package com.example.cityflux.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.ui.notifications.NotificationsViewModel
import com.example.cityflux.ui.theme.*

// ─── Bottom nav tab definitions ────────────────────────────────
enum class CitizenTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    MAP("Map", Icons.Filled.Map, Icons.Outlined.Map),
    PARKING("Parking", Icons.Filled.LocalParking, Icons.Outlined.LocalParking),
    REPORT("Report", Icons.Filled.ReportProblem, Icons.Outlined.ReportProblem),
    ALERTS("Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

/**
 * Single-activity citizen shell with persistent bottom navigation.
 * Hosts 6 tabs: Home, Map, Parking, Report, Alerts, Profile.
 * Each tab's state is preserved across switches.
 */
@Composable
fun CitizenMainScreen(
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = CitizenTab.entries.toTypedArray()
    val colors = MaterialTheme.cityFluxColors
    val notificationsVm: NotificationsViewModel = viewModel()
    val unreadCount by notificationsVm.unreadCount.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = colors.bottomNavBackground,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            if (tab == CitizenTab.ALERTS && unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = AccentRed,
                                            contentColor = Color.White
                                        ) {
                                            Text(
                                                if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(if (selected) 26.dp else 24.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.size(if (selected) 26.dp else 24.dp)
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
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
            label = "tab_crossfade",
            modifier = Modifier.padding(innerPadding)
        ) { tabIndex ->
            when (tabs[tabIndex]) {
                CitizenTab.HOME -> CitizenHomeContent(
                    onNavigateToTab = { tab -> selectedTab = tabs.indexOf(tab) }
                )
                CitizenTab.MAP -> com.example.cityflux.ui.map.MapScreen(
                    onBack = { selectedTab = 0 }
                )
                CitizenTab.PARKING -> com.example.cityflux.ui.parking.ParkingScreen(
                    onBack = { selectedTab = 0 }
                )
                CitizenTab.REPORT -> com.example.cityflux.ui.report.ReportIssueScreen(
                    onReportSubmitted = { selectedTab = 0 }
                )
                CitizenTab.ALERTS -> com.example.cityflux.ui.notifications.NotificationsScreen(
                    onNavigateToMap = { _, _ -> selectedTab = tabs.indexOf(CitizenTab.MAP) },
                    vm = notificationsVm
                )
                CitizenTab.PROFILE -> com.example.cityflux.ui.profile.ProfileScreen(
                    onLogout = onLogout,
                    onNavigateToMap = { _, _ -> selectedTab = tabs.indexOf(CitizenTab.MAP) }
                )
            }
        }
    }
}
