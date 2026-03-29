package com.example.cityflux.ui.parking

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.data.BookingNotificationRepository
import com.example.cityflux.data.BookingRepository
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingBreakdown
import com.example.cityflux.service.PricingService
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ViewModel for BookNowDialog
 * Manages complex booking flow with location verification, 
 * real-time slot tracking, and instant booking
 */
class BookNowViewModel(
    private val bookingRepository: BookingRepository = BookingRepository(),
    private val notificationRepository: BookingNotificationRepository = BookingNotificationRepository()
) : ViewModel() {
    
    private val _dialogState = MutableStateFlow(BookNowDialogState())
    val dialogState: StateFlow<BookNowDialogState> = _dialogState.asStateFlow()
    
    private val _bookingForm = MutableStateFlow(BookingFormState())
    val bookingForm: StateFlow<BookingFormState> = _bookingForm.asStateFlow()
    
    private val _uiState = MutableStateFlow(BookNowUiState())
    val uiState: StateFlow<BookNowUiState> = _uiState.asStateFlow()
    
    private val _parkingLiveData = MutableStateFlow<ParkingLive?>(null)
    val parkingLiveData: StateFlow<ParkingLive?> = _parkingLiveData.asStateFlow()
    
    private val _locationStatus = MutableStateFlow<LocationStatus>(LocationStatus.Checking)
    val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()
    
    private val database = Firebase.database
    
    /**
     * Initialize dialog with parking spot and user location
     * Resets state if a different parking spot is selected
     */
    fun initializeDialog(parkingSpot: ParkingSpot, userLocation: Location?) {
        // Check if this is a different parking spot - reset if so
        val currentParkingId = _bookingForm.value.parkingSpotId
        if (currentParkingId.isNotEmpty() && currentParkingId != parkingSpot.id) {
            // Different parking selected - reset everything
            resetDialog()
        }
        
        viewModelScope.launch {
            // Initialize form with spot details
            _bookingForm.value = _bookingForm.value.copy(
                parkingSpotId = parkingSpot.id,
                parkingSpotName = parkingSpot.address, // Using address as name
                parkingSpotAddress = parkingSpot.address
            )
            
            // Load recent vehicles for user
            loadRecentVehicles()
            
            // Start real-time slot monitoring
            startSlotMonitoring(parkingSpot.id)
            
            // Check location status
            checkLocationStatus(parkingSpot, userLocation)
        }
    }
    
    /**
     * Load user's recent vehicles for quick selection
     */
    private suspend fun loadRecentVehicles() {
        try {
            val recentBookings = bookingRepository.getUserBookings(limit = 5)
            val recentVehicles = recentBookings
                .map { it.vehicleNumber }
                .distinct()
                .take(3)
            
            _dialogState.value = _dialogState.value.copy(
                recentVehicles = recentVehicles
            )
        } catch (e: Exception) {
            // Handle error silently for recent vehicles
        }
    }
    
    /**
     * Start monitoring real-time slot availability
     */
    private fun startSlotMonitoring(parkingId: String) {
        val parkingLiveRef = database.reference.child("parking_live").child(parkingId)
        
        parkingLiveRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val parkingLive = snapshot.getValue(ParkingLive::class.java)
                _parkingLiveData.value = parkingLive
            }
            
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error
            }
        })
    }
    
    /**
     * Check user's proximity to parking location
     */
    private fun checkLocationStatus(parkingSpot: ParkingSpot, userLocation: Location?) {
        if (userLocation == null) {
            _locationStatus.value = LocationStatus.NoLocation
            return
        }
        
        val parkingLocation = Location("parking").apply {
            latitude = parkingSpot.location?.latitude ?: 0.0
            longitude = parkingSpot.location?.longitude ?: 0.0
        }
        
        val distance = userLocation.distanceTo(parkingLocation).roundToInt()
        
        _locationStatus.value = when {
            distance <= 50 -> LocationStatus.AtParking(distance)
            distance <= 1000 -> LocationStatus.TooFar(distance)
            else -> LocationStatus.TooFar(distance)
        }
    }
    
    /**
     * Verify user location manually
     */
    fun verifyLocation() {
        _locationStatus.value = LocationStatus.Checking
        
        viewModelScope.launch {
            // Simulate location verification
            kotlinx.coroutines.delay(2000)
            
            // For demo, assume verification successful
            _locationStatus.value = LocationStatus.AtParking(25)
            moveToNextStep()
        }
    }
    
    /**
     * Skip location verification
     */
    fun skipLocationVerification() {
        moveToNextStep()
    }
    
    /**
     * Move to next step in booking flow
     */
    fun moveToNextStep() {
        val currentStep = _dialogState.value.currentStep
        val nextStep = when (currentStep) {
            BookingStep.LOCATION_VERIFICATION -> BookingStep.SLOT_SELECTION
            BookingStep.SLOT_SELECTION -> BookingStep.DURATION_AND_PRICING
            BookingStep.DURATION_AND_PRICING -> BookingStep.VEHICLE_DETAILS
            BookingStep.VEHICLE_DETAILS -> BookingStep.PAYMENT
            BookingStep.PAYMENT -> BookingStep.CONFIRMATION
            BookingStep.CONFIRMATION -> BookingStep.CONFIRMATION
        }
        
        _dialogState.value = _dialogState.value.copy(currentStep = nextStep)
    }
    
    /**
     * Move to previous step in booking flow
     */
    fun moveToPreviousStep() {
        val currentStep = _dialogState.value.currentStep
        val previousStep = when (currentStep) {
            BookingStep.LOCATION_VERIFICATION -> BookingStep.LOCATION_VERIFICATION
            BookingStep.SLOT_SELECTION -> BookingStep.LOCATION_VERIFICATION
            BookingStep.DURATION_AND_PRICING -> BookingStep.SLOT_SELECTION
            BookingStep.VEHICLE_DETAILS -> BookingStep.DURATION_AND_PRICING
            BookingStep.PAYMENT -> BookingStep.VEHICLE_DETAILS
            BookingStep.CONFIRMATION -> BookingStep.PAYMENT
        }
        
        _dialogState.value = _dialogState.value.copy(currentStep = previousStep)
    }
    
    /**
     * Check if back navigation is possible
     */
    fun canGoBack(): Boolean {
        return _dialogState.value.currentStep != BookingStep.LOCATION_VERIFICATION
    }
    
    /**
     * Update vehicle type selection
     */
    fun updateVehicleType(vehicleType: VehicleType) {
        _bookingForm.value = _bookingForm.value.copy(vehicleType = vehicleType)
    }
    
    /**
     * Update parking duration
     */
    fun updateDuration(hours: Int) {
        _bookingForm.value = _bookingForm.value.copy(durationHours = hours)
    }
    
    /**
     * Update booking type (Now/Later/Recurring)
     */
    fun updateBookingType(bookingType: BookingType) {
        _bookingForm.value = _bookingForm.value.copy(bookingType = bookingType)
    }
    
    /**
     * Update vehicle number
     */
    fun updateVehicleNumber(vehicleNumber: String) {
        _bookingForm.value = _bookingForm.value.copy(vehicleNumber = vehicleNumber)
    }
    
    /**
     * Update additional notes
     */
    fun updateNotes(notes: String) {
        _bookingForm.value = _bookingForm.value.copy(notes = notes)
    }
    
    /**
     * Select recent vehicle
     */
    fun selectRecentVehicle(vehicleNumber: String) {
        _bookingForm.value = _bookingForm.value.copy(vehicleNumber = vehicleNumber)
    }
    
    /**
     * Select payment method
     */
    fun selectPaymentMethod(method: PaymentMethod) {
        _bookingForm.value = _bookingForm.value.copy(paymentMethod = method)
    }
    
    /**
     * Create parking booking
     */
    fun createBooking() {
        val form = _bookingForm.value
        val pricing = _uiState.value.pricingBreakdown
        
        if (form.vehicleNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter vehicle number")
            return
        }
        
        if (pricing == null) {
            _uiState.value = _uiState.value.copy(error = "Pricing information not available")
            return
        }
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                // Check slot availability again
                val currentSlots = _parkingLiveData.value?.availableSlots ?: 0
                if (currentSlots <= 0) {
                    throw Exception("No slots available. Please try another parking spot.")
                }
                
                // Calculate booking times
                val startTime = com.google.firebase.Timestamp.now()
                val endTimeMillis = System.currentTimeMillis() + (form.durationHours * 3600 * 1000)
                val endTime = com.google.firebase.Timestamp(endTimeMillis / 1000, 0)
                
                // Create booking with all details
                val booking = ParkingBooking(
                    parkingSpotId = form.parkingSpotId,
                    parkingSpotName = form.parkingSpotName,
                    parkingAddress = form.parkingSpotAddress,
                    vehicleNumber = form.vehicleNumber,
                    vehicleType = form.vehicleType,
                    durationHours = form.durationHours,
                    bookingStartTime = startTime,
                    bookingEndTime = endTime,
                    status = BookingStatus.CONFIRMED,
                    paymentStatus = PaymentStatus.COMPLETED,
                    baseAmount = pricing.baseAmount,
                    gstAmount = pricing.gst,
                    totalAmount = pricing.totalAmount,
                    amount = pricing.totalAmount,
                    isPaid = true,
                    paymentMethod = form.paymentMethod.name,
                    paymentTimestamp = com.google.firebase.Timestamp.now(),
                    notes = form.notes
                )
                
                // Create booking in Firestore
                val bookingId = bookingRepository.createBooking(booking)
                
                // Update booking with generated ID
                val confirmedBooking = booking.copy(id = bookingId)
                
                // Create booking notification
                notificationRepository.createBookingConfirmedNotification(confirmedBooking)
                
                // Update slot availability in real-time
                updateSlotAvailability(form.parkingSpotId, -1)
                
                // Show success dialog
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    showSuccessDialog = true,
                    successBooking = confirmedBooking
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to create booking"
                )
            }
        }
    }
    
    /**
     * Update slot availability in real-time database
     */
    private suspend fun updateSlotAvailability(parkingId: String, change: Int) {
        try {
            val parkingLiveRef = database.reference.child("parking_live").child(parkingId)
            val currentSlots = _parkingLiveData.value?.availableSlots ?: 0
            val newSlots = (currentSlots + change).coerceAtLeast(0)
            
            parkingLiveRef.child("availableSlots").setValue(newSlots)
        } catch (e: Exception) {
            // Log error but don't fail booking
        }
    }
    
    /**
     * Reset dialog to initial state
     */
    fun resetDialog() {
        _dialogState.value = BookNowDialogState()
        _bookingForm.value = BookingFormState()
        _uiState.value = BookNowUiState()
    }
    
    /**
     * Clear error and success messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
    
    /**
     * Dismiss success dialog
     */
    fun dismissSuccessDialog() {
        _uiState.value = _uiState.value.copy(showSuccessDialog = false, successBooking = null)
        resetDialog()
    }
    
    /**
     * Update pricing breakdown when duration changes
     */
    fun updatePricing(pricing: PricingBreakdown) {
        _uiState.value = _uiState.value.copy(pricingBreakdown = pricing)
    }
}

/**
 * Dialog state management
 */
data class BookNowDialogState(
    val currentStep: BookingStep = BookingStep.LOCATION_VERIFICATION,
    val recentVehicles: List<String> = emptyList()
)

/**
 * Booking form state
 */
data class BookingFormState(
    val parkingSpotId: String = "",
    val parkingSpotName: String = "",
    val parkingSpotAddress: String = "",
    val vehicleNumber: String = "",
    val vehicleType: VehicleType = VehicleType.CAR,
    val durationHours: Int = 2,
    val bookingType: BookingType = BookingType.BOOK_NOW,
    val notes: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.UPI
)

/**
 * UI state for loading and messages
 */
data class BookNowUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val pricingBreakdown: PricingBreakdown? = null,
    val showSuccessDialog: Boolean = false,
    val successBooking: ParkingBooking? = null
)

/**
 * Booking flow steps
 */
enum class BookingStep {
    LOCATION_VERIFICATION,
    SLOT_SELECTION,
    DURATION_AND_PRICING,
    VEHICLE_DETAILS,
    PAYMENT,
    CONFIRMATION
}

/**
 * Location verification status
 */
sealed class LocationStatus {
    object Checking : LocationStatus()
    data class AtParking(val distance: Int) : LocationStatus()
    data class TooFar(val distance: Int) : LocationStatus()
    object NoLocation : LocationStatus()
}

/**
 * Booking types
 */
enum class BookingType(val displayName: String) {
    BOOK_NOW("Book Now"),
    BOOK_LATER("Book Later"),
    RECURRING("Recurring")
}

/**
 * Payment methods
 */
enum class PaymentMethod(val displayName: String, val description: String) {
    UPI("UPI Payment", "Pay using UPI apps"),
    CARD("Credit/Debit Card", "Visa, Mastercard, RuPay"),
    WALLET("Digital Wallet", "Paytm, PhonePe, etc."),
    NET_BANKING("Net Banking", "Direct bank transfer")
}