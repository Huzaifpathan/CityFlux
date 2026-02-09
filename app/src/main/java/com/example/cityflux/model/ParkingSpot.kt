package com.example.cityflux.model

import com.google.firebase.firestore.GeoPoint

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
