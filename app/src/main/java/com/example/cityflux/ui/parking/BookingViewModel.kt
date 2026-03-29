package com.example.cityflux.ui.parking

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.data.BookingRepository
import com.example.cityflux.model.BookingStatus
import com.example.cityflux.model.ParkingBooking
import com.example.cityflux.model.VehicleType
import com.example.cityflux.model.isActive
import com.example.cityflux.service.PricingService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for booking operations
 * Phase 4: Manages booking creation, history, and status
 */
class BookingViewModel : ViewModel() {
    
    private val repository = BookingRepository()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "BookingViewModel"
    }
    
    // UI State
    data class BookingUiState(
        val isLoading: Boolean = false,
        val activeBookings: List<ParkingBooking> = emptyList(),
        val pastBookings: List<ParkingBooking> = emptyList(),
        val selectedBooking: ParkingBooking? = null,
        val error: String? = null,
        val successMessage: String? = null
    )
    
    // Booking form state
    data class BookingFormState(
        val parkingSpotId: String = "",
        val parkingSpotName: String = "",
        val parkingAddress: String = "",
        val vehicleNumber: String = "",
        val vehicleType: VehicleType = VehicleType.CAR,
        val durationHours: Int = 2,
        val startTime: Date = Date(),
        val notes: String = ""
    )
    
    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()
    
    private val _bookingForm = MutableStateFlow(BookingFormState())
    val bookingForm: StateFlow<BookingFormState> = _bookingForm.asStateFlow()
    
    init {
        observeBookings()
    }
    
    // ═══════════════════════════════════════════════════════
    // Observe Bookings
    // ═══════════════════════════════════════════════════════
    
    private fun observeBookings() {
        viewModelScope.launch {
            repository.observeUserBookings()
                .catch { e: Throwable ->
                    Log.e(TAG, "Error observing bookings", e)
                    _uiState.update { it.copy(error = "Failed to load bookings") }
                }
                .collect { result: Result<List<ParkingBooking>> ->
                    result.onSuccess { bookings: List<ParkingBooking> ->
                        val active = bookings.filter { booking -> booking.status.isActive() }
                        val past = bookings.filter { booking -> !booking.status.isActive() }
                        
                        _uiState.update {
                            it.copy(
                                activeBookings = active,
                                pastBookings = past,
                                isLoading = false
                            )
                        }
                    }.onFailure { e: Throwable ->
                        _uiState.update { it.copy(error = e.message ?: "Unknown error") }
                    }
                }
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // Create Booking
    // ═══════════════════════════════════════════════════════
    
    fun createBooking() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val form = _bookingForm.value
            val user = auth.currentUser
            
            if (user == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Please login to book parking"
                    )
                }
                return@launch
            }
            
            // Calculate pricing
            val pricing = PricingService.calculateParkingFee(
                vehicleType = form.vehicleType,
                durationHours = form.durationHours
            )
            
            // Calculate end time
            val endTime = Calendar.getInstance().apply {
                time = form.startTime
                add(Calendar.HOUR_OF_DAY, form.durationHours)
            }.time
            
            // Create booking
            val booking = ParkingBooking(
                userId = user.uid,
                userName = user.displayName ?: "User",
                userPhone = user.phoneNumber ?: "",
                parkingSpotId = form.parkingSpotId,
                parkingSpotName = form.parkingSpotName,
                parkingAddress = form.parkingAddress,
                vehicleNumber = form.vehicleNumber,
                vehicleType = form.vehicleType,
                bookingStartTime = Timestamp(form.startTime),
                bookingEndTime = Timestamp(endTime),
                durationHours = form.durationHours,
                amount = pricing.totalAmount,
                notes = form.notes.ifBlank { null },
                status = BookingStatus.PENDING
            )
            
            try {
                val bookingId = repository.createBooking(booking)
                Log.d(TAG, "Booking created successfully: $bookingId")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Booking created! ID: $bookingId"
                    )
                }
                resetForm()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create booking", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to create booking"
                    )
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // Cancel Booking
    // ═══════════════════════════════════════════════════════
    
    fun cancelBooking(bookingId: String, reason: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            repository.cancelBooking(bookingId)
                .onSuccess {
                    Log.d(TAG, "Booking cancelled: $bookingId")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Booking cancelled successfully"
                        )
                    }
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "Failed to cancel booking", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to cancel booking"
                        )
                    }
                }
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // Extend Booking
    // ═══════════════════════════════════════════════════════
    
    fun extendBooking(bookingId: String, additionalHours: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            // Get current booking to determine vehicle type
            repository.getBooking(bookingId)
                .onSuccess { booking: ParkingBooking ->
                    val pricing = PricingService.calculateParkingFee(
                        vehicleType = booking.vehicleType,
                        durationHours = additionalHours
                    )
                    
                    repository.extendBooking(
                        bookingId = bookingId,
                        additionalHours = additionalHours
                    )
                        .onSuccess {
                            Log.d(TAG, "Booking extended: $bookingId")
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    successMessage = "Booking extended by $additionalHours hours"
                                )
                            }
                        }
                        .onFailure { e: Throwable ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.message ?: "Failed to extend booking"
                                )
                            }
                        }
                }
                .onFailure { e: Throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to fetch booking"
                        )
                    }
                }
        }
    }
    
    // ═══════════════════════════════════════════════════════
    // Form Management
    // ═══════════════════════════════════════════════════════
    
    fun initBookingForm(
        spotId: String,
        spotName: String,
        spotAddress: String
    ) {
        _bookingForm.update {
            it.copy(
                parkingSpotId = spotId,
                parkingSpotName = spotName,
                parkingAddress = spotAddress
            )
        }
    }
    
    fun updateVehicleNumber(number: String) {
        _bookingForm.update { it.copy(vehicleNumber = number) }
    }
    
    fun updateVehicleType(type: VehicleType) {
        _bookingForm.update { it.copy(vehicleType = type) }
    }
    
    fun updateDuration(hours: Int) {
        _bookingForm.update { it.copy(durationHours = hours) }
    }
    
    fun updateStartTime(time: Date) {
        _bookingForm.update { it.copy(startTime = time) }
    }
    
    fun updateNotes(notes: String) {
        _bookingForm.update { it.copy(notes = notes) }
    }
    
    fun resetForm() {
        _bookingForm.value = BookingFormState()
    }
    
    // ═══════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════
    
    fun calculatePrice(vehicleType: VehicleType, hours: Int): Double {
        return PricingService.calculateParkingFee(vehicleType, hours).totalAmount
    }
    
    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
    
    fun selectBooking(booking: ParkingBooking?) {
        _uiState.update { it.copy(selectedBooking = booking) }
    }
    
    fun loadBookingHistory() {
        viewModelScope.launch {
            repository.getBookingHistory()
                .onSuccess { bookings: List<ParkingBooking> ->
                    _uiState.update { it.copy(pastBookings = bookings) }
                }
                .onFailure { e: Throwable ->
                    Log.e(TAG, "Failed to load history", e)
                }
        }
    }
}
