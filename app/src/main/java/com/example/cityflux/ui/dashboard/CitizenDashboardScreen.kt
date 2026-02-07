package com.example.cityflux.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitizenDashboardScreen(
    onParkingClick: () -> Unit,
    onTrafficClick: () -> Unit,
    onReportClick: () -> Unit,
    onUpdatesClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "CityFlux",
                showNotification = true,
                showProfile = true
            )
        },
        containerColor = SurfaceWhite
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400))
            ) {
                Column {
                    Text(
                        text = "Welcome to CityFlux",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Citizen Dashboard",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Smart Parking - Green accent
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 100)) + 
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(400, delayMillis = 100)
                            )
                ) {
                    DashboardActionCard(
                        title = "Smart Parking",
                        description = "Check parking availability",
                        icon = Icons.Filled.DirectionsCar,
                        onClick = onParkingClick,
                        accentColor = AccentParking
                    )
                }

                // Traffic Status - Blue accent
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 200)) + 
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(400, delayMillis = 200)
                            )
                ) {
                    DashboardActionCard(
                        title = "Traffic Status",
                        description = "View traffic conditions",
                        icon = Icons.Filled.Traffic,
                        onClick = onTrafficClick,
                        accentColor = AccentTraffic
                    )
                }

                // Report an Issue - Red accent
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 300)) + 
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(400, delayMillis = 300)
                            )
                ) {
                    DashboardActionCard(
                        title = "Report an Issue",
                        description = "Report city problems",
                        icon = Icons.Filled.Report,
                        onClick = onReportClick,
                        accentColor = AccentIssues
                    )
                }

                // City Updates - Orange accent
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 400)) + 
                            slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(400, delayMillis = 400)
                            )
                ) {
                    DashboardActionCard(
                        title = "City Updates",
                        description = "View trends & announcements",
                        icon = Icons.Filled.Notifications,
                        onClick = onUpdatesClick,
                        accentColor = AccentAlerts
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
