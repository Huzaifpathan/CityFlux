package com.example.cityflux.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Represents a parking spot document in the Firestore `parking` collection.
 * Document ID = auto-generated parkingId.
 */
data class ParkingSpot(
    val id: String = "",
    val location: GeoPoint? = null,
    val address: String = "",
    val totalSlots: Int = 0,
    val availableSlots: Int = 0,
    val isLegal: Boolean = true,
    val parkingType: String = "free", // "free" or "paid"
    val ratePerHour: Int = 0,
    val minDuration: Int = 0,
    val maxDuration: Int = 0,
    val vehicleTypes: List<String> = emptyList(), // ["car", "bike", "truck"]
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    // Helper properties for backward compatibility
    val isFree: Boolean
        get() = parkingType.equals("free", ignoreCase = true)
    
    val rateDisplayString: String
        get() = if (isFree) "FREE" else "₹$ratePerHour/hr"
}

/**
 * Helper function to safely parse ParkingSpot from Firestore DocumentSnapshot.
 * Handles both GeoPoint and Array [lat, lng] formats for the location field.
 */
fun DocumentSnapshot.toParkingSpot(): ParkingSpot? {
    return try {
        val id = this.id
        val address = getString("address") ?: ""
        val totalSlots = getLong("totalSlots")?.toInt() ?: 0
        val availableSlots = getLong("availableSlots")?.toInt() ?: 0
        val isLegal = getBoolean("isLegal") ?: true
        val parkingType = getString("parkingType") ?: "free"
        val ratePerHour = getLong("ratePerHour")?.toInt() ?: 0
        val minDuration = getLong("minDuration")?.toInt() ?: 0
        val maxDuration = getLong("maxDuration")?.toInt() ?: 0
        val vehicleTypes = get("vehicleTypes") as? List<String> ?: emptyList()
        val createdAt = getTimestamp("createdAt")
        val updatedAt = getTimestamp("updatedAt")
        
        // Handle location field - can be GeoPoint or Array
        val location = try {
            getGeoPoint("location")
        } catch (e: Exception) {
            // If GeoPoint fails, try parsing as array [lat, lng]
            val locationArray = get("location") as? List<*>
            if (locationArray != null && locationArray.size >= 2) {
                val lat = (locationArray[0] as? Number)?.toDouble() ?: 0.0
                val lng = (locationArray[1] as? Number)?.toDouble() ?: 0.0
                GeoPoint(lat, lng)
            } else {
                null
            }
        }
        
        ParkingSpot(
            id = id,
            location = location,
            address = address,
            totalSlots = totalSlots,
            availableSlots = availableSlots,
            isLegal = isLegal,
            parkingType = parkingType,
            ratePerHour = ratePerHour,
            minDuration = minDuration,
            maxDuration = maxDuration,
            vehicleTypes = vehicleTypes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    } catch (e: Exception) {
        null
    }
}
