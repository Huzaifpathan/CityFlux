package com.example.cityflux.data

import android.util.Log
import com.example.cityflux.model.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Firebase Realtime Database Setup Helper
 * Initialize real-time parking availability data
 */
class RealtimeParkingSetup {
    
    private val realtimeDb = Firebase.database
    
    companion object {
        private const val TAG = "RealtimeParkingSetup"
        private const val PARKING_LIVE_PATH = "parking_live"
    }
    
    /**
     * Initialize real-time parking data for all parking spots
     */
    suspend fun initializeParkingLiveData() {
        try {
            Log.d(TAG, "Initializing real-time parking data...")
            
            // Sample parking spots with live data
            val parkingLiveData = mapOf(
                "parking_001" to createParkingLive(
                    totalSlots = 100,
                    availableSlots = 45,
                    carSlots = SlotTypeInfo(total = 60, available = 20),
                    bikeSlots = SlotTypeInfo(total = 30, available = 15),
                    suvSlots = SlotTypeInfo(total = 10, available = 10)
                ),
                "parking_002" to createParkingLive(
                    totalSlots = 150,
                    availableSlots = 78,
                    carSlots = SlotTypeInfo(total = 80, available = 35),
                    bikeSlots = SlotTypeInfo(total = 50, available = 30),
                    suvSlots = SlotTypeInfo(total = 20, available = 13)
                ),
                "parking_003" to createParkingLive(
                    totalSlots = 75,
                    availableSlots = 12,
                    carSlots = SlotTypeInfo(total = 40, available = 5),
                    bikeSlots = SlotTypeInfo(total = 25, available = 5),
                    suvSlots = SlotTypeInfo(total = 10, available = 2)
                ),
                "parking_004" to createParkingLive(
                    totalSlots = 200,
                    availableSlots = 156,
                    carSlots = SlotTypeInfo(total = 120, available = 89),
                    bikeSlots = SlotTypeInfo(total = 60, available = 50),
                    suvSlots = SlotTypeInfo(total = 20, available = 17)
                ),
                "parking_005" to createParkingLive(
                    totalSlots = 50,
                    availableSlots = 8,
                    carSlots = SlotTypeInfo(total = 30, available = 3),
                    bikeSlots = SlotTypeInfo(total = 15, available = 3),
                    suvSlots = SlotTypeInfo(total = 5, available = 2)
                )
            )
            
            // Upload to Firebase Realtime Database
            val ref = realtimeDb.reference.child(PARKING_LIVE_PATH)
            
            parkingLiveData.forEach { (parkingId, liveData) ->
                ref.child(parkingId).setValue(liveData).await()
                Log.d(TAG, "Initialized live data for $parkingId")
            }
            
            Log.d(TAG, "Real-time parking data initialization completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing real-time parking data", e)
            throw e
        }
    }
    
    /**
     * Create ParkingLive object with vehicle type breakdown
     */
    private fun createParkingLive(
        totalSlots: Int,
        availableSlots: Int,
        carSlots: SlotTypeInfo,
        bikeSlots: SlotTypeInfo,
        suvSlots: SlotTypeInfo
    ): ParkingLive {
        val slotsByType = mapOf(
            VehicleType.CAR.name to carSlots,
            VehicleType.TWO_WHEELER.name to bikeSlots,
            VehicleType.SUV.name to suvSlots,
            VehicleType.TRUCK.name to SlotTypeInfo(total = 0, available = 0),
            VehicleType.BUS.name to SlotTypeInfo(total = 0, available = 0)
        )
        
        return ParkingLive(
            availableSlots = availableSlots,
            totalSlots = totalSlots,
            slotsByType = slotsByType,
            lastUpdated = System.currentTimeMillis(),
            peakHourMultiplier = calculatePeakHourMultiplier(),
            isActive = true
        )
    }
    
    /**
     * Calculate peak hour multiplier based on current time
     */
    private fun calculatePeakHourMultiplier(): Double {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 8..10 -> 1.5 // Morning peak
            in 12..14 -> 1.3 // Lunch peak
            in 17..20 -> 1.5 // Evening peak
            else -> 1.0 // Normal hours
        }
    }
    
    /**
     * Simulate real-time slot changes (for demo purposes)
     */
    suspend fun simulateSlotChanges() {
        try {
            val parkingIds = listOf("parking_001", "parking_002", "parking_003", "parking_004", "parking_005")
            val random = kotlin.random.Random.Default
            
            parkingIds.forEach { parkingId ->
                val ref = realtimeDb.reference.child(PARKING_LIVE_PATH).child(parkingId)
                
                // Get current data
                val snapshot = ref.get().await()
                val currentData = snapshot.getValue(ParkingLive::class.java)
                
                currentData?.let { data ->
                    // Randomly change 1-3 slots
                    val change = random.nextInt(-3, 4) // -3 to +3 slots
                    val newAvailable = (data.availableSlots + change).coerceIn(0, data.totalSlots)
                    
                    val updatedData = data.copy(
                        availableSlots = newAvailable,
                        lastUpdated = System.currentTimeMillis(),
                        peakHourMultiplier = calculatePeakHourMultiplier()
                    )
                    
                    ref.setValue(updatedData).await()
                    Log.d(TAG, "Updated $parkingId: ${data.availableSlots} -> $newAvailable slots")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error simulating slot changes", e)
        }
    }
    
    /**
     * Update specific parking slot availability
     */
    suspend fun updateParkingSlots(
        parkingId: String, 
        vehicleType: VehicleType, 
        change: Int
    ) {
        try {
            val ref = realtimeDb.reference.child(PARKING_LIVE_PATH).child(parkingId)
            val snapshot = ref.get().await()
            val currentData = snapshot.getValue(ParkingLive::class.java)
            
            currentData?.let { data ->
                val currentTypeSlots = data.slotsByType[vehicleType.name] ?: SlotTypeInfo()
                val newAvailable = (currentTypeSlots.available + change).coerceIn(0, currentTypeSlots.total)
                val totalChange = newAvailable - currentTypeSlots.available
                
                val updatedSlotsByType = data.slotsByType.toMutableMap()
                updatedSlotsByType[vehicleType.name] = currentTypeSlots.copy(available = newAvailable)
                
                val updatedData = data.copy(
                    availableSlots = (data.availableSlots + totalChange).coerceIn(0, data.totalSlots),
                    slotsByType = updatedSlotsByType,
                    lastUpdated = System.currentTimeMillis()
                )
                
                ref.setValue(updatedData).await()
                Log.d(TAG, "Updated $parkingId ${vehicleType.name}: ${currentTypeSlots.available} -> $newAvailable")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating parking slots", e)
            throw e
        }
    }
}