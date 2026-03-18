package com.example.cityflux.ui.parking

import android.graphics.Bitmap
import androidx.compose.animation.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingService
import com.example.cityflux.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Booking Bottom Sheet
 * Phase 4: Create new parking booking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingBottomSheet(
    parkingSpot: ParkingSpot,
    onDismiss: () -> Unit,
    onBookingCreated: (String) -> Unit,
    viewModel: BookingViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val formState by viewModel.bookingForm.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    // Initialize form with parking spot details
    LaunchedEffect(parkingSpot) {
        viewModel.initBookingForm(
            spotId = parkingSpot.id,
            spotName = parkingSpot.name,
            spotAddress = parkingSpot.address
        )
    }
    
    // Handle success
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            onBookingCreated(message)
            viewModel.clearMessages()
        }
    }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Text(
                text = "Book Parking",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(Spacing.Small))
            
            // Parking spot info
            ParkingSpotInfoCard(parkingSpot, colors)
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Vehicle details
            Text(
                text = "Vehicle Details",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(Modifier.height(Spacing.Small))
            
            // Vehicle number input
            OutlinedTextField(
                value = formState.vehicleNumber,
                onValueChange = { viewModel.updateVehicleNumber(it.uppercase()) },
                label = { Text("Vehicle Number") },
                placeholder = { Text("MH-12-AB-1234") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = colors.inputBorder
                )
            )
            
            Spacer(Modifier.height(Spacing.Small))
            
            // Vehicle type selection
            Text(
                text = "Vehicle Type",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            
            Spacer(Modifier.height(Spacing.ExtraSmall))
            
            VehicleTypeSelector(
                selectedType = formState.vehicleType,
                onTypeSelected = { viewModel.updateVehicleType(it) },
                colors = colors
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Duration selection
            Text(
                text = "Parking Duration",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(Modifier.height(Spacing.Small))
            
            DurationSelector(
                selectedHours = formState.durationHours,
                onDurationSelected = { viewModel.updateDuration(it) },
                vehicleType = formState.vehicleType,
                colors = colors
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Price breakdown
            PriceBreakdownCard(
                vehicleType = formState.vehicleType,
                hours = formState.durationHours,
                colors = colors
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Notes (optional)
            OutlinedTextField(
                value = formState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Additional Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = colors.inputBorder
                )
            )
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // Error message
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = AccentRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = Spacing.Small)
                )
            }
            
            // Book button
            Button(
                onClick = { viewModel.createBooking() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = formState.vehicleNumber.isNotBlank() && !uiState.isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = colors.surfaceVariant
                ),
                shape = RoundedCornerShape(CornerRadius.Medium)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        text = "Confirm Booking",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(Spacing.Medium))
        }
    }
}

@Composable
private fun ParkingSpotInfoCard(spot: ParkingSpot, colors: CityFluxColors) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.cardBorder)
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
                    text = spot.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = spot.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun VehicleTypeSelector(
    selectedType: VehicleType,
    onTypeSelected: (VehicleType) -> Unit,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        VehicleType.entries.take(3).forEach { type ->
            VehicleTypeChip(
                type = type,
                isSelected = type == selectedType,
                onClick = { onTypeSelected(type) },
                colors = colors,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VehicleTypeChip(
    type: VehicleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: CityFluxColors,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (type) {
                    VehicleType.TWO_WHEELER -> Icons.Default.TwoWheeler
                    VehicleType.CAR -> Icons.Default.DirectionsCar
                    VehicleType.SUV -> Icons.Default.DirectionsCar
                    else -> Icons.Default.LocalShipping
                },
                contentDescription = null,
                tint = if (isSelected) PrimaryBlue else colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) PrimaryBlue else colors.textSecondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun DurationSelector(
    selectedHours: Int,
    onDurationSelected: (Int) -> Unit,
    vehicleType: VehicleType,
    colors: CityFluxColors
) {
    val durations = listOf(1, 2, 4, 8, 24)
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            durations.forEach { hours ->
                DurationChip(
                    hours = hours,
                    isSelected = hours == selectedHours,
                    onClick = { onDurationSelected(hours) },
                    price = PricingService.calculateParkingFee(vehicleType, hours).totalAmount,
                    colors = colors,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DurationChip(
    hours: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    price: Double,
    colors: CityFluxColors,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$hours hr",
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) PrimaryBlue else colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "₹${price.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) PrimaryBlue else colors.textSecondary
            )
        }
    }
}

@Composable
private fun PriceBreakdownCard(
    vehicleType: VehicleType,
    hours: Int,
    colors: CityFluxColors
) {
    val pricing = PricingService.calculateParkingFee(vehicleType, hours)
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = AccentParking.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, AccentParking.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium)
        ) {
            Text(
                text = "Price Breakdown",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(Spacing.Small))
            
            PriceRow("Base (${pricing.durationHours}h × ₹${pricing.baseRate.toInt()})", pricing.baseAmount, colors)
            
            if (pricing.discount > 0) {
                PriceRow(
                    "Discount ${pricing.getDiscountLabel()}",
                    -pricing.discount,
                    colors,
                    color = AccentSuccess
                )
            }
            
            PriceRow("GST (18%)", pricing.gst, colors)
            
            Divider(
                modifier = Modifier.padding(vertical = Spacing.Small),
                color = colors.divider
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "₹${pricing.totalAmount.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AccentParking,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PriceRow(
    label: String,
    amount: Double,
    colors: CityFluxColors,
    color: Color = colors.textSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Text(
            text = "₹${amount.toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
