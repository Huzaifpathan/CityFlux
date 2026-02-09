package com.example.cityflux.ui.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.model.TrafficStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
        val parkingSpots: List<ParkingSpot> = emptyList(),
        val parkingLive: Map<String, ParkingLive> = emptyMap(),
        val incidents: List<Report> = emptyList(),
        val isOffline: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()

    init {
        observeTraffic()
        observeParkingLive()
        fetchParkingSpots()
        fetchIncidents()
    }

    // ═══════════════════════════════════════════════════════
    // Realtime DB listeners (Flow-based, auto-cancel on clear)
    // ═══════════════════════════════════════════════════════

    private fun observeTraffic() {
        viewModelScope.launch {
            RealtimeDbService.observeTraffic()
                .catch { e ->
                    Log.e(TAG, "Traffic observe error", e)
                    _uiState.update { it.copy(error = "Traffic data unavailable") }
                }
                .collect { map ->
                    _uiState.update {
                        it.copy(
                            trafficMap = map,
                            isLoading = false,
                            isOffline = false,
                            error = null
                        )
                    }
                }
        }
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
                    doc.toObject(ParkingSpot::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _uiState.update {
                    it.copy(parkingSpots = spots, isLoading = false)
                }
                Log.d(TAG, "Loaded ${spots.size} parking spots from Firestore")
            }
    }

    private fun fetchIncidents() {
        firestore.collection("reports")
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
                Log.d(TAG, "Loaded ${reports.size} incidents from Firestore")
            }
    }

    // ═══════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
        observeTraffic()
        observeParkingLive()
        fetchParkingSpots()
        fetchIncidents()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
