package com.example.cityflux.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.LiveUserLocation
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.toParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.model.SolapurDummyData
import com.example.cityflux.model.TrafficCamera
import com.example.cityflux.model.CameraSample
import com.example.cityflux.model.computePeakVehicles
import com.example.cityflux.model.computeCameraTrend
import com.example.cityflux.model.isHighCongestionForFiveMinutes
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.model.debugTrafficCameraParse
import com.example.cityflux.model.toTrafficCamera
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel that aggregates all map data from Firebase:
 *  - Traffic levels from Realtime DB
 *  - Parking spots from Firestore + live availability from Realtime DB
 *  - Incident reports from Firestore
 *
 * Exposes a single [uiState] StateFlow consumed by the MapScreen composable.
 */
class MapViewModel : ViewModel() {

    companion object {
        private const val TAG = "MapViewModel"
    }

    // ── UI state container ──
    data class MapUiState(
        val isLoading: Boolean = true,
        val trafficMap: Map<String, TrafficStatus> = emptyMap(),
        val trafficCameras: List<TrafficCamera> = emptyList(),
        val parkingSpots: List<ParkingSpot> = emptyList(),
        val parkingLive: Map<String, ParkingLive> = emptyMap(),
        val incidents: List<Report> = emptyList(),
        val liveLocations: Map<String, LiveUserLocation> = emptyMap(),
        val cameraHistory: Map<String, List<CameraSample>> = emptyMap(),
        val isOffline: Boolean = false,
        val error: String? = null
    ) {
        // Congestion analytics (computed from trafficMap)
        val highCount get() = trafficMap.count { it.value.congestionLevel.equals("HIGH", true) }
        val mediumCount get() = trafficMap.count { it.value.congestionLevel.equals("MEDIUM", true) }
        val lowCount get() = trafficMap.count { it.value.congestionLevel.equals("LOW", true) }
        val totalZones get() = trafficMap.size
        val liveUsersCount get() = liveLocations.size
        val cameraHighCount get() = trafficCameras.count { it.congestionLevel == "HIGH" }
        val cameraMediumCount get() = trafficCameras.count { it.congestionLevel == "MEDIUM" }
        val cameraLowCount get() = trafficCameras.count { it.congestionLevel == "LOW" }
        val highFor5MinCameras get() = cameraHistory.count { (_, history) -> isHighCongestionForFiveMinutes(history) }
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ── Dummy Solapur live users (refreshed every 60s to stay "alive") ──
    private val _dummyLocations = MutableStateFlow(SolapurDummyData.dummyUsers)
    
    // ── Dummy traffic data for fallback ──
    private val _dummyTraffic = MutableStateFlow(SolapurDummyData.dummyTrafficMap)

    init {
        observeTraffic()
        observeTrafficCameras()
        observeParkingLive()
        observeLiveLocations()
        fetchParkingSpots()
        fetchIncidents()
        keepDummyUsersAlive() // refresh dummy timestamps so they aren't filtered out
    }

    /** Keeps dummy user timestamps fresh so the 2-minute staleness filter in
     *  RealtimeDbService doesn't hide them. Runs every 60 seconds. */
    private fun keepDummyUsersAlive() {
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val now = System.currentTimeMillis()
                _dummyLocations.value = _dummyLocations.value.mapValues {
                    it.value.copy(timestamp = now)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Realtime DB listeners (Flow-based, auto-cancel on clear)
    // ═══════════════════════════════════════════════════════

    private fun observeTraffic() {
        viewModelScope.launch {
            RealtimeDbService.observeTraffic()
                .catch { e ->
                    Log.e(TAG, "Traffic observe error", e)
                    // Use dummy traffic data as fallback on error
                    _uiState.update { 
                        it.copy(
                            trafficMap = _dummyTraffic.value,
                            error = null,
                            isLoading = false
                        ) 
                    }
                }
                .collect { map ->
                    // Merge: dummy traffic as base, real Firebase traffic overrides
                    val mergedTraffic = if (map.isEmpty()) {
                        _dummyTraffic.value
                    } else {
                        _dummyTraffic.value.toMutableMap().apply { putAll(map) }
                    }
                    _uiState.update {
                        it.copy(
                            trafficMap = mergedTraffic,
                            isLoading = false,
                            isOffline = false,
                            error = null
                        )
                    }
                }
        }
    }

    private fun observeTrafficCameras() {
        var singularCameras: List<TrafficCamera> = emptyList()
        var pluralCameras: List<TrafficCamera> = emptyList()
        var hyphenCameras: List<TrafficCamera> = emptyList()

        fun publishMerged() {
            val mergedById = linkedMapOf<String, TrafficCamera>()
            (singularCameras + pluralCameras + hyphenCameras).forEach { cam ->
                val existing = mergedById[cam.id]
                if (existing == null || cam.lastUpdated >= existing.lastUpdated) {
                    mergedById[cam.id] = cam
                }
            }
            val cameras = mergedById.values.toList()
            Log.d(
                TAG,
                "Camera merge: singular=${singularCameras.size}, plural=${pluralCameras.size}, hyphen=${hyphenCameras.size}, merged=${cameras.size}"
            )

            val now = System.currentTimeMillis()
            val previousHistory = _uiState.value.cameraHistory
            val nextHistory = buildMap<String, List<CameraSample>> {
                cameras.forEach { camera ->
                    val existing = previousHistory[camera.id].orEmpty()
                    val updated = (existing + CameraSample(now, camera.vehicleCount))
                        .filter { now - it.timestamp <= 60 * 60_000L }
                    put(camera.id, updated)
                }
            }

            _uiState.update {
                it.copy(
                    trafficCameras = cameras,
                    cameraHistory = nextHistory,
                    isLoading = false
                )
            }
        }

        firestore.collection("traffic camera")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Traffic camera (singular) error", error)
                    singularCameras = emptyList()
                    publishMerged()
                    return@addSnapshotListener
                }
                singularCameras = snapshot?.documents
                    ?.mapNotNull { doc ->
                        val camera = doc.toTrafficCamera()
                        if (camera == null) {
                            Log.w(TAG, "Skip singular camera doc: ${doc.debugTrafficCameraParse()}")
                            null
                        } else {
                            camera
                        }
                    }
                    ?.filter { cam ->
                        val valid = cam.hasValidLocation()
                        if (!valid) Log.w(TAG, "Invalid singular camera lat/lng: id=${cam.id} lat=${cam.latitude} lng=${cam.longitude}")
                        valid
                    }
                    .orEmpty()
                publishMerged()
            }

        firestore.collection("traffic cameras")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Traffic cameras (plural) error", error)
                    pluralCameras = emptyList()
                    publishMerged()
                    return@addSnapshotListener
                }
                pluralCameras = snapshot?.documents
                    ?.mapNotNull { doc ->
                        val camera = doc.toTrafficCamera()
                        if (camera == null) {
                            Log.w(TAG, "Skip plural camera doc: ${doc.debugTrafficCameraParse()}")
                            null
                        } else {
                            camera
                        }
                    }
                    ?.filter { cam ->
                        val valid = cam.hasValidLocation()
                        if (!valid) Log.w(TAG, "Invalid plural camera lat/lng: id=${cam.id} lat=${cam.latitude} lng=${cam.longitude}")
                        valid
                    }
                    .orEmpty()
                publishMerged()
            }

        firestore.collection("traffic-cameras")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Traffic cameras (hyphen) error", error)
                    hyphenCameras = emptyList()
                    publishMerged()
                    return@addSnapshotListener
                }
                hyphenCameras = snapshot?.documents
                    ?.mapNotNull { doc ->
                        val camera = doc.toTrafficCamera()
                        if (camera == null) {
                            Log.w(TAG, "Skip hyphen camera doc: ${doc.debugTrafficCameraParse()}")
                            null
                        } else {
                            camera
                        }
                    }
                    ?.filter { cam ->
                        val valid = cam.hasValidLocation()
                        if (!valid) Log.w(TAG, "Invalid hyphen camera lat/lng: id=${cam.id} lat=${cam.latitude} lng=${cam.longitude}")
                        valid
                    }
                    .orEmpty()
                publishMerged()
            }
    }

    fun getCameraTrend(cameraId: String, windowMinutes: Int): Int {
        val history = _uiState.value.cameraHistory[cameraId].orEmpty()
        return computeCameraTrend(history, windowMinutes)
    }

    fun getCameraPeak(cameraId: String, windowHours: Int): Int {
        val history = _uiState.value.cameraHistory[cameraId].orEmpty()
        return computePeakVehicles(history, windowHours * 60L * 60_000L)
    }

    private fun observeParkingLive() {
        viewModelScope.launch {
            RealtimeDbService.observeParkingLive()
                .catch { e ->
                    Log.e(TAG, "Parking live observe error", e)
                }
                .collect { map ->
                    _uiState.update {
                        it.copy(parkingLive = map, isLoading = false)
                    }
                }
        }
    }

    private fun observeLiveLocations() {
        viewModelScope.launch {
            RealtimeDbService.observeLiveLocations()
                .catch { e ->
                    Log.e(TAG, "Live locations observe error", e)
                    _uiState.update { it.copy(error = "Live locations unavailable") }
                }
                .collect { realMap ->
                    // Merge: dummy users as base, real Firebase users override
                    val merged = _dummyLocations.value.toMutableMap()
                    merged.putAll(realMap)
                    _uiState.update {
                        it.copy(liveLocations = merged, isLoading = false)
                    }
                }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Firestore one-shot + real-time listeners
    // ═══════════════════════════════════════════════════════

    private fun fetchParkingSpots() {
        firestore.collection("parking")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Parking spots error", error)
                    return@addSnapshotListener
                }
                val spots = snapshot?.documents?.mapNotNull { doc ->
                    doc.toParkingSpot()
                } ?: emptyList()

                _uiState.update {
                    it.copy(parkingSpots = spots, isLoading = false)
                }
                Log.d(TAG, "Loaded ${spots.size} parking spots from Firestore")
            }
    }

    private fun fetchIncidents() {
        if (currentUserId.isEmpty()) return
        firestore.collection("reports")
            .whereEqualTo("userId", currentUserId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Incidents error", error)
                    return@addSnapshotListener
                }
                val reports = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                }?.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    ?: emptyList()

                _uiState.update {
                    it.copy(incidents = reports, isLoading = false)
                }
                Log.d(TAG, "Loaded ${reports.size} of my incidents from Firestore")
            }
    }

    // ═══════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
        observeTraffic()
        observeTrafficCameras()
        observeParkingLive()
        observeLiveLocations()
        fetchParkingSpots()
        fetchIncidents()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
