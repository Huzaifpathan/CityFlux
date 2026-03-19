package com.example.cityflux.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

/**
 * ONE-TIME SETUP: Add sample parking spots to Firestore
 * Run this once, then delete this file
 */
object ParkingSetupHelper {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    fun addSampleParkingSpots() {
        val spots = listOf(
            mapOf(
                "address" to "City Center, Near Mall, Solapur",
                "availableSlots" to 45,
                "isLegal" to true,
                "location" to GeoPoint(17.6599, 75.9064),
                "totalSlots" to 100
            ),
            mapOf(
                "address" to "Railway Station Road, Solapur",
                "availableSlots" to 20,
                "isLegal" to true,
                "location" to GeoPoint(17.6715, 75.9164),
                "totalSlots" to 50
            ),
            mapOf(
                "address" to "Civil Hospital, Medical Road, Solapur",
                "availableSlots" to 10,
                "isLegal" to true,
                "location" to GeoPoint(17.6530, 75.9130),
                "totalSlots" to 30
            ),
            mapOf(
                "address" to "Budhwar Peth Market, Solapur",
                "availableSlots" to 5,
                "isLegal" to true,
                "location" to GeoPoint(17.6650, 75.9100),
                "totalSlots" to 25
            ),
            mapOf(
                "address" to "College Road, Solapur",
                "availableSlots" to 0,
                "isLegal" to false,
                "location" to GeoPoint(17.6450, 75.9200),
                "totalSlots" to 15
            ),
            mapOf(
                "address" to "Bus Stand Parking, Solapur",
                "availableSlots" to 30,
                "isLegal" to true,
                "location" to GeoPoint(17.6700, 75.9050),
                "totalSlots" to 75
            ),
            mapOf(
                "address" to "Shopping Complex, Murarji Peth",
                "availableSlots" to 15,
                "isLegal" to true,
                "location" to GeoPoint(17.6580, 75.9080),
                "totalSlots" to 40
            ),
            mapOf(
                "address" to "Sports Stadium Parking",
                "availableSlots" to 60,
                "isLegal" to true,
                "location" to GeoPoint(17.6620, 75.9150),
                "totalSlots" to 120
            )
        )
        
        spots.forEach { spotData ->
            firestore.collection("parking")
                .add(spotData)
                .addOnSuccessListener { ref ->
                    Log.d("ParkingSetup", "Added spot: ${ref.id}")
                }
                .addOnFailureListener { e ->
                    Log.e("ParkingSetup", "Error adding spot", e)
                }
        }
    }
}
