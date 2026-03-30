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
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * My Bookings Screen - Enhanced with 3 tabs and pull-to-refresh
 * Supports Active, Upcoming, and Past bookings with real-time updates
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
    
    // Pull to refresh state
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            isRefreshing = true
            viewModel.refreshBookings()
            delay(1000) // Simulate refresh time
            isRefreshing = false
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }
    
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
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)
                        ) {
                            Text("Upcoming")
                            if (uiState.upcomingBookings.isNotEmpty()) {
                                Badge {
                                    Text("${uiState.upcomingBookings.size}")
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") }
                )
            }
            
            // Content with pull-to-refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection)
            ) {
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
                    1 -> UpcomingBookingsTab(
                        bookings = uiState.upcomingBookings,
                        onBookingClick = onBookingClick,
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onModifyClick = { booking ->
                            // TODO: Implement modify booking
                        },
                        colors = colors
                    )
                    2 -> HistoryTab(
                        bookings = uiState.pastBookings,
                        onBookingClick = onBookingClick,
                        colors = colors
                    )
                }
                
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                )
            }
        }
    }
}

/**
 * MyBookingsContent - Reusable component for embedding My Bookings within ParkingScreen tab
 * Contains the 3 internal tabs (Active, Upcoming, History) without the Scaffold/TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsContent(
    onBookingClick: (ParkingBooking) -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    var selectedBookingsTab by remember { mutableIntStateOf(0) }
    
    // Pull to refresh state
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            isRefreshing = true
            viewModel.refreshBookings()
            delay(1000)
            isRefreshing = false
        }
    }
    
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullToRefreshState.endRefresh()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Sub-Tab Row for Active/Upcoming/History
        TabRow(
            selectedTabIndex = selectedBookingsTab,
            containerColor = colors.cardBackground,
            contentColor = PrimaryBlue
        ) {
            Tab(
                selected = selectedBookingsTab == 0,
                onClick = { selectedBookingsTab = 0 },
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
                selected = selectedBookingsTab == 1,
                onClick = { selectedBookingsTab = 1 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall)
                    ) {
                        Text("Upcoming")
                        if (uiState.upcomingBookings.isNotEmpty()) {
                            Badge {
                                Text("${uiState.upcomingBookings.size}")
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedBookingsTab == 2,
                onClick = { selectedBookingsTab = 2 },
                text = { Text("History") }
            )
        }
        
        // Content with pull-to-refresh
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when (selectedBookingsTab) {
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
                1 -> UpcomingBookingsTab(
                    bookings = uiState.upcomingBookings,
                    onBookingClick = onBookingClick,
                    onCancelClick = { booking ->
                        viewModel.cancelBooking(booking.id, "User cancelled")
                    },
                    onModifyClick = { booking ->
                        // TODO: Implement modify booking
                    },
                    colors = colors
                )
                2 -> HistoryTab(
                    bookings = uiState.pastBookings,
                    onBookingClick = onBookingClick,
                    colors = colors
                )
            }
            
            if (pullToRefreshState.isRefreshing || pullToRefreshState.progress > 0f) {
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
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
private fun UpcomingBookingsTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onCancelClick: (ParkingBooking) -> Unit,
    onModifyClick: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyState(
            icon = Icons.Default.Schedule,
            title = "No Upcoming Bookings",
            message = "You don't have any scheduled parking bookings",
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            items(bookings, key = { it.id }) { booking ->
                UpcomingBookingCard(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    onCancel = { onCancelClick(booking) },
                    onModify = { onModifyClick(booking) },
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
            
            // Real-time countdown timer
            RealTimeCountdown(
                booking = booking,
                colors = colors
            )
            
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
private fun UpcomingBookingCard(
    booking: ParkingBooking,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onModify: () -> Unit,
    colors: CityFluxColors
) {
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
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = PrimaryBlue,
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
            
            // Time until booking starts
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Small),
                color = PrimaryBlue.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        text = "Starts in 2h 30m", // TODO: Calculate actual time
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(Modifier.height(Spacing.Small))
            
            // Details
            InfoRow(
                icon = Icons.Default.CalendarToday,
                label = "Start Time",
                value = booking.bookingStartTime?.toDate()?.let {
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
                    onClick = onModify,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Modify")
                }
                
                Button(
                    onClick = { /* Add to calendar */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Event, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Calendar")
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
        BookingStatus.ENDING_SOON -> Triple(
            AccentAlerts.copy(alpha = 0.1f),
            AccentAlerts,
            "Ending Soon"
        )
        BookingStatus.REFUNDED -> Triple(
            colors.textSecondary.copy(alpha = 0.1f),
            colors.textSecondary,
            "Refunded"
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

/**
 * Real-time countdown timer that updates every second
 */
@Composable
private fun RealTimeCountdown(
    booking: ParkingBooking,
    colors: CityFluxColors
) {
    // State for real-time updates
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Update every second
    LaunchedEffect(booking.id) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000) // Update every second
        }
    }
    
    // Calculate remaining time
    val endTime = booking.bookingEndTime?.toDate()?.time ?: 0
    val remainingMillis = endTime - currentTime
    val remainingMinutes = (remainingMillis / (1000 * 60)).coerceAtLeast(0)
    val isExpiringSoon = remainingMinutes in 1..15
    val isExpired = remainingMillis <= 0
    
    // Format time display
    val timeText = when {
        isExpired -> "Expired"
        remainingMinutes < 60 -> "${remainingMinutes}m remaining"
        else -> {
            val hours = remainingMinutes / 60
            val mins = remainingMinutes % 60
            "${hours}h ${mins}m remaining"
        }
    }
    
    // Color based on time remaining
    val (backgroundColor, textColor, iconColor) = when {
        isExpired -> Triple(
            AccentRed.copy(alpha = 0.1f),
            AccentRed,
            AccentRed
        )
        isExpiringSoon -> Triple(
            AccentAlerts.copy(alpha = 0.1f),
            AccentAlerts,
            AccentAlerts
        )
        else -> Triple(
            colors.surfaceVariant,
            colors.textPrimary,
            PrimaryBlue
        )
    }
    
    // Animated surface for visual feedback
    AnimatedContent(
        targetState = isExpiringSoon,
        transitionSpec = {
            fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
        },
        label = "countdown_animation"
    ) { expiring ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expiring) {
                        Modifier.animateContentSize()
                    } else Modifier
                ),
            shape = RoundedCornerShape(CornerRadius.Small),
            color = backgroundColor,
            border = if (isExpiringSoon) BorderStroke(1.dp, AccentAlerts) else null
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpired) Icons.Default.ErrorOutline else Icons.Default.Timer,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = if (isExpiringSoon) FontWeight.Bold else FontWeight.Medium
                )
                
                if (isExpiringSoon && !isExpired) {
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AccentAlerts,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
