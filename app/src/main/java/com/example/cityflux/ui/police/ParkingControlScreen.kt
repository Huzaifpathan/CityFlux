package com.example.cityflux.ui.police

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*

// ═══════════════════════════════════════════════════════════════════
// Parking Control Screen — Manage and monitor parking zones
// Placeholder — will be fully implemented with enforcement features
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingControlScreen(
    onBack: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Parking Control",
                showBack = false,
                showNotification = false,
                showProfile = false,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalParking,
                    contentDescription = null,
                    tint = AccentParking.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "Parking Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Manage and enforce parking zones",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
    }
}
