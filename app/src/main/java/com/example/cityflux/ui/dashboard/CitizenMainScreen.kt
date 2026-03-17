package com.example.cityflux.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    ALERTS("Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications)
}

/**
 * Single-activity citizen shell with persistent bottom navigation.
 * Hosts 5 bottom tabs: Home, Map, Parking, Report, Alerts.
 * Profile is accessible from the Home screen header icon.
 * Features: Crossfade transitions, animated tab icons, context-aware FAB.
 */
@Composable
fun CitizenMainScreen(
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = CitizenTab.entries.toTypedArray()
    var showProfile by rememberSaveable { mutableStateOf(false) }
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
                    val selected = selectedTab == index && !showProfile

                    // Animated icon scale
                    val iconScale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "iconScale_$index"
                    )

                    NavigationBarItem(
                        selected = selected,
                        onClick = { showProfile = false; selectedTab = index },
                        icon = {
                            val iconContent: @Composable () -> Unit = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .scale(iconScale)
                                )
                            }

                            if (tab == CitizenTab.ALERTS && unreadCount > 0) {
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
                                ) { iconContent() }
                            } else {
                                iconContent()
                            }
                        },
                        label = {
                            AnimatedContent(
                                targetState = selected,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                                },
                                label = "tabLabel_$index"
                            ) { isSelected ->
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = if (isSelected) 11.sp else 10.sp,
                                        lineHeight = 12.sp
                                    ),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
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
        // Crossfade transition between tabs
        Crossfade(
            targetState = if (showProfile) -1 else selectedTab,
            animationSpec = tween(250),
            label = "citizen_tab_crossfade",
            modifier = Modifier.padding(innerPadding)
        ) { tabIndex ->
            when {
                tabIndex == -1 -> com.example.cityflux.ui.profile.ProfileScreen(
                    onLogout = onLogout,
                    onNavigateToMap = { _, _ ->
                        showProfile = false
                        selectedTab = tabs.indexOf(CitizenTab.MAP)
                    }
                )
                else -> when (tabs[tabIndex]) {
                    CitizenTab.HOME -> CitizenHomeContent(
                        onNavigateToTab = { tab -> selectedTab = tabs.indexOf(tab) },
                        onProfileClick = { showProfile = true }
                    )
                    CitizenTab.MAP -> com.example.cityflux.ui.map.MapScreen(
                        onBack = { selectedTab = 0 },
                        onNavigateToReport = { selectedTab = tabs.indexOf(CitizenTab.REPORT) },
                        onNavigateToParking = { selectedTab = tabs.indexOf(CitizenTab.PARKING) }
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
                }
            }
        }
    }
}


