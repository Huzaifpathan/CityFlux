package com.example.cityflux.ui.parking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

@Composable
fun ParkingScreen(
    onBack: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    // ── Analytics: log once per screen open ──
    LaunchedEffect(Unit) {
        try {
            Firebase.analytics.logEvent("parking_viewed", null)
        } catch (_: Exception) { /* analytics must not crash */ }
    }

    // ── Live parking data from Realtime DB ──
    val parkingMap by RealtimeDbService.observeParkingLive()
        .collectAsState(initial = emptyMap())

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Parking",
                showBack = true,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (parkingMap.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.LocalParking,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    Text(
                        "No parking data available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                    Text(
                        "Parking spots will appear here in real time",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.XLarge),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                contentPadding = PaddingValues(vertical = Spacing.Large)
            ) {
                item {
                    ScreenHeader(
                        title = "Available Parking",
                        subtitle = "Real-time slot availability"
                    )
                    Spacer(modifier = Modifier.height(Spacing.Large))
                }

                items(parkingMap.entries.toList()) { (id, live) ->
                    ParkingCard(parkingId = id, live = live)
                }
            }
        }
    }
}

@Composable
private fun ParkingCard(parkingId: String, live: ParkingLive) {
    val colors = MaterialTheme.cityFluxColors
    val available = live.availableSlots
    val statusColor = when {
        available == 0 -> AccentRed
        available <= 5 -> AccentAlerts
        else -> AccentParking
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Outlined.LocalParking,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parkingId.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (available == 0) "Full — no slots" else "$available slots available",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "$available",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}
