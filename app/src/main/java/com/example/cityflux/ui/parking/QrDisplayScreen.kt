package com.example.cityflux.ui.parking

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.model.ParkingBooking
import com.example.cityflux.model.QrCodeGenerator
import com.example.cityflux.model.getRemainingTimeMinutes
import com.example.cityflux.model.isExpiringSoon
import com.example.cityflux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * QR Code Display Screen
 * Phase 4: Show QR code for parking entry/exit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrDisplayScreen(
    booking: ParkingBooking,
    onNavigateBack: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val qrBitmap = remember(booking.qrCodeData) {
        QrCodeGenerator.generateBrandedQrCode(booking.qrCodeData, 800)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry QR Code") },
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
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status banner
            val isExpiring = booking.isExpiringSoon()
            val remainingMinutes = booking.getRemainingTimeMinutes()
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = if (isExpiring) 
                    AccentAlerts.copy(alpha = 0.1f) 
                else 
                    AccentGreen.copy(alpha = 0.1f),
                border = BorderStroke(
                    1.dp,
                    if (isExpiring) AccentAlerts else AccentGreen
                )
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpiring) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isExpiring) AccentAlerts else AccentGreen,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(Spacing.Medium))
                    Column {
                        Text(
                            text = if (isExpiring) 
                                "Booking Expiring Soon!" 
                            else 
                                "Active Booking",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isExpiring) AccentAlerts else AccentGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${remainingMinutes / 60}h ${remainingMinutes % 60}m remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(Spacing.Large))
            
            // QR Code
            qrBitmap?.let { bitmap ->
                Surface(
                    modifier = Modifier
                        .size(300.dp)
                        .border(
                            BorderStroke(2.dp, PrimaryBlue),
                            RoundedCornerShape(CornerRadius.Large)
                        ),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    color = Color.White
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Booking QR Code",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.Medium)
                    )
                }
            } ?: run {
                CircularProgressIndicator()
            }
            
            Spacer(Modifier.height(Spacing.Large))
            
            // Instructions
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = PrimaryBlue.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Medium)
                ) {
                    Text(
                        text = "📱 How to Use",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(Spacing.Small))
                    InstructionItem("Show this QR code at parking entry gate")
                    InstructionItem("Security will scan and verify your booking")
                    InstructionItem("Show again at exit to complete your booking")
                    InstructionItem("Keep your phone charged for smooth exit")
                }
            }
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Booking details
            BookingDetailsCard(booking, colors)
            
            Spacer(Modifier.height(Spacing.Large))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                OutlinedButton(
                    onClick = { /* Share QR */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.Small))
                    Text("Share")
                }
                
                Button(
                    onClick = { /* Download QR */ },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Icon(Icons.Default.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.Small))
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun InstructionItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryBlue,
            modifier = Modifier.padding(end = Spacing.Small)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.cityFluxColors.textSecondary
        )
    }
}

@Composable
private fun BookingDetailsCard(
    booking: ParkingBooking,
    colors: CityFluxColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = colors.cardBackground,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium)
        ) {
            Text(
                text = "Booking Details",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            DetailRow("Parking Spot", booking.parkingSpotName, colors)
            DetailRow("Vehicle", booking.vehicleNumber, colors)
            DetailRow("Type", booking.vehicleType.displayName, colors)
            DetailRow(
                "Valid Until",
                booking.bookingEndTime?.toDate()?.let {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it)
                } ?: "N/A",
                colors
            )
            DetailRow("Amount Paid", "₹${booking.amount.toInt()}", colors)
            DetailRow("Booking ID", booking.id.take(12) + "...", colors)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
