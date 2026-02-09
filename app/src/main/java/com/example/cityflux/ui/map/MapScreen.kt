package com.example.cityflux.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

@Composable
fun MapScreen(
    onBack: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    // ── Analytics: log once per screen open ──
    LaunchedEffect(Unit) {
        try {
            Firebase.analytics.logEvent("map_opened", null)
        } catch (_: Exception) { /* analytics must not crash */ }
    }

    // ── Live traffic data from Realtime DB ──
    val trafficMap by RealtimeDbService.observeTraffic()
        .collectAsState(initial = emptyMap())

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Traffic Map",
                showBack = true,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (trafficMap.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = colors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    Text(
                        "No traffic data yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                    Text(
                        "Congestion levels will appear here in real time",
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
                        title = "Traffic Overview",
                        subtitle = "Real-time congestion levels across roads"
                    )
                    Spacer(modifier = Modifier.height(Spacing.Large))
                }

                items(trafficMap.entries.toList()) { (roadId, status) ->
                    TrafficCard(roadId = roadId, status = status)
                }
            }
        }
    }
}

@Composable
private fun TrafficCard(roadId: String, status: TrafficStatus) {
    val colors = MaterialTheme.cityFluxColors

    val level = status.congestionLevel.uppercase()
    val statusColor = when (level) {
        "HIGH" -> AccentRed
        "MEDIUM" -> AccentAlerts
        else -> AccentParking
    }
    val label = when (level) {
        "HIGH" -> "Heavy"
        "MEDIUM" -> "Moderate"
        else -> "Clear"
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
                        imageVector = Icons.Outlined.Traffic,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = roadId.replace("_", ", "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Congestion: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}
