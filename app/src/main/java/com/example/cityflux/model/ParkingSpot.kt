package com.example.cityflux.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Exclude

/**
 * Parking type enum - Free or Paid
 */
enum class ParkingType(val displayName: String) {
    FREE("Free Parking"),
    PAID("Paid Parking");
    
    companion object {
        fun fromString(value: String?): ParkingType {
            return when (value?.lowercase()) {
                "free" -> FREE
                "paid" -> PAID
                else -> PAID // Default to paid
            }
        }
    }
}

/**
 * Represents a parking spot document in the Firestore `parking` collection.
 * Document ID = auto-generated parkingId.
 * 
 * New format includes:
 * - parkingType: "free" or "paid"
 * - ratePerHour: pricing per hour (₹)
 * - minDuration: minimum parking time in minutes
 * - maxDuration: maximum parking time in minutes
 * - vehicleTypes: list of supported vehicle types
 */
data class ParkingSpot(
    val id: String = "",
    val location: GeoPoint? = null,
    val address: String = "",
    val totalSlots: Int = 0,
    val availableSlots: Int = 0,
    val isLegal: Boolean = true,
    
    // New fields for zone-based parking - maps to "parkingType" in Firebase
    val parkingType: String = "paid",
    
    val ratePerHour: Double = 0.0,
    val minDuration: Int = 15,      // in minutes
    val maxDuration: Int = 480,     // in minutes (8 hours default)
    val vehicleTypes: List<String> = listOf("car", "bike", "ev"),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    /** Get parking type as enum (excluded from Firebase serialization) */
    @get:Exclude
    val parkingTypeEnum: ParkingType
        get() = ParkingType.fromString(parkingType)
    
    /** Check if this is a free parking spot */
    @get:Exclude
    val isFree: Boolean
        get() = parkingTypeEnum == ParkingType.FREE
    
    /** Check if this is a paid parking spot */
    @get:Exclude
    val isPaid: Boolean
        get() = parkingTypeEnum == ParkingType.PAID
    
    /** Get formatted rate string */
    @get:Exclude
    val rateDisplayString: String
        get() = if (isFree) "FREE" else "₹${ratePerHour.toInt()}/hr"
    
    /** Get min duration in hours */
    @get:Exclude
    val minDurationHours: Float
        get() = minDuration / 60f
    
    /** Get max duration in hours */
    @get:Exclude
    val maxDurationHours: Float
        get() = maxDuration / 60f
    
    /** Check if vehicle type is supported */
    fun supportsVehicleType(type: VehicleType): Boolean {
        return vehicleTypes.any { it.equals(type.name, ignoreCase = true) }
    }
    
    /** Calculate price for given duration (in hours) */
    fun calculatePrice(durationHours: Int): Double {
        return if (isFree) 0.0 else ratePerHour * durationHours
    }
}
