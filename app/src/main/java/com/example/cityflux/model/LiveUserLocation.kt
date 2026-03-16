package com.example.cityflux.model

/**
 * Represents a citizen's live location in Firebase Realtime Database.
 * Path: live_locations/{userId}
 */
data class LiveUserLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Int = 0,
    val heading: Double = 0.0,
    val name: String = "Citizen",
    val timestamp: Long = 0L
)
