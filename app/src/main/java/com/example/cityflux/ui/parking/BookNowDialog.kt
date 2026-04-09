package com.example.cityflux.ui.parking

import android.app.Activity
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingBreakdown
import com.example.cityflux.service.PricingService
import com.example.cityflux.service.UpiPaymentService
import com.example.cityflux.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// Color constants for BookNow dialog
private val AccentGreen = Color(0xFF10B981)
private val AccentOrange = Color(0xFFF59E0B)
private val AccentRed = Color(0xFFEF4444)
private val AccentBlue = Color(0xFF3B82F6)
private val AccentParking = Color(0xFF8B5CF6)
private val PrimaryBlue = Color(0xFF2563EB)

/**
 * Advanced Book Now Dialog
 * Complete parking booking experience with location verification,
 * real-time slot tracking, smart pricing, and instant booking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookNowDialog(
    parkingSpot: ParkingSpot,
    userLocation: Location?,
    onDismiss: () -> Unit,
    onBookingCreated: (String) -> Unit,
    onNavigateToParking: () -> Unit = {},
    viewModel: BookNowViewModel = viewModel()
) {
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current
    
    // State management
    val dialogState by viewModel.dialogState.collectAsState()
    val bookingForm by viewModel.bookingForm.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val parkingLive by viewModel.parkingLiveData.collectAsState()
    val locationStatus by viewModel.locationStatus.collectAsState()
    
    // Initialize dialog
    LaunchedEffect(parkingSpot) {
        viewModel.initializeDialog(parkingSpot, userLocation)
    }
    
    // Handle booking success
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            onBookingCreated(message)
            viewModel.clearMessages()
        }
    }

    val upiPaymentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val response = result.data?.getStringExtra("response")
            ?: result.data?.getStringExtra("txnResponse")
        val paymentResult = UpiPaymentService.parsePaymentResponse(response)
        when {
            paymentResult.isSuccess -> {
                viewModel.createBooking(
                    paymentTransactionId = paymentResult.transactionId,
                    paymentTransactionRef = paymentResult.transactionRef
                )
            }
            result.resultCode == Activity.RESULT_CANCELED || paymentResult.isCancelled -> {
                viewModel.setError("Payment cancelled. Please complete payment to confirm booking.")
            }
            else -> {
                viewModel.setError(paymentResult.message)
            }
        }
    }
    
    // Show Premium Success Dialog
    if (uiState.showSuccessDialog && uiState.successBooking != null) {
        BookingSuccessDialog(
            booking = uiState.successBooking!!,
            onDismiss = {
                viewModel.dismissSuccessDialog()
                onDismiss()
            },
            onViewBooking = {
                viewModel.dismissSuccessDialog()
                onBookingCreated(uiState.successBooking!!.id)
            },
            onNavigate = {
                viewModel.dismissSuccessDialog()
                onNavigateToParking()
            },
            onShare = { /* Share handled inside dialog */ }
        )
        return // Don't show main dialog when success dialog is showing
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = colors.cardBackground,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with back and close buttons
                DialogHeader(
                    currentStep = dialogState.currentStep,
                    onBack = { viewModel.moveToPreviousStep() },
                    onDismiss = onDismiss,
                    colors = colors
                )
                
                Spacer(Modifier.height(16.dp))
                
                // Current dialog step content
                when (dialogState.currentStep) {
                    BookingStep.LOCATION_VERIFICATION -> {
                        LocationVerificationStep(
                            parkingSpot = parkingSpot,
                            locationStatus = locationStatus,
                            onVerifyLocation = { viewModel.verifyLocation() },
                            onNavigateToParking = onNavigateToParking,
                            onSkipVerification = { viewModel.skipLocationVerification() },
                            colors = colors
                        )
                    }
                    
                    BookingStep.SLOT_SELECTION -> {
                        SlotSelectionStep(
                            parkingSpot = parkingSpot,
                            parkingLive = parkingLive,
                            selectedVehicleType = bookingForm.vehicleType,
                            onVehicleTypeSelected = { viewModel.updateVehicleType(it) },
                            onBack = { viewModel.moveToPreviousStep() },
                            onContinue = { viewModel.moveToNextStep() },
                            colors = colors
                        )
                    }
                    
                    BookingStep.DURATION_AND_PRICING -> {
                        DurationPricingStep(
                            bookingForm = bookingForm,
                            parkingSpot = parkingSpot,
                            onDurationSelected = { viewModel.updateDuration(it) },
                            onBookingTypeChanged = { viewModel.updateBookingType(it) },
                            onStartTimeChanged = { viewModel.updateStartTime(it) },
                            onBack = { viewModel.moveToPreviousStep() },
                            onContinue = { viewModel.moveToNextStep() },
                            colors = colors
                        )
                    }
                    
                    BookingStep.VEHICLE_DETAILS -> {
                        VehicleDetailsStep(
                            bookingForm = bookingForm,
                            recentVehicles = dialogState.recentVehicles,
                            onVehicleNumberChanged = { viewModel.updateVehicleNumber(it) },
                            onNotesChanged = { viewModel.updateNotes(it) },
                            onRecentVehicleSelected = { viewModel.selectRecentVehicle(it) },
                            onBack = { viewModel.moveToPreviousStep() },
                            onContinue = { viewModel.moveToNextStep() },
                            colors = colors
                        )
                    }
                    
                    BookingStep.PAYMENT -> {
                        PaymentStep(
                            bookingForm = bookingForm,
                            parkingSpot = parkingSpot,
                            uiState = uiState,
                            onPaymentMethodSelected = { viewModel.selectPaymentMethod(it) },
                            onBack = { viewModel.moveToPreviousStep() },
                            onConfirmBooking = { pricing ->
                                val bookingRef = "CF${System.currentTimeMillis()}"
                                val upiIntent = UpiPaymentService.createPaymentChooserIntent(
                                    amount = pricing.totalAmount,
                                    parkingName = parkingSpot.address,
                                    bookingId = bookingRef,
                                    vehicleNumber = bookingForm.vehicleNumber,
                                    upiId = UpiPaymentService.getUpiIdForParking(parkingSpot.id)
                                )
                                try {
                                    upiPaymentLauncher.launch(upiIntent)
                                } catch (_: Exception) {
                                    viewModel.setError("No UPI app found. Please install Google Pay, PhonePe, or Paytm.")
                                }
                            },
                            onUpdatePricing = { viewModel.updatePricing(it) },
                            colors = colors
                        )
                    }
                    
                    BookingStep.CONFIRMATION -> {
                        BookingConfirmationStep(
                            bookingId = uiState.successMessage ?: "",
                            bookingForm = bookingForm,
                            parkingSpot = parkingSpot,
                            onViewBooking = { onBookingCreated(uiState.successMessage ?: "") },
                            onCreateAnother = { viewModel.resetDialog() },
                            colors = colors
                        )
                    }
                }
                
                // Error message display
                uiState.error?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    ErrorMessage(error = error, colors = colors)
                }
                
                // Step progress indicator
                if (dialogState.currentStep != BookingStep.CONFIRMATION) {
                    Spacer(Modifier.height(20.dp))
                    StepProgressIndicator(
                        currentStep = dialogState.currentStep,
                        totalSteps = BookingStep.entries.size - 1,
                        colors = colors
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(
    currentStep: BookingStep,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    colors: CityFluxColors
) {
    val showBackButton = currentStep != BookingStep.LOCATION_VERIFICATION && 
                         currentStep != BookingStep.CONFIRMATION
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back button (shown after first step)
            if (showBackButton) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textSecondary
                    )
                }
            }
            
            Text(
                text = when (currentStep) {
                    BookingStep.LOCATION_VERIFICATION -> "Book Parking Now"
                    BookingStep.SLOT_SELECTION -> "Select Slot"
                    BookingStep.DURATION_AND_PRICING -> "Duration & Price"
                    BookingStep.VEHICLE_DETAILS -> "Vehicle Details"
                    BookingStep.PAYMENT -> "Payment"
                    BookingStep.CONFIRMATION -> "Booking Confirmed!"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
private fun LocationVerificationStep(
    parkingSpot: ParkingSpot,
    locationStatus: LocationStatus,
    onVerifyLocation: () -> Unit,
    onNavigateToParking: () -> Unit,
    onSkipVerification: () -> Unit,
    colors: CityFluxColors
) {
    Column {
        // Parking spot info
        ParkingSpotCard(parkingSpot = parkingSpot, colors = colors)
        
        Spacer(Modifier.height(16.dp))
        
        // Location status
        LocationStatusCard(
            locationStatus = locationStatus,
            onVerifyLocation = onVerifyLocation,
            onNavigateToParking = onNavigateToParking,
            colors = colors
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSkipVerification,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textSecondary
                )
            ) {
                Text("Skip Verification")
            }
            
            Button(
                onClick = when (locationStatus) {
                    is LocationStatus.AtParking -> onVerifyLocation
                    is LocationStatus.TooFar -> onNavigateToParking
                    else -> onVerifyLocation
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(
                    imageVector = when (locationStatus) {
                        is LocationStatus.AtParking -> Icons.Default.CheckCircle
                        is LocationStatus.TooFar -> Icons.Default.Navigation
                        else -> Icons.Default.LocationOn
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (locationStatus) {
                        is LocationStatus.AtParking -> "I'm Here"
                        is LocationStatus.TooFar -> "Navigate"
                        else -> "Verify Location"
                    }
                )
            }
        }
    }
}

@Composable
private fun SlotSelectionStep(
    parkingSpot: ParkingSpot,
    parkingLive: ParkingLive?,
    selectedVehicleType: VehicleType,
    onVehicleTypeSelected: (VehicleType) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    colors: CityFluxColors
) {
    Column {
        Text(
            text = "Select Vehicle Type",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Real-time availability display
        parkingLive?.let { live ->
            AvailabilityCard(
                totalSlots = parkingSpot.totalSlots,
                availableSlots = live.availableSlots,
                colors = colors
            )
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Vehicle type selection with availability
        VehicleTypeSelectionGrid(
            selectedType = selectedVehicleType,
            onTypeSelected = onVehicleTypeSelected,
            availableSlots = parkingLive?.availableSlots ?: parkingSpot.availableSlots,
            colors = colors
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(2f).height(50.dp),
                enabled = parkingLive?.availableSlots ?: 0 > 0,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Continue")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DurationPricingStep(
    bookingForm: BookingFormState,
    parkingSpot: ParkingSpot,
    onDurationSelected: (Int) -> Unit,
    onBookingTypeChanged: (BookingType) -> Unit,
    onStartTimeChanged: (Long) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    colors: CityFluxColors
) {
    // Calculate available duration options based on parking spot limits
    val minHours = (parkingSpot.minDuration / 60f).coerceAtLeast(1f).toInt()
    val maxHours = (parkingSpot.maxDuration / 60f).coerceAtLeast(1f).toInt()
    
    Column {
        Text(
            text = "Parking Duration",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        
        // Show parking type info
        if (parkingSpot.isFree) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = AccentGreen.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🎉 FREE Parking Zone - No charges!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = AccentBlue.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Paid Parking • ${parkingSpot.rateDisplayString}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Booking type selector (Now/Later/Recurring)
        BookingTypeSelector(
            selectedType = bookingForm.bookingType,
            onTypeSelected = onBookingTypeChanged,
            colors = colors
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Start time picker for BOOK_LATER
        if (bookingForm.bookingType == BookingType.BOOK_LATER) {
            StartTimePicker(
                selectedTimeMillis = bookingForm.startTime,
                onTimeSelected = onStartTimeChanged,
                colors = colors
            )
            Spacer(Modifier.height(16.dp))
        }
        
        // Duration selection with smart pricing - uses parking spot limits
        SmartDurationSelector(
            selectedHours = bookingForm.durationHours,
            vehicleType = bookingForm.vehicleType,
            parkingSpot = parkingSpot,
            onDurationSelected = onDurationSelected,
            colors = colors
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Enhanced price breakdown - uses parking spot rate
        EnhancedPriceBreakdown(
            parkingSpot = parkingSpot,
            vehicleType = bookingForm.vehicleType,
            hours = bookingForm.durationHours,
            bookingType = bookingForm.bookingType,
            colors = colors
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(2f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Continue")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StartTimePicker(
    selectedTimeMillis: Long,
    onTimeSelected: (Long) -> Unit,
    colors: CityFluxColors
) {
    val calendar = remember { Calendar.getInstance() }
    val now = remember { System.currentTimeMillis() }
    
    // Quick time selections (relative to current time)
    val quickTimes = remember {
        listOf(
            "In 1 Hour" to (now + 3600000),
            "In 2 Hours" to (now + 7200000),
            "In 4 Hours" to (now + 14400000),
            "Tomorrow" to (now + 86400000)
        )
    }
    
    Column {
        Text(
            text = "Start Time",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(Modifier.height(8.dp))
        
        // Quick time selections
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickTimes) { (label, timeMillis) ->
                Surface(
                    onClick = { onTimeSelected(timeMillis) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selectedTimeMillis == timeMillis) PrimaryBlue else colors.cardBackground,
                    border = BorderStroke(1.dp, if (selectedTimeMillis == timeMillis) PrimaryBlue else colors.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (selectedTimeMillis == timeMillis) Color.White else colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedTimeMillis == timeMillis) Color.White else colors.textPrimary,
                            fontWeight = if (selectedTimeMillis == timeMillis) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Selected time display
        calendar.timeInMillis = selectedTimeMillis
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AccentBlue.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Parking Starts",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = dateFormat.format(Date(selectedTimeMillis)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VehicleDetailsStep(
    bookingForm: BookingFormState,
    recentVehicles: List<String>,
    onVehicleNumberChanged: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onRecentVehicleSelected: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    colors: CityFluxColors
) {
    Column {
        Text(
            text = "Vehicle Details",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Recent vehicles quick select
        if (recentVehicles.isNotEmpty()) {
            Text(
                text = "Recent Vehicles",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            
            Spacer(Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentVehicles) { vehicle ->
                    RecentVehicleChip(
                        vehicleNumber = vehicle,
                        isSelected = vehicle == bookingForm.vehicleNumber,
                        onClick = { onRecentVehicleSelected(vehicle) },
                        colors = colors
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
        }
        
        // Vehicle number input with validation
        val vehicleNumberError = remember(bookingForm.vehicleNumber) {
            if (bookingForm.vehicleNumber.isEmpty()) null
            else if (!isValidVehicleNumber(bookingForm.vehicleNumber)) "Invalid format. Use: MH12AB1234"
            else null
        }
        
        // Check if vehicle number is valid for enabling continue button
        val isVehicleNumberValid = bookingForm.vehicleNumber.isNotBlank() && 
            isValidVehicleNumber(bookingForm.vehicleNumber)
        
        OutlinedTextField(
            value = bookingForm.vehicleNumber,
            onValueChange = { 
                val formatted = it.uppercase().replace(" ", "").replace("-", "")
                if (formatted.length <= 13) {
                    onVehicleNumberChanged(formatted)
                }
            },
            label = { Text("Vehicle Number") },
            placeholder = { Text("MH12AB1234") },
            supportingText = {
                if (vehicleNumberError != null) {
                    Text(vehicleNumberError, color = AccentRed)
                } else {
                    Text("Format: StateCode + Number + Letters + Number", fontSize = 11.sp)
                }
            },
            isError = vehicleNumberError != null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = when (bookingForm.vehicleType) {
                        VehicleType.TWO_WHEELER -> Icons.Default.TwoWheeler
                        VehicleType.CAR -> Icons.Default.DirectionsCar
                        else -> Icons.Default.LocalShipping
                    },
                    contentDescription = null,
                    tint = if (vehicleNumberError != null) AccentRed else PrimaryBlue
                )
            },
            trailingIcon = {
                if (bookingForm.vehicleNumber.isNotEmpty() && vehicleNumberError == null) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = colors.inputBorder,
                errorBorderColor = AccentRed
            )
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Additional notes
        OutlinedTextField(
            value = bookingForm.notes,
            onValueChange = onNotesChanged,
            label = { Text("Special Requirements (Optional)") },
            placeholder = { Text("Disabled parking, ground floor, etc.") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = colors.inputBorder
            )
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            
            Button(
                onClick = onContinue,
                modifier = Modifier.weight(2f).height(50.dp),
                enabled = isVehicleNumberValid,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Payment")
            }
        }
    }
}

@Composable
private fun PaymentStep(
    bookingForm: BookingFormState,
    parkingSpot: ParkingSpot,
    uiState: BookNowUiState,
    onPaymentMethodSelected: (PaymentMethod) -> Unit,
    onBack: () -> Unit,
    onConfirmBooking: (PricingBreakdown) -> Unit,
    onUpdatePricing: (PricingBreakdown) -> Unit,
    colors: CityFluxColors
) {
    // Use parking spot's rate for pricing
    val pricing = PricingService.calculateParkingFee(parkingSpot, bookingForm.vehicleType, bookingForm.durationHours)
    
    // Update pricing in ViewModel when entering this step
    LaunchedEffect(pricing) {
        onUpdatePricing(pricing)
    }
    
    Column {
        Text(
            text = "Confirm Booking",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Booking summary
        BookingSummaryCard(
            parkingSpot = parkingSpot,
            bookingForm = bookingForm,
            colors = colors
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Payment Amount Display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AccentGreen.copy(alpha = 0.1f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Total Amount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "₹${pricing.totalAmount.toInt()}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Including ₹${pricing.gst.toInt()} GST",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        PaymentMethodSelector(
            selectedMethod = bookingForm.paymentMethod,
            onMethodSelected = onPaymentMethodSelected,
            colors = colors
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                enabled = !uiState.isLoading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Back")
            }
            
            Button(
                onClick = { onConfirmBooking(pricing) },
                modifier = Modifier.weight(2f).height(56.dp),
                enabled = !uiState.isLoading && bookingForm.vehicleNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pay & Confirm",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingConfirmationStep(
    bookingId: String,
    bookingForm: BookingFormState,
    parkingSpot: ParkingSpot,
    onViewBooking: () -> Unit,
    onCreateAnother: () -> Unit,
    colors: CityFluxColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success animation
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "Booking Confirmed!",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Booking ID: $bookingId",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Quick booking details
        ConfirmationDetailsCard(
            parkingSpot = parkingSpot,
            bookingForm = bookingForm,
            colors = colors
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCreateAnother,
                modifier = Modifier.weight(1f)
            ) {
                Text("Book Another")
            }
            
            Button(
                onClick = onViewBooking,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("View Booking")
            }
        }
    }
}

// Helper Composables

@Composable
private fun ParkingSpotCard(
    parkingSpot: ParkingSpot,
    colors: CityFluxColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalParking,
                contentDescription = null,
                tint = AccentParking,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parkingSpot.address,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = parkingSpot.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

@Composable
private fun LocationStatusCard(
    locationStatus: LocationStatus,
    onVerifyLocation: () -> Unit,
    onNavigateToParking: () -> Unit,
    colors: CityFluxColors
) {
    val (icon, statusColor, title, description) = when (locationStatus) {
        is LocationStatus.Checking -> 
            listOf(Icons.Default.LocationSearching, colors.textSecondary, "Checking Location", "Verifying your current location...")
        is LocationStatus.AtParking -> 
            listOf(Icons.Default.LocationOn, AccentGreen, "You're Here!", "You are within ${locationStatus.distance}m of parking")
        is LocationStatus.TooFar -> 
            listOf(Icons.Default.LocationOff, AccentOrange, "Too Far", "You are ${locationStatus.distance}m away")
        is LocationStatus.NoLocation -> 
            listOf(Icons.Default.LocationDisabled, AccentRed, "Location Unavailable", "Please enable GPS for better experience")
    }
    
    val surfaceColor = statusColor as Color

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, surfaceColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon as ImageVector,
                contentDescription = null,
                tint = surfaceColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title as String,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description as String,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun AvailabilityCard(
    totalSlots: Int,
    availableSlots: Int,
    colors: CityFluxColors
) {
    val occupancy = ((totalSlots - availableSlots).toFloat() / totalSlots * 100).toInt()
    val statusColor = when {
        occupancy < 50 -> AccentGreen
        occupancy < 80 -> AccentOrange
        else -> AccentRed
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = statusColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Real-time Availability",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = CircleShape,
                    color = statusColor
                ) {
                    Text(
                        text = "LIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$availableSlots Available",
                    style = MaterialTheme.typography.headlineSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$totalSlots Total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Availability progress bar
            LinearProgressIndicator(
                progress = { (totalSlots - availableSlots).toFloat() / totalSlots },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun VehicleTypeSelectionGrid(
    selectedType: VehicleType,
    onTypeSelected: (VehicleType) -> Unit,
    availableSlots: Int,
    colors: CityFluxColors
) {
    Column {
        VehicleType.entries.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowTypes.forEach { type ->
                    VehicleTypeCard(
                        type = type,
                        isSelected = type == selectedType,
                        isAvailable = availableSlots > 0,
                        onClick = { onTypeSelected(type) },
                        colors = colors,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill empty space if odd number of items
                if (rowTypes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VehicleTypeCard(
    type: VehicleType,
    isSelected: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit,
    colors: CityFluxColors,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(90.dp).clickable(enabled = isAvailable) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = when {
            !isAvailable -> colors.surfaceVariant.copy(alpha = 0.5f)
            isSelected -> PrimaryBlue.copy(alpha = 0.1f)
            else -> colors.surfaceVariant
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = when {
                !isAvailable -> colors.cardBorder.copy(alpha = 0.5f)
                isSelected -> PrimaryBlue
                else -> colors.cardBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (type) {
                    VehicleType.TWO_WHEELER -> Icons.Default.TwoWheeler
                    VehicleType.CAR -> Icons.Default.DirectionsCar
                    VehicleType.SUV -> Icons.Default.DirectionsCar
                    VehicleType.TRUCK -> Icons.Default.LocalShipping
                    VehicleType.BUS -> Icons.Default.DirectionsBus
                },
                contentDescription = null,
                tint = when {
                    !isAvailable -> colors.textSecondary.copy(alpha = 0.5f)
                    isSelected -> PrimaryBlue
                    else -> colors.textSecondary
                },
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !isAvailable -> colors.textSecondary.copy(alpha = 0.5f)
                    isSelected -> PrimaryBlue
                    else -> colors.textSecondary
                },
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            if (!isAvailable) {
                Text(
                    text = "Full",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BookingTypeSelector(
    selectedType: BookingType,
    onTypeSelected: (BookingType) -> Unit,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BookingType.entries.forEach { type ->
            BookingTypeChip(
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
private fun BookingTypeChip(
    type: BookingType,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: CityFluxColors,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (type) {
                    BookingType.BOOK_NOW -> Icons.Default.Schedule
                    BookingType.BOOK_LATER -> Icons.Default.EventNote
                    BookingType.RECURRING -> Icons.Default.Repeat
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
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SmartDurationSelector(
    selectedHours: Int,
    vehicleType: VehicleType,
    parkingSpot: ParkingSpot,
    onDurationSelected: (Int) -> Unit,
    colors: CityFluxColors
) {
    // Filter durations based on parking spot min/max limits
    val minHours = (parkingSpot.minDuration / 60f).coerceAtLeast(0.5f)
    val maxHours = (parkingSpot.maxDuration / 60f).coerceAtLeast(1f)
    
    val allDurations = listOf(1, 2, 4, 8, 12, 24)
    val durations = allDurations.filter { 
        it >= minHours && it <= maxHours 
    }.ifEmpty { listOf(1, 2, 4) } // Default fallback
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(durations) { hours ->
            SmartDurationChip(
                hours = hours,
                isSelected = hours == selectedHours,
                vehicleType = vehicleType,
                parkingSpot = parkingSpot,
                onClick = { onDurationSelected(hours) },
                colors = colors
            )
        }
    }
}

@Composable
private fun SmartDurationChip(
    hours: Int,
    isSelected: Boolean,
    vehicleType: VehicleType,
    parkingSpot: ParkingSpot,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    // Use parking spot's rate for pricing
    val pricing = PricingService.calculateParkingFee(parkingSpot, vehicleType, hours)
    val hasDiscount = pricing.discount > 0
    
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(90.dp)
            .height(80.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (hours < 24) "${hours}h" else "All Day",
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) PrimaryBlue else colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(4.dp))
            
            // Show FREE or price
            Text(
                text = pricing.getTotalDisplayString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (pricing.isFreeParking) AccentGreen 
                       else if (isSelected) PrimaryBlue 
                       else colors.textSecondary,
                fontWeight = if (pricing.isFreeParking) FontWeight.Bold else FontWeight.Normal
            )
            
            if (hasDiscount && !pricing.isFreeParking) {
                Text(
                    text = "${pricing.getDiscountLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EnhancedPriceBreakdown(
    parkingSpot: ParkingSpot,
    vehicleType: VehicleType,
    hours: Int,
    bookingType: BookingType,
    colors: CityFluxColors
) {
    // Use parking spot's rate for pricing
    val pricing = PricingService.calculateParkingFee(parkingSpot, vehicleType, hours)
    
    // Different UI for FREE vs PAID parking
    if (pricing.isFreeParking) {
        // FREE parking breakdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AccentGreen.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "FREE PARKING",
                    style = MaterialTheme.typography.titleLarge,
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${hours} hour${if (hours > 1) "s" else ""} • No charges applicable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
    } else {
        // PAID parking breakdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = AccentParking.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, AccentParking.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Price Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (bookingType == BookingType.BOOK_LATER) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentOrange.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Advance Booking",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                PriceRow("Base (${hours}h × ₹${pricing.baseRate.toInt()})", pricing.baseAmount, colors.textSecondary)
                
                if (pricing.discount > 0) {
                    PriceRow(
                        "Discount ${pricing.getDiscountLabel()}",
                        -pricing.discount,
                        AccentGreen
                    )
                }
                
                PriceRow("GST (18%)", pricing.gst, colors.textSecondary)
                
                if (bookingType == BookingType.RECURRING) {
                    PriceRow("Weekly Discount (5%)", -pricing.totalAmount * 0.05, AccentGreen)
                }
                
                Spacer(Modifier.height(8.dp))
                Divider(color = colors.divider)
                Spacer(Modifier.height(8.dp))
                
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
}

@Composable
private fun PriceRow(
    label: String,
    amount: Double,
    textColor: Color
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
            color = textColor
        )
        Text(
            text = "₹${amount.toInt()}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentVehicleChip(
    vehicleNumber: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Text(
            text = vehicleNumber,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) PrimaryBlue else colors.textSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun BookingSummaryCard(
    parkingSpot: ParkingSpot,
    bookingForm: BookingFormState,
    colors: CityFluxColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant,
        border = BorderStroke(1.dp, colors.cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Booking Summary",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(12.dp))
            
            SummaryRow("Location", parkingSpot.address, colors)
            SummaryRow("Vehicle", "${bookingForm.vehicleNumber} (${bookingForm.vehicleType.displayName})", colors)
            SummaryRow("Duration", "${bookingForm.durationHours} hours", colors)
            
            val pricing = PricingService.calculateParkingFee(bookingForm.vehicleType, bookingForm.durationHours)
            SummaryRow("Amount", "₹${pricing.totalAmount.toInt()}", colors, isHighlighted = true)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    colors: CityFluxColors,
    isHighlighted: Boolean = false
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
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isHighlighted) AccentParking else colors.textPrimary,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PaymentMethodSelector(
    selectedMethod: PaymentMethod,
    onMethodSelected: (PaymentMethod) -> Unit,
    colors: CityFluxColors
) {
    Column {
        Text(
            text = "Payment Method",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(12.dp))
        
        PaymentMethod.entries.forEach { method ->
            PaymentMethodItem(
                method = method,
                isSelected = method == selectedMethod,
                onClick = { onMethodSelected(method) },
                colors = colors
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PaymentMethodItem(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else colors.surfaceVariant,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryBlue else colors.cardBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (method) {
                    PaymentMethod.UPI -> Icons.Default.Payment
                },
                contentDescription = null,
                tint = if (isSelected) PrimaryBlue else colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) PrimaryBlue else colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = method.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmationDetailsCard(
    parkingSpot: ParkingSpot,
    bookingForm: BookingFormState,
    colors: CityFluxColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AccentGreen.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Booking Details",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(12.dp))
            
            ConfirmationRow("Parking", parkingSpot.address, colors)
            ConfirmationRow("Vehicle", bookingForm.vehicleNumber, colors)
            ConfirmationRow("Duration", "${bookingForm.durationHours} hours", colors)
            
            val timing = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                .format(Date())
            ConfirmationRow("Start Time", timing, colors)
        }
    }
}

@Composable
private fun ConfirmationRow(
    label: String,
    value: String,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorMessage(
    error: String,
    colors: CityFluxColors
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AccentRed.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = AccentRed
            )
        }
    }
}

@Composable
private fun StepProgressIndicator(
    currentStep: BookingStep,
    totalSteps: Int,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BookingStep.entries.dropLast(1).forEachIndexed { index, step ->
            val isActive = step.ordinal <= currentStep.ordinal
            val isCompleted = step.ordinal < currentStep.ordinal
            
            Surface(
                shape = CircleShape,
                color = when {
                    isCompleted -> AccentGreen
                    isActive -> PrimaryBlue
                    else -> colors.surfaceVariant
                },
                modifier = Modifier.size(8.dp)
            ) {}
            
            if (index < totalSteps - 1) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * Validates Indian vehicle number format
 * Formats: MH12AB1234, DL01CAB1234, etc.
 * Pattern: StateCode(2-3 letters) + Number(2 digits) + Letters(1-3) + Number(4 digits)
 */
private fun isValidVehicleNumber(number: String): Boolean {
    if (number.isBlank()) return false
    
    // Indian vehicle number regex patterns
    val patterns = listOf(
        // Standard: MH12AB1234 (State + 2digit + 2letter + 4digit)
        Regex("^[A-Z]{2}\\d{2}[A-Z]{2}\\d{4}$"),
        // Old format: MH01A1234 (State + 2digit + 1letter + 4digit)
        Regex("^[A-Z]{2}\\d{2}[A-Z]\\d{4}$"),
        // New BH series: BH01AB1234 or 22BH1234AB
        Regex("^[A-Z]{2}\\d{2}[A-Z]{2}\\d{4}$"),
        Regex("^\\d{2}BH\\d{4}[A-Z]{2}$")
    )
    
    return patterns.any { it.matches(number) }
}
