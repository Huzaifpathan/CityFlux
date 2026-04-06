package com.example.cityflux.data

import android.util.Log
import com.example.cityflux.model.*
import com.example.cityflux.service.PricingService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Enhanced Repository for parking booking operations
 * Features: Real-time slot management, advanced booking, payment integration
 */
class BookingRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val realtimeDb = Firebase.database
    private val auth = FirebaseAuth.getInstance()
    
    // Event to notify when a new booking is created
    private val _bookingCreatedEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1)
    val bookingCreatedEvent: kotlinx.coroutines.flow.SharedFlow<String> = _bookingCreatedEvent
    
    // Simple counter to track booking changes
    private val _lastBookingTimestamp = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val lastBookingTimestamp: kotlinx.coroutines.flow.StateFlow<Long> = _lastBookingTimestamp
    
    companion object {
        private const val TAG = "BookingRepository"
        private const val BOOKINGS_COLLECTION = "bookings"
        private const val PARKING_LIVE_PATH = "parking_live"
        
        // Singleton instance for shared event
        @Volatile
        private var INSTANCE: BookingRepository? = null
        
        fun getInstance(): BookingRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookingRepository().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Create a new parking booking with real-time slot management
     */
    suspend fun createBooking(booking: ParkingBooking): String {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        
        try {
            // Check slot availability first
            val parkingLive = getParkingLiveData(booking.parkingSpotId)
            
            // Check if slots are available (uses general slots if vehicle-specific not configured)
            if (!parkingLive.isAvailableForType(booking.vehicleType)) {
                throw Exception("No available slots at this parking location")
            }
            
            // Generate unique QR code and booking ID
            val bookingId = generateBookingId()
            val qrData = generateQrCodeData(booking, bookingId)
            
            // Create booking with complete data
            // Preserve start/end times if already set by caller, otherwise calculate
            val completeBooking = booking.copy(
                id = bookingId,
                userId = userId,
                qrCodeData = qrData,
                status = BookingStatus.CONFIRMED,
                bookingCreatedAt = Timestamp.now(),
                bookingStartTime = booking.bookingStartTime ?: calculateStartTime(booking),
                bookingEndTime = booking.bookingEndTime ?: calculateEndTime(booking)
            )
            
            // Atomic operation: Create booking + Update slots
            val batch = firestore.batch()
            
            // Add booking to firestore
            val bookingRef = firestore.collection(BOOKINGS_COLLECTION).document(bookingId)
            batch.set(bookingRef, completeBooking)
            
            // Commit firestore transaction
            batch.commit().await()
            
            // Update real-time slot availability
            updateSlotAvailability(
                parkingId = booking.parkingSpotId,
                vehicleType = booking.vehicleType,
                change = -1
            )
            
            // Emit event that booking was created
            _bookingCreatedEvent.emit(bookingId)
            
            // Update timestamp to trigger UI refresh
            _lastBookingTimestamp.value = System.currentTimeMillis()
            
            // Log successful booking
            Log.d(TAG, "Booking created successfully: $bookingId")
            
            return bookingId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating booking: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Update slot availability in real-time database
     */
    private suspend fun updateSlotAvailability(
        parkingId: String,
        vehicleType: VehicleType,
        change: Int
    ) {
        try {
            val parkingLiveRef = realtimeDb.reference
                .child(PARKING_LIVE_PATH)
                .child(parkingId)
            
            // Update total available slots
            parkingLiveRef.child("availableSlots").get().await().value?.let { current ->
                val currentSlots = (current as? Long)?.toInt() ?: 0
                val newSlots = (currentSlots + change).coerceAtLeast(0)
                parkingLiveRef.child("availableSlots").setValue(newSlots).await()
            }
            
            // Update vehicle type specific slots
            val typeRef = parkingLiveRef.child("slotsByType").child(vehicleType.name)
            typeRef.child("available").get().await().value?.let { current ->
                val currentTypeSlots = (current as? Long)?.toInt() ?: 0
                val newTypeSlots = (currentTypeSlots + change).coerceAtLeast(0)
                typeRef.child("available").setValue(newTypeSlots).await()
            }
            
            // Update last modified timestamp
            parkingLiveRef.child("lastUpdated").setValue(System.currentTimeMillis()).await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating slot availability", e)
            // Don't fail the booking for slot update errors
        }
    }
    
    /**
     * Get real-time parking availability data
     */
    private suspend fun getParkingLiveData(parkingId: String): ParkingLive {
        return try {
            val snapshot = realtimeDb.reference
                .child(PARKING_LIVE_PATH)
                .child(parkingId)
                .get()
                .await()
            
            snapshot.getValue(ParkingLive::class.java) ?: ParkingLive()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting parking live data", e)
            ParkingLive() // Return empty data on error
        }
    }
    
    /**
     * Get user's booking history with filtering
     */
    suspend fun getUserBookings(
        status: BookingStatus? = null,
        limit: Int = 50
    ): List<ParkingBooking> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            var query: Query = firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("bookingCreatedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            
            // Filter by status if specified
            status?.let {
                query = query.whereEqualTo("status", it.name)
            }
            
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user bookings", e)
            emptyList()
        }
    }
    
    /**
     * Observe real-time booking updates
     */
    fun observeBooking(bookingId: String): Flow<ParkingBooking?> = callbackFlow {
        val listener = firestore.collection(BOOKINGS_COLLECTION)
            .document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val booking = snapshot?.toObject(ParkingBooking::class.java)?.copy(
                    id = snapshot.id
                )
                trySend(booking)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get booking by ID
     */
    private suspend fun getBookingById(bookingId: String): ParkingBooking? {
        return try {
            val doc = firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .get()
                .await()
            
            doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting booking by ID", e)
            null
        }
    }
    
    // Helper methods
    
    private fun generateBookingId(): String {
        return "BK${System.currentTimeMillis()}${(1000..9999).random()}"
    }
    
    private fun generateQrCodeData(booking: ParkingBooking, bookingId: String): String {
        return "CITYFLUX:$bookingId:${booking.parkingSpotId}:${System.currentTimeMillis()}"
    }
    
    private fun calculateStartTime(booking: ParkingBooking): Timestamp {
        // For immediate bookings, start time is now
        // For future bookings, would use scheduled time
        return Timestamp.now()
    }
    
    private fun calculateEndTime(booking: ParkingBooking): Timestamp {
        val startTime = calculateStartTime(booking)
        val endTime = Date(startTime.toDate().time + (booking.durationHours * 60 * 60 * 1000))
        return Timestamp(endTime)
    }
    
    /**
     * Observe user bookings in real-time
     */
    fun observeUserBookings(): Flow<Result<List<ParkingBooking>>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(Result.success(emptyList()))
            awaitClose { }
            return@callbackFlow
        }
        
        val listener = firestore.collection(BOOKINGS_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("bookingCreatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                
                val bookings = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(Result.success(bookings))
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Cancel a booking
     */
    suspend fun cancelBooking(bookingId: String): Result<Unit> {
        return try {
            val booking = getBookingById(bookingId)
                ?: return Result.failure(Exception("Booking not found"))
            
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update("status", BookingStatus.CANCELLED.name)
                .await()
            
            // Restore slot availability
            updateSlotAvailability(
                parkingId = booking.parkingSpotId,
                vehicleType = booking.vehicleType,
                change = 1
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get booking by ID
     */
    suspend fun getBooking(bookingId: String): Result<ParkingBooking> {
        return try {
            val booking = getBookingById(bookingId)
                ?: return Result.failure(Exception("Booking not found"))
            Result.success(booking)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fetch user bookings directly from Firestore (for instant refresh)
     */
    suspend fun fetchUserBookings(): Result<List<ParkingBooking>> {
        val userId = auth.currentUser?.uid
            ?: return Result.success(emptyList())
        
        return try {
            // Try with orderBy first, fallback to simple query if index missing
            val snapshot = try {
                firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("bookingCreatedAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
            } catch (e: Exception) {
                // Fallback: simple query without ordering (if composite index missing)
                Log.w(TAG, "Composite index may be missing, using simple query", e)
                firestore.collection(BOOKINGS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
            }
            
            val bookings = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.bookingCreatedAt?.toDate() }
            
            Log.d(TAG, "fetchUserBookings() returned ${bookings.size} bookings")
            Result.success(bookings)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user bookings", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extend booking duration
     */
    suspend fun extendBooking(bookingId: String, additionalHours: Int): Result<Unit> {
        return try {
            val booking = getBookingById(bookingId)
                ?: return Result.failure(Exception("Booking not found"))
            
            val newEndTime = Date(
                booking.bookingEndTime?.toDate()?.time 
                    ?: System.currentTimeMillis() + (additionalHours * 60 * 60 * 1000)
            )
            
            // Calculate additional cost
            val additionalCost = (booking.totalAmount / booking.durationHours) * additionalHours
            val newTotalAmount = booking.totalAmount + additionalCost
            
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update(
                    mapOf(
                        "durationHours" to booking.durationHours + additionalHours,
                        "bookingEndTime" to Timestamp(newEndTime),
                        "totalAmount" to newTotalAmount,
                        "extendedAt" to Timestamp.now()
                    )
                )
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error extending booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get booking history
     */
    suspend fun getBookingHistory(limit: Int = 50): Result<List<ParkingBooking>> {
        return try {
            val bookings = getUserBookings(limit = limit)
            Result.success(bookings)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting booking history", e)
            Result.failure(e)
        }
    }
}