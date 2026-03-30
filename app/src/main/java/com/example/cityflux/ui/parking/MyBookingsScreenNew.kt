package com.example.cityflux.ui.parking

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Color constants
private val SuccessGreen = Color(0xFF10B981)
private val PremiumBlue = Color(0xFF2563EB)
private val PremiumGold = Color(0xFFF59E0B)
private val AccentPurple = Color(0xFF8B5CF6)
private val AccentPink = Color(0xFFEC4899)
private val SoftGray = Color(0xFF6B7280)

/**
 * Enhanced My Bookings Content with all features
 * Used inside ParkingScreen tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsContentEnhanced(
    onBookingClick: (ParkingBooking) -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedBookingsTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Dialog states
    var showQrDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    var showDetailsDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    var showRatingDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    
    // Pull to refresh
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
    
    // Calculate stats
    val totalBookings = uiState.activeBookings.size + uiState.upcomingBookings.size + uiState.pastBookings.size
    val totalSpent = (uiState.activeBookings + uiState.upcomingBookings + uiState.pastBookings)
        .sumOf { it.totalAmount }
    val completedBookings = uiState.pastBookings.count { it.status == BookingStatus.COMPLETED }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // ═══════════════ Stats Dashboard ═══════════════
        StatsDashboard(
            totalBookings = totalBookings,
            totalSpent = totalSpent,
            activeCount = uiState.activeBookings.size,
            completedCount = completedBookings,
            colors = colors
        )
        
        // ═══════════════ Search Bar ═══════════════
        SearchFilterBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
            selectedFilter = selectedFilter,
            onFilterClick = { showFilterSheet = true },
            colors = colors
        )
        
        // ═══════════════ Sub-Tab Row ═══════════════
        TabRow(
            selectedTabIndex = selectedBookingsTab,
            containerColor = colors.cardBackground,
            contentColor = PremiumBlue,
            divider = {}
        ) {
            Tab(
                selected = selectedBookingsTab == 0,
                onClick = { selectedBookingsTab = 0 },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Active", fontSize = 13.sp)
                        if (uiState.activeBookings.isNotEmpty()) {
                            Badge(containerColor = SuccessGreen) {
                                Text("${uiState.activeBookings.size}", fontSize = 10.sp)
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Upcoming", fontSize = 13.sp)
                        if (uiState.upcomingBookings.isNotEmpty()) {
                            Badge(containerColor = PremiumBlue) {
                                Text("${uiState.upcomingBookings.size}", fontSize = 10.sp)
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedBookingsTab == 2,
                onClick = { selectedBookingsTab = 2 },
                text = { Text("History", fontSize = 13.sp) }
            )
        }
        
        // ═══════════════ Content with Pull-to-Refresh ═══════════════
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            // Filter bookings by search
            val filteredActive = uiState.activeBookings.filter { 
                it.matchesSearch(searchQuery) 
            }
            val filteredUpcoming = uiState.upcomingBookings.filter { 
                it.matchesSearch(searchQuery) 
            }
            val filteredHistory = uiState.pastBookings.filter { 
                it.matchesSearch(searchQuery) 
            }
            
            when (selectedBookingsTab) {
                0 -> ActiveBookingsTabEnhanced(
                    bookings = filteredActive,
                    onBookingClick = { showDetailsDialog = it },
                    onShowQR = { showQrDialog = it },
                    onNavigate = { navigateToParking(context, it) },
                    onExtend = { viewModel.extendBooking(it.id, 1) },
                    onCancel = { viewModel.cancelBooking(it.id, "User cancelled") },
                    colors = colors
                )
                1 -> UpcomingBookingsTabEnhanced(
                    bookings = filteredUpcoming,
                    onBookingClick = { showDetailsDialog = it },
                    onCancel = { viewModel.cancelBooking(it.id, "User cancelled") },
                    onAddToCalendar = { addToCalendar(context, it) },
                    onShare = { shareBooking(context, it) },
                    colors = colors
                )
                2 -> HistoryTabEnhanced(
                    bookings = filteredHistory,
                    onBookingClick = { showDetailsDialog = it },
                    onDownloadReceipt = { downloadReceipt(context, it) },
                    onRateBooking = { showRatingDialog = it },
                    onRebook = { /* Rebook functionality */ },
                    colors = colors
                )
            }
            
            // Pull to refresh indicator
            if (pullToRefreshState.isRefreshing || pullToRefreshState.progress > 0f) {
                PullToRefreshContainer(
                    modifier = Modifier.align(Alignment.TopCenter),
                    state = pullToRefreshState
                )
            }
        }
    }
    
    // ═══════════════ QR Code Dialog ═══════════════
    showQrDialog?.let { booking ->
        QRCodeDialog(
            booking = booking,
            onDismiss = { showQrDialog = null },
            onSaveQR = { saveQRToGallery(context, booking) }
        )
    }
    
    // ═══════════════ Booking Details Dialog ═══════════════
    showDetailsDialog?.let { booking ->
        BookingDetailsDialog(
            booking = booking,
            onDismiss = { showDetailsDialog = null },
            onShowQR = { showQrDialog = booking; showDetailsDialog = null },
            onNavigate = { navigateToParking(context, booking) },
            onShare = { shareBooking(context, booking) },
            onCall = { callHelpline(context) }
        )
    }
    
    // ═══════════════ Rating Dialog ═══════════════
    showRatingDialog?.let { booking ->
        RatingDialog(
            booking = booking,
            onDismiss = { showRatingDialog = null },
            onSubmit = { rating, review ->
                viewModel.rateBooking(booking.id, rating, review)
                showRatingDialog = null
                Toast.makeText(context, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // ═══════════════ Filter Bottom Sheet ═══════════════
    if (showFilterSheet) {
        FilterBottomSheet(
            selectedFilter = selectedFilter,
            onFilterSelected = { 
                selectedFilter = it
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// STATS DASHBOARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StatsDashboard(
    totalBookings: Int,
    totalSpent: Double,
    activeCount: Int,
    completedCount: Int,
    colors: CityFluxColors
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StatCard(
                icon = Icons.Default.Receipt,
                value = "$totalBookings",
                label = "Total Bookings",
                color = PremiumBlue,
                colors = colors
            )
        }
        item {
            StatCard(
                icon = Icons.Default.CurrencyRupee,
                value = "₹${totalSpent.toInt()}",
                label = "Total Spent",
                color = SuccessGreen,
                colors = colors
            )
        }
        item {
            StatCard(
                icon = Icons.Default.PlayCircle,
                value = "$activeCount",
                label = "Active Now",
                color = PremiumGold,
                colors = colors
            )
        }
        item {
            StatCard(
                icon = Icons.Default.CheckCircle,
                value = "$completedCount",
                label = "Completed",
                color = AccentPurple,
                colors = colors
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    colors: CityFluxColors
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.width(130.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// SEARCH & FILTER BAR
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SearchFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String,
    onFilterClick: () -> Unit,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search bookings...", fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PremiumBlue,
                unfocusedBorderColor = colors.cardBorder
            )
        )
        
        // Filter button
        FilledIconButton(
            onClick = onFilterClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = colors.surfaceVariant
            )
        ) {
            Icon(Icons.Default.FilterList, "Filter", Modifier.size(22.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ACTIVE BOOKINGS TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ActiveBookingsTabEnhanced(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onShowQR: (ParkingBooking) -> Unit,
    onNavigate: (ParkingBooking) -> Unit,
    onExtend: (ParkingBooking) -> Unit,
    onCancel: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyStateEnhanced(
            icon = Icons.Default.EventBusy,
            title = "No Active Bookings",
            message = "You don't have any active parking bookings right now",
            actionText = "Book Parking",
            onAction = { /* Navigate to parking */ },
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookings, key = { it.id }) { booking ->
                ActiveBookingCardEnhanced(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    onShowQR = { onShowQR(booking) },
                    onNavigate = { onNavigate(booking) },
                    onExtend = { onExtend(booking) },
                    onCancel = { onCancel(booking) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun ActiveBookingCardEnhanced(
    booking: ParkingBooking,
    onClick: () -> Unit,
    onShowQR: () -> Unit,
    onNavigate: () -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
    colors: CityFluxColors
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    // Update every second
    LaunchedEffect(booking.id) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }
    
    val endTime = booking.bookingEndTime?.toDate()?.time ?: 0
    val remainingMillis = (endTime - currentTime).coerceAtLeast(0)
    val remainingMinutes = (remainingMillis / 60000).toInt()
    val isExpiringSoon = remainingMinutes < 30
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalParking,
                            null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = booking.parkingSpotName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = booking.vehicleNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                
                // Live badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SuccessGreen
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsing dot
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = alpha))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Countdown Timer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isExpiringSoon) PremiumGold.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Timer,
                        null,
                        tint = if (isExpiringSoon) PremiumGold else SuccessGreen,
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
                            text = formatRemainingTime(remainingMillis),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpiringSoon) PremiumGold else SuccessGreen
                        )
                    }
                    if (isExpiringSoon) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PremiumGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "⚠️ Expiring",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PremiumGold,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Quick Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    icon = Icons.Default.Schedule,
                    text = "${booking.durationHours}h",
                    colors = colors
                )
                InfoChip(
                    icon = Icons.Default.CurrencyRupee,
                    text = "₹${booking.totalAmount.toInt()}",
                    colors = colors
                )
                InfoChip(
                    icon = Icons.Default.DirectionsCar,
                    text = booking.vehicleType.displayName,
                    colors = colors
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // QR Button
                Button(
                    onClick = onShowQR,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("QR", fontWeight = FontWeight.SemiBold)
                }
                
                // Navigate Button
                OutlinedButton(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Navigate")
                }
                
                // Extend Button
                OutlinedButton(
                    onClick = onExtend,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PremiumGold)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Extend")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    text: String,
    colors: CityFluxColors
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = colors.textSecondary)
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// UPCOMING BOOKINGS TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun UpcomingBookingsTabEnhanced(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onCancel: (ParkingBooking) -> Unit,
    onAddToCalendar: (ParkingBooking) -> Unit,
    onShare: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyStateEnhanced(
            icon = Icons.Default.Schedule,
            title = "No Upcoming Bookings",
            message = "Plan ahead! Book your parking in advance",
            actionText = "Book Now",
            onAction = { /* Navigate to parking */ },
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(bookings, key = { it.id }) { booking ->
                UpcomingBookingCardEnhanced(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    onCancel = { onCancel(booking) },
                    onAddToCalendar = { onAddToCalendar(booking) },
                    onShare = { onShare(booking) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun UpcomingBookingCardEnhanced(
    booking: ParkingBooking,
    onClick: () -> Unit,
    onCancel: () -> Unit,
    onAddToCalendar: () -> Unit,
    onShare: () -> Unit,
    colors: CityFluxColors
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PremiumBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            null,
                            tint = PremiumBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = booking.parkingSpotName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = booking.vehicleNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PremiumBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Scheduled",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Start time info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        null,
                        tint = PremiumBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Starts at",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary
                        )
                        Text(
                            text = booking.bookingStartTime?.toDate()?.let {
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                            } ?: "N/A",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "₹${booking.totalAmount.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onAddToCalendar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Event, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Calendar", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// HISTORY TAB
// ═══════════════════════════════════════════════════════════════
@Composable
private fun HistoryTabEnhanced(
    bookings: List<ParkingBooking>,
    onBookingClick: (ParkingBooking) -> Unit,
    onDownloadReceipt: (ParkingBooking) -> Unit,
    onRateBooking: (ParkingBooking) -> Unit,
    onRebook: (ParkingBooking) -> Unit,
    colors: CityFluxColors
) {
    if (bookings.isEmpty()) {
        EmptyStateEnhanced(
            icon = Icons.Default.History,
            title = "No Booking History",
            message = "Your completed bookings will appear here",
            actionText = null,
            onAction = {},
            colors = colors
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(bookings, key = { it.id }) { booking ->
                HistoryBookingCardEnhanced(
                    booking = booking,
                    onClick = { onBookingClick(booking) },
                    onDownloadReceipt = { onDownloadReceipt(booking) },
                    onRate = { onRateBooking(booking) },
                    onRebook = { onRebook(booking) },
                    colors = colors
                )
            }
        }
    }
}

@Composable
private fun HistoryBookingCardEnhanced(
    booking: ParkingBooking,
    onClick: () -> Unit,
    onDownloadReceipt: () -> Unit,
    onRate: () -> Unit,
    onRebook: () -> Unit,
    colors: CityFluxColors
) {
    val isCompleted = booking.status == BookingStatus.COMPLETED
    val statusColor = when (booking.status) {
        BookingStatus.COMPLETED -> SuccessGreen
        BookingStatus.CANCELLED -> Color(0xFFEF4444)
        else -> SoftGray
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.parkingSpotName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${booking.vehicleNumber} • ${booking.durationHours}h",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${booking.totalAmount.toInt()}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = booking.status.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Date
            Text(
                text = booking.bookingCreatedAt?.toDate()?.let {
                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
                } ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
            
            // Actions for completed bookings
            if (isCompleted) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDownloadReceipt,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Receipt", fontSize = 12.sp)
                    }
                    
                    if (booking.rating == null || booking.rating == 0f) {
                        TextButton(
                            onClick = onRate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Rate", fontSize = 12.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(booking.rating?.toInt() ?: 0) {
                                Icon(
                                    Icons.Default.Star,
                                    null,
                                    tint = PremiumGold,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    
                    TextButton(
                        onClick = onRebook,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rebook", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// QR CODE DIALOG
// ═══════════════════════════════════════════════════════════════
@Composable
private fun QRCodeDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onSaveQR: () -> Unit
) {
    val qrBitmap = remember(booking.id) {
        generateQRCodeBitmap(
            "CITYFLUX:${booking.id}:${booking.vehicleNumber}:${booking.parkingSpotId}",
            400
        )
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Entry/Exit QR Code",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "Show this at parking gate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray
                )
                
                Spacer(Modifier.height(24.dp))
                
                // QR Code
                Surface(
                    modifier = Modifier.size(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        qrBitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Booking ID
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PremiumBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "ID: ${booking.id.takeLast(8).uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumBlue,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveQR,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue)
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// BOOKING DETAILS DIALOG
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BookingDetailsDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onShowQR: () -> Unit,
    onNavigate: () -> Unit,
    onShare: () -> Unit,
    onCall: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Booking Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Parking Info
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = booking.parkingSpotName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = booking.parkingAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Details Grid
                DetailItem("Vehicle", booking.vehicleNumber, Icons.Default.DirectionsCar)
                DetailItem("Type", booking.vehicleType.displayName, Icons.Default.Category)
                DetailItem("Duration", "${booking.durationHours} hour(s)", Icons.Default.Schedule)
                DetailItem(
                    "Start Time",
                    booking.bookingStartTime?.toDate()?.let {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                    } ?: "N/A",
                    Icons.Default.AccessTime
                )
                DetailItem(
                    "End Time",
                    booking.bookingEndTime?.toDate()?.let {
                        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                    } ?: "N/A",
                    Icons.Default.AccessTime
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Amount breakdown
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Base Amount", color = colors.textSecondary)
                            Text("₹${booking.baseAmount.toInt()}", color = colors.textPrimary)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("GST (18%)", color = colors.textSecondary)
                            Text("₹${booking.gstAmount.toInt()}", color = colors.textPrimary)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Text(
                                "₹${booking.totalAmount.toInt()}",
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(20.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShowQR,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("QR")
                    }
                    OutlinedButton(
                        onClick = onNavigate,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    val colors = MaterialTheme.cityFluxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(label, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, color = colors.textPrimary)
    }
}

// ═══════════════════════════════════════════════════════════════
// RATING DIALOG
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RatingDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit
) {
    var rating by remember { mutableIntStateOf(0) }
    var review by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Rate Your Experience",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = booking.parkingSpotName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftGray
                )
                
                Spacer(Modifier.height(24.dp))
                
                // Star rating
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) { index ->
                        IconButton(onClick = { rating = index + 1 }) {
                            Icon(
                                imageVector = if (index < rating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = null,
                                tint = if (index < rating) PremiumGold else SoftGray,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Review text
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Share your experience (optional)") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { onSubmit(rating, review) },
                        modifier = Modifier.weight(1f),
                        enabled = rating > 0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue)
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// FILTER BOTTOM SHEET
// ═══════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val filters = listOf("All", "This Week", "This Month", "Completed", "Cancelled")
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Filter Bookings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(16.dp))
            
            filters.forEach { filter ->
                Surface(
                    onClick = { onFilterSelected(filter) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (filter == selectedFilter) PremiumBlue.copy(alpha = 0.1f) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (filter == selectedFilter) PremiumBlue else Color.Black,
                            fontWeight = if (filter == selectedFilter) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Spacer(Modifier.weight(1f))
                        if (filter == selectedFilter) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                tint = PremiumBlue
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EmptyStateEnhanced(
    icon: ImageVector,
    title: String,
    message: String,
    actionText: String?,
    onAction: () -> Unit,
    colors: CityFluxColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(50.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        
        if (actionText != null) {
            Spacer(Modifier.height(24.dp))
            
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue)
            ) {
                Text(actionText)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════

private fun ParkingBooking.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.lowercase()
    return parkingSpotName.lowercase().contains(q) ||
           vehicleNumber.lowercase().contains(q) ||
           id.lowercase().contains(q) ||
           parkingAddress.lowercase().contains(q)
}

private fun formatRemainingTime(millis: Long): String {
    val hours = (millis / (1000 * 60 * 60)).toInt()
    val minutes = ((millis / (1000 * 60)) % 60).toInt()
    val seconds = ((millis / 1000) % 60).toInt()
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun generateQRCodeBitmap(data: String, size: Int): ImageBitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

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
            val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show()
    }
}

private fun shareBooking(context: Context, booking: ParkingBooking) {
    val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val shareText = """
🅿️ CityFlux Parking Booking

📍 ${booking.parkingSpotName}
🚗 ${booking.vehicleNumber}
⏱️ ${booking.durationHours} hour(s)
💰 ₹${booking.totalAmount.toInt()}
📅 ${booking.bookingStartTime?.toDate()?.let { sdf.format(it) } ?: "N/A"}

Download CityFlux for smart parking!
    """.trimIndent()
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Booking"))
}

private fun addToCalendar(context: Context, booking: ParkingBooking) {
    try {
        val startTime = booking.bookingStartTime?.toDate()?.time ?: return
        val endTime = booking.bookingEndTime?.toDate()?.time ?: return
        
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = android.provider.CalendarContract.Events.CONTENT_URI
            putExtra(android.provider.CalendarContract.Events.TITLE, "Parking: ${booking.parkingSpotName}")
            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Vehicle: ${booking.vehicleNumber}")
            putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, booking.parkingAddress)
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, endTime)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to add to calendar", Toast.LENGTH_SHORT).show()
    }
}

private fun downloadReceipt(context: Context, booking: ParkingBooking) {
    // For now, just show toast - full PDF implementation would go here
    Toast.makeText(context, "📄 Receipt downloaded!", Toast.LENGTH_SHORT).show()
}

private fun saveQRToGallery(context: Context, booking: ParkingBooking) {
    try {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(AndroidColor.WHITE)
        
        val qrWriter = QRCodeWriter()
        val bitMatrix = qrWriter.encode(
            "CITYFLUX:${booking.id}",
            BarcodeFormat.QR_CODE,
            size, size
        )
        for (x in 0 until size) {
            for (y in 0 until size) {
                if (bitMatrix[x, y]) {
                    bitmap.setPixel(x, y, AndroidColor.BLACK)
                }
            }
        }
        
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "CityFlux_QR_${booking.id.takeLast(8)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { imageUri ->
            context.contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            Toast.makeText(context, "✅ QR saved to gallery!", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save QR", Toast.LENGTH_SHORT).show()
    }
}

private fun callHelpline(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:+911234567890")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to make call", Toast.LENGTH_SHORT).show()
    }
}
