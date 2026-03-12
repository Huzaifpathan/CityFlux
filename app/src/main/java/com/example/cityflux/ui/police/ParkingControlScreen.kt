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
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID


// ═══════════════════════════════════════════════════════════════════
// ParkingControlScreen — Police parking violations enforcement center
// Map + list view, violation capture with camera, fine/warning notes,
// mark cleared, nearby alerts, live parking data, full CRUD
// ═══════════════════════════════════════════════════════════════════


// ── Data Models ──

data class ParkingViolation(
    val id: String = "",
    val officerId: String = "",
    val vehicleInfo: String = "",
    val violationType: String = "illegal_parking",
    val description: String = "",
    val imageUrl: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val fineAmount: String = "",
    val note: String = "",
    val actionType: String = "warning",  // "warning" | "fine" | "tow"
    val status: String = "Active",       // "Active" | "Cleared"
    val timestamp: Timestamp? = null
)


// ── ViewModel ──

class ParkingControlViewModel : ViewModel() {

    companion object { private const val TAG = "ParkingControlVM" }

    data class ParkingControlState(
        val isLoading: Boolean = true,
        val violations: List<ParkingViolation> = emptyList(),
        val parkingSpots: List<ParkingSpot> = emptyList(),
        val parkingLive: Map<String, ParkingLive> = emptyMap(),
        val error: String? = null
    ) {
        val activeCount get() = violations.count { it.status.equals("Active", true) }
        val clearedCount get() = violations.count { it.status.equals("Cleared", true) }
        val totalFines get() = violations.filter { it.actionType == "fine" }.size
        val totalWarnings get() = violations.filter { it.actionType == "warning" }.size
        val illegalSpots get() = parkingSpots.count { !it.isLegal }
    }

    private val _uiState = MutableStateFlow(ParkingControlState())
    val uiState: StateFlow<ParkingControlState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    init {
        observeViolations()
        observeParkingSpots()
        observeParkingLive()
    }

    private fun observeViolations() {
        firestore.collection("parking_violations")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Violations error", err)
                    _uiState.update { it.copy(error = "Failed to load violations", isLoading = false) }
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ParkingViolation::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _uiState.update { it.copy(violations = list, isLoading = false) }
            }
    }

    private fun observeParkingSpots() {
        firestore.collection("parking")
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                val spots = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ParkingSpot::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _uiState.update { it.copy(parkingSpots = spots) }
            }
    }

    private fun observeParkingLive() {
        viewModelScope.launch {
            RealtimeDbService.observeParkingLive()
                .catch { e -> Log.e(TAG, "Parking live error", e) }
                .collect { map ->
                    _uiState.update { it.copy(parkingLive = map) }
                }
        }
    }

    fun submitViolation(
        vehicleInfo: String,
        violationType: String,
        description: String,
        photoUri: Uri?,
        latitude: Double,
        longitude: Double,
        address: String,
        fineAmount: String,
        note: String,
        actionType: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            onError("Not signed in"); return
        }

        viewModelScope.launch {
            try {
                val violationId = UUID.randomUUID().toString()
                var imageUrl = ""

                photoUri?.let { uri ->
                    val imageRef = storage.reference.child("violations/$violationId.jpg")
                    imageRef.putFile(uri).await()
                    imageUrl = imageRef.downloadUrl.await().toString()
                }

                val data = hashMapOf(
                    "officerId" to uid,
                    "vehicleInfo" to vehicleInfo,
                    "violationType" to violationType,
                    "description" to description,
                    "imageUrl" to imageUrl,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "address" to address,
                    "fineAmount" to fineAmount,
                    "note" to note,
                    "actionType" to actionType,
                    "status" to "Active",
                    "timestamp" to Timestamp.now()
                )

                firestore.collection("parking_violations")
                    .document(violationId).set(data).await()

                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Submit violation failed", e)
                onError("Failed to submit: ${e.localizedMessage}")
            }
        }
    }

    fun markCleared(violationId: String) {
        firestore.collection("parking_violations")
            .document(violationId)
            .update("status", "Cleared")
    }

    fun markActive(violationId: String) {
        firestore.collection("parking_violations")
            .document(violationId)
            .update("status", "Active")
    }
}


// ═══════════════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkingControlScreen(
    onBack: () -> Unit,
    vm: ParkingControlViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.cityFluxColors
    val scope = rememberCoroutineScope()
    val state by vm.uiState.collectAsState()

    // ── View Mode ──
    var isMapView by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var showAddViolation by remember { mutableStateOf(false) }
    var expandedViolationId by remember { mutableStateOf<String?>(null) }
    var photoDialogUrl by remember { mutableStateOf<String?>(null) }

    val filterOptions = listOf("All", "Active", "Cleared", "Fines", "Warnings")

    // ── Location ──
    var hasLocationPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val loc: Location? = fused.lastLocation.await()
            loc?.let { userLocation = LatLng(it.latitude, it.longitude) }
        } catch (_: Exception) {}
    }

    // ── Filtered violations ──
    val filteredViolations = remember(state.violations, selectedFilter, searchQuery) {
        var list = state.violations
        list = when (selectedFilter) {
            "Active" -> list.filter { it.status.equals("Active", true) }
            "Cleared" -> list.filter { it.status.equals("Cleared", true) }
            "Fines" -> list.filter { it.actionType == "fine" }
            "Warnings" -> list.filter { it.actionType == "warning" }
            else -> list
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.vehicleInfo.lowercase().contains(q) ||
                        it.address.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.violationType.lowercase().replace("_", " ").contains(q)
            }
        }
        list
    }

    // ── Nearby Alert ──
    val nearbyActiveViolations = remember(state.violations, userLocation) {
        if (userLocation == null) 0
        else state.violations.count { v ->
            v.status.equals("Active", true) && v.latitude != 0.0 && v.longitude != 0.0 &&
                    distanceBetweenParking(
                        userLocation!!.latitude, userLocation!!.longitude,
                        v.latitude, v.longitude
                    ) < 500 // 500 meters radius
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddViolation = true },
                shape = CircleShape,
                containerColor = AccentRed,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                Icon(Icons.Filled.Add, "Add Violation", Modifier.size(26.dp))
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ══════════════════════ Top Header ══════════════════════
            ParkingControlTopBar(colors = colors, nearbyCount = nearbyActiveViolations)

            // ══════════════════════ Stats Strip ══════════════════════
            ParkingStatsStrip(state = state, colors = colors)

            // ══════════════════════ Nearby Alert Banner ══════════════════════
            AnimatedVisibility(
                visible = nearbyActiveViolations > 0,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.XSmall),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = AccentRed.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NearbyPulsingIcon()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$nearbyActiveViolations active violation${if (nearbyActiveViolations > 1) "s" else ""} nearby",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentRed
                            )
                            Text(
                                "Within 500m of your location",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Search + View Toggle ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Search violations...", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, "Clear", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.cardBackground,
                        unfocusedContainerColor = colors.cardBackground,
                        focusedBorderColor = AccentParking,
                        unfocusedBorderColor = colors.cardBorder.copy(alpha = 0.3f),
                        cursorColor = AccentParking
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                // View toggle
                FloatingActionButton(
                    onClick = { isMapView = !isMapView },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    containerColor = if (isMapView) AccentParking else colors.cardBackground,
                    elevation = FloatingActionButtonDefaults.elevation(2.dp)
                ) {
                    Icon(
                        if (isMapView) Icons.Outlined.ViewList else Icons.Outlined.Map,
                        "Toggle View",
                        tint = if (isMapView) Color.White else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ══════════════════════ Filter Chips ══════════════════════
            LazyRow(
                modifier = Modifier.padding(vertical = Spacing.XSmall),
                contentPadding = PaddingValues(horizontal = Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    val count = when (filter) {
                        "Active" -> state.activeCount
                        "Cleared" -> state.clearedCount
                        "Fines" -> state.totalFines
                        "Warnings" -> state.totalWarnings
                        else -> state.violations.size
                    }
                    ParkingFilterChip(
                        label = filter,
                        count = count,
                        isSelected = isSelected,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Small))

            // ══════════════════════ Content Area ══════════════════════
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingSpinner()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading violations...", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                        }
                    }
                }

                isMapView -> {
                    // ── Map View ──
                    ParkingViolationsMap(
                        violations = filteredViolations,
                        parkingSpots = state.parkingSpots,
                        parkingLive = state.parkingLive,
                        hasLocationPermission = hasLocationPermission,
                        userLocation = userLocation,
                        onViolationClick = { expandedViolationId = it.id },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                filteredViolations.isEmpty() && state.parkingSpots.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.LocalParking, null, tint = colors.textTertiary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No violations recorded", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("Tap + to report a new violation", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                else -> {
                    // ── List View ──
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(
                            start = Spacing.Large, end = Spacing.Large,
                            top = Spacing.Small, bottom = 100.dp
                        )
                    ) {
                        // Illegal parking spots section
                        val illegalSpots = state.parkingSpots.filter { !it.isLegal }
                        if (illegalSpots.isNotEmpty() && selectedFilter in listOf("All", "Active")) {
                            item {
                                ParkingSectionHeader(
                                    title = "Illegal Parking Zones",
                                    subtitle = "${illegalSpots.size} zones flagged",
                                    icon = Icons.Filled.Warning,
                                    color = AccentAlerts
                                )
                            }
                            items(illegalSpots, key = { "spot_${it.id}" }) { spot ->
                                SlideUpFadeIn(visible = true, delay = 50) {
                                    IllegalSpotCard(
                                        spot = spot,
                                        live = state.parkingLive[spot.id],
                                        userLocation = userLocation
                                    )
                                }
                            }
                            item { Spacer(Modifier.height(Spacing.Medium)) }
                        }

                        // Violations section
                        if (filteredViolations.isNotEmpty()) {
                            item {
                                ParkingSectionHeader(
                                    title = "Violations",
                                    subtitle = "${filteredViolations.size} records",
                                    icon = Icons.Filled.Report,
                                    color = AccentRed
                                )
                            }
                            itemsIndexed(
                                items = filteredViolations,
                                key = { _, v -> v.id }
                            ) { index, violation ->
                                SlideUpFadeIn(visible = true, delay = staggeredDelay(index)) {
                                    ViolationCard(
                                        violation = violation,
                                        isExpanded = expandedViolationId == violation.id,
                                        onToggle = {
                                            expandedViolationId = if (expandedViolationId == violation.id) null else violation.id
                                        },
                                        onViewPhoto = { photoDialogUrl = it },
                                        onMarkCleared = { vm.markCleared(violation.id) },
                                        onMarkActive = { vm.markActive(violation.id) },
                                        colors = colors
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════ Add Violation Dialog ══════════════════════
        if (showAddViolation) {
            AddViolationDialog(
                onDismiss = { showAddViolation = false },
                userLocation = userLocation,
                vm = vm,
                context = context
            )
        }

        // ══════════════════════ Photo Viewer ══════════════════════
        if (photoDialogUrl != null) {
            Dialog(onDismissRequest = { photoDialogUrl = null }) {
                Card(
                    shape = RoundedCornerShape(CornerRadius.XLarge),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    elevation = CardDefaults.cardElevation(16.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photoDialogUrl).crossfade(true).build(),
                            contentDescription = "Evidence photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .clip(RoundedCornerShape(CornerRadius.XLarge)),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoDialogUrl = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Top Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingControlTopBar(colors: CityFluxColors, nearbyCount: Int) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = AccentParking.copy(alpha = 0.1f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.LocalParking, null, tint = AccentParking, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Parking Control",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        "Violations enforcement",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            if (nearbyCount > 0) {
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = AccentRed.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NearbyPulsingIcon()
                        Text("$nearbyCount nearby", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentRed)
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Stats Strip
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingStatsStrip(state: ParkingControlViewModel.ParkingControlState, colors: CityFluxColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ParkingStatChip("Active", state.activeCount.toString(), AccentRed, Modifier.weight(1f))
        ParkingStatChip("Cleared", state.clearedCount.toString(), AccentGreen, Modifier.weight(1f))
        ParkingStatChip("Fines", state.totalFines.toString(), AccentAlerts, Modifier.weight(1f))
        ParkingStatChip("Warnings", state.totalWarnings.toString(), AccentOrange, Modifier.weight(1f))
    }
}

@Composable
private fun ParkingStatChip(label: String, value: String, color: Color, modifier: Modifier) {
    val tc = MaterialTheme.cityFluxColors
    Surface(modifier = modifier, shape = RoundedCornerShape(CornerRadius.Medium), color = color.copy(alpha = 0.08f)) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = tc.textTertiary, fontWeight = FontWeight.Medium)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Filter Chip
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingFilterChip(label: String, count: Int, isSelected: Boolean, onClick: () -> Unit) {
    val tc = MaterialTheme.cityFluxColors
    val color = when (label) {
        "Active" -> AccentRed; "Cleared" -> AccentGreen; "Fines" -> AccentAlerts; "Warnings" -> AccentOrange; else -> AccentParking
    }
    val bg = if (isSelected) color.copy(alpha = 0.15f) else tc.cardBackground
    val border = if (isSelected) color.copy(alpha = 0.4f) else tc.cardBorder.copy(alpha = 0.2f)
    val content = if (isSelected) color else tc.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = content)
            if (count > 0 && isSelected) {
                Surface(shape = CircleShape, color = content.copy(alpha = 0.2f), modifier = Modifier.size(18.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(count.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = content)
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Section Header
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingSectionHeader(
    title: String, subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color
) {
    val tc = MaterialTheme.cityFluxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tc.textPrimary)
        Text("· $subtitle", style = MaterialTheme.typography.labelSmall, color = tc.textTertiary)
    }
}


// ═══════════════════════════════════════════════════════════════════
// Illegal Spot Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun IllegalSpotCard(
    spot: ParkingSpot,
    live: ParkingLive?,
    userLocation: LatLng?
) {
    val colors = MaterialTheme.cityFluxColors
    val available = live?.availableSlots ?: spot.availableSlots
    val dist = if (userLocation != null && spot.location != null)
        distanceBetweenParking(userLocation.latitude, userLocation.longitude, spot.location.latitude, spot.location.longitude)
    else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(AccentAlerts)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(Modifier.size(38.dp), shape = RoundedCornerShape(CornerRadius.Medium), color = AccentAlerts.copy(alpha = 0.1f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Warning, null, tint = AccentAlerts, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            spot.address.ifBlank { "Zone ${spot.id.take(6)}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildString {
                                append("$available / ${spot.totalSlots} occupied")
                                if (dist != null) append(" · ${formatDistParking(dist)}")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentRed.copy(alpha = 0.12f)
                ) {
                    Text(
                        "ILLEGAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Violation Card (Expandable)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViolationCard(
    violation: ParkingViolation,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onViewPhoto: (String) -> Unit,
    onMarkCleared: () -> Unit,
    onMarkActive: () -> Unit,
    colors: CityFluxColors
) {
    val accentColor = when (violation.actionType) {
        "fine" -> AccentRed
        "tow" -> Color(0xFF7C3AED)
        else -> AccentOrange
    }
    val typeLabel = violation.violationType.replace("_", " ").replaceFirstChar { it.uppercase() }
    val timeAgo = violation.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    } ?: "Unknown"
    val isActive = violation.status.equals("Active", true)

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                if (isExpanded) 8.dp else 4.dp,
                RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = if (isExpanded) BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)) else null
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(42.dp), shape = RoundedCornerShape(CornerRadius.Medium), color = accentColor.copy(alpha = 0.1f)) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                when (violation.actionType) {
                                    "fine" -> Icons.Outlined.Receipt
                                    "tow" -> Icons.Outlined.LocalShipping
                                    else -> Icons.Outlined.Warning
                                },
                                null, tint = accentColor, modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            violation.vehicleInfo.ifBlank { typeLabel },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(4.dp), color = accentColor.copy(alpha = 0.1f)) {
                                Text(
                                    violation.actionType.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text("· $timeAgo", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                    }
                    // Status chip
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Small),
                        color = if (isActive) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.12f)
                    ) {
                        Text(
                            violation.status,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) AccentRed else AccentGreen
                        )
                    }
                }

                // Description
                if (violation.description.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.Small))
                    Text(
                        violation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = if (isExpanded) 10 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Collapsed: thumbnail + address hints
                if (!isExpanded) {
                    if (violation.imageUrl.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.XSmall))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(violation.imageUrl).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text("Evidence attached", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                    }
                    if (violation.address.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.LocationOn, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                            Text(violation.address, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ExpandMore, "Expand", tint = colors.textTertiary.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                    }
                }

                // ── Expanded Detail ──
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(tween(300)) + fadeIn(),
                    exit = shrinkVertically(tween(200)) + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.Medium))
                        HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.15f))
                        Spacer(Modifier.height(Spacing.Medium))

                        // Photo
                        if (violation.imageUrl.isNotBlank()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(CornerRadius.Medium))
                                    .clickable { onViewPhoto(violation.imageUrl) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(violation.imageUrl).crossfade(true).build(),
                                    contentDescription = "Evidence",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)))),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Surface(Modifier.padding(8.dp), shape = RoundedCornerShape(CornerRadius.Small), color = Color.Black.copy(alpha = 0.5f)) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Outlined.ZoomIn, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Text("Enlarge", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(Spacing.Medium))
                        }

                        // Meta info
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(CornerRadius.Medium), color = colors.textTertiary.copy(alpha = 0.04f)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (violation.vehicleInfo.isNotBlank()) {
                                    ViolationMetaRow("Vehicle", violation.vehicleInfo, Icons.Outlined.DirectionsCar)
                                }
                                if (violation.address.isNotBlank()) {
                                    ViolationMetaRow("Location", violation.address, Icons.Outlined.LocationOn)
                                }
                                if (violation.fineAmount.isNotBlank()) {
                                    ViolationMetaRow("Fine", "₹${violation.fineAmount}", Icons.Outlined.CurrencyRupee)
                                }
                                if (violation.note.isNotBlank()) {
                                    ViolationMetaRow("Note", violation.note, Icons.Outlined.StickyNote2)
                                }
                                ViolationMetaRow("Type", typeLabel, Icons.Outlined.Category)
                                ViolationMetaRow("Time", timeAgo, Icons.Outlined.Schedule)
                            }
                        }

                        Spacer(Modifier.height(Spacing.Large))

                        // Actions
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isActive) {
                                Button(
                                    onClick = onMarkCleared,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(CornerRadius.Medium),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    elevation = ButtonDefaults.buttonElevation(2.dp, 0.dp)
                                ) {
                                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Mark Cleared", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextOnPrimary)
                                }
                            } else {
                                Button(
                                    onClick = onMarkActive,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(CornerRadius.Medium),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                                    elevation = ButtonDefaults.buttonElevation(2.dp, 0.dp)
                                ) {
                                    Icon(Icons.Outlined.Replay, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reopen", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextOnPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViolationMetaRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val c = MaterialTheme.cityFluxColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = c.textTertiary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary, modifier = Modifier.width(60.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}


// ═══════════════════════════════════════════════════════════════════
// Map View
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
private fun ParkingViolationsMap(
    violations: List<ParkingViolation>,
    parkingSpots: List<ParkingSpot>,
    parkingLive: Map<String, ParkingLive>,
    hasLocationPermission: Boolean,
    userLocation: LatLng?,
    onViolationClick: (ParkingViolation) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultLocation = LatLng(19.0760, 72.8777)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation ?: defaultLocation, 14f)
    }

    val mapProperties by remember(hasLocationPermission) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.NORMAL,
                isBuildingEnabled = true
            )
        )
    }

    val violationBitmap = remember { createParkingMarkerBitmap(0xFFEF4444.toInt(), 40) }
    val clearedBitmap = remember { createParkingMarkerBitmap(0xFF10B981.toInt(), 36) }
    val illegalBitmap = remember { createParkingMarkerBitmap(0xFFF59E0B.toInt(), 38) }
    val legalBitmap = remember { createParkingMarkerBitmap(0xFF2563EB.toInt(), 32) }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = true,
            myLocationButtonEnabled = true,
            mapToolbarEnabled = false
        )
    ) {
        // Violation markers
        violations.forEach { v ->
            if (v.latitude == 0.0 && v.longitude == 0.0) return@forEach
            val isActive = v.status.equals("Active", true)
            Marker(
                state = MarkerState(position = LatLng(v.latitude, v.longitude)),
                title = v.vehicleInfo.ifBlank { v.violationType.replace("_", " ") },
                snippet = "${v.actionType.replaceFirstChar { it.uppercase() }} · ${v.status}",
                icon = BitmapDescriptorFactory.fromBitmap(if (isActive) violationBitmap else clearedBitmap),
                onClick = { onViolationClick(v); true }
            )
            // Circle around active violations
            if (isActive) {
                Circle(
                    center = LatLng(v.latitude, v.longitude),
                    radius = 50.0,
                    fillColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                    strokeColor = Color(0xFFEF4444).copy(alpha = 0.4f),
                    strokeWidth = 2f
                )
            }
        }

        // Parking spot markers
        parkingSpots.forEach { spot ->
            val loc = spot.location ?: return@forEach
            Marker(
                state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                title = spot.address.ifBlank { "Parking ${spot.id.take(6)}" },
                snippet = if (spot.isLegal) "Legal" else "ILLEGAL ZONE",
                icon = BitmapDescriptorFactory.fromBitmap(if (spot.isLegal) legalBitmap else illegalBitmap)
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Add Violation Dialog — full capture form
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddViolationDialog(
    onDismiss: () -> Unit,
    userLocation: LatLng?,
    vm: ParkingControlViewModel,
    context: Context
) {
    val colors = MaterialTheme.cityFluxColors
    var vehicleInfo by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var fineAmount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("illegal_parking") }
    var selectedAction by remember { mutableStateOf("warning") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Camera
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) photoUri = cameraImageUri
    }
    var hasCameraPermission by remember { mutableStateOf(false) }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            val uri = createParkingTempUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoUri = it }
    }

    val violationTypes = listOf(
        "illegal_parking" to "Illegal Parking",
        "no_parking_zone" to "No Parking Zone",
        "double_parking" to "Double Parking",
        "handicap_zone" to "Handicap Zone",
        "fire_hydrant" to "Near Fire Hydrant",
        "expired_meter" to "Expired Meter",
        "other" to "Other"
    )
    val actionTypes = listOf("warning" to "Warning", "fine" to "Fine", "tow" to "Tow")

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.Large)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddCircle, null, tint = AccentRed, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New Violation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    }
                    IconButton(onClick = { if (!isSubmitting) onDismiss() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Vehicle info
                OutlinedTextField(
                    value = vehicleInfo, onValueChange = { vehicleInfo = it },
                    label = { Text("Vehicle Number / Info") },
                    leadingIcon = { Icon(Icons.Outlined.DirectionsCar, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentParking, cursorColor = AccentParking)
                )

                Spacer(Modifier.height(Spacing.Medium))

                // Violation type
                Text("Violation Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(violationTypes) { (type, label) ->
                        val isSel = selectedType == type
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedType = type },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(CornerRadius.Round),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentRed.copy(alpha = 0.15f),
                                selectedLabelColor = AccentRed
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = colors.cardBorder.copy(alpha = 0.3f),
                                selectedBorderColor = AccentRed.copy(alpha = 0.5f),
                                enabled = true, selected = isSel
                            )
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                // Action type
                Text("Action", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actionTypes.forEach { (type, label) ->
                        val isSel = selectedAction == type
                        val color = when (type) { "fine" -> AccentRed; "tow" -> Color(0xFF7C3AED); else -> AccentOrange }
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedAction = type },
                            label = { Text(label, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    when (type) { "fine" -> Icons.Outlined.Receipt; "tow" -> Icons.Outlined.LocalShipping; else -> Icons.Outlined.Warning },
                                    null, modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(CornerRadius.Round),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.15f),
                                selectedLabelColor = color,
                                selectedLeadingIconColor = color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = colors.cardBorder.copy(alpha = 0.3f),
                                selectedBorderColor = color.copy(alpha = 0.5f),
                                enabled = true, selected = isSel
                            )
                        )
                    }
                }

                // Fine amount (only if fine)
                AnimatedVisibility(visible = selectedAction == "fine") {
                    Column {
                        Spacer(Modifier.height(Spacing.Medium))
                        OutlinedTextField(
                            value = fineAmount, onValueChange = { fineAmount = it },
                            label = { Text("Fine Amount (₹)") },
                            leadingIcon = { Icon(Icons.Outlined.CurrencyRupee, null, Modifier.size(20.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentRed, cursorColor = AccentRed)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                // Description
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentParking, cursorColor = AccentParking)
                )

                Spacer(Modifier.height(Spacing.Medium))

                // Address
                OutlinedTextField(
                    value = address, onValueChange = { address = it },
                    label = { Text("Address / Landmark") },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentParking, cursorColor = AccentParking)
                )

                Spacer(Modifier.height(Spacing.Medium))

                // Note
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    leadingIcon = { Icon(Icons.Outlined.StickyNote2, null, Modifier.size(20.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentParking, cursorColor = AccentParking)
                )

                Spacer(Modifier.height(Spacing.Large))

                // Photo capture
                Text("Evidence Photo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))

                if (photoUri != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photoUri).crossfade(true).build(),
                            contentDescription = "Evidence",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            border = BorderStroke(1.dp, AccentParking.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp), tint = AccentParking)
                            Spacer(Modifier.width(6.dp))
                            Text("Camera", color = AccentParking, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp), tint = colors.textSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.XLarge))

                // Submit
                Button(
                    onClick = {
                        if (vehicleInfo.isBlank() && description.isBlank()) {
                            Toast.makeText(context, "Please fill vehicle info or description", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        vm.submitViolation(
                            vehicleInfo = vehicleInfo,
                            violationType = selectedType,
                            description = description,
                            photoUri = photoUri,
                            latitude = userLocation?.latitude ?: 0.0,
                            longitude = userLocation?.longitude ?: 0.0,
                            address = address,
                            fineAmount = fineAmount,
                            note = note,
                            actionType = selectedAction,
                            onSuccess = {
                                isSubmitting = false
                                Toast.makeText(context, "Violation recorded!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = { msg ->
                                isSubmitting = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !isSubmitting,
                    elevation = ButtonDefaults.buttonElevation(4.dp, 0.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Submitting...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Record Violation", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Nearby Pulsing Icon
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NearbyPulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "nearby")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "nearbyAlpha"
    )
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(AccentRed.copy(alpha = alpha))
    )
}


// ═══════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════

private fun createParkingTempUri(context: Context): Uri {
    val dir = File(context.cacheDir, "parking_photos")
    dir.mkdirs()
    val file = File(dir, "violation_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun createParkingMarkerBitmap(color: Int, sizeDp: Int): Bitmap {
    val sizePx = (sizeDp * 2.5f).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = sizePx * 0.14f }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
    val r = sizePx / 2f
    canvas.drawCircle(r, r, r - sizePx * 0.07f, paint)
    canvas.drawCircle(r, r, r - sizePx * 0.07f, border)
    canvas.drawCircle(r, r, r * 0.2f, dot)
    return bitmap
}

private fun distanceBetweenParking(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}

private fun formatDistParking(meters: Float): String = when {
    meters < 1000 -> "${meters.toInt()}m"
    else -> String.format("%.1fkm", meters / 1000f)
}
