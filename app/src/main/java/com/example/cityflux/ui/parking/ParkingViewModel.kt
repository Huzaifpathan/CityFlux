package com.example.cityflux.ui.parking

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for ParkingScreen — aggregates Firestore parking spots
 * with Realtime DB live availability. Supports filtering and sorting.
 */
class ParkingViewModel : ViewModel() {

    companion object {
        private const val TAG = "ParkingViewModel"
    }

    // ── Filter / Sort enums ──
    enum class SortMode { NEAREST, MOST_AVAILABLE, NAME }
    enum class LegalFilter { ALL, LEGAL, ILLEGAL }

    // ── UI State ──
    data class ParkingUiState(
        val isLoading: Boolean = true,
        val parkingSpots: List<ParkingSpot> = emptyList(),
        val parkingLive: Map<String, ParkingLive> = emptyMap(),
        val sortMode: SortMode = SortMode.NEAREST,
        val legalFilter: LegalFilter = LegalFilter.ALL,
        val showAvailableOnly: Boolean = false,
        val isOffline: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ParkingUiState())
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    // User location for distance calculation
    private val _userLocation = MutableStateFlow<Location?>(null)

    private val firestore = FirebaseFirestore.getInstance()

    init {
        fetchParkingSpots()
        observeParkingLive()
    }

    // ═══════════════════════════════════════════════════════
    // Data Sources
    // ═══════════════════════════════════════════════════════

    private fun fetchParkingSpots() {
        firestore.collection("parking")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Parking spots error", error)
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load parking") }
                    return@addSnapshotListener
                }
                val spots = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ParkingSpot::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                _uiState.update { it.copy(parkingSpots = spots, isLoading = false, error = null) }
                Log.d(TAG, "Loaded ${spots.size} parking spots")
            }
    }

    private fun observeParkingLive() {
        viewModelScope.launch {
            RealtimeDbService.observeParkingLive()
                .catch { e ->
                    Log.e(TAG, "Parking live error", e)
                    _uiState.update { it.copy(error = "Live data unavailable") }
                }
                .collect { map ->
                    _uiState.update { it.copy(parkingLive = map, isLoading = false, isOffline = false) }
                }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Filtered + Sorted List
    // ═══════════════════════════════════════════════════════

    fun getFilteredSpots(state: ParkingUiState): List<ParkingSpot> {
        var spots = state.parkingSpots

        // Legal filter
        spots = when (state.legalFilter) {
            LegalFilter.ALL -> spots
            LegalFilter.LEGAL -> spots.filter { it.isLegal }
            LegalFilter.ILLEGAL -> spots.filter { !it.isLegal }
        }

        // Available only filter
        if (state.showAvailableOnly) {
            spots = spots.filter { spot ->
                val avail = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
                avail > 0
            }
        }

        // Sort
        spots = when (state.sortMode) {
            SortMode.NEAREST -> {
                val loc = _userLocation.value
                if (loc != null) {
                    spots.sortedBy { spot ->
                        spot.location?.let { geo ->
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                loc.latitude, loc.longitude,
                                geo.latitude, geo.longitude,
                                results
                            )
                            results[0]
                        } ?: Float.MAX_VALUE
                    }
                } else spots
            }
            SortMode.MOST_AVAILABLE -> spots.sortedByDescending { spot ->
                state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
            }
            SortMode.NAME -> spots.sortedBy { it.address.ifBlank { it.id } }
        }

        return spots
    }

    /** Compute distance from user to a parking spot in meters. */
    fun distanceTo(spot: ParkingSpot): Float? {
        val loc = _userLocation.value ?: return null
        val geo = spot.location ?: return null
        val results = FloatArray(1)
        Location.distanceBetween(loc.latitude, loc.longitude, geo.latitude, geo.longitude, results)
        return results[0]
    }

    /** Find the nearest parking with available slots. */
    fun findNearestAvailable(): ParkingSpot? {
        val state = _uiState.value
        val loc = _userLocation.value ?: return state.parkingSpots.firstOrNull()
        return state.parkingSpots
            .filter { spot ->
                val avail = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
                avail > 0 && spot.location != null
            }
            .minByOrNull { spot ->
                val geo = spot.location!!
                val results = FloatArray(1)
                Location.distanceBetween(loc.latitude, loc.longitude, geo.latitude, geo.longitude, results)
                results[0]
            }
    }

    // ═══════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════

    fun setUserLocation(location: Location) {
        _userLocation.value = location
    }

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setLegalFilter(filter: LegalFilter) {
        _uiState.update { it.copy(legalFilter = filter) }
    }

    fun toggleAvailableOnly() {
        _uiState.update { it.copy(showAvailableOnly = !it.showAvailableOnly) }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetchParkingSpots()
        observeParkingLive()
    }
}
