package com.example.cityflux.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun CitizenDashboardScreen(
    onReportIssue: () -> Unit,
    onViewParking: () -> Unit,
    onViewAlerts: () -> Unit,   // now used for My Reports
    onProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val colors = MaterialTheme.cityFluxColors

    CleanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.XLarge)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            Spacer(modifier = Modifier.height(Spacing.Large))

            ScreenHeader(
                title = "Citizen Dashboard",
                subtitle = "Manage traffic and parking services"
            )

            Spacer(modifier = Modifier.height(Spacing.XXLarge))

            // Dashboard Action Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.Large)
            ) {
                DashboardActionCard(
                    title = "Report Issue",
                    description = "Report traffic or parking issues",
                    icon = Icons.Outlined.ReportProblem,
                    accentColor = AccentIssues,
                    onClick = onReportIssue
                )

                DashboardActionCard(
                    title = "Parking",
                    description = "Find available parking spots",
                    icon = Icons.Outlined.LocalParking,
                    accentColor = AccentParking,
                    onClick = onViewParking
                )

                DashboardActionCard(
                    title = "My Reports",
                    description = "View your submitted reports",
                    icon = Icons.Outlined.Assignment,
                    accentColor = AccentTraffic,
                    onClick = onViewAlerts
                )

                DashboardActionCard(
                    title = "Profile",
                    description = "View and edit your profile",
                    icon = Icons.Outlined.Person,
                    accentColor = AccentAlerts,
                    onClick = onProfile
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(Spacing.XXLarge))

            // Logout button
            OutlinedButton(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentRed
                ),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AccentRed)
            ) {
                Text(
                    "Logout", 
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Spacer(modifier = Modifier.height(Spacing.Large))
        }
    }
}
