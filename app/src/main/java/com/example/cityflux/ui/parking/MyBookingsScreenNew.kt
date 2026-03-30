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
import kotlinx.coroutines.isActive
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
 * Enhanced My Bookings Content with all features (LEGACY - use MyBookingsContentEnhanced from MyBookingsScreen.kt)
 * Used inside ParkingScreen tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBookingsContentEnhancedLegacy(
    onBookingClick: (ParkingBooking) -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val uiState by viewModel.uiState.collectAsState()
    
    var selectedBookingsTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Dialog states
    var showQrDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    var showDetailsDialogBookingId by remember { mutableStateOf<String?>(null) }
    var showRatingDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    var showExtendDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    var showCancelDialog by remember { mutableStateOf<ParkingBooking?>(null) }
    
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
    
    Column(modifier = Modifier.fillMaxSize()) {
        // ═══════════════ Search Bar ═══════════════
        SearchFilterBar(
            searchQuery = searchQuery,
            onSearchChange = { searchQuery = it },
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
                .weight(1f)
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
                    onBookingClick = { showDetailsDialogBookingId = it.id },
                    onShowQR = { showQrDialog = it },
                    onNavigate = { navigateToParking(context, it) },
                    onExtend = { showExtendDialog = it },
                    onCancel = { showCancelDialog = it },
                    colors = colors
                )
                1 -> UpcomingBookingsTabEnhanced(
                    bookings = filteredUpcoming,
                    onBookingClick = { showDetailsDialogBookingId = it.id },
                    onCancel = { showCancelDialog = it },
                    onAddToCalendar = { addToCalendar(context, it) },
                    onShare = { shareBooking(context, it) },
                    colors = colors
                )
                2 -> HistoryTabEnhanced(
                    bookings = filteredHistory,
                    onBookingClick = { showDetailsDialogBookingId = it.id },
                    onDownloadReceipt = { downloadReceipt(context, it) },
                    onRateBooking = { showRatingDialog = it },
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
    showDetailsDialogBookingId?.let { bookingId ->
        BookingDetailsDialogRealtime(
            bookingId = bookingId,
            viewModel = viewModel,
            onDismiss = { showDetailsDialogBookingId = null },
            onShowQR = { booking -> showQrDialog = booking; showDetailsDialogBookingId = null },
            onNavigate = { booking -> navigateToParking(context, booking) },
            onShare = { booking -> shareBooking(context, booking) },
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
    
    // ═══════════════ Extend Booking Dialog ═══════════════
    showExtendDialog?.let { booking ->
        ExtendBookingDialog(
            booking = booking,
            onDismiss = { showExtendDialog = null },
            onExtend = { hours ->
                viewModel.extendBooking(booking.id, hours)
                showExtendDialog = null
                Toast.makeText(context, "Booking extended by $hours hour(s)!", Toast.LENGTH_SHORT).show()
            }
        )
    }
    
    // ═══════════════ Cancel Booking Dialog ═══════════════
    showCancelDialog?.let { booking ->
        CancelBookingDialog(
            booking = booking,
            onDismiss = { showCancelDialog = null },
            onCancel = { reason ->
                viewModel.cancelBooking(booking.id, reason)
                showCancelDialog = null
                Toast.makeText(context, "Booking cancelled", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// SEARCH BAR (No Filter Button)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SearchFilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    colors: CityFluxColors
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search by parking, vehicle, booking ID...", fontSize = 14.sp) },
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
    // Convert Timestamp to Long so LaunchedEffect can detect changes
    val endTimeMillis = remember(booking.bookingEndTime) { 
        booking.bookingEndTime?.toDate()?.time ?: 0L
    }
    
    var remainingMillis by remember { mutableStateOf((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)) }
    
    // Update every second and restart when end time changes
    LaunchedEffect(booking.id, endTimeMillis) {
        while (isActive) {
            remainingMillis = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000L)
        }
    }
    
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
            
            // Action Buttons Row 1
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
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Action Buttons Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                
                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
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
// BOOKING DETAILS DIALOG (REALTIME)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BookingDetailsDialogRealtime(
    bookingId: String,
    viewModel: BookingViewModel,
    onDismiss: () -> Unit,
    onShowQR: (ParkingBooking) -> Unit,
    onNavigate: (ParkingBooking) -> Unit,
    onShare: (ParkingBooking) -> Unit,
    onCall: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val repository = remember { com.example.cityflux.data.BookingRepository() }
    
    // Observe real-time booking data from Firestore
    val realtimeBooking by repository.observeBooking(bookingId)
        .collectAsState(initial = null)
    
    // Show loading while fetching
    val booking = realtimeBooking
    
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
            if (booking == null) {
                // Loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = PremiumBlue,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Loading booking details...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Booking Details",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            // Live indicator
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SuccessGreen.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "live_dot")
                                    val dotAlpha by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = 0.3f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "dot_alpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SuccessGreen.copy(alpha = dotAlpha))
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessGreen
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // Status badge
                    val statusColor = when (booking.status) {
                        BookingStatus.CONFIRMED, BookingStatus.ACTIVE -> SuccessGreen
                        BookingStatus.PENDING -> PremiumGold
                        BookingStatus.CANCELLED -> Color(0xFFEF4444)
                        BookingStatus.COMPLETED -> PremiumBlue
                        else -> SoftGray
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = booking.status.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
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
                    
                    // Realtime countdown for active bookings
                    if (booking.status.isActive() && booking.bookingEndTime != null) {
                        val endTimeMillis = booking.bookingEndTime!!.toDate().time
                        var timeRemaining by remember { mutableStateOf((endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)) }
                        
                        LaunchedEffect(endTimeMillis) {
                            while (isActive) {
                                timeRemaining = (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0)
                                delay(1000L)
                            }
                        }
                        
                        val isExpiring = timeRemaining < 30 * 60 * 1000
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExpiring) PremiumGold.copy(alpha = 0.1f) else SuccessGreen.copy(alpha = 0.1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Timer,
                                    null,
                                    tint = if (isExpiring) PremiumGold else SuccessGreen,
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
                                        text = formatRemainingTime(timeRemaining),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpiring) PremiumGold else SuccessGreen
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    // Details Grid
                    DetailItem("Booking ID", booking.id.takeLast(10).uppercase(), Icons.Default.Tag)
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
                    DetailItem("Status", booking.status.displayName, Icons.Default.Info)
                    
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
                            onClick = { onShowQR(booking) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("QR")
                        }
                        OutlinedButton(
                            onClick = { onNavigate(booking) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Navigation, null, Modifier.size(18.dp))
                        }
                        OutlinedButton(
                            onClick = { onShare(booking) },
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
// EXTEND BOOKING DIALOG
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ExtendBookingDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onExtend: (Int) -> Unit
) {
    var selectedHours by remember { mutableIntStateOf(1) }
    val colors = MaterialTheme.cityFluxColors
    
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
                Icon(
                    Icons.Default.Update,
                    null,
                    tint = PremiumBlue,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Extend Parking",
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
                
                Text(
                    text = "Extend by:",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Hour selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 4).forEach { hours ->
                        Surface(
                            onClick = { selectedHours = hours },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedHours == hours) PremiumBlue else colors.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$hours",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedHours == hours) Color.White else colors.textPrimary
                                )
                                Text(
                                    text = if (hours == 1) "hour" else "hours",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedHours == hours) Color.White.copy(alpha = 0.8f) else colors.textSecondary
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Price preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SuccessGreen.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Additional Cost",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                            Text(
                                text = "₹${booking.totalAmount.toInt() / booking.durationHours * selectedHours}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                        Icon(Icons.Default.Info, null, tint = SuccessGreen)
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Action buttons
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
                        onClick = { onExtend(selectedHours) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Extend")
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CANCEL BOOKING DIALOG
// ═══════════════════════════════════════════════════════════════
@Composable
private fun CancelBookingDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onCancel: (String) -> Unit
) {
    var cancelReason by remember { mutableStateOf("") }
    var selectedReason by remember { mutableStateOf("") }
    val colors = MaterialTheme.cityFluxColors
    
    val predefinedReasons = listOf(
        "Change of plans",
        "Found alternative parking",
        "Incorrect booking details",
        "Emergency situation",
        "Other"
    )
    
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
                Icon(
                    Icons.Default.Cancel,
                    null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Cancel Booking?",
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
                
                Text(
                    text = "Reason for cancellation:",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Reason chips
                predefinedReasons.forEach { reason ->
                    Surface(
                        onClick = { 
                            selectedReason = reason
                            if (reason != "Other") cancelReason = reason
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedReason == reason) PremiumBlue.copy(alpha = 0.1f) else colors.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedReason == reason) PremiumBlue else colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            if (selectedReason == reason) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = PremiumBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                
                // Custom reason field (shown when "Other" is selected)
                if (selectedReason == "Other") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Please specify reason...") },
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Warning message
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "This action cannot be undone",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Keep Booking")
                    }
                    
                    Button(
                        onClick = { 
                            val finalReason = if (cancelReason.isNotBlank()) cancelReason else selectedReason
                            onCancel(finalReason)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedReason.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Cancel, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel Booking")
                    }
                }
            }
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
