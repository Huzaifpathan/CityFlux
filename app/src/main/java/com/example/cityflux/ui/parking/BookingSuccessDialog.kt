package com.example.cityflux.ui.parking

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.cityflux.model.ParkingBooking
import com.example.cityflux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Success dialog shown after booking is confirmed
 * Shows booking summary with quick actions
 */
@Composable
fun BookingSuccessDialog(
    booking: ParkingBooking,
    onDismiss: () -> Unit,
    onViewBooking: () -> Unit = {},
    onNavigate: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    var showAnimation by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        showAnimation = true
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Icon with Animation
                SuccessCheckmark(showAnimation = showAnimation)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Success Title
                Text(
                    text = "Booking Successful! 🎉",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Your parking slot is confirmed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Booking Summary Card
                BookingSummaryCard(booking)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // View Booking
                    QuickActionButton(
                        icon = Icons.Default.Article,
                        label = "View",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onViewBooking()
                            onDismiss()
                        }
                    )
                    
                    // Navigate
                    QuickActionButton(
                        icon = Icons.Default.Navigation,
                        label = "Navigate",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onNavigate()
                            onDismiss()
                        }
                    )
                    
                    // Share
                    QuickActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onShare()
                            onDismiss()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Animated success checkmark
 */
@Composable
private fun SuccessCheckmark(showAnimation: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (showAnimation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkmark_scale"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(AccentGreen.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            modifier = Modifier.size(48.dp),
            tint = AccentGreen
        )
    }
}

/**
 * Booking summary card with details
 */
@Composable
private fun BookingSummaryCard(booking: ParkingBooking) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LightSurfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Parking Name
            SummaryRow(
                icon = Icons.Default.LocalParking,
                label = "Location",
                value = booking.parkingSpotName
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Vehicle
            SummaryRow(
                icon = Icons.Default.DirectionsCar,
                label = "Vehicle",
                value = booking.vehicleNumber
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Duration
            SummaryRow(
                icon = Icons.Default.Schedule,
                label = "Duration",
                value = "${booking.durationHours} hours"
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Time
            SummaryRow(
                icon = Icons.Default.AccessTime,
                label = "Valid Until",
                value = formatTime(booking.bookingEndTime?.toDate()?.time ?: 0)
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Amount
            SummaryRow(
                icon = Icons.Default.Payment,
                label = "Amount Paid",
                value = "₹${booking.totalAmount.toInt()}",
                valueColor = AccentGreen
            )
        }
    }
}

/**
 * Summary row component
 */
@Composable
private fun SummaryRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.Black
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PrimaryBlue
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

/**
 * Quick action button
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryBlue
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            width = 1.5.dp
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Format timestamp to readable time
 */
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    return sdf.format(timestamp)
}

/**
 * Preview
 */
@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun BookingSuccessDialogPreview() {
    MaterialTheme {
        BookingSuccessDialog(
            booking = ParkingBooking(
                parkingSpotName = "City Center Parking",
                vehicleNumber = "MH12AB1234",
                durationHours = 3,
                totalAmount = 150.0
            ),
            onDismiss = {}
        )
    }
}
