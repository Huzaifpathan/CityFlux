package com.example.cityflux.ui.parking

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.data.BookingRepository
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingService
import com.example.cityflux.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    
    // Dialog states
    var showExtendDialog by remember { mutableStateOf(false) }
    var showBookingDetailsDialog by remember { mutableStateOf(false) }
    var selectedBookingForAction by remember { mutableStateOf<ParkingBooking?>(null) }
    
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
            // Tab Row - fixed styling
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
                                Badge(
                                    containerColor = PrimaryBlue,
                                    contentColor = Color.White
                                ) {
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
                                Badge(
                                    containerColor = AccentAlerts,
                                    contentColor = Color.White
                                ) {
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
                        onBookingClick = { booking ->
                            selectedBookingForAction = booking
                            showBookingDetailsDialog = true
                        },
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onNavigateClick = { booking ->
                            // Legacy component - navigation not implemented for simplicity
                            // Use MyBookingsContentEnhanced instead for full navigation support
                        },
                        colors = colors
                    )
                    1 -> UpcomingBookingsTab(
                        bookings = uiState.upcomingBookings,
                        onBookingClick = { booking ->
                            selectedBookingForAction = booking
                            showBookingDetailsDialog = true
                        },
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
                        onBookingClick = { booking ->
                            selectedBookingForAction = booking
                            showBookingDetailsDialog = true
                        },
                        colors = colors
                    )
                }
                
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState,
                )
            }
        }
        
        // Booking Details Dialog
        if (showBookingDetailsDialog && selectedBookingForAction != null) {
            BookingDetailsDialog(
                booking = selectedBookingForAction!!,
                onDismiss = {
                    showBookingDetailsDialog = false
                    selectedBookingForAction = null
                },
                onNavigate = {
                    // Legacy component - navigation not implemented
                    // Use MyBookingsContentEnhanced instead for full navigation support
                    showBookingDetailsDialog = false
                    selectedBookingForAction = null
                },
                colors = colors
            )
        }
        
        // Extend Booking Dialog
        if (showExtendDialog && selectedBookingForAction != null) {
            ExtendBookingDialog(
                booking = selectedBookingForAction!!,
                onDismiss = { 
                    showExtendDialog = false
                    selectedBookingForAction = null
                },
                onConfirm = { hours ->
                    viewModel.extendBooking(selectedBookingForAction!!.id, hours)
                    showExtendDialog = false
                    selectedBookingForAction = null
                },
                colors = colors
            )
        }
    }
}

/**
 * Enhanced My Bookings Content - Embeddable version without Scaffold
 * Used within ParkingScreen's tab layout
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsContentEnhanced(
    onBookingClick: (ParkingBooking) -> Unit,
    onNavigateToMap: (parkingSpotId: String) -> Unit = {},
    viewModel: BookingViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Dialog states
    var showBookingDetailsDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    
    // Observe booking timestamp from repository for real-time updates
    val repository = remember { BookingRepository.getInstance() }
    val lastBookingTimestamp by repository.lastBookingTimestamp.collectAsState()
    
    // Refresh bookings when timestamp changes or composable becomes visible
    LaunchedEffect(lastBookingTimestamp) {
        viewModel.refreshBookings()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.cardBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tab Row - fixed styling
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
                                Badge(
                                    containerColor = PrimaryBlue,
                                    contentColor = Color.White
                                ) {
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
                                Badge(
                                    containerColor = AccentAlerts,
                                    contentColor = Color.White
                                ) {
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
            
            // Content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(colors.cardBackground)
            ) {
                when (selectedTab) {
                    0 -> ActiveBookingsTab(
                        bookings = uiState.activeBookings,
                        onBookingClick = { booking -> showBookingDetailsDialog = booking },
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onNavigateClick = { booking ->
                            onNavigateToMap(booking.parkingSpotId)
                        },
                        onRefresh = { viewModel.refreshBookings() },
                        colors = colors
                    )
                    1 -> UpcomingBookingsTab(
                        bookings = uiState.upcomingBookings,
                        onBookingClick = { booking -> showBookingDetailsDialog = booking },
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onModifyClick = { booking ->
                            // TODO: Implement modify booking
                        },
                        onRefresh = { viewModel.refreshBookings() },
                        colors = colors
                    )
                    2 -> HistoryTab(
                        bookings = uiState.pastBookings,
                        onBookingClick = { booking -> showBookingDetailsDialog = booking },
                        onRefresh = { viewModel.refreshBookings() },
                        colors = colors
                    )
                }
            }
        }
        
        // Booking Details Dialog
        showBookingDetailsDialog?.let { booking ->
            BookingDetailsDialog(
                booking = booking,
                onDismiss = { showBookingDetailsDialog = null },
                onNavigate = { 
                    onNavigateToMap(booking.parkingSpotId)
                    showBookingDetailsDialog = null
                },
                colors = colors
            )
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
    
    // Dialog states for this component
    var showExtendDialog by remember { mutableStateOf(false) }
    var selectedBookingForAction by remember { mutableStateOf<ParkingBooking?>(null) }
    
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
                .background(colors.cardBackground)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            when (selectedBookingsTab) {
                0 -> ActiveBookingsTab(
                    bookings = uiState.activeBookings,
                    onBookingClick = onBookingClick,
                    onCancelClick = { booking ->
                        viewModel.cancelBooking(booking.id, "User cancelled")
                    },
                    onNavigateClick = { booking ->
                        // Legacy MyBookingsContent - navigation not implemented
                        // Use MyBookingsContentEnhanced for full navigation support
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
    
    // Extend Booking Dialog
    if (showExtendDialog && selectedBookingForAction != null) {
        ExtendBookingDialog(
            booking = selectedBookingForAction!!,
            onDismiss = {
                showExtendDialog = false
                selectedBookingForAction = null
            },
            onConfirm = { hours: Int ->
                viewModel.extendBooking(selectedBookingForAction!!.id, hours)
                showExtendDialog = false
                selectedBookingForAction = null
            },
            colors = colors
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveBookingsTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onCancelClick: (ParkingBooking) -> Unit,
    onNavigateClick: (ParkingBooking) -> Unit,
    onRefresh: () -> Unit = {},
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
                    onNavigate = { onNavigateClick(booking) },
                    colors = colors
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpcomingBookingsTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onCancelClick: (ParkingBooking) -> Unit,
    onModifyClick: (ParkingBooking) -> Unit,
    onRefresh: () -> Unit = {},
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTab(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onRefresh: () -> Unit = {},
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
    onNavigate: () -> Unit,
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
            
            // Actions - Navigate and Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                // Navigate Button (Blue)
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
                
                // Cancel Button
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

// ═══════════════════════════════════════════════════════
// Extend Booking Dialog
// ═══════════════════════════════════════════════════════

@Composable
private fun ExtendBookingDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    colors: CityFluxColors
) {
    var selectedHours by remember { mutableIntStateOf(1) }
    val hourOptions = listOf(1, 2, 3, 4, 5, 6)
    
    // Calculate price for extension - recalculates when selectedHours changes
    val extensionPrice by remember {
        derivedStateOf {
            val pricing = PricingService.calculateParkingFee(
                vehicleType = booking.vehicleType,
                durationHours = selectedHours
            )
            pricing.totalAmount
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(Spacing.Medium),
            shape = RoundedCornerShape(CornerRadius.Large),
            color = colors.cardBackground,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Large)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Extend Booking",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // Booking info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = colors.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalParking,
                            contentDescription = null,
                            tint = AccentParking,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(Spacing.Medium))
                        Column {
                            Text(
                                text = booking.parkingSpotName,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${booking.vehicleNumber} • ${booking.vehicleType.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Duration selector
                Text(
                    text = "Extend by",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                
                Spacer(Modifier.height(Spacing.Small))
                
                // Hour chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    hourOptions.take(3).forEach { hours ->
                        FilterChip(
                            selected = selectedHours == hours,
                            onClick = { selectedHours = hours },
                            label = { Text("$hours hr") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Small))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    hourOptions.drop(3).forEach { hours ->
                        FilterChip(
                            selected = selectedHours == hours,
                            onClick = { selectedHours = hours },
                            label = { Text("$hours hr") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryBlue,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Price summary
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = PrimaryBlue.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Extension Cost",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "$selectedHours hour${if (selectedHours > 1) "s" else ""} additional",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                        Text(
                            text = "₹${extensionPrice.toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { onConfirm(selectedHours) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue
                        )
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Extend Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingDetailsDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
    colors: CityFluxColors
) {
    // Observe real-time booking data
    val repository = remember { BookingRepository.getInstance() }
    val realtimeBooking by repository.observeBooking(booking.id)
        .collectAsState(initial = booking)
    
    val currentBooking = realtimeBooking ?: booking
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(Spacing.Medium),
            shape = RoundedCornerShape(CornerRadius.Large),
            color = colors.cardBackground,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.Large)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Live indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Booking Details",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        // Live indicator
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentGreen.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // Real-time countdown for active bookings
                if (currentBooking.status.isActive() && currentBooking.bookingEndTime != null) {
                    val endTimeMillis = currentBooking.bookingEndTime!!.toDate().time
                    var timeRemaining by remember { mutableStateOf((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)) }
                    
                    LaunchedEffect(endTimeMillis) {
                        while (isActive) {
                            timeRemaining = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
                            delay(1000L)
                        }
                    }
                    
                    val hours = (timeRemaining / (1000 * 60 * 60)).toInt()
                    val minutes = ((timeRemaining / (1000 * 60)) % 60).toInt()
                    val seconds = ((timeRemaining / 1000) % 60).toInt()
                    val isExpiring = timeRemaining < 30 * 60 * 1000
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = if (isExpiring) AccentAlerts.copy(alpha = 0.1f) else AccentGreen.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                null,
                                tint = if (isExpiring) AccentAlerts else AccentGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Time Remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpiring) AccentAlerts else AccentGreen
                                )
                            }
                            if (isExpiring) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = AccentAlerts,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(Spacing.Medium))
                }
                
                // Booking ID Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = PrimaryBlue.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(Spacing.Small))
                        Column {
                            Text(
                                text = "Booking ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Text(
                                text = currentBooking.id.take(12).uppercase(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // Details Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    BookingDetailRow(
                        icon = Icons.Default.LocalParking,
                        label = "Parking Spot",
                        value = currentBooking.parkingSpotName,
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.Place,
                        label = "Address",
                        value = currentBooking.parkingAddress.ifBlank { "N/A" },
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.DirectionsCar,
                        label = "Vehicle Number",
                        value = currentBooking.vehicleNumber,
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.Schedule,
                        label = "Duration",
                        value = "${currentBooking.durationHours} hours",
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.CalendarToday,
                        label = "Start Time",
                        value = currentBooking.bookingStartTime?.toDate()?.let { 
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it) 
                        } ?: "N/A",
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.EventAvailable,
                        label = "End Time",
                        value = currentBooking.bookingEndTime?.toDate()?.let { 
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it) 
                        } ?: "N/A",
                        colors = colors
                    )
                    BookingDetailRow(
                        icon = Icons.Default.Payment,
                        label = "Amount Paid",
                        value = "₹${currentBooking.totalAmount.toInt()}",
                        colors = colors,
                        valueColor = AccentGreen
                    )
                    BookingDetailRow(
                        icon = Icons.Default.Info,
                        label = "Status",
                        value = currentBooking.status.displayName,
                        colors = colors,
                        valueColor = when(currentBooking.status) {
                            BookingStatus.ACTIVE -> AccentGreen
                            BookingStatus.CONFIRMED -> PrimaryBlue
                            BookingStatus.COMPLETED -> colors.textSecondary
                            BookingStatus.CANCELLED -> AccentRed
                            else -> colors.textPrimary
                        }
                    )
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Navigate Button (Blue, Full Width)
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Navigate to Parking")
                }
            }
        }
    }
}

@Composable
private fun BookingDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    colors: CityFluxColors,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (valueColor != Color.Unspecified) valueColor else colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════
// QR Code Dialog
// ═══════════════════════════════════════════════════════
@Composable
private fun QRCodeDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    colors: CityFluxColors
) {
    val qrBitmap = remember(booking.qrCodeData, booking.id) {
        generateQRCode(booking.qrCodeData.ifBlank { "CITYFLUX:${booking.id}" })
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(Spacing.Medium),
            shape = RoundedCornerShape(CornerRadius.Large),
            color = colors.cardBackground,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Parking QR Code",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // QR Code
                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: CircularProgressIndicator()
                    }
                }
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // Booking Info
                Text(
                    text = booking.parkingSpotName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Vehicle: ${booking.vehicleNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                
                Spacer(Modifier.height(Spacing.Small))
                
                Text(
                    text = "Show this QR code at the parking entrance",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(Spacing.Large))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

// Generate QR Code bitmap
private fun generateQRCode(data: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

// Navigate to parking location using Google Maps
private fun navigateToParking(context: Context, booking: ParkingBooking) {
    try {
        val address = booking.parkingAddress.ifBlank { booking.parkingSpotName }
        val encodedAddress = Uri.encode(address)
        val gmmIntentUri = Uri.parse("google.navigation:q=$encodedAddress&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback to browser
            val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show()
    }
}
