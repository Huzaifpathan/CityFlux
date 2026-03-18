package com.example.cityflux.data

import android.util.Log
import com.example.cityflux.model.BookingStatus
import com.example.cityflux.model.ParkingBooking
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * Repository for parking booking operations
 * Phase 4: Booking & Reservation System
 */
class BookingRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "BookingRepository"
        private const val BOOKINGS_COLLECTION = "bookings"
    }
    
    /**
     * Create a new parking booking
     */
    suspend fun createBooking(booking: ParkingBooking): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("User not authenticated")
            )
            
            // Generate unique QR code data
            val qrData = generateQrCodeData(booking)
            
            // Create booking with server timestamp
            val bookingWithQr = booking.copy(
                userId = userId,
                qrCodeData = qrData,
                status = BookingStatus.PENDING
            )
            
            val docRef = firestore.collection(BOOKINGS_COLLECTION)
                .add(bookingWithQr)
                .await()
            
            Log.d(TAG, "Booking created: ${docRef.id}")
            Result.success(docRef.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update booking status
     */
    suspend fun updateBookingStatus(
        bookingId: String,
        status: BookingStatus
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "status" to status.name
            )
            
            // Add timestamp for specific status changes
            when (status) {
                BookingStatus.ACTIVE -> {
                    updates["entryTime"] = Timestamp.now()
                }
                BookingStatus.COMPLETED -> {
                    updates["exitTime"] = Timestamp.now()
                }
                BookingStatus.CANCELLED -> {
                    updates["cancelledAt"] = Timestamp.now()
                }
                else -> {}
            }
            
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Booking status updated: $bookingId -> $status")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancel a booking
     */
    suspend fun cancelBooking(
        bookingId: String,
        reason: String
    ): Result<Unit> {
        return try {
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update(
                    mapOf(
                        "status" to BookingStatus.CANCELLED.name,
                        "cancellationReason" to reason,
                        "cancelledAt" to Timestamp.now()
                    )
                )
                .await()
            
            Log.d(TAG, "Booking cancelled: $bookingId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Extend booking duration
     */
    suspend fun extendBooking(
        bookingId: String,
        additionalHours: Int,
        additionalAmount: Double
    ): Result<Unit> {
        return try {
            val booking = getBooking(bookingId).getOrThrow()
            val newEndTime = Calendar.getInstance().apply {
                time = booking.bookingEndTime?.toDate() ?: Date()
                add(Calendar.HOUR_OF_DAY, additionalHours)
            }.time
            
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update(
                    mapOf(
                        "bookingEndTime" to Timestamp(newEndTime),
                        "durationHours" to booking.durationHours + additionalHours,
                        "amount" to booking.amount + additionalAmount
                    )
                )
                .await()
            
            Log.d(TAG, "Booking extended: $bookingId by $additionalHours hours")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extending booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get single booking by ID
     */
    suspend fun getBooking(bookingId: String): Result<ParkingBooking> {
        return try {
            val snapshot = firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .get()
                .await()
            
            val booking = snapshot.toObject(ParkingBooking::class.java)
                ?.copy(id = snapshot.id)
                ?: return Result.failure(Exception("Booking not found"))
            
            Result.success(booking)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching booking", e)
            Result.failure(e)
        }
    }
    
    /**
     * Observe user's bookings in real-time
     */
    fun observeUserBookings(
        includeCompleted: Boolean = false
    ): Flow<List<ParkingBooking>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            close(Exception("User not authenticated"))
            return@callbackFlow
        }
        
        var query = firestore.collection(BOOKINGS_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("bookingCreatedAt", Query.Direction.DESCENDING)
        
        if (!includeCompleted) {
            query = query.whereNotIn("status", listOf(
                BookingStatus.COMPLETED.name,
                BookingStatus.CANCELLED.name,
                BookingStatus.EXPIRED.name
            ))
        }
        
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error observing bookings", error)
                close(error)
                return@addSnapshotListener
            }
            
            val bookings = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            
            trySend(bookings)
        }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get booking history with filters
     */
    suspend fun getBookingHistory(
        status: BookingStatus? = null,
        limit: Int = 50
    ): Result<List<ParkingBooking>> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(
                Exception("User not authenticated")
            )
            
            var query = firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("bookingCreatedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            
            if (status != null) {
                query = query.whereEqualTo("status", status.name)
            }
            
            val snapshot = query.get().await()
            val bookings = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ParkingBooking::class.java)?.copy(id = doc.id)
            }
            
            Result.success(bookings)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching booking history", e)
            Result.failure(e)
        }
    }
    
    /**
     * Verify booking by QR code
     */
    suspend fun verifyBookingByQr(qrData: String): Result<ParkingBooking> {
        return try {
            val snapshot = firestore.collection(BOOKINGS_COLLECTION)
                .whereEqualTo("qrCodeData", qrData)
                .limit(1)
                .get()
                .await()
            
            val booking = snapshot.documents.firstOrNull()
                ?.toObject(ParkingBooking::class.java)
                ?.copy(id = snapshot.documents.first().id)
                ?: return Result.failure(Exception("Invalid QR code"))
            
            Result.success(booking)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying QR code", e)
            Result.failure(e)
        }
    }
    
    /**
     * Add rating and review
     */
    suspend fun addReview(
        bookingId: String,
        rating: Float,
        review: String
    ): Result<Unit> {
        return try {
            firestore.collection(BOOKINGS_COLLECTION)
                .document(bookingId)
                .update(
                    mapOf(
                        "rating" to rating,
                        "review" to review
                    )
                )
                .await()
            
            Log.d(TAG, "Review added: $bookingId")
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding review", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generate unique QR code data
     */
    private fun generateQrCodeData(booking: ParkingBooking): String {
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().take(8)
        return "CITYFLUX-${booking.parkingSpotId}-$timestamp-$random"
    }
}
