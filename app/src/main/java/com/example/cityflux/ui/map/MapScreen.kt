package com.example.cityflux.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════════
// MapScreen — Full-screen Google Map with live Firebase data
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    onBack: () -> Unit,
    vm: MapViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val scope = rememberCoroutineScope()
    val state by vm.uiState.collectAsState()

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

    // ── Map Properties ──
    val mapProperties by remember(hasLocationPermission) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL,
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

    // ── Custom Marker Bitmaps (cached once) ──
    val parkingGreenBitmap = remember { createCircleMarkerBitmap(AccentParking.toArgb(), 40) }
    val parkingRedBitmap = remember { createCircleMarkerBitmap(AccentRed.toArgb(), 40) }
    val incidentAccidentBitmap = remember { createCircleMarkerBitmap(AccentRed.toArgb(), 36) }
    val incidentParkingBitmap = remember { createCircleMarkerBitmap(AccentAlerts.toArgb(), 36) }
    val incidentHawkerBitmap = remember { createCircleMarkerBitmap(AccentOrange.toArgb(), 36) }
    val incidentDefaultBitmap = remember { createCircleMarkerBitmap(PrimaryBlue.toArgb(), 36) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ══════════════════════ Google Map ══════════════════════
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapClick = {
                selectedParking = null
                selectedIncident = null
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
        }

        // ══════════════════════ Top Row: Back + Layer Toggles ══════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            FloatingActionButton(
                onClick = onBack,
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                containerColor = colors.cardBackground,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Layer toggle chips
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                LayerChip(
                    label = "Parking",
                    icon = Icons.Outlined.LocalParking,
                    isActive = showParking,
                    activeColor = AccentParking,
                    onClick = { showParking = !showParking }
                )
                LayerChip(
                    label = "Incidents",
                    icon = Icons.Outlined.Warning,
                    isActive = showIncidents,
                    activeColor = AccentIssues,
                    onClick = { showIncidents = !showIncidents }
                )
            }
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
                onClick = { onBack() },
                shape = CircleShape,
                containerColor = AccentIssues,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.ReportProblem, "Report Issue", Modifier.size(20.dp))
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
                    nearestParking?.let { spot ->
                        val loc = spot.location ?: return@let
                        scope.launch {
                            selectedParking = spot
                            selectedIncident = null
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude), 17f
                                ),
                                durationMs = 600
                            )
                        }
                    }
                },
                shape = CircleShape,
                containerColor = AccentParking,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.LocalParking, "Nearest Parking", Modifier.size(20.dp))
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
