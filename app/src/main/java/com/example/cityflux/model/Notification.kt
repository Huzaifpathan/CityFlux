package com.example.cityflux.model

import com.google.firebase.Timestamp

/**
 * Represents a notification/alert stored in Firestore at
 * users/{uid}/notifications/{notificationId}
 */
data class Notification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "general",       // traffic, parking, accident, emergency, weather, general
    val priority: String = "medium",    // low, medium, high, critical
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val read: Boolean = false,
    val pinned: Boolean = false,
    val timestamp: Timestamp? = null
)
