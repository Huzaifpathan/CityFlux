package com.example.cityflux.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.example.cityflux.service.LocationTrackingService
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
// MapScreen — Full-screen Google Map with live Firebase data
// ═══════════════════════════════════════════════════════════════════

data class LiveUserLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val speed: Int = 0,
    val heading: Double = 0.0,
    val name: String = "Citizen",
    val timestamp: Long = 0L
)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onNavigateToReport: () -> Unit = {},
    onNavigateToParking: () -> Unit = {},
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by vm.uiState.collectAsState()

    // ── Initialize Maps renderer (ensures latest renderer is used) ──
    var mapLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(
                context,
                com.google.android.gms.maps.MapsInitializer.Renderer.LATEST
            ) { renderer ->
                Log.d("MapScreen", "Maps renderer initialized: $renderer")
            }
        } catch (e: Exception) {
            Log.e("MapScreen", "MapsInitializer failed", e)
        }
    }

    // ── Analytics ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("map_opened", null) } catch (_: Exception) {}
    }

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
        } catch (_: Exception) { /* graceful fallback */ }
    }

    // ── Camera / Map State ──
    val defaultLocation = LatLng(19.0760, 72.8777) // Mumbai default
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation ?: defaultLocation, 14f)
    }

    // Move camera when user location is first obtained
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, 15f),
                durationMs = 800
            )
        }
    }

    // ── Map Type Toggle ──
    var currentMapType by remember { mutableStateOf(MapType.NORMAL) }

    // ── Map Properties ──
    val mapProperties by remember(hasLocationPermission, currentMapType) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = currentMapType,
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

    // ── Selected Marker State ──
    var selectedParking by remember { mutableStateOf<ParkingSpot?>(null) }
    var selectedIncident by remember { mutableStateOf<Report?>(null) }

    // ── Connectivity Check ──
    val isOffline = remember { !isNetworkAvailable(context) }

    // ── Layer Visibility ──
    var showParking by remember { mutableStateOf(true) }
    var showIncidents by remember { mutableStateOf(true) }
    var showZones by remember { mutableStateOf(true) }

    // ── Stats Panel Toggle ──
    var showStatsPanel by remember { mutableStateOf(false) }

    // ── Congestion Breakdown (derived from ViewModel traffic data) ──
    val highZones = remember(state.trafficMap) {
        state.trafficMap.entries.filter { it.value.congestionLevel.equals("HIGH", true) }
    }
    val mediumZones = remember(state.trafficMap) {
        state.trafficMap.entries.filter { it.value.congestionLevel.equals("MEDIUM", true) }
    }
    val lowZones = remember(state.trafficMap) {
        state.trafficMap.entries.filter {
            !it.value.congestionLevel.equals("HIGH", true) &&
            !it.value.congestionLevel.equals("MEDIUM", true)
        }
    }

    // ── Pulsing animation for HIGH zones ──
    val infiniteTransition = rememberInfiniteTransition(label = "zone_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // ── Search Location State ──
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchPin by remember { mutableStateOf<LatLng?>(null) }
    val focusManager = LocalFocusManager.current

    // ── Emergency Services Layer ──
    var showEmergencyServices by remember { mutableStateOf(false) }

    // ── Heatmap Overlay ──
    var showHeatmap by remember { mutableStateOf(false) }

    // ── Radius Filter ──
    var radiusKm by remember { mutableStateOf(0f) } // 0 = no filter
    var showRadiusSlider by remember { mutableStateOf(false) }

    // ── Route Planner ──
    var routeMode by remember { mutableStateOf(false) }
    var routeStart by remember { mutableStateOf<LatLng?>(null) }
    var routeEnd by remember { mutableStateOf<LatLng?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDurationMin by remember { mutableStateOf(0) }
    var routeDistanceKm by remember { mutableStateOf(0.0) }
    var isRouteLoading by remember { mutableStateOf(false) }

    // ── Live Location Tracking (synced with SharedPreferences) ──
    val livePrefs = remember { context.getSharedPreferences("profile_settings", android.content.Context.MODE_PRIVATE) }
    var isLiveSharing by remember { mutableStateOf(livePrefs.getBoolean("live_location", false)) }
    var showLiveUsers by remember { mutableStateOf(isLiveSharing) } // auto-show if already sharing
    var liveLocations by remember { mutableStateOf<Map<String, LiveUserLocation>>(emptyMap()) }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    // Check if service is actually running (own UID in RTDB = sharing)
    LaunchedEffect(Unit) {
        if (isLiveSharing) showLiveUsers = true
    }

    // ── RTDB Listener for live locations ──
    DisposableEffect(showLiveUsers) {
        val rtdb = FirebaseDatabase.getInstance()
        val ref = rtdb.getReference("live_locations")
        val listener = if (showLiveUsers) {
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val map = mutableMapOf<String, LiveUserLocation>()
                    snapshot.children.forEach { child ->
                        val uid = child.key ?: return@forEach
                        val loc = LiveUserLocation(
                            lat = child.child("lat").getValue(Double::class.java) ?: 0.0,
                            lng = child.child("lng").getValue(Double::class.java) ?: 0.0,
                            speed = child.child("speed").getValue(Int::class.java) ?: 0,
                            heading = child.child("heading").getValue(Double::class.java) ?: 0.0,
                            name = child.child("name").getValue(String::class.java) ?: "Citizen",
                            timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        )
                        // Only show users updated within last 2 minutes
                        if (System.currentTimeMillis() - loc.timestamp < 120_000) {
                            map[uid] = loc
                        }
                    }
                    liveLocations = map
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e("MapScreen", "Live locations listener cancelled", error.toException())
                }
            }.also { ref.addValueEventListener(it) }
        } else null

        onDispose {
            listener?.let { ref.removeEventListener(it) }
            if (!showLiveUsers) liveLocations = emptyMap()
        }
    }

    // ── Emergency Services Marker Bitmaps ──
    val hospitalBitmap = remember { createCircleMarkerBitmap(AccentGreen.toArgb(), 34) }
    val policeBitmap = remember { createCircleMarkerBitmap(PrimaryBlue.toArgb(), 34) }
    val fireBitmap = remember { createCircleMarkerBitmap(AccentOrange.toArgb(), 34) }

    // ── Custom Marker Bitmaps (cached once) ──
    val parkingGreenBitmap = remember { createCircleMarkerBitmap(AccentParking.toArgb(), 40) }
    val parkingRedBitmap = remember { createCircleMarkerBitmap(AccentRed.toArgb(), 40) }
    val incidentAccidentBitmap = remember { createCircleMarkerBitmap(AccentRed.toArgb(), 36) }
    val incidentParkingBitmap = remember { createCircleMarkerBitmap(AccentAlerts.toArgb(), 36) }
    val incidentHawkerBitmap = remember { createCircleMarkerBitmap(AccentOrange.toArgb(), 36) }
    val incidentDefaultBitmap = remember { createCircleMarkerBitmap(PrimaryBlue.toArgb(), 36) }

    // ── Map load timeout diagnostic ──
    var mapLoadTimedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        if (!mapLoaded) {
            mapLoadTimedOut = true
            Log.w("MapScreen", "Map did not load within 5s — check API key & restrictions")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ══════════════════════ Google Map ══════════════════════
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapLoaded = {
                mapLoaded = true
                Log.d("MapScreen", "Google Map loaded successfully")
            },
            onMapClick = { latLng ->
                selectedParking = null
                selectedIncident = null
                // Route planner tap handling
                if (routeMode) {
                    if (routeStart == null) {
                        routeStart = latLng
                    } else if (routeEnd == null) {
                        routeEnd = latLng
                        val start = routeStart!!
                        val end = latLng
                        // Fetch real road route from Directions API
                        scope.launch {
                            isRouteLoading = true
                            val apiKey = getMapApiKey(context)
                            Log.d("MapScreen", "Route: API key length=${apiKey.length}, start=$start, end=$end")
                            val result = fetchDirectionsRoute(start, end, apiKey)
                            routePoints = result.points
                            routeDurationMin = result.durationMinutes
                            routeDistanceKm = result.distanceKm
                            isRouteLoading = false
                            // Show user feedback
                            if (result.points.size <= 2) {
                                snackbarHostState.showSnackbar(
                                    "⚠ Road route unavailable — check Logcat \"MapScreen\" for details"
                                )
                            }
                            // Auto-zoom to fit the full route
                            if (result.points.isNotEmpty()) {
                                val boundsBuilder = LatLngBounds.builder()
                                result.points.forEach { boundsBuilder.include(it) }
                                // Include user location in bounds if available
                                userLocation?.let { boundsBuilder.include(it) }
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                                    durationMs = 800
                                )
                            }
                        }
                    }
                }
            }
        ) {
            // ──────── Parking Markers ────────
            if (showParking) {
                state.parkingSpots.forEach { spot ->
                    val loc = spot.location ?: return@forEach
                    val live = state.parkingLive[spot.id]
                    val available = live?.availableSlots ?: spot.availableSlots
                    val isFull = available <= 0
                    val markerIcon = if (isFull) parkingRedBitmap else parkingGreenBitmap

                    Marker(
                        state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                        title = spot.address.ifBlank { "Parking ${spot.id}" },
                        snippet = if (isFull) "FULL" else "$available slots available",
                        icon = BitmapDescriptorFactory.fromBitmap(markerIcon),
                        onClick = {
                            try {
                                Firebase.analytics.logEvent("parking_marker_clicked", null)
                            } catch (_: Exception) {}
                            selectedIncident = null
                            selectedParking = spot
                            true
                        }
                    )
                }
            }

            // ──────── Incident Markers ────────
            if (showIncidents) {
                state.incidents.forEach { report ->
                    if (report.latitude == 0.0 && report.longitude == 0.0) return@forEach

                    val bitmap = when (report.type.lowercase()) {
                        "accident" -> incidentAccidentBitmap
                        "illegal_parking" -> incidentParkingBitmap
                        "hawker" -> incidentHawkerBitmap
                        else -> incidentDefaultBitmap
                    }

                    Marker(
                        state = MarkerState(
                            position = LatLng(report.latitude, report.longitude)
                        ),
                        title = report.title.ifBlank {
                            report.type.replace("_", " ")
                                .replaceFirstChar { it.uppercase() }
                        },
                        snippet = report.status,
                        icon = BitmapDescriptorFactory.fromBitmap(bitmap),
                        onClick = {
                            selectedParking = null
                            selectedIncident = report
                            true
                        }
                    )
                }
            }

            // ──────── Congestion Zone Circles ────────
            if (showZones) {
                state.trafficMap.forEach { (roadId, traffic) ->
                    val latLng = parseRoadLatLng(roadId)
                    if (latLng != null) {
                        val isHigh = traffic.congestionLevel.equals("HIGH", true)
                        val isMedium = traffic.congestionLevel.equals("MEDIUM", true)
                        val zoneColor = when {
                            isHigh -> Color(0xFFEF4444)
                            isMedium -> Color(0xFFF97316)
                            else -> Color(0xFF22C55E)
                        }
                        val radius = when {
                            isHigh -> 450.0
                            isMedium -> 350.0
                            else -> 250.0
                        }
                        val alpha = if (isHigh) pulseAlpha else if (isMedium) 0.3f else 0.2f

                        Circle(
                            center = latLng,
                            radius = radius,
                            fillColor = zoneColor.copy(alpha = alpha),
                            strokeColor = zoneColor.copy(alpha = 0.6f),
                            strokeWidth = 2f,
                            clickable = true,
                            onClick = {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                                        durationMs = 600
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ──────── Heatmap Overlay (incident density) ────────
            if (showHeatmap && state.incidents.isNotEmpty()) {
                val heatCells = remember(state.incidents) {
                    buildHeatmapCells(state.incidents)
                }
                heatCells.forEach { cell ->
                    Circle(
                        center = cell.center,
                        radius = cell.radius,
                        fillColor = cell.color,
                        strokeColor = Color.Transparent,
                        strokeWidth = 0f
                    )
                }
            }

            // ──────── Search Pin Marker ────────
            searchPin?.let { pin ->
                Marker(
                    state = MarkerState(position = pin),
                    title = "Search Result",
                    snippet = searchQuery,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )
            }

            // ──────── Radius Filter Circle ────────
            if (radiusKm > 0f && userLocation != null) {
                Circle(
                    center = userLocation!!,
                    radius = (radiusKm * 1000).toDouble(),
                    fillColor = PrimaryBlue.copy(alpha = 0.08f),
                    strokeColor = PrimaryBlue.copy(alpha = 0.5f),
                    strokeWidth = 2f
                )
            }

            // ──────── Route Polyline (Google Maps style) ────────
            if (routePoints.isNotEmpty()) {
                val routePurple = Color(0xFF4A0FBF)

                // Shadow polyline (wider, translucent)
                Polyline(
                    points = routePoints,
                    color = routePurple.copy(alpha = 0.25f),
                    width = 22f,
                    geodesic = true
                )
                // Main route polyline (purple like Google Maps)
                Polyline(
                    points = routePoints,
                    color = routePurple,
                    width = 10f,
                    geodesic = true
                )

                // Dotted walking line: user location → nearest route point
                if (userLocation != null) {
                    val nearestPt = routePoints.minByOrNull { pt ->
                        haversineDistance(userLocation!!, pt)
                    }
                    if (nearestPt != null) {
                        Polyline(
                            points = listOf(userLocation!!, nearestPt),
                            color = Color(0xFF555555),
                            width = 6f,
                            pattern = listOf(Dot(), Gap(12f)),
                            geodesic = true
                        )
                    }
                }

                // Dotted walking line: route end → destination marker
                routeEnd?.let { dest ->
                    val routeEndPt = routePoints.lastOrNull()
                    if (routeEndPt != null && haversineDistance(routeEndPt, dest) > 0.01) {
                        Polyline(
                            points = listOf(routeEndPt, dest),
                            color = Color(0xFF555555),
                            width = 6f,
                            pattern = listOf(Dot(), Gap(12f)),
                            geodesic = true
                        )
                    }
                }

                // Start marker
                routePoints.firstOrNull()?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "Start",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
                // Destination marker
                routePoints.lastOrNull()?.let { end ->
                    Marker(
                        state = MarkerState(position = end),
                        title = "Destination",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }

                // ETA badge marker at route midpoint
                if (routeDurationMin > 0) {
                    val midIdx = routePoints.size / 2
                    val midPoint = routePoints[midIdx.coerceIn(0, routePoints.lastIndex)]
                    val etaBitmap = remember(routeDurationMin) {
                        createEtaBadgeBitmap(routeDurationMin)
                    }
                    Marker(
                        state = MarkerState(position = midPoint),
                        title = "$routeDurationMin min",
                        icon = BitmapDescriptorFactory.fromBitmap(etaBitmap),
                        anchor = Offset(0.5f, 0.5f),
                        flat = true
                    )
                }
            }

            // ──────── Route Tap Markers (before route computed) ────────
            if (routeMode && routePoints.isEmpty()) {
                routeStart?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "Start Point",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
                routeEnd?.let { end ->
                    Marker(
                        state = MarkerState(position = end),
                        title = "Destination",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }

            // ──────── Emergency Services Markers ────────
            if (showEmergencyServices && userLocation != null) {
                val nearbyEmergency = remember(userLocation) {
                    generateEmergencyServiceLocations(userLocation!!)
                }
                nearbyEmergency.forEach { svc ->
                    val bmp = when (svc.type) {
                        "hospital" -> hospitalBitmap
                        "police" -> policeBitmap
                        else -> fireBitmap
                    }
                    Marker(
                        state = MarkerState(position = svc.location),
                        title = svc.name,
                        snippet = svc.type.replaceFirstChar { it.uppercase() },
                        icon = BitmapDescriptorFactory.fromBitmap(bmp),
                        onClick = {
                            selectedParking = null
                            selectedIncident = null
                            scope.launch {
                                snackbarHostState.showSnackbar("${svc.name} — Tap for directions")
                            }
                            true
                        }
                    )
                }
            }

            // ──────── Live User Location Markers ────────
            if (showLiveUsers) {
                liveLocations.forEach { (uid, loc) ->
                    val isMe = uid == currentUserId
                    val position = LatLng(loc.lat, loc.lng)
                    val markerColor = if (isMe) BitmapDescriptorFactory.HUE_AZURE 
                                      else BitmapDescriptorFactory.HUE_GREEN
                    
                    Marker(
                        state = MarkerState(position = position),
                        title = if (isMe) "You" else loc.name,
                        snippet = "${loc.speed} km/h",
                        icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                        rotation = loc.heading.toFloat(),
                        flat = true,
                        anchor = Offset(0.5f, 0.5f),
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    "${if (isMe) "You" else loc.name} • ${loc.speed} km/h"
                                )
                            }
                            true
                        }
                    )
                }
            }
        }

        // ══════════════════════ Top Row: Back + Title + Toggles ══════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button with title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    FloatingActionButton(
                        onClick = onBack,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        containerColor = colors.cardBackground,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Title card
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Large),
                        color = colors.cardBackground.copy(alpha = 0.95f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(PrimaryBlue, GradientBright)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Map,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    "City Map",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text(
                                    "Live traffic & parking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Analytics + Map Type toggles
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    // Analytics toggle
                    FloatingActionButton(
                        onClick = { showStatsPanel = !showStatsPanel },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        containerColor = if (showStatsPanel) PrimaryBlue
                            else colors.cardBackground.copy(alpha = 0.95f),
                        elevation = FloatingActionButtonDefaults.elevation(3.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Analytics,
                            "Analytics",
                            tint = if (showStatsPanel) Color.White else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Map type toggle
                    FloatingActionButton(
                        onClick = {
                            currentMapType = when (currentMapType) {
                                MapType.NORMAL -> MapType.SATELLITE
                                MapType.SATELLITE -> MapType.HYBRID
                                else -> MapType.NORMAL
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        containerColor = colors.cardBackground.copy(alpha = 0.95f),
                        elevation = FloatingActionButtonDefaults.elevation(3.dp)
                    ) {
                        Icon(
                            when (currentMapType) {
                                MapType.SATELLITE -> Icons.Outlined.Satellite
                                MapType.HYBRID -> Icons.Outlined.Layers
                                else -> Icons.Outlined.Map
                            },
                            "Map Type",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Layer toggle chips with badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                LayerChipWithBadge(
                    label = "Zones",
                    icon = Icons.Outlined.RadioButtonChecked,
                    isActive = showZones,
                    activeColor = AccentTraffic,
                    badge = state.trafficMap.size.let { if (it > 0) "$it" else null },
                    onClick = { showZones = !showZones }
                )
                LayerChipWithBadge(
                    label = "Parking",
                    icon = Icons.Outlined.LocalParking,
                    isActive = showParking,
                    activeColor = AccentParking,
                    badge = state.parkingSpots.size.let { if (it > 0) "$it" else null },
                    onClick = { showParking = !showParking }
                )
                LayerChipWithBadge(
                    label = "Incidents",
                    icon = Icons.Outlined.Warning,
                    isActive = showIncidents,
                    activeColor = AccentIssues,
                    badge = state.incidents.size.let { if (it > 0) "$it" else null },
                    onClick = { showIncidents = !showIncidents }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // ── Extra Feature Chips Row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                LayerChipWithBadge(
                    label = "Services",
                    icon = Icons.Outlined.LocalHospital,
                    isActive = showEmergencyServices,
                    activeColor = AccentGreen,
                    badge = null,
                    onClick = { showEmergencyServices = !showEmergencyServices }
                )
                LayerChipWithBadge(
                    label = "Radius",
                    icon = Icons.Outlined.MyLocation,
                    isActive = showRadiusSlider,
                    activeColor = PrimaryBlue,
                    badge = if (radiusKm > 0f) "${radiusKm.toInt()}km" else null,
                    onClick = { showRadiusSlider = !showRadiusSlider }
                )
                LayerChipWithBadge(
                    label = "Heat",
                    icon = Icons.Outlined.Whatshot,
                    isActive = showHeatmap,
                    activeColor = AccentRed,
                    badge = null,
                    onClick = { showHeatmap = !showHeatmap }
                )
                LayerChipWithBadge(
                    label = "Live",
                    icon = Icons.Outlined.People,
                    isActive = showLiveUsers,
                    activeColor = AccentGreen,
                    badge = if (showLiveUsers && liveLocations.isNotEmpty()) "${liveLocations.size}" else null,
                    onClick = { showLiveUsers = !showLiveUsers }
                )
                LayerChipWithBadge(
                    label = "Route",
                    icon = Icons.Outlined.AltRoute,
                    isActive = routeMode,
                    activeColor = GradientBright,
                    badge = null,
                    onClick = {
                        routeMode = !routeMode
                        if (!routeMode) {
                            routeStart = null
                            routeEnd = null
                            routePoints = emptyList()
                            routeDurationMin = 0
                            routeDistanceKm = 0.0
                            isRouteLoading = false
                        }
                    }
                )
                // Search toggle
                FloatingActionButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    containerColor = if (showSearchBar) PrimaryBlue
                        else colors.cardBackground.copy(alpha = 0.95f),
                    elevation = FloatingActionButtonDefaults.elevation(3.dp)
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        "Search",
                        tint = if (showSearchBar) Color.White else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── Search Bar ──
            AnimatedVisibility(
                visible = showSearchBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Small),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    color = colors.cardBackground.copy(alpha = 0.97f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text("Search location...", fontSize = 13.sp)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    scope.launch {
                                        val result = geocodeAddress(context, searchQuery)
                                        isSearching = false
                                        if (result != null) {
                                            searchPin = result
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(result, 15f),
                                                durationMs = 800
                                            )
                                        } else {
                                            snackbarHostState.showSnackbar("Location not found")
                                        }
                                    }
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = colors.textPrimary
                            )
                        )
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (searchQuery.isNotBlank()) {
                                        isSearching = true
                                        scope.launch {
                                            val result = geocodeAddress(context, searchQuery)
                                            isSearching = false
                                            if (result != null) {
                                                searchPin = result
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(result, 15f),
                                                    durationMs = 800
                                                )
                                            } else {
                                                snackbarHostState.showSnackbar("Location not found")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Search,
                                    "Search",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (searchPin != null) {
                            IconButton(
                                onClick = {
                                    searchPin = null
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    "Clear",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── Radius Slider ──
            AnimatedVisibility(
                visible = showRadiusSlider,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Small),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    color = colors.cardBackground.copy(alpha = 0.97f),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filter Radius",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                if (radiusKm == 0f) "Off" else "${radiusKm.toInt()} km",
                                style = MaterialTheme.typography.labelMedium,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = radiusKm,
                            onValueChange = { radiusKm = it },
                            valueRange = 0f..20f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryBlue,
                                activeTrackColor = PrimaryBlue
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Off", fontSize = 9.sp, color = colors.textTertiary)
                            Text("5km", fontSize = 9.sp, color = colors.textTertiary)
                            Text("10km", fontSize = 9.sp, color = colors.textTertiary)
                            Text("15km", fontSize = 9.sp, color = colors.textTertiary)
                            Text("20km", fontSize = 9.sp, color = colors.textTertiary)
                        }
                    }
                }
            }

            // ── Route Planner Instructions ──
            AnimatedVisibility(
                visible = routeMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.Small),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    color = colors.cardBackground.copy(alpha = 0.97f),
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.AltRoute,
                                    null,
                                    tint = GradientBright,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Route Planner",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Reset route
                                IconButton(
                                    onClick = {
                                        routeStart = null
                                        routeEnd = null
                                        routePoints = emptyList()
                                        routeDurationMin = 0
                                        routeDistanceKm = 0.0
                                        isRouteLoading = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Refresh,
                                        "Reset",
                                        tint = AccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Step indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RouteStepIndicator(
                                step = 1,
                                label = if (routeStart != null) "Start ✓" else "Tap start",
                                isDone = routeStart != null,
                                modifier = Modifier.weight(1f)
                            )
                            RouteStepIndicator(
                                step = 2,
                                label = if (routeEnd != null) "End ✓" else "Tap end",
                                isDone = routeEnd != null,
                                modifier = Modifier.weight(1f)
                            )
                            RouteStepIndicator(
                                step = 3,
                                label = when {
                                    routePoints.isNotEmpty() -> "Done ✓"
                                    isRouteLoading -> "Loading..."
                                    else -> "Route"
                                },
                                isDone = routePoints.isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Congestion warning on route
                        if (routePoints.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF4A0FBF).copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Distance + ETA from Directions API
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "%.1f km".format(routeDistanceKm),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A0FBF)
                                            )
                                            Text(
                                                "Distance",
                                                fontSize = 9.sp,
                                                color = colors.textTertiary
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "$routeDurationMin min",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen
                                            )
                                            Text(
                                                "ETA",
                                                fontSize = 9.sp,
                                                color = colors.textTertiary
                                            )
                                        }
                                    }
                                    // Open in Google Maps (secondary)
                                    Surface(
                                        onClick = {
                                            val start = routeStart!!
                                            val end = routeEnd!!
                                            val uri = Uri.parse(
                                                "https://www.google.com/maps/dir/${start.latitude},${start.longitude}/${end.latitude},${end.longitude}"
                                            )
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                                    setPackage("com.google.android.apps.maps")
                                                })
                                            } catch (_: Exception) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.OpenInNew,
                                                null,
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                "Google Maps",
                                                fontSize = 10.sp,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            val congestedOnRoute = state.trafficMap.count { (roadId, traffic) ->
                                val ll = parseRoadLatLng(roadId) ?: return@count false
                                traffic.congestionLevel.equals("HIGH", true) &&
                                    routePoints.any { rp ->
                                        haversineDistance(rp, ll) < 0.5
                                    }
                            }
                            if (congestedOnRoute > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentRed.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            "⚠ $congestedOnRoute high-congestion zone(s) on route",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AccentRed,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════ Analytics Stats Panel ══════════════════════
        AnimatedVisibility(
            visible = showStatsPanel,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 110.dp)
        ) {
            CongestionStatsPanel(
                highCount = highZones.size,
                mediumCount = mediumZones.size,
                lowCount = lowZones.size,
                totalZones = state.trafficMap.size,
                pendingIncidents = state.incidents.count {
                    it.status.equals("Pending", true) || it.status.equals("submitted", true)
                },
                totalIncidents = state.incidents.size
            )
        }

        // ══════════════════════ Loading Indicator ══════════════════════
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
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
                .padding(top = 56.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = AccentRed.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Spacing.Medium,
                        vertical = Spacing.Small
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        "No internet connection",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { vm.retry() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ══════════════════════ Map Load Warning ══════════════════════
        AnimatedVisibility(
            visible = mapLoadTimedOut && !mapLoaded,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = AccentOrange.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = Spacing.Medium,
                        vertical = Spacing.Small
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        "Map not loading — check API key in Google Cloud Console",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
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
            // Report Issue FAB
            SmallFloatingActionButton(
                onClick = { onNavigateToReport() },
                shape = CircleShape,
                containerColor = AccentIssues,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.ReportProblem, "Report Issue", Modifier.size(20.dp))
            }

            // Jump to Crisis FAB (only if HIGH zones exist)
            if (highZones.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = {
                        val firstHigh = highZones.firstOrNull()
                        if (firstHigh != null) {
                            val latLng = parseRoadLatLng(firstHigh.key)
                            if (latLng != null) {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                                        durationMs = 600
                                    )
                                }
                            }
                        }
                    },
                    shape = CircleShape,
                    containerColor = AccentRed,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp)
                ) {
                    Icon(Icons.Outlined.GpsFixed, "Jump to Crisis", Modifier.size(20.dp))
                }
            }

            // Nearest Parking FAB
            SmallFloatingActionButton(
                onClick = {
                    try {
                        Firebase.analytics.logEvent("navigate_to_parking_clicked", null)
                    } catch (_: Exception) {}
                    val nearestParking = findNearestParking(
                        userLocation = userLocation,
                        spots = state.parkingSpots,
                        live = state.parkingLive
                    )
                    if (nearestParking != null) {
                        val loc = nearestParking.location ?: return@SmallFloatingActionButton
                        scope.launch {
                            selectedParking = nearestParking
                            selectedIncident = null
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude), 17f
                                ),
                                durationMs = 600
                            )
                        }
                    } else {
                        onNavigateToParking()
                    }
                },
                shape = CircleShape,
                containerColor = AccentParking,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.LocalParking, "Nearest Parking", Modifier.size(20.dp))
            }

            // Reset View FAB
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
                contentColor = colors.textSecondary,
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

            // Fit Route in-app FAB
            if (routeMode && routePoints.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val boundsBuilder = LatLngBounds.builder()
                            routePoints.forEach { boundsBuilder.include(it) }
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120),
                                durationMs = 800
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = GradientBright,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(Icons.Outlined.ZoomOutMap, "Fit Route", Modifier.size(18.dp))
                }
            }
        }

        // ── Go Live Toggle Button ──
        AnimatedVisibility(
            visible = mapLoaded,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Spacing.Large, bottom = 100.dp)
        ) {
            val liveButtonAlpha by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "live_button_pulse"
            )
            
            FloatingActionButton(
                onClick = {
                    val serviceIntent = Intent(context, LocationTrackingService::class.java)
                    if (isLiveSharing) {
                        serviceIntent.action = LocationTrackingService.ACTION_STOP
                        context.startService(serviceIntent)
                        isLiveSharing = false
                        livePrefs.edit().putBoolean("live_location", false).apply()
                        scope.launch {
                            snackbarHostState.showSnackbar("📍 Location sharing stopped")
                        }
                    } else {
                        serviceIntent.action = LocationTrackingService.ACTION_START
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                        isLiveSharing = true
                        showLiveUsers = true
                        livePrefs.edit().putBoolean("live_location", true).apply()
                        scope.launch {
                            snackbarHostState.showSnackbar("📍 Sharing your live location")
                        }
                    }
                },
                containerColor = if (isLiveSharing) AccentGreen.copy(alpha = liveButtonAlpha) else MaterialTheme.cityFluxColors.cardBackground,
                contentColor = if (isLiveSharing) Color.White else MaterialTheme.cityFluxColors.textPrimary,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    if (isLiveSharing) Icons.Filled.LocationOn else Icons.Outlined.LocationOn,
                    contentDescription = if (isLiveSharing) "Stop sharing" else "Go live",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // ── Live Users Count Badge ──
        if (showLiveUsers && liveLocations.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.Large, bottom = 155.dp),
                shape = RoundedCornerShape(CornerRadius.Round),
                color = AccentGreen,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        "${liveLocations.size} live",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // ══════════════════════ Map Legend (bottom-left) ══════════════════════
        if (showZones && state.trafficMap.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.Medium, bottom = Spacing.Large)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(CornerRadius.Large),
                color = colors.cardBackground.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(Spacing.Medium)) {
                    Text(
                        "Congestion",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LegendItem(
                        color = Color(0xFFEF4444),
                        label = "Heavy",
                        count = highZones.size
                    )
                    LegendItem(
                        color = Color(0xFFF97316),
                        label = "Moderate",
                        count = mediumZones.size
                    )
                    LegendItem(
                        color = Color(0xFF22C55E),
                        label = "Clear",
                        count = lowZones.size
                    )
                }
            }
        }

        // ══════════════════════ Parking Info Popup ══════════════════════
        AnimatedVisibility(
            visible = selectedParking != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedParking?.let { spot ->
                ParkingInfoCard(
                    spot = spot,
                    availableSlots = state.parkingLive[spot.id]?.availableSlots
                        ?: spot.availableSlots,
                    userLocation = userLocation,
                    onNavigate = { lat, lng ->
                        try {
                            Firebase.analytics.logEvent("navigate_to_parking_clicked", null)
                        } catch (_: Exception) {}
                        openNavigation(context, lat, lng)
                    },
                    onDismiss = { selectedParking = null }
                )
            }
        }

        // ══════════════════════ Incident Info Popup ══════════════════════
        AnimatedVisibility(
            visible = selectedIncident != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedIncident?.let { report ->
                IncidentInfoCard(
                    report = report,
                    onDismiss = { selectedIncident = null }
                )
            }
        }

        // ══════════════════════ Snackbar Host ══════════════════════
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp)
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// Layer Toggle Chip
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LayerChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val bgColor = if (isActive) activeColor.copy(alpha = 0.15f)
    else colors.cardBackground.copy(alpha = 0.9f)
    val contentColor = if (isActive) activeColor else colors.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        shadowElevation = 3.dp,
        modifier = Modifier.height(32.dp)
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
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Parking Info Card (Bottom popup)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingInfoCard(
    spot: ParkingSpot,
    availableSlots: Int,
    userLocation: LatLng?,
    onNavigate: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val isFull = availableSlots <= 0
    val statusColor = if (isFull) AccentRed else AccentParking

    val distance = if (userLocation != null && spot.location != null) {
        val results = FloatArray(1)
        Location.distanceBetween(
            userLocation.latitude, userLocation.longitude,
            spot.location.latitude, spot.location.longitude,
            results
        )
        results[0]
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
            .navigationBarsPadding()
            .shadow(
                8.dp,
                RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Header row
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
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.LocalParking,
                                null,
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
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
                        if (distance != null) {
                            Text(
                                formatDistance(distance),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        "Close",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Slots info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                SlotInfoChip(
                    label = "Available",
                    value = availableSlots.toString(),
                    color = statusColor,
                    modifier = Modifier.weight(1f)
                )
                SlotInfoChip(
                    label = "Total",
                    value = spot.totalSlots.toString(),
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                SlotInfoChip(
                    label = "Type",
                    value = if (spot.isLegal) "Legal" else "Illegal",
                    color = if (spot.isLegal) AccentParking else AccentAlerts,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Navigate button
            if (spot.location != null) {
                Button(
                    onClick = {
                        onNavigate(spot.location.latitude, spot.location.longitude)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Outlined.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        "Navigate",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}


@Composable
private fun SlotInfoChip(
    label: String,
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
            modifier = Modifier.padding(vertical = Spacing.Small, horizontal = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Incident Info Card (Bottom popup)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun IncidentInfoCard(
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
            it.toDate().time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
            .navigationBarsPadding()
            .shadow(
                8.dp,
                RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = colors.cardShadow
            ),
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
                                null,
                                tint = typeColor,
                                modifier = Modifier.size(22.dp)
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
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
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
                    Icon(
                        Icons.Filled.Close,
                        "Close",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
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
                        .data(report.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Incident photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(CornerRadius.Medium)),
                    contentScale = ContentScale.Crop
                )
            }

            // Status chip
            Spacer(Modifier.height(Spacing.Medium))
            StatusChip(status = report.status)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Utility Functions
// ═══════════════════════════════════════════════════════════════════

/** Create a solid circle bitmap for map markers. */
private fun createCircleMarkerBitmap(color: Int, sizeDp: Int): Bitmap {
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
        strokeWidth = sizePx * 0.12f
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius - sizePx * 0.06f, paint)
    canvas.drawCircle(radius, radius, radius - sizePx * 0.06f, borderPaint)
    return bitmap
}

/** Format distance in meters/km. */
private fun formatDistance(meters: Float): String = when {
    meters < 1000 -> "${meters.toInt()}m away"
    else -> String.format("%.1fkm away", meters / 1000f)
}

/** Find nearest parking spot with available slots. */
private fun findNearestParking(
    userLocation: LatLng?,
    spots: List<ParkingSpot>,
    live: Map<String, com.example.cityflux.model.ParkingLive>
): ParkingSpot? {
    if (userLocation == null) return spots.firstOrNull()
    return spots
        .filter { spot ->
            val avail = live[spot.id]?.availableSlots ?: spot.availableSlots
            avail > 0 && spot.location != null
        }
        .minByOrNull { spot ->
            val loc = spot.location!!
            val results = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                loc.latitude, loc.longitude,
                results
            )
            results[0]
        }
}

/** Open Google Maps navigation intent. */
private fun openNavigation(context: Context, lat: Double, lng: Double) {
    try {
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (_: Exception) { /* graceful */ }
}

/** Check network availability. */
private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}


// ═══════════════════════════════════════════════════════════════════
// Layer Chip with Badge (upgraded)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LayerChipWithBadge(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val bgColor = if (isActive) activeColor.copy(alpha = 0.15f)
        else colors.cardBackground.copy(alpha = 0.9f)
    val contentColor = if (isActive) activeColor else colors.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        shadowElevation = 3.dp,
        modifier = Modifier.height(32.dp)
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
            if (badge != null && isActive) {
                Surface(
                    shape = CircleShape,
                    color = activeColor,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Analytics Stats Panel
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CongestionStatsPanel(
    highCount: Int,
    mediumCount: Int,
    lowCount: Int,
    totalZones: Int,
    pendingIncidents: Int,
    totalIncidents: Int
) {
    val colors = MaterialTheme.cityFluxColors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium),
        shape = RoundedCornerShape(CornerRadius.Large),
        color = colors.cardBackground.copy(alpha = 0.97f),
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Distribution bar
            if (totalZones > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    val highFraction = highCount.toFloat() / totalZones
                    val medFraction = mediumCount.toFloat() / totalZones
                    val lowFraction = lowCount.toFloat() / totalZones

                    if (highFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(highFraction.coerceAtLeast(0.05f))
                                .fillMaxHeight()
                                .background(Color(0xFFEF4444))
                        )
                    }
                    if (medFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(medFraction.coerceAtLeast(0.05f))
                                .fillMaxHeight()
                                .background(Color(0xFFF97316))
                        )
                    }
                    if (lowFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(lowFraction.coerceAtLeast(0.05f))
                                .fillMaxHeight()
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.Medium))
            }

            // Stats grid (2×3)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                StatMiniCard(
                    label = "High",
                    value = "$highCount",
                    color = Color(0xFFEF4444),
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Medium",
                    value = "$mediumCount",
                    color = Color(0xFFF97316),
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Low",
                    value = "$lowCount",
                    color = Color(0xFF22C55E),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                StatMiniCard(
                    label = "Pending",
                    value = "$pendingIncidents",
                    color = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Incidents",
                    value = "$totalIncidents",
                    color = AccentIssues,
                    modifier = Modifier.weight(1f)
                )
                StatMiniCard(
                    label = "Zones",
                    value = "$totalZones",
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    label: String,
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
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontSize = 9.sp
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Map Legend Item
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LegendItem(color: Color, label: String, count: Int) {
    val textColors = MaterialTheme.cityFluxColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = textColors.textPrimary,
            fontSize = 10.sp
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// Parse Road LatLng from road ID
// ═══════════════════════════════════════════════════════════════════

/** Parse lat/lng from road ID like "road_19.076_72.877" or fallback hash-based. */
private fun parseRoadLatLng(roadId: String): LatLng? {
    // Try format: road_<lat>_<lng> or <name>_<lat>_<lng>
    val parts = roadId.split("_")
    if (parts.size >= 3) {
        val lat = parts[parts.size - 2].toDoubleOrNull()
        val lng = parts[parts.size - 1].toDoubleOrNull()
        if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
            return LatLng(lat, lng)
        }
    }
    // Hash-based fallback: deterministic position around Mumbai
    val hash = roadId.hashCode()
    val baseLat = 19.076 + (hash % 100) * 0.002
    val baseLng = 72.877 + ((hash / 100) % 100) * 0.002
    return LatLng(baseLat, baseLng)
}


// ═══════════════════════════════════════════════════════════════════
// Geocode Address → LatLng
// ═══════════════════════════════════════════════════════════════════

@Suppress("DEPRECATION")
private suspend fun geocodeAddress(context: Context, query: String): LatLng? {
    return withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocationName(query, 1)
            if (!results.isNullOrEmpty()) {
                LatLng(results[0].latitude, results[0].longitude)
            } else null
        } catch (e: Exception) {
            Log.e("MapScreen", "Geocode failed: ${e.message}")
            null
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Emergency Service Data + Generator
// ═══════════════════════════════════════════════════════════════════

private data class EmergencyServicePin(
    val name: String,
    val type: String, // "hospital", "police", "fire"
    val location: LatLng
)

/** Generate sample emergency service locations around user's position */
private fun generateEmergencyServiceLocations(center: LatLng): List<EmergencyServicePin> {
    val offset = 0.008
    return listOf(
        EmergencyServicePin("City Hospital", "hospital", LatLng(center.latitude + offset, center.longitude + offset * 0.5)),
        EmergencyServicePin("General Hospital", "hospital", LatLng(center.latitude - offset * 0.7, center.longitude + offset)),
        EmergencyServicePin("Police Station", "police", LatLng(center.latitude + offset * 0.3, center.longitude - offset)),
        EmergencyServicePin("Traffic Police HQ", "police", LatLng(center.latitude - offset, center.longitude - offset * 0.6)),
        EmergencyServicePin("Fire Station", "fire", LatLng(center.latitude + offset * 0.9, center.longitude + offset * 1.2)),
        EmergencyServicePin("Fire Brigade Unit", "fire", LatLng(center.latitude - offset * 1.1, center.longitude + offset * 0.3))
    )
}


// ═══════════════════════════════════════════════════════════════════
// Haversine Distance (km)
// ═══════════════════════════════════════════════════════════════════

private fun haversineDistance(a: LatLng, b: LatLng): Double {
    val R = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}


// ═══════════════════════════════════════════════════════════════════
// Google Directions API — Real Road Route
// ═══════════════════════════════════════════════════════════════════

/** Directions API result with polyline, duration and distance */
private data class DirectionsResult(
    val points: List<LatLng>,
    val durationMinutes: Int,
    val distanceKm: Double
)

/** Get Maps API key from AndroidManifest meta-data */
private fun getMapApiKey(context: Context): String {
    return try {
        val ai = context.packageManager.getApplicationInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_META_DATA
        )
        ai.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
    } catch (_: Exception) { "" }
}

/** Fetch route from Google Directions API, fallback to straight line */
private suspend fun fetchDirectionsRoute(
    start: LatLng,
    end: LatLng,
    apiKey: String
): DirectionsResult = withContext(Dispatchers.IO) {
    val fallback = DirectionsResult(listOf(start, end), 0, 0.0)
    try {
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${start.latitude},${start.longitude}" +
                "&destination=${end.latitude},${end.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"
        Log.d("MapScreen", "Directions API request: $url")
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        val response = if (responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "no body"
            Log.e("MapScreen", "Directions API HTTP $responseCode: $errorBody")
            connection.disconnect()
            return@withContext fallback
        }
        connection.disconnect()

        val json = org.json.JSONObject(response)
        val status = json.optString("status", "UNKNOWN")
        Log.d("MapScreen", "Directions API status: $status")

        if (status != "OK") {
            val errorMsg = json.optString("error_message", "no details")
            Log.e("MapScreen", "Directions API error: $status — $errorMsg")
            return@withContext fallback
        }

        val routes = json.optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            Log.w("MapScreen", "Directions API: no routes returned")
            return@withContext fallback
        }

        val route = routes.getJSONObject(0)

        // Extract duration & distance from first leg
        val legs = route.optJSONArray("legs")
        val leg = legs?.optJSONObject(0)
        val durationSec = leg?.optJSONObject("duration")?.optInt("value", 0) ?: 0
        val distanceMeters = leg?.optJSONObject("distance")?.optInt("value", 0) ?: 0

        val overviewPolyline = route
            .optJSONObject("overview_polyline")
            ?.optString("points", "")
            ?: ""

        if (overviewPolyline.isNotEmpty()) {
            val decoded = decodePolyline(overviewPolyline)
            Log.d("MapScreen", "Directions API: decoded ${decoded.size} points, ${durationSec}s, ${distanceMeters}m")
            DirectionsResult(
                points = decoded,
                durationMinutes = (durationSec / 60.0).toInt().coerceAtLeast(1),
                distanceKm = distanceMeters / 1000.0
            )
        } else {
            Log.w("MapScreen", "Directions API: empty polyline")
            fallback
        }
    } catch (e: Exception) {
        Log.e("MapScreen", "Directions API failed: ${e.javaClass.simpleName}: ${e.message}")
        fallback
    }
}

/** Decode Google Maps encoded polyline string into LatLng list */
private fun decodePolyline(encoded: String): List<LatLng> {
    val poly = mutableListOf<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        // Decode latitude
        var shift = 0
        var result = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        // Decode longitude
        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1F) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

        poly.add(LatLng(lat / 1E5, lng / 1E5))
    }
    return poly
}

/** Create a bitmap badge showing ETA text (like Google Maps "7 min" badge) */
private fun createEtaBadgeBitmap(minutes: Int): Bitmap {
    val text = "$minutes min"
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val padding = 20f
    val textWidth = textPaint.measureText(text)
    val width = (textWidth + padding * 2).toInt()
    val height = 52
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // Dark blue rounded rect background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A73E8.toInt()
        style = Paint.Style.FILL
    }
    val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
    canvas.drawRoundRect(rect, 14f, 14f, bgPaint)
    // Text centered
    val textY = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(text, width / 2f, textY, textPaint)
    return bitmap
}


// ═══════════════════════════════════════════════════════════════════
// Route Step Indicator
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RouteStepIndicator(
    step: Int,
    label: String,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isDone) AccentGreen.copy(alpha = 0.12f)
            else colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isDone) AccentGreen else colors.textTertiary.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$step",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) Color.White else colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isDone) AccentGreen else colors.textSecondary,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Heatmap Cell Data + Builder
// ═══════════════════════════════════════════════════════════════════

private data class HeatCell(val center: LatLng, val radius: Double, val color: Color)

/** Build heatmap density circles from incident locations */
private fun buildHeatmapCells(incidents: List<Report>): List<HeatCell> {
    val validIncidents = incidents.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    if (validIncidents.isEmpty()) return emptyList()

    // Grid-based density: group incidents into cells
    val gridSize = 0.005 // ~500m cells
    val groups = validIncidents.groupBy { incident ->
        val latBucket = (incident.latitude / gridSize).toInt()
        val lngBucket = (incident.longitude / gridSize).toInt()
        latBucket to lngBucket
    }

    return groups.map { (bucket, group) ->
        val avgLat = group.sumOf { it.latitude } / group.size
        val avgLng = group.sumOf { it.longitude } / group.size
        val density = group.size
        val (radius, color) = when {
            density >= 5 -> 500.0 to Color(0xFFEF4444).copy(alpha = 0.35f)
            density >= 3 -> 400.0 to Color(0xFFF97316).copy(alpha = 0.30f)
            density >= 2 -> 300.0 to Color(0xFFEAB308).copy(alpha = 0.25f)
            else -> 200.0 to Color(0xFF22C55E).copy(alpha = 0.20f)
        }
        HeatCell(LatLng(avgLat, avgLng), radius, color)
    }
}
