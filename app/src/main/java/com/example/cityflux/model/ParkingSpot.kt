package com.example.cityflux.model

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
    val isLegal: Boolean = true
)

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
            isLegal = isLegal
        )
    } catch (e: Exception) {
        null
    }
}
