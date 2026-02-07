package com.example.cityflux.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020C2B),
                        Color(0xFF031A3D),
                        Color(0xFF020C2B)
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "Citizen Dashboard",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Manage traffic and parking services",
                color = Color(0xFF9FB3FF),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // GRID
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        title = "Report Issue",
                        onClick = onReportIssue,
                        modifier = Modifier.weight(1f)
                    )

                    DashboardCard(
                        title = "Parking",
                        onClick = onViewParking,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DashboardCard(
                        title = "My Reports",   // changed label
                        onClick = onViewAlerts,
                        modifier = Modifier.weight(1f)
                    )

                    DashboardCard(
                        title = "Profile",
                        onClick = onProfile,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Logout button
            Button(
                onClick = {
                    auth.signOut()
                    onLogout()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252)
                )
            ) {
                Text("Logout", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DashboardCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1C3F).copy(alpha = 0.75f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
