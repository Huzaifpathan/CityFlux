package com.example.cityflux.data

import android.util.Log
import com.example.cityflux.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Booking Notifications
 * Manages notifications displayed in notification tab
 */
class BookingNotificationRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "BookingNotificationRepo"
        private const val NOTIFICATIONS_COLLECTION = "booking_notifications"
    }
    
    /**
     * Create notification when booking is confirmed
     */
    suspend fun createBookingConfirmedNotification(
        booking: ParkingBooking
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            val notification = BookingNotification(
                id = generateNotificationId(),
                userId = userId,
                bookingId = booking.id,
                type = NotificationType.BOOKING_CONFIRMED,
                title = "Booking Confirmed! 🎉",
                message = "Your parking at ${booking.parkingSpotName} is confirmed for ${booking.durationHours} hours",
                timestamp = Timestamp.now(),
                isRead = false,
                priority = NotificationPriority.HIGH,
                bookingData = BookingSnapshot(
                    parkingName = booking.parkingSpotName,
                    parkingAddress = booking.parkingAddress,
                    vehicleNumber = booking.vehicleNumber,
                    amount = booking.totalAmount,
                    startTime = booking.bookingStartTime,
                    endTime = booking.bookingEndTime,
                    status = booking.status.displayName
                )
            )
            
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notification.id)
                .set(notification)
                .await()
            
            Log.d(TAG, "Notification created: ${notification.id}")
            Result.success(notification.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create payment success notification
     */
    suspend fun createPaymentSuccessNotification(
        booking: ParkingBooking
    ): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            val notification = BookingNotification(
                id = generateNotificationId(),
                userId = userId,
                bookingId = booking.id,
                type = NotificationType.PAYMENT_SUCCESS,
                title = "Payment Successful ✅",
                message = "₹${booking.totalAmount.toInt()} paid for ${booking.parkingSpotName}",
                timestamp = Timestamp.now(),
                isRead = false,
                priority = NotificationPriority.HIGH,
                bookingData = BookingSnapshot(
                    parkingName = booking.parkingSpotName,
                    parkingAddress = booking.parkingAddress,
                    vehicleNumber = booking.vehicleNumber,
                    amount = booking.totalAmount,
                    startTime = booking.bookingStartTime,
                    endTime = booking.bookingEndTime,
                    status = booking.status.displayName
                )
            )
            
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notification.id)
                .set(notification)
                .await()
            
            Result.success(notification.id)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating payment notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Observe user's booking notifications in real-time
     */
    fun observeUserNotifications(): Flow<List<BookingNotification>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        
        val listener = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing notifications", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(BookingNotification::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                
                trySend(notifications)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Mark notification as read
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("isRead", true)
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking notification as read", e)
            Result.failure(e)
        }
    }
    
    /**
     * Mark all notifications as read
     */
    suspend fun markAllAsRead(): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            val notifications = firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
            
            val batch = firestore.batch()
            notifications.documents.forEach { doc ->
                batch.update(doc.reference, "isRead", true)
            }
            batch.commit().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking all as read", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete notification
     */
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .delete()
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting notification", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get unread notification count
     */
    fun getUnreadCount(): Flow<Int> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(0)
            awaitClose { }
            return@callbackFlow
        }
        
        val listener = firestore.collection(NOTIFICATIONS_COLLECTION)
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(0)
                    return@addSnapshotListener
                }
                
                trySend(snapshot?.size() ?: 0)
            }
        
        awaitClose { listener.remove() }
    }
    
    private fun generateNotificationId(): String {
        return "NOT${System.currentTimeMillis()}${(1000..9999).random()}"
    }
}
