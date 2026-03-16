package com.example.cityflux.data

import android.util.Log
import com.example.cityflux.model.LiveUserLocation
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.TrafficStatus
import com.google.firebase.database.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Singleton service that provides real-time [Flow]s for
 * traffic congestion and parking availability from Firebase Realtime Database.
 *
 * Database structure:
 * ```
 * traffic/
 *   road_101/
 *     congestionLevel: "LOW"
 *     lastUpdated: 1707469200000
 *
 * parking_live/
 *   parking_01/
 *     availableSlots: 12
 * ```
 *
 * Usage in a Composable / ViewModel:
 * ```
 * val traffic by RealtimeDbService.observeTraffic()
 *     .collectAsState(initial = emptyMap())
 *
 * val parking by RealtimeDbService.observeParkingLive()
 *     .collectAsState(initial = emptyMap())
 * ```
 */
object RealtimeDbService {

    private const val TAG = "RealtimeDbService"

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    // ─────────────────────────────────────────────────────────
    // 🚦  TRAFFIC — observe all roads
    // ─────────────────────────────────────────────────────────

    /**
     * Returns a [Flow] that emits `Map<roadId, TrafficStatus>` every time
     * any child under `traffic/` changes.
     *
     * Congestion level changes are pushed instantly so the map colors
     * can update in real time.
     */
    fun observeTraffic(): Flow<Map<String, TrafficStatus>> = callbackFlow {
        val ref = database.getReference("traffic")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, TrafficStatus>()
                for (child in snapshot.children) {
                    val roadId = child.key ?: continue
                    val status = child.getValue(TrafficStatus::class.java) ?: continue
                    map[roadId] = status
                }
                trySend(map)
                Log.d(TAG, "Traffic updated: ${map.size} roads")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Traffic listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe a single road's traffic in real time.
     */
    fun observeRoadTraffic(roadId: String): Flow<TrafficStatus?> = callbackFlow {
        val ref = database.getReference("traffic").child(roadId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(TrafficStatus::class.java)
                trySend(status)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Road $roadId listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────
    // 🅿️  PARKING LIVE — observe all parking areas
    // ─────────────────────────────────────────────────────────

    /**
     * Returns a [Flow] that emits `Map<parkingId, ParkingLive>` every time
     * any child under `parking_live/` changes.
     *
     * Available-slot changes are pushed instantly so the UI can
     * reflect current availability.
     */
    fun observeParkingLive(): Flow<Map<String, ParkingLive>> = callbackFlow {
        val ref = database.getReference("parking_live")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, ParkingLive>()
                for (child in snapshot.children) {
                    val parkingId = child.key ?: continue
                    val live = child.getValue(ParkingLive::class.java) ?: continue
                    map[parkingId] = live
                }
                trySend(map)
                Log.d(TAG, "Parking updated: ${map.size} areas")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Parking listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Observe a single parking area in real time.
     */
    fun observeParkingArea(parkingId: String): Flow<ParkingLive?> = callbackFlow {
        val ref = database.getReference("parking_live").child(parkingId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val live = snapshot.getValue(ParkingLive::class.java)
                trySend(live)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Parking $parkingId listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ─────────────────────────────────────────────────────────
    // 📍  LIVE LOCATIONS — observe all sharing citizens
    // ─────────────────────────────────────────────────────────

    /**
     * Returns a [Flow] that emits `Map<userId, LiveUserLocation>` every time
     * any child under `live_locations/` changes.
     *
     * Only includes users whose timestamp is within the last 2 minutes
     * to filter out stale entries from crashed sessions.
     */
    fun observeLiveLocations(): Flow<Map<String, LiveUserLocation>> = callbackFlow {
        val ref = database.getReference("live_locations")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, LiveUserLocation>()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val loc = LiveUserLocation(
                        lat = child.child("lat").getValue(Double::class.java) ?: 0.0,
                        lng = child.child("lng").getValue(Double::class.java) ?: 0.0,
                        speed = child.child("speed").getValue(Int::class.java) ?: 0,
                        heading = child.child("heading").getValue(Double::class.java) ?: 0.0,
                        name = child.child("name").getValue(String::class.java) ?: "Citizen",
                        timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                    // Only include users updated within last 2 minutes
                    if (System.currentTimeMillis() - loc.timestamp < 120_000) {
                        map[uid] = loc
                    }
                }
                trySend(map)
                Log.d(TAG, "Live locations updated: ${map.size} users online")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Live locations listener cancelled: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
