package com.example.cityflux.ui.police

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════════
// CongestionMapScreen — Police Command Map with live congestion overlays,
// incident markers, parking spots, zone circles with severity-based
// darkness, and police-specific analytics panel
// ═══════════════════════════════════════════════════════════════════

// ── ViewModel ─────────────────────────────────────────────

class CongestionMapViewModel : ViewModel() {

    companion object {
        private const val TAG = "CongestionMapVM"
    }

    data class CongestionUiState(
        val isLoading: Boolean = true,
        val trafficMap: Map<String, TrafficStatus> = emptyMap(),
        val parkingSpots: List<ParkingSpot> = emptyList(),
        val parkingLive: Map<String, ParkingLive> = emptyMap(),
        val incidents: List<Report> = emptyList(),
        val isOffline: Boolean = false,
        val error: String? = null
    ) {
        val highCount get() = trafficMap.count { it.value.congestionLevel.equals("HIGH", true) }
        val mediumCount get() = trafficMap.count { it.value.congestionLevel.equals("MEDIUM", true) }
        val lowCount get() = trafficMap.count { it.value.congestionLevel.equals("LOW", true) }
        val totalZones get() = trafficMap.size
        val pendingIncidents get() = incidents.count { it.status.equals("Pending", true) }
        val activeIncidents get() = incidents.count { it.status.equals("In Progress", true) }
    }

    private val _uiState = MutableStateFlow(CongestionUiState())
    val uiState: StateFlow<CongestionUiState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()

    init {
        observeTraffic()
        observeParkingLive()
        fetchParkingSpots()
        fetchIncidents()
    }

    private fun observeTraffic() {
        viewModelScope.launch {
            RealtimeDbService.observeTraffic()
                .catch { e ->
                    Log.e(TAG, "Traffic observe error", e)
                    _uiState.update { it.copy(error = "Traffic data unavailable") }
                }
                .collect { map ->
                    _uiState.update {
                        it.copy(trafficMap = map, isLoading = false, isOffline = false, error = null)
                    }
                }
        }
    }

    private fun observeParkingLive() {
        viewModelScope.launch {
            RealtimeDbService.observeParkingLive()
                .catch { e -> Log.e(TAG, "Parking live error", e) }
                .collect { map ->
                    _uiState.update { it.copy(parkingLive = map, isLoading = false) }
                }
        }
    }

    private fun fetchParkingSpots() {
        firestore.collection("parking")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "Parking error", err); return@addSnapshotListener }
                val spots = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ParkingSpot::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _uiState.update { it.copy(parkingSpots = spots, isLoading = false) }
            }
    }

    private fun fetchIncidents() {
        firestore.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "Incidents error", err); return@addSnapshotListener }
                val reports = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                }?.filter { it.latitude != 0.0 && it.longitude != 0.0 } ?: emptyList()
                _uiState.update { it.copy(incidents = reports, isLoading = false) }
            }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
        observeTraffic()
        observeParkingLive()
        fetchParkingSpots()
        fetchIncidents()
    }
}


// ═══════════════════════════════════════════════════════════════════
// Main Screen Composable
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun CongestionMapScreen(
    onBack: () -> Unit,
    vm: CongestionMapViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val scope = rememberCoroutineScope()
    val state by vm.uiState.collectAsState()

    // ── Location Permission ──
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // ── User Location ──
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val loc: Location? = fusedClient.lastLocation.await()
            loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
        } catch (_: Exception) {}
    }

    // ── Camera State ──
    val defaultLocation = LatLng(19.0760, 72.8777) // Mumbai
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation ?: defaultLocation, 13f)
    }
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, 14f), durationMs = 800
            )
        }
    }

    // ── Map Properties ──
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    val mapProperties by remember(hasLocationPermission, mapType) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = mapType,
                isTrafficEnabled = true,
                isBuildingEnabled = true
            )
        )
    }
    val uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                rotationGesturesEnabled = true,
                tiltGesturesEnabled = true,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            )
        )
    }

    // ── Selection State ──
    var selectedIncident by remember { mutableStateOf<Report?>(null) }
    var selectedParking by remember { mutableStateOf<ParkingSpot?>(null) }

    // ── Layer Toggles ──
    var showCongestionZones by remember { mutableStateOf(true) }
    var showIncidents by remember { mutableStateOf(true) }
    var showParking by remember { mutableStateOf(true) }
    var showStatsPanel by remember { mutableStateOf(false) }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailablePolice(context) }

    // ── Custom Marker Bitmaps ──
    val highBitmap = remember { createCongestionMarkerBitmap(0xFFB91C1C.toInt(), 44) }
    val medBitmap = remember { createCongestionMarkerBitmap(0xFFF59E0B.toInt(), 38) }
    val lowBitmap = remember { createCongestionMarkerBitmap(0xFF10B981.toInt(), 32) }
    val parkingGreen = remember { createCongestionMarkerBitmap(AccentParking.toArgb(), 36) }
    val parkingRed = remember { createCongestionMarkerBitmap(AccentRed.toArgb(), 36) }
    val incidentAccident = remember { createCongestionMarkerBitmap(0xFFDC2626.toInt(), 34) }
    val incidentParking = remember { createCongestionMarkerBitmap(AccentAlerts.toArgb(), 34) }
    val incidentHawker = remember { createCongestionMarkerBitmap(AccentOrange.toArgb(), 34) }
    val incidentDefault = remember { createCongestionMarkerBitmap(PrimaryBlue.toArgb(), 34) }

    // ── Congestion zone colors for circles ──
    val congestionHighFill = Color(0xFFB91C1C).copy(alpha = 0.30f)
    val congestionHighStroke = Color(0xFFB91C1C).copy(alpha = 0.65f)
    val congestionMedFill = Color(0xFFF59E0B).copy(alpha = 0.20f)
    val congestionMedStroke = Color(0xFFF59E0B).copy(alpha = 0.50f)
    val congestionLowFill = Color(0xFF10B981).copy(alpha = 0.10f)
    val congestionLowStroke = Color(0xFF10B981).copy(alpha = 0.35f)

    // Pulsing animation for HIGH severity zones
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // ══════════════════════ Google Map ══════════════════════
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapClick = {
                selectedIncident = null
                selectedParking = null
            }
        ) {

            // ──── Congestion Zone Circles ────
            if (showCongestionZones) {
                state.trafficMap.forEach { (roadId, traffic) ->
                    // Parse lat/lng from roadId format: "road_lat_lng" or use hash-based positions
                    val latLng = parseRoadLatLng(roadId, defaultLocation)
                    val level = traffic.congestionLevel.uppercase()

                    val (fillColor, strokeColor, radius) = when (level) {
                        "HIGH" -> Triple(
                            congestionHighFill.copy(alpha = pulseAlpha),
                            congestionHighStroke,
                            450.0
                        )
                        "MEDIUM" -> Triple(congestionMedFill, congestionMedStroke, 350.0)
                        else -> Triple(congestionLowFill, congestionLowStroke, 250.0)
                    }

                    Circle(
                        center = latLng,
                        radius = radius,
                        fillColor = fillColor,
                        strokeColor = strokeColor,
                        strokeWidth = 3f,
                        clickable = true,
                        onClick = {
                            // Zoom into the congestion zone on click
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                                    durationMs = 600
                                )
                            }
                        }
                    )

                    // Congestion marker at center of zone
                    val bitmap = when (level) {
                        "HIGH" -> highBitmap
                        "MEDIUM" -> medBitmap
                        else -> lowBitmap
                    }
                    Marker(
                        state = MarkerState(position = latLng),
                        title = "$level Congestion",
                        snippet = "Zone: $roadId | Last updated: ${
                            if (traffic.lastUpdated > 0) DateUtils.getRelativeTimeSpanString(
                                traffic.lastUpdated, System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS
                            ).toString() else "Unknown"
                        }",
                        icon = BitmapDescriptorFactory.fromBitmap(bitmap)
                    )
                }
            }

            // ──── Incident Markers ────
            if (showIncidents) {
                state.incidents.forEach { report ->
                    if (report.latitude == 0.0 && report.longitude == 0.0) return@forEach

                    val bitmap = when (report.type.lowercase()) {
                        "accident" -> incidentAccident
                        "illegal_parking" -> incidentParking
                        "hawker" -> incidentHawker
                        else -> incidentDefault
                    }

                    Marker(
                        state = MarkerState(
                            position = LatLng(report.latitude, report.longitude)
                        ),
                        title = report.title.ifBlank {
                            report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
                        },
                        snippet = "${report.status} | ${report.type.replace("_", " ")}",
                        icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                        onClick = {
                            selectedParking = null
                            selectedIncident = report
                            true
                        }
                    )
                }
            }

            // ──── Parking Markers ────
            if (showParking) {
                state.parkingSpots.forEach { spot ->
                    val loc = spot.location ?: return@forEach
                    val live = state.parkingLive[spot.id]
                    val available = live?.availableSlots ?: spot.availableSlots
                    val isFull = available <= 0
                    val icon = if (isFull) parkingRed else parkingGreen

                    Marker(
                        state = MarkerState(
                            position = LatLng(loc.latitude, loc.longitude)
                        ),
                        title = spot.address.ifBlank { "Parking ${spot.id}" },
                        snippet = if (isFull) "FULL" else "$available slots free",
                        icon = BitmapDescriptorFactory.fromBitmap(icon),
                        onClick = {
                            selectedIncident = null
                            selectedParking = spot
                            true
                        }
                    )
                }
            }
        }

        // ══════════════════════ Top Controls Bar ══════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
        ) {
            // Row 1: Back button + Title + Stats toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Police command badge
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = colors.cardBackground.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Congestion Command",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Stats panel toggle
                    FloatingActionButton(
                        onClick = { showStatsPanel = !showStatsPanel },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        containerColor = if (showStatsPanel) PrimaryBlue else colors.cardBackground,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Analytics,
                            contentDescription = "Stats",
                            tint = if (showStatsPanel) Color.White else colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Map type toggle
                    FloatingActionButton(
                        onClick = {
                            mapType = when (mapType) {
                                MapType.NORMAL -> MapType.SATELLITE
                                MapType.SATELLITE -> MapType.HYBRID
                                else -> MapType.NORMAL
                            }
                        },
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        containerColor = colors.cardBackground,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Layers,
                            contentDescription = "Map Type",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Row 2: Layer filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    CongestionLayerChip(
                        label = "Zones",
                        icon = Icons.Outlined.Radar,
                        isActive = showCongestionZones,
                        activeColor = Color(0xFFB91C1C),
                        count = state.totalZones,
                        onClick = { showCongestionZones = !showCongestionZones }
                    )
                }
                item {
                    CongestionLayerChip(
                        label = "Incidents",
                        icon = Icons.Outlined.Warning,
                        isActive = showIncidents,
                        activeColor = AccentIssues,
                        count = state.incidents.size,
                        onClick = { showIncidents = !showIncidents }
                    )
                }
                item {
                    CongestionLayerChip(
                        label = "Parking",
                        icon = Icons.Outlined.LocalParking,
                        isActive = showParking,
                        activeColor = AccentParking,
                        count = state.parkingSpots.size,
                        onClick = { showParking = !showParking }
                    )
                }
            }
        }

        // ══════════════════════ Live Stats Panel ══════════════════════
        AnimatedVisibility(
            visible = showStatsPanel,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 100.dp)
                .padding(horizontal = Spacing.Medium)
        ) {
            CongestionStatsPanel(state = state)
        }

        // ══════════════════════ Loading Indicator ══════════════════════
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 90.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PrimaryBlue,
                    trackColor = PrimaryBlue.copy(alpha = 0.15f)
                )
            }
        }

        // ══════════════════════ Offline Banner ══════════════════════
        AnimatedVisibility(
            visible = isOffline || state.isOffline,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 90.dp, start = Spacing.Medium, end = Spacing.Medium)
        ) {
            Surface(
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = AccentRed.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("No connection", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { vm.retry() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) { Text("Retry", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                }
            }
        }

        // ══════════════════════ Congestion Legend (bottom-left) ══════════════════════
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.Medium, bottom = Spacing.Large)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(CornerRadius.Large),
            color = colors.cardBackground.copy(alpha = 0.95f),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "CONGESTION LEVEL",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textTertiary,
                    letterSpacing = 1.sp
                )
                LegendRow(color = Color(0xFFB91C1C), label = "High", count = state.highCount)
                LegendRow(color = Color(0xFFF59E0B), label = "Medium", count = state.mediumCount)
                LegendRow(color = Color(0xFF10B981), label = "Low", count = state.lowCount)
            }
        }

        // ══════════════════════ FABs (bottom-right) ══════════════════════
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.Medium, bottom = Spacing.Large)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            horizontalAlignment = Alignment.End
        ) {
            // Zoom to high-congestion zones
            SmallFloatingActionButton(
                onClick = {
                    val highZone = state.trafficMap.entries.firstOrNull {
                        it.value.congestionLevel.equals("HIGH", true)
                    }
                    if (highZone != null) {
                        val latLng = parseRoadLatLng(highZone.key, defaultLocation)
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                                durationMs = 600
                            )
                        }
                    }
                },
                shape = CircleShape,
                containerColor = Color(0xFFB91C1C),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Filled.PriorityHigh, "Jump to High", Modifier.size(20.dp))
            }

            // Reset view to default
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                userLocation ?: defaultLocation, 13f
                            ),
                            durationMs = 600
                        )
                    }
                },
                shape = CircleShape,
                containerColor = colors.cardBackground,
                contentColor = colors.textPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.ZoomOutMap, "Reset View", Modifier.size(20.dp))
            }

            // My Location FAB
            FloatingActionButton(
                onClick = {
                    userLocation?.let { loc ->
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(loc, 16f),
                                durationMs = 600
                            )
                        }
                    }
                },
                shape = CircleShape,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.MyLocation, "My Location", Modifier.size(24.dp))
            }
        }

        // ══════════════════════ Incident Info Popup ══════════════════════
        AnimatedVisibility(
            visible = selectedIncident != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedIncident?.let { report -> PoliceIncidentCard(report) { selectedIncident = null } }
        }

        // ══════════════════════ Parking Info Popup ══════════════════════
        AnimatedVisibility(
            visible = selectedParking != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedParking?.let { spot ->
                PoliceParkingCard(
                    spot = spot,
                    availableSlots = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots,
                    onDismiss = { selectedParking = null }
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Layer Filter Chip
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CongestionLayerChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    count: Int = 0,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val bgColor = if (isActive) activeColor.copy(alpha = 0.18f)
    else colors.cardBackground.copy(alpha = 0.92f)
    val contentColor = if (isActive) activeColor else colors.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        shadowElevation = 4.dp,
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (count > 0) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            count.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Stats Panel — expandable analytics overlay
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CongestionStatsPanel(state: CongestionMapViewModel.CongestionUiState) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground.copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Analytics,
                    null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "LIVE ANALYTICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            // Congestion breakdown bar
            if (state.totalZones > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    val highFraction = state.highCount.toFloat() / state.totalZones
                    val medFraction = state.mediumCount.toFloat() / state.totalZones
                    val lowFraction = state.lowCount.toFloat() / state.totalZones

                    if (highFraction > 0) {
                        Box(
                            Modifier
                                .weight(highFraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFFB91C1C))
                        )
                    }
                    if (medFraction > 0) {
                        Box(
                            Modifier
                                .weight(medFraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFFF59E0B))
                        )
                    }
                    if (lowFraction > 0) {
                        Box(
                            Modifier
                                .weight(lowFraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(Color(0xFF10B981))
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Stats grid (2x2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "High",
                    value = state.highCount.toString(),
                    color = Color(0xFFB91C1C),
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Medium",
                    value = state.mediumCount.toString(),
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Low",
                    value = state.lowCount.toString(),
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatMiniCard(
                    title = "Pending",
                    value = state.pendingIncidents.toString(),
                    color = AccentAlerts,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Active",
                    value = state.activeIncidents.toString(),
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    title = "Total",
                    value = state.incidents.size.toString(),
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
private fun StatMiniCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontSize = 10.sp
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Legend Row
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LegendRow(color: Color, label: String, count: Int) {
    val themeColors = MaterialTheme.cityFluxColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = themeColors.textPrimary,
            modifier = Modifier.width(52.dp)
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// Police Incident Info Card (Bottom popup)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceIncidentCard(
    report: Report,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val typeColor = when (report.type.lowercase()) {
        "accident" -> AccentRed
        "illegal_parking" -> AccentAlerts
        "hawker" -> AccentOrange
        "road_damage" -> AccentIssues
        "traffic_violation" -> AccentTraffic
        else -> PrimaryBlue
    }
    val typeLabel = report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
    val timeAgo = report.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(
            it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
            .navigationBarsPadding()
            .shadow(8.dp, RoundedCornerShape(CornerRadius.XLarge), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = typeColor.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                when (report.type.lowercase()) {
                                    "accident" -> Icons.Outlined.CarCrash
                                    "illegal_parking" -> Icons.Outlined.LocalParking
                                    "hawker" -> Icons.Outlined.Store
                                    "road_damage" -> Icons.Outlined.Warning
                                    else -> Icons.Outlined.ReportProblem
                                },
                                null, tint = typeColor, modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            report.title.ifBlank { typeLabel },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = typeColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = typeColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (timeAgo.isNotBlank()) {
                                Text(
                                    " · $timeAgo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }

            // Description
            if (report.description.isNotBlank()) {
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    report.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Photo
            if (report.imageUrl.isNotBlank()) {
                Spacer(Modifier.height(Spacing.Medium))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(report.imageUrl).crossfade(true).build(),
                    contentDescription = "Incident photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(CornerRadius.Medium)),
                    contentScale = ContentScale.Crop
                )
            }

            // Status & Coordinates
            Spacer(Modifier.height(Spacing.Medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(status = report.status)
                Text(
                    "%.4f, %.4f".format(report.latitude, report.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary.copy(alpha = 0.7f)
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Police Parking Info Card (Bottom popup)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceParkingCard(
    spot: ParkingSpot,
    availableSlots: Int,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val isFull = availableSlots <= 0
    val statusColor = if (isFull) AccentRed else AccentParking

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
            .navigationBarsPadding()
            .shadow(8.dp, RoundedCornerShape(CornerRadius.XLarge), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocalParking, null, tint = statusColor, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(Spacing.Medium))
                    Column {
                        Text(
                            spot.address.ifBlank { "Parking ${spot.id}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (isFull) "FULL" else "$availableSlots / ${spot.totalSlots} slots free",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Slots row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ParkingSlotChip("Available", availableSlots.toString(), statusColor, Modifier.weight(1f))
                ParkingSlotChip("Total", spot.totalSlots.toString(), PrimaryBlue, Modifier.weight(1f))
                ParkingSlotChip("Type", if (spot.isLegal) "Legal" else "Illegal", if (spot.isLegal) AccentParking else AccentAlerts, Modifier.weight(1f))
            }
        }
    }
}


@Composable
private fun ParkingSlotChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val themeColors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = themeColors.textTertiary, fontSize = 10.sp)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════

/** Create a colored circle bitmap for map markers with a ring border. */
private fun createCongestionMarkerBitmap(color: Int, sizeDp: Int): Bitmap {
    val sizePx = (sizeDp * 2.5f).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = sizePx * 0.14f
    }
    val innerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius - sizePx * 0.07f, paint)
    canvas.drawCircle(radius, radius, radius - sizePx * 0.07f, borderPaint)
    canvas.drawCircle(radius, radius, radius * 0.22f, innerDot)
    return bitmap
}

/**
 * Parse lat/lng from road ID. Supports format "road_LAT_LNG" (underscores as separators),
 * or falls back to a deterministic offset from the default center so zones spread out on the map.
 */
private fun parseRoadLatLng(roadId: String, defaultCenter: LatLng): LatLng {
    // Try format: road_19.0760_72.8777 or area_19.0760_72.8777
    val parts = roadId.split("_")
    if (parts.size >= 3) {
        val lat = parts[parts.size - 2].toDoubleOrNull()
        val lng = parts[parts.size - 1].toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
            return LatLng(lat, lng)
        }
    }
    // Fallback: hash-based deterministic spread around default center
    val hash = roadId.hashCode()
    val latOffset = ((hash % 1000) / 1000.0) * 0.06 - 0.03
    val lngOffset = (((hash / 1000) % 1000) / 1000.0) * 0.06 - 0.03
    return LatLng(defaultCenter.latitude + latOffset, defaultCenter.longitude + lngOffset)
}

/** Check network connectivity. */
private fun isNetworkAvailablePolice(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val cap = cm.getNetworkCapabilities(network) ?: return false
    return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
