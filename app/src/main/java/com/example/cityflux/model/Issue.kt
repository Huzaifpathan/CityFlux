package com.example.cityflux.model

data class Issue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val type: String = "",
    val status: String = "Pending",
    val timestamp: Long = 0L
)
