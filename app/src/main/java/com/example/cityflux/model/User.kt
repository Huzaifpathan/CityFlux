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
    val lastLoginAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)
