package com.example.cityflux.model

import com.google.firebase.Timestamp

/**
 * Represents a saved place in the Firestore sub-collection:
 * users/{uid}/saved_places/{placeId}
 */
data class SavedPlace(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Timestamp? = null
)
