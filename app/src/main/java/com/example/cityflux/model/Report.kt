package com.example.cityflux.model

import com.google.firebase.Timestamp

/**
 * Represents a report document in the Firestore `reports` collection.
 * Document ID = auto-generated reportId.
 *
 * type: "illegal_parking", "accident", "hawker", etc.
 * status: "Pending", "In Progress", "Resolved"
 */
data class Report(
    val id: String = "",
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "Pending",
    val timestamp: Timestamp? = null
)
