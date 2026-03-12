package com.example.cityflux.model

import com.google.firebase.Timestamp

/**
 * Represents a user document in the Firestore `users` collection.
 * Document ID = Firebase Auth userId.
 */
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "",          // "citizen" or "police"
    val profileImageUrl: String = "",
    val workingAreaName: String = "",
    val workingLatitude: Double = 0.0,
    val workingLongitude: Double = 0.0,
    val lastLoginAt: Timestamp? = null,
    val createdAt: Timestamp? = null
) {
    /** Returns true if this police user has configured their working location. */
    val hasWorkingLocation: Boolean
        get() = workingAreaName.isNotBlank() && (workingLatitude != 0.0 || workingLongitude != 0.0)
}
