package com.example.cityflux.model

/**
 * Represents a parking live node in Firebase Realtime Database.
 * Path: parking_live/{parkingId}
 *
 * Enhanced with vehicle type specific slot tracking and real-time updates
 */
data class ParkingLive(
    val availableSlots: Int = 0,
    val totalSlots: Int = 0,
    val slotsByType: Map<String, SlotTypeInfo> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis(),
    val peakHourMultiplier: Double = 1.0,
    val isActive: Boolean = true
) {
    /** No-arg constructor required by Firebase */
    constructor() : this(0, 0, emptyMap(), System.currentTimeMillis(), 1.0, true)
    
    /**
     * Get available slots for specific vehicle type
     * Returns general slots if vehicle-specific slots not configured
     */
    fun getAvailableSlotsForType(vehicleType: VehicleType): Int {
        val typeSlots = slotsByType[vehicleType.name]?.available
        // If vehicle-specific slots not configured, return general availability
        return typeSlots ?: availableSlots
    }
    
    /**
     * Get occupancy percentage
     */
    fun getOccupancyPercentage(): Int {
        return if (totalSlots > 0) {
            ((totalSlots - availableSlots).toFloat() / totalSlots * 100).toInt()
        } else 0
    }
    
    /**
     * Check if parking is available for vehicle type
     * Returns true if either general slots or vehicle-specific slots are available
     */
    fun isAvailableForType(vehicleType: VehicleType): Boolean {
        val typeSlots = slotsByType[vehicleType.name]?.available
        // Check general availability if vehicle-specific not configured
        val slotsAvailable = typeSlots ?: availableSlots
        return slotsAvailable > 0 && isActive
    }
    
    /**
     * Check if any slots are available
     */
    fun hasAvailableSlots(): Boolean {
        return availableSlots > 0 && isActive
    }
}

/**
 * Slot information by vehicle type
 */
data class SlotTypeInfo(
    val total: Int = 0,
    val available: Int = 0,
    val reserved: Int = 0
) {
    constructor() : this(0, 0, 0)
    
    fun getOccupied(): Int = total - available
    fun getOccupancyRate(): Float = if (total > 0) getOccupied().toFloat() / total else 0f
}
