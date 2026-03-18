package com.example.cityflux.ui.parking

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * My Bookings Screen
 * Phase 4: View active and past parking bookings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsScreen(
    onNavigateBack: () -> Unit,
    onBookingClick: (ParkingBooking) -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Bookings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.cardBackground,
                    titleContentColor = colors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colors.cardBackground,
                contentColor = PrimaryBlue
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)
                        ) {
                            Text("Active")
                            if (uiState.activeBookings.isNotEmpty()) {
                                Badge {
                                    Text("${uiState.activeBookings.size}")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("History") }
                )
            }
            
            // Content
            when (selectedTab) {
                0 -> ActiveBookingsTab(
                    bookings = uiState.activeBookings,
                    onBookingClick = onBookingClick,
                    onCancelClick = { booking ->
                        viewModel.cancelBooking(booking.id, "User cancelled")
                    },
                    onExtendClick = { booking ->
                        viewModel.extendBooking(booking.id, 1)
                    },
                    colors = colors
                )
                1 -> HistoryTab(
                    bookings = uiState.pastBookings,
                    onBookingClick = onBookingClick,
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ActiveBookingsTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onCancelClick: (ParkingBooking) -> Unit,
    onExtendClick: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.EventBusy,
            title = "No Active Bookings",
            message = "You don't have any active parking bookings",
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            items(bookings, key = { it.id }) { booking ->
                ActiveBookingCard(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    onCancel = { onCancelClick(booking) },
                    onExtend = { onExtendClick(booking) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "No Booking History",
            message = "Your completed bookings will appear here",
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            items(bookings, key = { it.id }) { booking ->
                BookingHistoryCard(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ActiveBookingCard(
    booking: ParkingBooking,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onExtend: () -> Unit,
    colors: CityFluxColors
) {
    var showActions by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        color = colors.cardBackground,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalParking,
                        contentDescription = null,
                        tint = AccentParking,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Column {
                        Text(
                            text = booking.parkingSpotName,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = booking.vehicleNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                
                BookingStatusBadge(status = booking.status, colors = colors)
            }
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Time remaining
            val remainingMinutes = booking.getRemainingTimeMinutes()
            val isExpiringSoon = booking.isExpiringSoon()
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Small),
                color = if (isExpiringSoon) 
                    AccentAlerts.copy(alpha = 0.1f) 
                else 
                    colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (isExpiringSoon) AccentAlerts else PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        text = "${remainingMinutes / 60}h ${remainingMinutes % 60}m remaining",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isExpiringSoon) AccentAlerts else colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(Modifier.height(Spacing.Small))
            
            // Details
            InfoRow(
                icon = Icons.Default.CalendarToday,
                label = "Booked Until",
                value = booking.bookingEndTime?.toDate()?.let {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                } ?: "N/A",
                colors = colors
            )
            
            InfoRow(
                icon = Icons.Default.AccountBalanceWallet,
                label = "Amount Paid",
                value = "₹${booking.amount.toInt()}",
                colors = colors
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                OutlinedButton(
                    onClick = onExtend,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Extend")
                }
                
                Button(
                    onClick = { /* Show QR */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Show QR")
                }
                
                if (booking.status.canBeCancelled()) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentRed
                        ),
                        border = BorderStroke(1.dp, AccentRed)
                    ) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingHistoryCard(
    booking: ParkingBooking,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = booking.parkingSpotName,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = booking.vehicleNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                
                BookingStatusBadge(status = booking.status, colors = colors)
            }
            
            Spacer(Modifier.height(Spacing.Small))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = booking.bookingCreatedAt?.toDate()?.let {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                    } ?: "N/A",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Text(
                    text = "₹${booking.amount.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BookingStatusBadge(
    status: BookingStatus,
    colors: CityFluxColors
) {
    val (backgroundColor, textColor, text) = when (status) {
        BookingStatus.PENDING -> Triple(
            AccentAlerts.copy(alpha = 0.1f),
            AccentAlerts,
            "Pending"
        )
        BookingStatus.CONFIRMED -> Triple(
            PrimaryBlue.copy(alpha = 0.1f),
            PrimaryBlue,
            "Confirmed"
        )
        BookingStatus.ACTIVE -> Triple(
            AccentGreen.copy(alpha = 0.1f),
            AccentGreen,
            "Active"
        )
        BookingStatus.COMPLETED -> Triple(
            colors.textSecondary.copy(alpha = 0.1f),
            colors.textSecondary,
            "Completed"
        )
        BookingStatus.CANCELLED -> Triple(
            AccentRed.copy(alpha = 0.1f),
            AccentRed,
            "Cancelled"
        )
        BookingStatus.EXPIRED -> Triple(
            AccentRed.copy(alpha = 0.1f),
            AccentRed,
            "Expired"
        )
        BookingStatus.NO_SHOW -> Triple(
            AccentRed.copy(alpha = 0.1f),
            AccentRed,
            "No Show"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(CornerRadius.Small),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(Spacing.Small))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    colors: CityFluxColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
    }
}
