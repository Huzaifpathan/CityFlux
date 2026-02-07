package com.example.cityflux.model

data class Issue(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val status: String = "Pending",
    val timestamp: Long = System.currentTimeMillis()
)
