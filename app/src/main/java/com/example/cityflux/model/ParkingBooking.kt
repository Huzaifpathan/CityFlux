package com.example.cityflux.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Parking booking model for Phase 4
 * Represents a parking slot reservation
 */
data class ParkingBooking(
    @DocumentId
    val id: String = "",
    
    // User & Parking Info
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val parkingSpotId: String = "",
    val parkingSpotName: String = "",
    val parkingAddress: String = "",
    
    // Vehicle Details
    val vehicleNumber: String = "",
    val vehicleType: VehicleType = VehicleType.CAR,
    
    // Booking Time
    @ServerTimestamp
    val bookingCreatedAt: Timestamp? = null,
    val bookingStartTime: Timestamp? = null,
    val bookingEndTime: Timestamp? = null,
    val durationHours: Int = 0,
    
    // Status & Payment
    val status: BookingStatus = BookingStatus.PENDING,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val amount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val baseAmount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val isPaid: Boolean = false,
    val paymentId: String? = null,
    val paymentMethod: String? = null,
    val paymentTimestamp: Timestamp? = null,
    val transactionId: String? = null,
    
    // QR Code & Verification
    val qrCodeData: String = "",
    val entryTime: Timestamp? = null,
    val exitTime: Timestamp? = null,
    val verifiedBy: String? = null,
    
    // Additional Info
    val notes: String? = null,
    val rating: Float? = null,
    val review: String? = null,
    val cancellationReason: String? = null,
    val cancelledAt: Timestamp? = null
)

/**
 * Booking status enum
 */
enum class BookingStatus(val displayName: String) {
    PENDING("Pending"),           // Just created, not yet paid
    CONFIRMED("Confirmed"),       // Payment successful, waiting for entry
    ACTIVE("Active"),             // Vehicle has entered
    ENDING_SOON("Ending Soon"),   // Less than 30 min remaining
    COMPLETED("Completed"),       // Vehicle has exited
    CANCELLED("Cancelled"),       // Cancelled by user
    EXPIRED("Expired"),           // Booking time expired without entry
    NO_SHOW("No Show"),           // User didn't show up
    REFUNDED("Refunded")          // Money refunded
}

/**
 * Payment status enum
 */
enum class PaymentStatus(val displayName: String) {
    PENDING("Pending"),
    PROCESSING("Processing"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    REFUND_INITIATED("Refund Initiated"),
    REFUNDED("Refunded")
}

/**
 * Vehicle type enum for pricing
 */
enum class VehicleType(val displayName: String) {
    TWO_WHEELER("Two Wheeler"),
    CAR("Car"),
    SUV("SUV"),
    TRUCK("Truck"),
    BUS("Bus")
}

/**
 * Extension functions for BookingStatus
 */
fun BookingStatus.isActive(): Boolean {
    return this == BookingStatus.CONFIRMED || this == BookingStatus.ACTIVE
}

fun BookingStatus.canBeCancelled(): Boolean {
    return this == BookingStatus.PENDING || this == BookingStatus.CONFIRMED
}

fun BookingStatus.canBeExtended(): Boolean {
    return this == BookingStatus.CONFIRMED || this == BookingStatus.ACTIVE
}

/**
 * Helper to calculate remaining time
 */
fun ParkingBooking.getRemainingTimeMinutes(): Long {
    val endTime = bookingEndTime?.toDate()?.time ?: return 0
    val now = System.currentTimeMillis()
    val remaining = (endTime - now) / (1000 * 60)
    return if (remaining > 0) remaining else 0
}

/**
 * Check if booking is about to expire (less than 15 minutes)
 */
fun ParkingBooking.isExpiringSoon(): Boolean {
    return getRemainingTimeMinutes() in 1..15
}

/**
 * Check if booking has expired
 */
fun ParkingBooking.isExpired(): Boolean {
    val endTime = bookingEndTime?.toDate()?.time ?: return false
    return System.currentTimeMillis() > endTime
}
