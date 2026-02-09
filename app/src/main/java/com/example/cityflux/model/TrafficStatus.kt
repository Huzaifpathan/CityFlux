package com.example.cityflux.model

/**
 * Represents a traffic node in Firebase Realtime Database.
 * Path: traffic/{roadId}
 *
 * congestionLevel: "LOW", "MEDIUM", or "HIGH"
 * lastUpdated: epoch milliseconds
 */
data class TrafficStatus(
    val congestionLevel: String = "LOW",
    val lastUpdated: Long = 0L
) {
    /** No-arg constructor required by Firebase */
    constructor() : this("LOW", 0L)
}
