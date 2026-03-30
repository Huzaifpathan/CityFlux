package com.example.cityflux.ui.parking

import android.graphics.Bitmap
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingService
import com.example.cityflux.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
    
    // Dialog states
    var showExtendDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }
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
                        onBookingClick = onBookingClick,
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onExtendClick = { booking ->
                            selectedBookingForAction = booking
                            showExtendDialog = true
                        },
                        onShowQRClick = { booking ->
                            selectedBookingForAction = booking
                            showQRDialog = true
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
        
        // QR Code Dialog
        if (showQRDialog && selectedBookingForAction != null) {
            QRCodeDialog(
                booking = selectedBookingForAction!!,
                onDismiss = { 
                    showQRDialog = false
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
    viewModel: BookingViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Dialog states
    var showExtendDialog by remember { mutableStateOf(false) }
    var showQRDialog by remember { mutableStateOf(false) }
    var selectedBookingForAction by remember { mutableStateOf<ParkingBooking?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
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
            ) {
                when (selectedTab) {
                    0 -> ActiveBookingsTab(
                        bookings = uiState.activeBookings,
                        onBookingClick = onBookingClick,
                        onCancelClick = { booking ->
                            viewModel.cancelBooking(booking.id, "User cancelled")
                        },
                        onExtendClick = { booking ->
                            selectedBookingForAction = booking
                            showExtendDialog = true
                        },
                        onShowQRClick = { booking ->
                            selectedBookingForAction = booking
                            showQRDialog = true
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
                onConfirm = { hours ->
                    viewModel.extendBooking(selectedBookingForAction!!.id, hours)
                    showExtendDialog = false
                    selectedBookingForAction = null
                },
                colors = colors
            )
        }
        
        // QR Code Dialog
        if (showQRDialog && selectedBookingForAction != null) {
            QRCodeDialog(
                booking = selectedBookingForAction!!,
                onDismiss = { 
                    showQRDialog = false
                    selectedBookingForAction = null
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
    onShowQRClick: (ParkingBooking) -> Unit,
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
                    onShowQR = { onShowQRClick(booking) },
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
    onShowQR: () -> Unit,
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
                    onClick = onShowQR,
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

// ═══════════════════════════════════════════════════════
// QR Code Dialog
// ═══════════════════════════════════════════════════════

@Composable
private fun QRCodeDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    colors: CityFluxColors
) {
    // Generate QR code content
    val qrContent = remember(booking) {
        "CITYFLUX:${booking.id}:${booking.parkingSpotId}:${booking.userId}"
    }
    
    // Generate QR bitmap
    val qrBitmap = remember(qrContent) {
        generateQRCode(qrContent, 512)
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
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // QR Code
                Surface(
                    modifier = Modifier.size(250.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.Medium),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Booking QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } ?: run {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    }
                }
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Booking details
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = colors.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Medium)
                    ) {
                        QRDetailRow(
                            label = "Location",
                            value = booking.parkingSpotName,
                            colors = colors
                        )
                        QRDetailRow(
                            label = "Vehicle",
                            value = booking.vehicleNumber,
                            colors = colors
                        )
                        QRDetailRow(
                            label = "Valid Until",
                            value = booking.bookingEndTime?.toDate()?.let {
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                            } ?: "N/A",
                            colors = colors
                        )
                        QRDetailRow(
                            label = "Booking ID",
                            value = booking.id.takeLast(8).uppercase(),
                            colors = colors
                        )
                    }
                }
                
                Spacer(Modifier.height(Spacing.Medium))
                
                // Instructions
                Text(
                    text = "Show this QR code to the parking attendant",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(Modifier.height(Spacing.Large))
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun QRDetailRow(
    label: String,
    value: String,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Generate QR code bitmap using ZXing
 */
private fun generateQRCode(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.Black.toArgb() else Color.White.toArgb())
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
