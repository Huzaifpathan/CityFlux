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
    val imageUrls: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "Pending",
    val assignedTo: String = "",
    val timestamp: Timestamp? = null,
    val priority: String = "medium",
    val isAnonymous: Boolean = false,
    val upvoteCount: Int = 0,
    val upvotedBy: List<String> = emptyList()
)
