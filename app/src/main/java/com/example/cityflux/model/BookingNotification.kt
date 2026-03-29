package com.example.cityflux.model

import com.google.firebase.Timestamp

/**
 * Booking Notification Model
 * Represents notifications for parking bookings shown in notification tab
 */
data class BookingNotification(
    val id: String = "",
    val userId: String = "",
    val bookingId: String = "",
    val type: NotificationType = NotificationType.BOOKING_CONFIRMED,
    val title: String = "",
    val message: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val priority: NotificationPriority = NotificationPriority.MEDIUM,
    
    // Booking snapshot for quick display
    val bookingData: BookingSnapshot? = null
) {
    /** No-arg constructor for Firebase */
    constructor() : this(
        id = "",
        userId = "",
        bookingId = "",
        type = NotificationType.BOOKING_CONFIRMED,
        title = "",
        message = "",
        timestamp = Timestamp.now(),
        isRead = false,
        priority = NotificationPriority.MEDIUM,
        bookingData = null
    )
}

/**
 * Booking snapshot for notifications
 */
data class BookingSnapshot(
    val parkingName: String = "",
    val parkingAddress: String = "",
    val vehicleNumber: String = "",
    val amount: Double = 0.0,
    val startTime: Timestamp? = null,
    val endTime: Timestamp? = null,
    val status: String = ""
) {
    constructor() : this("", "", "", 0.0, null, null, "")
}

/**
 * Notification types for different booking events
 */
enum class NotificationType(val displayName: String) {
    BOOKING_CONFIRMED("Booking Confirmed"),
    PAYMENT_SUCCESS("Payment Successful"),
    BOOKING_STARTED("Parking Started"),
    BOOKING_REMINDER("Booking Reminder"),
    BOOKING_ENDING_SOON("Ending Soon"),
    BOOKING_COMPLETED("Parking Completed"),
    BOOKING_CANCELLED("Booking Cancelled"),
    BOOKING_EXTENDED("Booking Extended"),
    REFUND_PROCESSED("Refund Processed"),
    BOOKING_EXPIRED("Booking Expired")
}

/**
 * Notification priority
 */
enum class NotificationPriority {
    HIGH,    // Requires immediate attention
    MEDIUM,  // Standard notification
    LOW      // Informational only
}
