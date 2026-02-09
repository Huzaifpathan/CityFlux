package com.example.cityflux.model

/**
 * Represents a parking live node in Firebase Realtime Database.
 * Path: parking_live/{parkingId}
 *
 * availableSlots: current number of free parking slots
 */
data class ParkingLive(
    val availableSlots: Int = 0
) {
    /** No-arg constructor required by Firebase */
    constructor() : this(0)
}
