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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
    var selectedTab by rememberSaveable { mutableStateOf(PoliceTab.HOME) }
    // Report ID to pass to ActionStatusScreen when navigating from Reports
    var pendingActionReportId by remember { mutableStateOf<String?>(null) }

    // ── Police working-area setup check ──
    var showLocationSetup by remember { mutableStateOf(false) }
    var locationCheckDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val areaName = doc.getString("workingAreaName") ?: ""
                val lat = doc.getDouble("workingLatitude") ?: 0.0
                val lon = doc.getDouble("workingLongitude") ?: 0.0
                showLocationSetup = areaName.isBlank() || (lat == 0.0 && lon == 0.0)
                locationCheckDone = true
            }
            .addOnFailureListener {
                locationCheckDone = true
            }
    }

    // Show mandatory location setup dialog
    if (showLocationSetup && locationCheckDone) {
        PoliceLocationSetupDialog(
            onSetupComplete = { showLocationSetup = false }
        )
    }

    // Bottom navigation should not expose the profile tab,
    // which is accessed from the header on the Home screen.
    val bottomTabs = listOf(
        PoliceTab.HOME,
        PoliceTab.CONGESTION,
        PoliceTab.REPORTS,
        PoliceTab.PARKING,
        PoliceTab.ACTIONS
    )
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
                bottomTabs.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
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
        ) { tab ->
            when (tab) {
                PoliceTab.HOME -> PoliceHomeScreen(
                    onNavigateToTab = { targetTab -> selectedTab = targetTab }
                )
                PoliceTab.CONGESTION -> CongestionMapScreen(
                    onBack = { selectedTab = PoliceTab.HOME }
                )
                PoliceTab.REPORTS -> ReportsScreen(
                    onBack = { selectedTab = PoliceTab.HOME },
                    onTakeAction = { report ->
                        // Assign this report to the officer and set In Progress
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            FirebaseFirestore.getInstance()
                                .collection("reports").document(report.id)
                                .update(
                                    mapOf(
                                        "assignedTo" to uid,
                                        "status" to "In Progress"
                                    )
                                )
                        }
                        pendingActionReportId = report.id
                        selectedTab = PoliceTab.ACTIONS
                    }
                )
                PoliceTab.PARKING -> ParkingControlScreen(
                    onBack = { selectedTab = PoliceTab.HOME }
                )
                PoliceTab.ACTIONS -> ActionStatusScreen(
                    onBack = { selectedTab = PoliceTab.HOME },
                    initialReportId = pendingActionReportId,
                    onReportHandled = { pendingActionReportId = null }
                )
                PoliceTab.PROFILE -> PoliceProfileScreen(
                    onLogout = onLogout
                )
            }
        }
    }
}
