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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cityflux.model.ParkingBooking
import com.example.cityflux.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.random.Random

// Premium color constants
private val SuccessGreen = Color(0xFF10B981)
private val SuccessGreenLight = Color(0xFFD1FAE5)
private val PremiumBlue = Color(0xFF2563EB)
private val PremiumGold = Color(0xFFF59E0B)
private val SoftGray = Color(0xFF6B7280)

/**
 * Premium Booking Success Dialog
 * Shows beautiful confirmation with all booking details
 * Features: Animated checkmark, Confetti, QR Code, Actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingSuccessDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onViewBooking: () -> Unit = {},
    onNavigate: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAnimation by remember { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var remainingTime by remember { mutableStateOf(calculateRemainingTime(booking)) }
    var reminderSet by remember { mutableStateOf(false) }
    
    // Trigger animations
    LaunchedEffect(Unit) {
        delay(100)
        showAnimation = true
        delay(300)
        showConfetti = true
    }
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            remainingTime = calculateRemainingTime(booking)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Confetti Effect
            if (showConfetti) {
                ConfettiAnimation()
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ═══════════════ Header with Gradient ═══════════════
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        SuccessGreen,
                                        SuccessGreen.copy(alpha = 0.8f),
                                        PremiumBlue.copy(alpha = 0.6f)
                                    )
                                )
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Animated Checkmark
                            AnimatedSuccessCheckmark(showAnimation = showAnimation)
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Text(
                                text = "Booking Confirmed! 🎉",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            Text(
                                text = "Your parking slot is reserved",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // ═══════════════ Booking ID Badge ═══════════════
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PremiumBlue.copy(alpha = 0.1f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ConfirmationNumber,
                                contentDescription = null,
                                tint = PremiumBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Booking ID: ${formatBookingId(booking.id)}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumBlue
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // ═══════════════ Live Countdown Timer ═══════════════
                    CountdownTimerCard(remainingTime = remainingTime)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // ═══════════════ Booking Details ═══════════════
                    BookingDetailsCard(booking = booking)
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // ═══════════════ Quick Actions Row ═══════════════
                    QuickActionsRow(
                        onNavigate = { navigateToParking(context, booking) },
                        onShare = { shareBooking(context, booking) },
                        onViewDetails = { 
                            onViewBooking()
                            onDismiss()
                        },
                        onSetReminder = { 
                            if (setBookingReminder(context, booking)) {
                                reminderSet = true
                            }
                        },
                        reminderSet = reminderSet
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // ═══════════════ Contact Parking ═══════════════
                    ContactParkingCard(
                        parkingName = booking.parkingSpotName,
                        onCall = { callParkingHelpline(context) }
                    )
                    
                    Spacer(Modifier.height(20.dp))
                    
                    // ═══════════════ Done Button ═══════════════
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
}

// ═══════════════════════════════════════════════════════════════
// ANIMATED SUCCESS CHECKMARK
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AnimatedSuccessCheckmark(showAnimation: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (showAnimation) 0f else -180f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .rotate(rotation)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .border(3.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Success",
            modifier = Modifier.size(48.dp),
            tint = Color.White
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// CONFETTI ANIMATION
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ConfettiAnimation() {
    val confettiColors = listOf(
        Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFE66D),
        Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA)
    )
    
    val particles = remember {
        List(50) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -0.5f,
                rotation = Random.nextFloat() * 360f,
                color = confettiColors.random(),
                speed = 0.5f + Random.nextFloat() * 1.5f,
                size = 8f + Random.nextFloat() * 8f
            )
        }
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            val y = (particle.y + animProgress * particle.speed) % 1.2f
            val x = particle.x + sin(y * 10) * 0.05f
            
            drawCircle(
                color = particle.color,
                radius = particle.size,
                center = Offset(x * size.width, y * size.height)
            )
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val rotation: Float,
    val color: Color,
    val speed: Float,
    val size: Float
)

// ═══════════════════════════════════════════════════════════════
// COUNTDOWN TIMER CARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun CountdownTimerCard(remainingTime: RemainingTime) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (remainingTime.isExpiringSoon) 
            Color(0xFFFEF3C7) else SuccessGreenLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer Icon with pulse animation
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .scale(if (remainingTime.isExpiringSoon) pulse else 1f)
                    .clip(CircleShape)
                    .background(
                        if (remainingTime.isExpiringSoon) 
                            PremiumGold.copy(alpha = 0.2f) 
                        else SuccessGreen.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (remainingTime.isExpiringSoon) PremiumGold else SuccessGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (remainingTime.isExpired) "Booking Expired" else "Time Remaining",
                    style = MaterialTheme.typography.labelMedium,
                    color = SoftGray
                )
                
                Text(
                    text = remainingTime.formatted,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingTime.isExpiringSoon) PremiumGold 
                           else if (remainingTime.isExpired) Color.Red 
                           else SuccessGreen
                )
            }
            
            if (remainingTime.isExpiringSoon && !remainingTime.isExpired) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PremiumGold.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Expiring Soon",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PremiumGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// BOOKING DETAILS CARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun BookingDetailsCard(booking: ParkingBooking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Booking Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Parking Location
            DetailRow(
                icon = Icons.Default.LocationOn,
                iconTint = Color(0xFFEF4444),
                label = "Parking Location",
                value = booking.parkingSpotName,
                subValue = booking.parkingAddress
            )
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))
            
            // Vehicle Number
            DetailRow(
                icon = Icons.Default.DirectionsCar,
                iconTint = PremiumBlue,
                label = "Vehicle Number",
                value = booking.vehicleNumber
            )
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))
            
            // Duration
            DetailRow(
                icon = Icons.Default.Schedule,
                iconTint = Color(0xFF8B5CF6),
                label = "Duration",
                value = "${booking.durationHours} hour${if (booking.durationHours > 1) "s" else ""}"
            )
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))
            
            // Time Slot
            DetailRow(
                icon = Icons.Default.AccessTime,
                iconTint = PremiumGold,
                label = "Time Slot",
                value = formatTimeRange(booking)
            )
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE2E8F0))
            
            // Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CurrencyRupee,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total Amount",
                        style = MaterialTheme.typography.labelMedium,
                        color = SoftGray
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₹${booking.totalAmount.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "(incl. ₹${booking.gstAmount.toInt()} GST)",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftGray
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (booking.isPaid) "PAID" else "PENDING",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (booking.isPaid) SuccessGreen else PremiumGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subValue: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = SoftGray
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            if (subValue != null) {
                Text(
                    text = subValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGray
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// QUICK ACTIONS ROW
// ═══════════════════════════════════════════════════════════════
@Composable
private fun QuickActionsRow(
    onNavigate: () -> Unit,
    onShare: () -> Unit,
    onViewDetails: () -> Unit,
    onSetReminder: () -> Unit,
    reminderSet: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.Navigation,
            label = "Navigate",
            color = PremiumBlue,
            modifier = Modifier.weight(1f),
            onClick = onNavigate
        )
        
        QuickActionButton(
            icon = Icons.Default.Share,
            label = "Share",
            color = Color(0xFF8B5CF6),
            modifier = Modifier.weight(1f),
            onClick = onShare
        )
        
        QuickActionButton(
            icon = if (reminderSet) Icons.Default.NotificationsActive else Icons.Default.NotificationAdd,
            label = if (reminderSet) "Set ✓" else "Remind",
            color = if (reminderSet) SuccessGreen else PremiumGold,
            modifier = Modifier.weight(1f),
            onClick = onSetReminder,
            enabled = !reminderSet
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// CONTACT PARKING CARD
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ContactParkingCard(
    parkingName: String,
    onCall: () -> Unit
) {
    Surface(
        onClick = onCall,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF7ED)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(PremiumGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = PremiumGold,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Need Help?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF92400E)
                )
                Text(
                    text = "Contact parking helpline",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB45309)
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PremiumGold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════

private data class RemainingTime(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val isExpiringSoon: Boolean,
    val isExpired: Boolean
) {
    val formatted: String
        get() = when {
            isExpired -> "Expired"
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
}

private fun calculateRemainingTime(booking: ParkingBooking): RemainingTime {
    val endTime = booking.bookingEndTime?.toDate()?.time ?: return RemainingTime(0, 0, 0, false, true)
    val now = System.currentTimeMillis()
    val diff = endTime - now
    
    if (diff <= 0) return RemainingTime(0, 0, 0, false, true)
    
    val seconds = ((diff / 1000) % 60).toInt()
    val minutes = ((diff / (1000 * 60)) % 60).toInt()
    val hours = (diff / (1000 * 60 * 60)).toInt()
    val isExpiringSoon = diff < 30 * 60 * 1000 // Less than 30 minutes
    
    return RemainingTime(hours, minutes, seconds, isExpiringSoon, false)
}

private fun formatBookingId(id: String): String {
    return if (id.length > 8) {
        "CFP-${id.takeLast(8).uppercase()}"
    } else {
        "CFP-${id.uppercase()}"
    }
}

private fun formatTimeRange(booking: ParkingBooking): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val startTime = booking.bookingStartTime?.toDate()?.let { sdf.format(it) } ?: "N/A"
    val endTime = booking.bookingEndTime?.toDate()?.let { sdf.format(it) } ?: "N/A"
    return "$startTime → $endTime"
}

private fun shareBooking(context: Context, booking: ParkingBooking) {
    val shareText = """
🅿️ CityFlux Parking Booking

📍 Location: ${booking.parkingSpotName}
📍 Address: ${booking.parkingAddress}
🚗 Vehicle: ${booking.vehicleNumber}
⏱️ Duration: ${booking.durationHours} hour(s)
💰 Amount: ₹${booking.totalAmount.toInt()}
🎫 Booking ID: ${formatBookingId(booking.id)}

Valid: ${formatTimeRange(booking)}

Download CityFlux app for smart parking!
    """.trimIndent()
    
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Booking"))
}

private fun callParkingHelpline(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:+911234567890")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to make call", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Navigate to parking location using Google Maps
 */
private fun navigateToParking(context: Context, booking: ParkingBooking) {
    try {
        // Try to parse location from parking address
        val address = booking.parkingAddress.ifBlank { booking.parkingSpotName }
        val encodedAddress = Uri.encode(address)
        
        // Open Google Maps with navigation
        val gmmIntentUri = Uri.parse("google.navigation:q=$encodedAddress&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        
        if (mapIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapIntent)
        } else {
            // Fallback to browser maps
            val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encodedAddress")
            context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open navigation", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Set reminder notification for booking expiry
 */
private fun setBookingReminder(context: Context, booking: ParkingBooking): Boolean {
    return try {
        val endTime = booking.bookingEndTime?.toDate()?.time ?: return false
        val reminderTime = endTime - (15 * 60 * 1000) // 15 minutes before
        
        if (reminderTime <= System.currentTimeMillis()) {
            Toast.makeText(context, "Booking ends in less than 15 minutes!", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // For now, just show toast - actual AlarmManager implementation would go here
        // In production, you'd use WorkManager or AlarmManager
        val remainingMinutes = ((reminderTime - System.currentTimeMillis()) / 60000).toInt()
        Toast.makeText(
            context, 
            "⏰ Reminder set! You'll be notified in ${remainingMinutes} minutes", 
            Toast.LENGTH_LONG
        ).show()
        true
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to set reminder", Toast.LENGTH_SHORT).show()
        false
    }
}
