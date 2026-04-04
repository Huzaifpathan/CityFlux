package com.example.cityflux.ui.parking

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
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════
// ParkingScreen — Production-grade parking with list + map toggle
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
fun ParkingScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
    vm: ParkingViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val state by vm.uiState.collectAsState()

    // ── Analytics ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("parking_opened", null) } catch (_: Exception) {}
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
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // ── Get user location ──
    var userLatLng by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) return@LaunchedEffect
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val loc: Location? = fusedClient.lastLocation.await()
            loc?.let {
                userLatLng = LatLng(it.latitude, it.longitude)
                vm.setUserLocation(it)
            }
        } catch (_: Exception) {}
    }

    // ── View mode toggle ──
    var selectedTab by remember { mutableIntStateOf(0) } // 0=List, 1=Map, 2=My Bookings

    // ── Filter sheet ──
    var showFilterSheet by remember { mutableStateOf(false) }

    // ── Selected parking for detail popup ──
    var selectedSpot by remember { mutableStateOf<ParkingSpot?>(null) }
    
    // ── Book Now Dialog State ──
    var showBookNowDialog by remember { mutableStateOf(false) }
    var bookNowSpot by remember { mutableStateOf<ParkingSpot?>(null) }
    
    // ── Distance restriction snackbar ──
    var showDistanceRestrictionSnackbar by remember { mutableStateOf(false) }
    var restrictedDistance by remember { mutableStateOf(0f) }
    
    // ── Navigate target (for internal map navigation) ──
    var navigateToSpot by remember { mutableStateOf<ParkingSpot?>(null) }
    
    // ══════════════════════════════════════════════════════════════
    // NAVIGATION MODE STATE (Google Maps style turn-by-turn)
    // ══════════════════════════════════════════════════════════════
    var isNavigating by remember { mutableStateOf(false) }
    var navigationTarget by remember { mutableStateOf<ParkingSpot?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeDistanceKm by remember { mutableStateOf(0.0) }
    var routeDurationMin by remember { mutableStateOf(0) }
    var isLoadingRoute by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    // ══════════════════════════════════════════════════════════════
    // TURN-BY-TURN STATE (Phase 3)
    // ══════════════════════════════════════════════════════════════
    var navigationSteps by remember { mutableStateOf<List<NavigationStep>>(emptyList()) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var distanceToNextStep by remember { mutableStateOf(0) } // meters
    
    // ══════════════════════════════════════════════════════════════
    // LIVE TRACKING STATE (Phase 2)
    // ══════════════════════════════════════════════════════════════
    var remainingDistanceKm by remember { mutableStateOf(0.0) }
    var remainingDurationMin by remember { mutableStateOf(0) }
    var coveredDistanceKm by remember { mutableStateOf(0.0) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var currentSpeed by remember { mutableFloatStateOf(0f) } // km/h
    var hasArrived by remember { mutableStateOf(false) }
    
    // ── Live Location Tracking ──
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Location callback for continuous updates during navigation
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val newLatLng = LatLng(location.latitude, location.longitude)
                    userLatLng = newLatLng
                    vm.setUserLocation(location)
                    
                    // Update speed (m/s to km/h)
                    currentSpeed = location.speed * 3.6f
                    
                    // Calculate remaining distance to destination
                    navigationTarget?.location?.let { destGeo ->
                        val destLatLng = LatLng(destGeo.latitude, destGeo.longitude)
                        val remainingMeters = calculateDistance(newLatLng, destLatLng)
                        remainingDistanceKm = remainingMeters / 1000.0
                        
                        // Estimate remaining time based on average speed or current speed
                        val avgSpeedKmh = if (currentSpeed > 5f) currentSpeed else 30f // default 30 km/h
                        remainingDurationMin = ((remainingDistanceKm / avgSpeedKmh) * 60).toInt().coerceAtLeast(1)
                        
                        // Calculate progress
                        if (routeDistanceKm > 0) {
                            coveredDistanceKm = routeDistanceKm - remainingDistanceKm
                            progressPercent = ((coveredDistanceKm / routeDistanceKm) * 100).toFloat().coerceIn(0f, 100f)
                        }
                        
                        // Check if arrived (within 30 meters)
                        if (remainingMeters < 30) {
                            hasArrived = true
                        }
                    }
                    
                    // ══════════════════════════════════════════════════════════════
                    // TURN-BY-TURN: Track current step (Phase 3)
                    // ══════════════════════════════════════════════════════════════
                    if (navigationSteps.isNotEmpty() && currentStepIndex < navigationSteps.size) {
                        val currentStep = navigationSteps[currentStepIndex]
                        
                        // Calculate distance to next step's end point
                        val distToStepEnd = calculateDistance(newLatLng, currentStep.endLocation)
                        distanceToNextStep = distToStepEnd.toInt()
                        
                        // If within 30m of step end, advance to next step
                        if (distToStepEnd < 30 && currentStepIndex < navigationSteps.size - 1) {
                            currentStepIndex++
                            Log.d("ParkingNavigation", "Advanced to step ${currentStepIndex + 1}/${navigationSteps.size}")
                        }
                    }
                }
            }
        }
    }
    
    // Start/Stop live location updates based on navigation state
    DisposableEffect(isNavigating, hasLocationPermission) {
        if (isNavigating && hasLocationPermission) {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                2000L // Update every 2 seconds
            ).apply {
                setMinUpdateIntervalMillis(1000L)
                setMinUpdateDistanceMeters(5f) // Update when moved 5 meters
            }.build()
            
            try {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
                Log.d("ParkingNavigation", "Started live location tracking")
            } catch (e: SecurityException) {
                Log.e("ParkingNavigation", "Location permission denied: ${e.message}")
            }
        }
        
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("ParkingNavigation", "Stopped live location tracking")
        }
    }
    
    // Reset live tracking state when navigation stops
    LaunchedEffect(isNavigating) {
        if (!isNavigating) {
            remainingDistanceKm = 0.0
            remainingDurationMin = 0
            coveredDistanceKm = 0.0
            progressPercent = 0f
            currentSpeed = 0f
            hasArrived = false
        } else {
            // Initialize remaining with total when navigation starts
            remainingDistanceKm = routeDistanceKm
            remainingDurationMin = routeDurationMin
        }
    }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailable(context) }

    // ── Report Illegal Parking ──
    var showReportDialog by remember { mutableStateOf(false) }

    // ── Find Nearby Best Parking Dialog ──
    var showFindNearbyDialog by remember { mutableStateOf(true) }  // Show automatically on screen open

    // ── Favorite Spots ──
    var favoriteIds by remember { mutableStateOf(setOf<String>()) }

    // ── Notify Me (slot alerts) ──
    var notifySpotIds by remember { mutableStateOf(setOf<String>()) }

    // ── Filtered & sorted list ──
    val filteredSpots = remember(state) { vm.getFilteredSpots(state) }

    // ── Totals for header ──
    val totalSpots = filteredSpots.size
    val totalAvailable = filteredSpots.sumOf { spot ->
        state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
    }
    val totalFull = filteredSpots.count { spot ->
        (state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots) <= 0
    }
    val nearbyCount = filteredSpots.count { spot ->
        val dist = vm.distanceTo(spot)
        dist != null && dist < 1000f
    }

    // ── Search State ──
    var searchQuery by remember { mutableStateOf("") }

    // ── Quick Filter ──
    var quickFilter by remember { mutableStateOf("All") }

    // ── Apply search + quick filter ──
    val displaySpots = remember(filteredSpots, searchQuery, quickFilter, state.parkingLive, favoriteIds) {
        filteredSpots.filter { spot ->
            val matchesSearch = searchQuery.isBlank() ||
                spot.address.contains(searchQuery, ignoreCase = true) ||
                spot.id.contains(searchQuery, ignoreCase = true)
            val available = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
            val dist = vm.distanceTo(spot)
            val matchesQuick = when (quickFilter) {
                "Available" -> available > 0
                "Full" -> available <= 0
                "Near Me" -> dist != null && dist < 1000f
                "Legal" -> spot.isLegal
                "★ Saved" -> spot.id in favoriteIds
                else -> true
            }
            matchesSearch && matchesQuick
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ══════════════════════ Header (Premium) ══════════════════════
            ParkingTopBar(
                colors = colors,
                totalSpots = totalSpots,
                totalAvailable = totalAvailable,
                isLoading = state.isLoading
            )

            // ══════════════════════ Stats Strip ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                ParkingStatMiniCard(
                    label = "Total",
                    value = "$totalSpots",
                    icon = Icons.Outlined.LocalParking,
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                ParkingStatMiniCard(
                    label = "Free",
                    value = "$totalAvailable",
                    icon = Icons.Outlined.CheckCircle,
                    color = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                ParkingStatMiniCard(
                    label = "Full",
                    value = "$totalFull",
                    icon = Icons.Outlined.Block,
                    color = AccentRed,
                    modifier = Modifier.weight(1f)
                )
            }

            // ══════════════════════ Search Bar ══════════════════════
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                shape = RoundedCornerShape(CornerRadius.Large),
                color = colors.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = colors.textPrimary
                        ),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Search parking by name or address...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary
                                )
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                "Clear",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Find Best Nearby Parking Button ══════════════════════
            Surface(
                onClick = { showFindNearbyDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                shape = RoundedCornerShape(CornerRadius.Large),
                color = AccentParking.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.TravelExplore,
                        contentDescription = "Find nearby",
                        tint = AccentParking,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "🎯 Find Best Nearby Parking",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentParking
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = AccentParking,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ══════════════════════ Quick Filter Chips ══════════════════════
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                val chipData = listOf(
                    "All" to totalSpots,
                    "Available" to totalAvailable,
                    "Full" to totalFull,
                    "Near Me" to nearbyCount,
                    "Legal" to filteredSpots.count { it.isLegal },
                    "★ Saved" to filteredSpots.count { it.id in favoriteIds }
                )
                items(chipData.size) { idx ->
                    val (label, count) = chipData[idx]
                    val isSelected = quickFilter == label
                    val chipColor = when (label) {
                        "Available" -> AccentGreen
                        "Full" -> AccentRed
                        "Near Me" -> AccentOrange
                        "Legal" -> AccentParking
                        "★ Saved" -> AccentYellow
                        else -> PrimaryBlue
                    }
                    Surface(
                        onClick = { quickFilter = if (isSelected && label != "All") "All" else label },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = if (isSelected) chipColor.copy(alpha = 0.15f)
                            else colors.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chipColor else colors.textSecondary
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) chipColor.copy(alpha = 0.2f)
                                    else colors.textTertiary.copy(alpha = 0.15f),
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "$count",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) chipColor else colors.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ══════════════════════ View Toggle + Filter Chips ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // List / Map / My Bookings tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.Round)),
                    containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = PrimaryBlue,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("List", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.AutoMirrored.Outlined.ViewList, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Map", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Outlined.Map, null, Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("My Bookings", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)) }
                    )
                }

                // Filter chips row (only for List & Map tabs)
                if (selectedTab < 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        // Available only toggle
                        FilterChip(
                            selected = state.showAvailableOnly,
                            onClick = { vm.toggleAvailableOnly() },
                            label = {
                                Text(
                                    "Available",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = if (state.showAvailableOnly) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(CornerRadius.Round),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentParking.copy(alpha = 0.15f),
                            selectedLabelColor = AccentParking,
                            selectedLeadingIconColor = AccentParking
                        ),
                        modifier = Modifier.height(32.dp)
                    )

                    // Filter button
                    SmallFloatingActionButton(
                        onClick = { showFilterSheet = true },
                        shape = CircleShape,
                        containerColor = colors.surfaceVariant,
                        contentColor = colors.textSecondary,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Tune, "Filters", Modifier.size(16.dp))
                    }
                }
            }
            } // End of if (selectedTab < 2)

            // ══════════════════════ Active Filters Row ══════════════════════
            if (state.legalFilter != ParkingViewModel.LegalFilter.ALL ||
                state.sortMode != ParkingViewModel.SortMode.NEAREST
            ) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = Spacing.XLarge),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    if (state.legalFilter != ParkingViewModel.LegalFilter.ALL) {
                        item {
                            AssistChip(
                                onClick = { vm.setLegalFilter(ParkingViewModel.LegalFilter.ALL) },
                                label = {
                                    Text(
                                        if (state.legalFilter == ParkingViewModel.LegalFilter.LEGAL) "Legal only"
                                        else "Illegal only",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                                shape = RoundedCornerShape(CornerRadius.Round),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                    if (state.sortMode != ParkingViewModel.SortMode.NEAREST) {
                        item {
                            AssistChip(
                                onClick = { vm.setSortMode(ParkingViewModel.SortMode.NEAREST) },
                                label = {
                                    Text(
                                        "Sort: ${
                                            when (state.sortMode) {
                                                ParkingViewModel.SortMode.MOST_AVAILABLE -> "Most slots"
                                                ParkingViewModel.SortMode.NAME -> "Name"
                                                else -> ""
                                            }
                                        }",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(14.dp)) },
                                shape = RoundedCornerShape(CornerRadius.Round),
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Loading ══════════════════════
            AnimatedVisibility(visible = state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp)),
                    color = PrimaryBlue,
                    trackColor = PrimaryBlue.copy(alpha = 0.1f)
                )
            }

            // ══════════════════════ Content ══════════════════════
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(300),
                label = "parking_view_toggle",
                modifier = Modifier.fillMaxSize()
            ) { currentTab ->
                when (currentTab) {
                    1 -> {
                    // ──────── Map View ────────
                    ParkingMapView(
                        spots = displaySpots,
                        parkingLive = state.parkingLive,
                        userLatLng = userLatLng,
                        navigateToSpot = navigateToSpot,
                        onNavigateConsumed = { navigateToSpot = null },
                        // Navigation mode params
                        isNavigating = isNavigating,
                        routePoints = routePoints,
                        routeDistanceKm = routeDistanceKm,
                        routeDurationMin = routeDurationMin,
                        navigationTarget = navigationTarget,
                        // Live tracking params (Phase 2)
                        remainingDistanceKm = remainingDistanceKm,
                        remainingDurationMin = remainingDurationMin,
                        progressPercent = progressPercent,
                        currentSpeed = currentSpeed,
                        hasArrived = hasArrived,
                        // Turn-by-turn params (Phase 3)
                        navigationSteps = navigationSteps,
                        currentStepIndex = currentStepIndex,
                        distanceToNextStep = distanceToNextStep,
                        onStopNavigation = {
                            isNavigating = false
                            navigationTarget = null
                            routePoints = emptyList()
                            routeDistanceKm = 0.0
                            routeDurationMin = 0
                            navigationSteps = emptyList()
                            currentStepIndex = 0
                        },
                        onMarkerClick = { spot ->
                            try { Firebase.analytics.logEvent("parking_card_clicked", null) } catch (_: Exception) {}
                            selectedSpot = spot
                        }
                    )
                    }
                    0 -> {
                    // ──────── List View ────────
                    if (state.isLoading && displaySpots.isEmpty()) {
                        // Shimmer loading
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.XLarge),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Spacer(Modifier.height(Spacing.Small))
                            repeat(5) { ShimmerParkingCard() }
                        }
                    } else if (displaySpots.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.LocalParking,
                                    null,
                                    modifier = Modifier.size(64.dp),
                                    tint = colors.textTertiary
                                )
                                Spacer(Modifier.height(Spacing.Medium))
                                Text(
                                    "No parking nearby",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textSecondary
                                )
                                Text(
                                    "Try adjusting your filters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary
                                )
                                if (state.error != null) {
                                    Spacer(Modifier.height(Spacing.Medium))
                                    OutlinedButton(
                                        onClick = { vm.retry() },
                                        shape = RoundedCornerShape(CornerRadius.Round)
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Spacing.XLarge, end = Spacing.XLarge,
                                top = Spacing.Small, bottom = 80.dp // room for FABs
                            ),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            items(displaySpots, key = { it.id }) { spot ->
                                ParkingCard(
                                    spot = spot,
                                    available = state.parkingLive[spot.id]?.availableSlots
                                        ?: spot.availableSlots,
                                    distance = vm.distanceTo(spot),
                                    isFavorite = spot.id in favoriteIds,
                                    isNotifyOn = spot.id in notifySpotIds,
                                    onToggleFavorite = {
                                        favoriteIds = if (spot.id in favoriteIds)
                                            favoriteIds - spot.id else favoriteIds + spot.id
                                    },
                                    onToggleNotify = {
                                        notifySpotIds = if (spot.id in notifySpotIds)
                                            notifySpotIds - spot.id else notifySpotIds + spot.id
                                    },
                                    onNavigate = {
                                        spot.location?.let { geo ->
                                            // Start navigation mode
                                            navigationTarget = spot
                                            selectedTab = 1
                                            isLoadingRoute = true
                                            
                                            // Fetch route from user location to parking
                                            coroutineScope.launch {
                                                userLatLng?.let { start ->
                                                    val end = LatLng(geo.latitude, geo.longitude)
                                                    val apiKey = getMapApiKey(context)
                                                    val result = fetchDirectionsRoute(start, end, apiKey)
                                                    routePoints = result.points
                                                    routeDistanceKm = result.distanceKm
                                                    routeDurationMin = result.durationMinutes
                                                    // Phase 3: Save turn-by-turn steps
                                                    navigationSteps = result.steps
                                                    currentStepIndex = 0
                                                    isNavigating = true
                                                    isLoadingRoute = false
                                                }
                                            }
                                        }
                                    },
                                    onBookNow = {
                                        // Check distance - only allow booking within 2km (2000 meters)
                                        val distanceMeters = vm.distanceTo(spot)
                                        if (distanceMeters != null && distanceMeters <= 2000f) {
                                            bookNowSpot = spot
                                            showBookNowDialog = true
                                        } else {
                                            restrictedDistance = distanceMeters ?: 0f
                                            showDistanceRestrictionSnackbar = true
                                        }
                                    },
                                    onClick = {
                                        try {
                                            Firebase.analytics.logEvent("parking_card_clicked", null)
                                        } catch (_: Exception) {}
                                        selectedSpot = spot
                                    }
                                )
                            }
                        }
                    }
                    }
                    2 -> {
                        // ──────── My Bookings View (Enhanced) ────────
                        MyBookingsContentEnhanced(
                            onBookingClick = { booking ->
                                // Handle booking click if needed
                            }
                        )
                    }
                }
            }
        }

        // ══════════════════════ FABs (bottom-right, list view only) ══════════════════════
        if (selectedTab == 0) Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.Medium, bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            horizontalAlignment = Alignment.End
        ) {
            // Filter FAB
            SmallFloatingActionButton(
                onClick = { showFilterSheet = true },
                shape = CircleShape,
                containerColor = colors.cardBackground,
                contentColor = colors.textPrimary,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.FilterList, "Filter", Modifier.size(20.dp))
            }

            // Report Illegal Parking FAB
            SmallFloatingActionButton(
                onClick = { showReportDialog = true },
                shape = CircleShape,
                containerColor = AccentRed.copy(alpha = 0.9f),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(6.dp)
            ) {
                Icon(Icons.Outlined.ReportProblem, "Report", Modifier.size(20.dp))
            }

            // Find Best Nearby Parking FAB
            FloatingActionButton(
                onClick = {
                    try {
                        Firebase.analytics.logEvent("find_best_parking_clicked", null)
                    } catch (_: Exception) {}
                    showFindNearbyDialog = true
                },
                shape = CircleShape,
                containerColor = AccentParking,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Filled.NearMe, "Nearest Parking", Modifier.size(24.dp))
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
                    modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WifiOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
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

        // ══════════════════════ Detail Popup ══════════════════════
        AnimatedVisibility(
            visible = selectedSpot != null && !isNavigating,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            selectedSpot?.let { spot ->
                ParkingDetailCard(
                    spot = spot,
                    available = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots,
                    distance = vm.distanceTo(spot),
                    onNavigate = {
                        try {
                            Firebase.analytics.logEvent("navigate_to_parking_clicked", null)
                        } catch (_: Exception) {}
                        spot.location?.let { geo ->
                            // Start navigation mode with route
                            navigationTarget = spot
                            selectedSpot = null
                            selectedTab = 1
                            isLoadingRoute = true
                            
                            coroutineScope.launch {
                                userLatLng?.let { start ->
                                    val end = LatLng(geo.latitude, geo.longitude)
                                    val apiKey = getMapApiKey(context)
                                    val result = fetchDirectionsRoute(start, end, apiKey)
                                    routePoints = result.points
                                    routeDistanceKm = result.distanceKm
                                    routeDurationMin = result.durationMinutes
                                    // Phase 3: Save turn-by-turn steps
                                    navigationSteps = result.steps
                                    currentStepIndex = 0
                                    isNavigating = true
                                    isLoadingRoute = false
                                }
                            }
                        }
                    },
                    onDismiss = { selectedSpot = null }
                )
            }
        }

        // ══════════════════════ Filter Bottom Sheet ══════════════════════
        if (showFilterSheet) {
            FilterBottomSheet(
                currentSort = state.sortMode,
                currentLegal = state.legalFilter,
                showAvailableOnly = state.showAvailableOnly,
                onSortChanged = { vm.setSortMode(it) },
                onLegalChanged = { vm.setLegalFilter(it) },
                onAvailableToggle = { vm.toggleAvailableOnly() },
                onDismiss = { showFilterSheet = false }
            )
        }

        // ══════════════════════ Report Illegal Parking Dialog ══════════════════════
        if (showReportDialog) {
            ReportIllegalParkingDialog(
                userLatLng = userLatLng,
                onDismiss = { showReportDialog = false }
            )
        }

        // ══════════════════════ Find Nearby Best Parking Dialog ══════════════════════
        if (showFindNearbyDialog) {
            FindNearbyBestParkingDialog(
                userLatLng = userLatLng,
                hasLocationPermission = hasLocationPermission,
                spots = filteredSpots,
                parkingLive = state.parkingLive,
                vm = vm,
                onSpotSelected = { spot ->
                    selectedSpot = spot
                    showFindNearbyDialog = false
                },
                onNavigateToSpot = { spot ->
                    showFindNearbyDialog = false
                    spot.location?.let { geo ->
                        navigationTarget = spot
                        selectedTab = 1
                        isLoadingRoute = true
                        coroutineScope.launch {
                            userLatLng?.let { start ->
                                val end = LatLng(geo.latitude, geo.longitude)
                                val apiKey = getMapApiKey(context)
                                val result = fetchDirectionsRoute(start, end, apiKey)
                                routePoints = result.points
                                routeDistanceKm = result.distanceKm
                                routeDurationMin = result.durationMinutes
                                navigationSteps = result.steps
                                currentStepIndex = 0
                                isNavigating = true
                                isLoadingRoute = false
                            }
                        }
                    }
                },
                onBookNow = { spot ->
                    showFindNearbyDialog = false
                    // Check distance - only allow booking within 2km (2000 meters)
                    val distanceMeters = vm.distanceTo(spot)
                    if (distanceMeters != null && distanceMeters <= 2000f) {
                        bookNowSpot = spot
                        showBookNowDialog = true
                    } else {
                        restrictedDistance = distanceMeters ?: 0f
                        showDistanceRestrictionSnackbar = true
                    }
                },
                onDismiss = { showFindNearbyDialog = false }
            )
        }
        
        // ══════════════════════ Book Now Dialog ══════════════════════
        if (showBookNowDialog && bookNowSpot != null) {
            BookNowDialog(
                parkingSpot = bookNowSpot!!,
                userLocation = userLatLng?.let { 
                    android.location.Location("user").apply {
                        latitude = it.latitude
                        longitude = it.longitude
                    }
                },
                onDismiss = { 
                    showBookNowDialog = false
                    bookNowSpot = null
                },
                onBookingCreated = { bookingId ->
                    showBookNowDialog = false
                    bookNowSpot = null
                    // Navigate to QR display or booking details
                    try {
                        Firebase.analytics.logEvent("booking_created", null)
                    } catch (_: Exception) {}
                },
                onNavigateToParking = {
                    bookNowSpot?.location?.let { geo ->
                        // Start navigation to parking
                        navigationTarget = bookNowSpot
                        showBookNowDialog = false
                        bookNowSpot = null
                        selectedTab = 1
                        isLoadingRoute = true
                        
                        coroutineScope.launch {
                            userLatLng?.let { start ->
                                val end = LatLng(geo.latitude, geo.longitude)
                                val apiKey = getMapApiKey(context)
                                val result = fetchDirectionsRoute(start, end, apiKey)
                                routePoints = result.points
                                routeDistanceKm = result.distanceKm
                                routeDurationMin = result.durationMinutes
                                navigationSteps = result.steps
                                currentStepIndex = 0
                                isNavigating = true
                                isLoadingRoute = false
                            }
                        }
                    }
                }
            )
        }
        
        // ══════════════════════ Distance Restriction Snackbar ══════════════════════
        if (showDistanceRestrictionSnackbar) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(Spacing.Large),
                containerColor = AccentRed,
                contentColor = Color.White,
                action = {
                    TextButton(onClick = { showDistanceRestrictionSnackbar = false }) {
                        Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOff,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Parking area is ${String.format("%.1f", restrictedDistance / 1000f)} km away. You can only book within 2 km of your location.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Auto-dismiss after 5 seconds
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(5000)
                showDistanceRestrictionSnackbar = false
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// View Toggle Button
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ViewToggleButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) PrimaryBlue else Color.Transparent
    val contentColor = if (isSelected) Color.White else MaterialTheme.cityFluxColors.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Parking Card (List item)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingCard(
    spot: ParkingSpot,
    available: Int,
    distance: Float?,
    isFavorite: Boolean,
    isNotifyOn: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleNotify: () -> Unit,
    onNavigate: () -> Unit,
    onBookNow: () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val statusColor = when {
        available == 0 -> AccentRed
        available <= 5 -> AccentAlerts
        else -> AccentParking
    }
    val statusLabel = when {
        available == 0 -> "Full"
        available <= 5 -> "Filling Up"
        else -> "Available"
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                4.dp,
                RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Left accent bar
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(statusColor)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Icon
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.LocalParking,
                            null,
                            tint = statusColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(Modifier.width(Spacing.Medium))

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        spot.address.ifBlank { "Parking ${spot.id}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                    ) {
                        if (distance != null) {
                            val distColor = when {
                                distance < 500f -> AccentGreen
                                distance < 2000f -> AccentOrange
                                else -> AccentRed
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = distColor.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.NearMe,
                                        null,
                                        tint = distColor,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        formatDistance(distance),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = distColor
                                    )
                                }
                            }
                            Text("·", color = colors.textTertiary)
                        }
                        Text(
                            "$available / ${spot.totalSlots} slots",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = statusColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        // Parking Type Badge (FREE / PAID)
                        val parkingTypeColor = if (spot.isFree) AccentGreen else AccentBlue
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = parkingTypeColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                spot.rateDisplayString,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = parkingTypeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        // Legal / Illegal tag
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (spot.isLegal) AccentParking.copy(alpha = 0.08f)
                            else AccentAlerts.copy(alpha = 0.08f)
                        ) {
                            Text(
                                if (spot.isLegal) "Legal" else "Illegal",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (spot.isLegal) AccentParking else AccentAlerts,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Slot count badge + favorite
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            "Favorite",
                            tint = if (isFavorite) AccentYellow else colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$available",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                            Text(
                                "free",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }
            }

            // ── Occupancy Progress Bar ──
            val occupancy = if (spot.totalSlots > 0)
                1f - (available.toFloat() / spot.totalSlots) else 0f
            Column(modifier = Modifier.padding(horizontal = Spacing.Large).padding(bottom = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${(occupancy * 100).toInt()}% occupied",
                        fontSize = 9.sp,
                        color = colors.textTertiary
                    )
                    Text(
                        "${spot.totalSlots - available}/${spot.totalSlots} used",
                        fontSize = 9.sp,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(occupancy.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(statusColor.copy(alpha = 0.7f), statusColor)
                                )
                            )
                    )
                }
            }

            // ── Action Row: Book + Navigate + Notify ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                // Book Now button (Phase 4) - disabled if distance > 2km
                if (available > 0) {
                    val isTooFar = distance != null && distance > 2000f
                    val buttonColor = if (isTooFar) colors.textSecondary else AccentParking
                    
                    Surface(
                        onClick = { if (!isTooFar) onBookNow() },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = buttonColor.copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isTooFar) Icons.Filled.LocationOff else Icons.Filled.CalendarMonth, 
                                null, 
                                tint = buttonColor, 
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isTooFar) "Too Far" else "Book Now", 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.SemiBold, 
                                color = buttonColor
                            )
                        }
                    }
                }
                
                // Navigate button
                if (spot.location != null) {
                    Surface(
                        onClick = onNavigate,
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.Navigation, null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Navigate", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                        }
                    }
                }
                // Notify Me button (for full spots)
                if (available == 0) {
                    Surface(
                        onClick = onToggleNotify,
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = if (isNotifyOn) AccentGreen.copy(alpha = 0.12f)
                            else AccentOrange.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f).height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (isNotifyOn) Icons.Filled.NotificationsActive
                                else Icons.Outlined.NotificationAdd,
                                null,
                                tint = if (isNotifyOn) AccentGreen else AccentOrange,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isNotifyOn) "Notifying" else "Notify Me",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isNotifyOn) AccentGreen else AccentOrange
                            )
                        }
                    }
                }
                // Last updated timestamp
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AccessTime,
                            null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Updated just now",
                            fontSize = 9.sp,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Parking Detail Card (Bottom popup)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingDetailCard(
    spot: ParkingSpot,
    available: Int,
    distance: Float?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val isFull = available <= 0
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.LocalParking, null, tint = statusColor, modifier = Modifier.size(24.dp))
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
                    Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Stats row - now includes pricing info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                StatChip("Available", available.toString(), statusColor, Modifier.weight(1f))
                StatChip("Total", spot.totalSlots.toString(), PrimaryBlue, Modifier.weight(1f))
                // Pricing badge
                val priceColor = if (spot.isFree) AccentGreen else AccentBlue
                StatChip("Rate", spot.rateDisplayString, priceColor, Modifier.weight(1f))
            }

            Spacer(Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                StatChip("Type", if (spot.isLegal) "Legal" else "Illegal",
                    if (spot.isLegal) AccentParking else AccentAlerts, Modifier.weight(1f))
                // Duration limits
                if (spot.minDuration > 0 || spot.maxDuration > 0) {
                    val minHrs = (spot.minDuration / 60f)
                    val maxHrs = (spot.maxDuration / 60f)
                    val durationText = if (maxHrs >= 8) "Min ${if (minHrs < 1) "${spot.minDuration}min" else "${minHrs.toInt()}hr"}"
                        else "${if (minHrs < 1) "${spot.minDuration}min" else "${minHrs.toInt()}hr"}-${maxHrs.toInt()}hr"
                    StatChip("Duration", durationText, AccentTraffic, Modifier.weight(1f))
                }
                if (distance != null) {
                    StatChip("Distance", formatDistance(distance), AccentOrange, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Occupancy bar
            val occupancy = if (spot.totalSlots > 0)
                1f - (available.toFloat() / spot.totalSlots) else 0f
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(occupancy * 100).toInt()}% occupied", fontSize = 10.sp, color = colors.textTertiary)
                    Text("${spot.totalSlots - available}/${spot.totalSlots} used", fontSize = 10.sp, color = colors.textTertiary)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(occupancy.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.7f), statusColor)))
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Navigate button
            if (spot.location != null) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Outlined.Navigation, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.Small))
                    Text("Navigate", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatChip(
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
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Parking Map View (mini map with markers + Navigation support)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingMapView(
    spots: List<ParkingSpot>,
    parkingLive: Map<String, com.example.cityflux.model.ParkingLive>,
    userLatLng: LatLng?,
    navigateToSpot: ParkingSpot? = null,
    onNavigateConsumed: () -> Unit = {},
    // Navigation mode params
    isNavigating: Boolean = false,
    routePoints: List<LatLng> = emptyList(),
    routeDistanceKm: Double = 0.0,
    routeDurationMin: Int = 0,
    navigationTarget: ParkingSpot? = null,
    // Live tracking params (Phase 2)
    remainingDistanceKm: Double = 0.0,
    remainingDurationMin: Int = 0,
    progressPercent: Float = 0f,
    currentSpeed: Float = 0f,
    hasArrived: Boolean = false,
    // Turn-by-turn params (Phase 3)
    navigationSteps: List<NavigationStep> = emptyList(),
    currentStepIndex: Int = 0,
    distanceToNextStep: Int = 0,
    onStopNavigation: () -> Unit = {},
    onMarkerClick: (ParkingSpot) -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val defaultLocation = LatLng(17.6599, 75.9064) // Solapur
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng ?: defaultLocation, 14f)
    }

    LaunchedEffect(userLatLng) {
        userLatLng?.let {
            if (!isNavigating) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f), durationMs = 600)
            }
        }
    }
    
    // Navigate to selected parking spot
    LaunchedEffect(navigateToSpot) {
        navigateToSpot?.location?.let { geo ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(geo.latitude, geo.longitude), 17f),
                durationMs = 800
            )
            onNavigateConsumed()
        }
    }
    
    // Fit route in view when navigation starts
    LaunchedEffect(routePoints) {
        if (routePoints.size >= 2) {
            val boundsBuilder = LatLngBounds.builder()
            routePoints.forEach { boundsBuilder.include(it) }
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100),
                durationMs = 800
            )
        }
    }

    val parkingGreenBitmap = remember {
        createCircleMarkerBitmap(AccentParking.toArgb(), 38)
    }
    val parkingRedBitmap = remember {
        createCircleMarkerBitmap(AccentRed.toArgb(), 38)
    }
    
    // Route colors (Google Maps style purple/blue)
    val routeColor = Color(0xFF4285F4)
    val routeShadowColor = Color(0xFF1A73E8)

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = userLatLng != null,
                mapType = MapType.NORMAL,
                isBuildingEnabled = true
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = !isNavigating,
                compassEnabled = true,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            // ══════════════════════════════════════════════════════
            // ROUTE POLYLINE (Google Maps style)
            // ══════════════════════════════════════════════════════
            if (routePoints.isNotEmpty()) {
                // Shadow/outline polyline
                Polyline(
                    points = routePoints,
                    color = routeShadowColor.copy(alpha = 0.3f),
                    width = 18f
                )
                // Main route polyline
                Polyline(
                    points = routePoints,
                    color = routeColor,
                    width = 12f
                )
                
                // Start marker (user location)
                routePoints.firstOrNull()?.let { start ->
                    Marker(
                        state = MarkerState(position = start),
                        title = "Your Location",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
                
                // End marker (parking destination)
                routePoints.lastOrNull()?.let { end ->
                    Marker(
                        state = MarkerState(position = end),
                        title = navigationTarget?.address ?: "Destination",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }
            
            // ══════════════════════════════════════════════════════
            // PARKING MARKERS
            // ══════════════════════════════════════════════════════
            spots.forEach { spot ->
                val loc = spot.location ?: return@forEach
                val available = parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
                val isFull = available <= 0
                
                // Hide other markers during navigation, only show destination
                if (isNavigating && spot.id != navigationTarget?.id) return@forEach

                Marker(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    title = spot.address.ifBlank { "Parking ${spot.id}" },
                    snippet = if (isFull) "FULL" else "$available slots available",
                    icon = BitmapDescriptorFactory.fromBitmap(
                        if (isFull) parkingRedBitmap else parkingGreenBitmap
                    ),
                    onClick = {
                        onMarkerClick(spot)
                        true
                    }
                )
            }
        }
        
        // ══════════════════════════════════════════════════════════
        // NAVIGATION PANEL (Bottom bar - Google Maps style)
        // ══════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = isNavigating,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NavigationPanel(
                destinationName = navigationTarget?.address ?: "Parking",
                // Use live remaining values if available, otherwise total
                distanceKm = if (remainingDistanceKm > 0) remainingDistanceKm else routeDistanceKm,
                durationMin = if (remainingDurationMin > 0) remainingDurationMin else routeDurationMin,
                progressPercent = progressPercent,
                currentSpeed = currentSpeed,
                hasArrived = hasArrived,
                // Turn-by-turn (Phase 3)
                navigationSteps = navigationSteps,
                currentStepIndex = currentStepIndex,
                distanceToNextStep = distanceToNextStep,
                onStopNavigation = onStopNavigation
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Filter Bottom Sheet
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentSort: ParkingViewModel.SortMode,
    currentLegal: ParkingViewModel.LegalFilter,
    showAvailableOnly: Boolean,
    onSortChanged: (ParkingViewModel.SortMode) -> Unit,
    onLegalChanged: (ParkingViewModel.LegalFilter) -> Unit,
    onAvailableToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.cardBackground,
        shape = RoundedCornerShape(topStart = CornerRadius.XLarge, topEnd = CornerRadius.XLarge)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.XLarge, vertical = Spacing.Medium)
        ) {
            Text(
                "Filter & Sort",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(Spacing.Large))

            // Sort by
            Text(
                "Sort by",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(Spacing.Small))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                ParkingViewModel.SortMode.entries.forEach { mode ->
                    FilterChip(
                        selected = currentSort == mode,
                        onClick = { onSortChanged(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    ParkingViewModel.SortMode.NEAREST -> "Nearest"
                                    ParkingViewModel.SortMode.MOST_AVAILABLE -> "Most Slots"
                                    ParkingViewModel.SortMode.NAME -> "Name"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.15f),
                            selectedLabelColor = PrimaryBlue
                        )
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Large))

            // Legal filter
            Text(
                "Parking type",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(Spacing.Small))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                ParkingViewModel.LegalFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = currentLegal == filter,
                        onClick = { onLegalChanged(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    ParkingViewModel.LegalFilter.ALL -> "All"
                                    ParkingViewModel.LegalFilter.LEGAL -> "Legal"
                                    ParkingViewModel.LegalFilter.ILLEGAL -> "Illegal"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentParking.copy(alpha = 0.15f),
                            selectedLabelColor = AccentParking
                        )
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Large))

            // Available only
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Show available only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary
                )
                Switch(
                    checked = showAvailableOnly,
                    onCheckedChange = { onAvailableToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentParking
                    )
                )
            }

            Spacer(Modifier.height(Spacing.Section))
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Shimmer Loading Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerParkingCard() {
    val colors = MaterialTheme.cityFluxColors
    val shimmerColors = listOf(
        colors.surfaceVariant,
        colors.surfaceVariant.copy(alpha = 0.5f),
        colors.surfaceVariant
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(brush)
            )
            Spacer(Modifier.width(Spacing.Medium))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            Box(
                Modifier
                    .size(48.dp, 40.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(brush)
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════

private fun createCircleMarkerBitmap(color: Int, sizeDp: Int): android.graphics.Bitmap {
    val sizePx = (sizeDp * 2.5f).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = sizePx * 0.12f
    }
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius - sizePx * 0.06f, paint)
    canvas.drawCircle(radius, radius, radius - sizePx * 0.06f, borderPaint)
    return bitmap
}

private fun formatDistance(meters: Float): String = when {
    meters < 1000 -> "${meters.toInt()}m"
    else -> String.format("%.1fkm", meters / 1000f)
}

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
    } catch (_: Exception) {}
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}


// ═══════════════════════════════════════════════════════════════════
// Navigation Panel (Google Maps style bottom bar with live tracking)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NavigationPanel(
    destinationName: String,
    distanceKm: Double,
    durationMin: Int,
    progressPercent: Float = 0f,
    currentSpeed: Float = 0f,
    hasArrived: Boolean = false,
    // Turn-by-turn params (Phase 3)
    navigationSteps: List<NavigationStep> = emptyList(),
    currentStepIndex: Int = 0,
    distanceToNextStep: Int = 0,
    onStopNavigation: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    // Current step
    val currentStep = navigationSteps.getOrNull(currentStepIndex)
    val nextStep = navigationSteps.getOrNull(currentStepIndex + 1)
    
    // Animated progress
    val animatedProgress by animateFloatAsState(
        targetValue = progressPercent / 100f,
        animationSpec = tween(500),
        label = "nav_progress"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.cardBackground,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Large)
        ) {
            // ══════════════════════════════════════════════════════
            // ARRIVED BANNER
            // ══════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = hasArrived,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Medium),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    color = AccentParking.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentParking,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(Spacing.Small))
                        Text(
                            "You have arrived!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentParking
                        )
                    }
                }
            }
            
            // ══════════════════════════════════════════════════════════════
            // TURN-BY-TURN INSTRUCTION CARD (Phase 3)
            // ══════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = currentStep != null && !hasArrived,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                currentStep?.let { step ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Spacing.Medium),
                        shape = RoundedCornerShape(CornerRadius.Large),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Maneuver icon
                            Surface(
                                shape = CircleShape,
                                color = PrimaryBlue,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    getManeuverIcon(step.maneuver),
                                    contentDescription = step.maneuver,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(24.dp)
                                )
                            }
                            
                            Spacer(Modifier.width(Spacing.Medium))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                // Distance to next maneuver
                                Text(
                                    formatDistanceMeters(distanceToNextStep),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                                // Instruction text
                                Text(
                                    step.instruction,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        // Next step preview
                        nextStep?.let { next ->
                            Spacer(Modifier.height(Spacing.Small))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Medium)
                                    .padding(bottom = Spacing.Small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Then: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary
                                )
                                Icon(
                                    getManeuverIcon(next.maneuver),
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    next.instruction.take(40) + if (next.instruction.length > 40) "..." else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            
            // ── Destination Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation icon with pulse animation when navigating
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_scale"
                )
                
                Surface(
                    shape = CircleShape,
                    color = if (hasArrived) AccentParking else PrimaryBlue,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = if (!hasArrived) pulseScale else 1f
                            scaleY = if (!hasArrived) pulseScale else 1f
                        }
                ) {
                    Icon(
                        if (hasArrived) Icons.Filled.LocalParking else Icons.Filled.Navigation,
                        contentDescription = "Navigating",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(12.dp)
                            .size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(Spacing.Medium))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (hasArrived) "Arrived at" else "Navigating to",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textTertiary
                    )
                    Text(
                        destinationName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Stop navigation button
                IconButton(
                    onClick = onStopNavigation,
                    modifier = Modifier
                        .size(44.dp)
                        .background(AccentRed.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Stop Navigation",
                        tint = AccentRed
                    )
                }
            }
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // ── Live Stats Row ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colors.cardBackground.copy(alpha = 0.5f),
                        RoundedCornerShape(CornerRadius.Large)
                    )
                    .padding(Spacing.Medium),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Remaining Distance
                NavigationStat(
                    icon = Icons.Outlined.Straighten,
                    value = formatDistanceKm(distanceKm),
                    label = "Remaining",
                    color = PrimaryBlue
                )
                
                // Divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.textTertiary.copy(alpha = 0.2f))
                )
                
                // ETA Duration
                NavigationStat(
                    icon = Icons.Outlined.Schedule,
                    value = "$durationMin min",
                    label = "ETA",
                    color = AccentParking
                )
                
                // Divider
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(colors.textTertiary.copy(alpha = 0.2f))
                )
                
                // Current Speed
                NavigationStat(
                    icon = Icons.Outlined.Speed,
                    value = "${currentSpeed.toInt()} km/h",
                    label = "Speed",
                    color = if (currentSpeed > 60f) AccentRed else colors.textSecondary
                )
            }
            
            Spacer(Modifier.height(Spacing.Medium))
            
            // ── Progress Bar with percentage ──
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Progress",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                    Text(
                        "${progressPercent.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (hasArrived) AccentParking else PrimaryBlue,
                    trackColor = colors.textTertiary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Composable
private fun NavigationStat(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cityFluxColors.textTertiary
        )
    }
}

private fun formatDistanceKm(km: Double): String = when {
    km < 1.0 -> "${(km * 1000).toInt()}m"
    else -> String.format("%.1f km", km)
}

private fun formatDistanceMeters(meters: Int): String = when {
    meters < 1000 -> "${meters}m"
    else -> String.format("%.1f km", meters / 1000.0)
}

/** Get appropriate icon for navigation maneuver */
@Composable
private fun getManeuverIcon(maneuver: String): ImageVector {
    return when {
        maneuver.contains("left") -> Icons.Filled.TurnLeft
        maneuver.contains("right") -> Icons.Filled.TurnRight
        maneuver.contains("uturn") || maneuver.contains("u-turn") -> Icons.Filled.UTurnLeft
        maneuver.contains("merge") -> Icons.Filled.MergeType
        maneuver.contains("ramp") -> Icons.Filled.CallMade
        maneuver.contains("fork") -> Icons.Filled.CallSplit
        maneuver.contains("roundabout") -> Icons.Filled.RotateRight
        maneuver.contains("straight") -> Icons.Filled.Straight
        maneuver.contains("arrive") || maneuver.contains("destination") -> Icons.Filled.Flag
        else -> Icons.Filled.ArrowUpward // Default: go straight
    }
}


// ═══════════════════════════════════════════════════════════════════
// Directions API Helper Functions
// ═══════════════════════════════════════════════════════════════════

/** Single navigation step (turn-by-turn instruction) */
data class NavigationStep(
    val instruction: String,           // "Turn left onto Main St"
    val distanceMeters: Int,           // Distance for this step
    val durationSeconds: Int,          // Duration for this step
    val maneuver: String,              // "turn-left", "turn-right", "straight", etc.
    val startLocation: LatLng,         // Start point of this step
    val endLocation: LatLng            // End point of this step
)

/** Directions API result with polyline, duration, distance and steps */
private data class DirectionsResult(
    val points: List<LatLng>,
    val durationMinutes: Int,
    val distanceKm: Double,
    val steps: List<NavigationStep> = emptyList()  // Turn-by-turn steps
)

/** Get Google Maps API key from AndroidManifest.xml */
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
    val fallback = DirectionsResult(listOf(start, end), 0, 0.0, emptyList())
    try {
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${start.latitude},${start.longitude}" +
                "&destination=${end.latitude},${end.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"
        Log.d("ParkingNavigation", "Directions API request: $url")
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.requestMethod = "GET"

        val responseCode = connection.responseCode
        val response = if (responseCode == 200) {
            connection.inputStream.bufferedReader().readText()
        } else {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "no body"
            Log.e("ParkingNavigation", "Directions API HTTP $responseCode: $errorBody")
            connection.disconnect()
            return@withContext fallback
        }
        connection.disconnect()

        val json = org.json.JSONObject(response)
        val status = json.optString("status", "UNKNOWN")
        Log.d("ParkingNavigation", "Directions API status: $status")

        if (status != "OK") {
            val errorMsg = json.optString("error_message", "no details")
            Log.e("ParkingNavigation", "Directions API error: $status - $errorMsg")
            return@withContext fallback
        }

        val routes = json.optJSONArray("routes")
        if (routes == null || routes.length() == 0) {
            Log.w("ParkingNavigation", "Directions API: no routes returned")
            return@withContext fallback
        }

        val route = routes.getJSONObject(0)

        // Extract duration & distance from first leg
        val legs = route.optJSONArray("legs")
        val leg = legs?.optJSONObject(0)
        val durationSec = leg?.optJSONObject("duration")?.optInt("value", 0) ?: 0
        val distanceMeters = leg?.optJSONObject("distance")?.optInt("value", 0) ?: 0
        
        // ══════════════════════════════════════════════════════════════
        // PARSE TURN-BY-TURN STEPS (Phase 3)
        // ══════════════════════════════════════════════════════════════
        val navigationSteps = mutableListOf<NavigationStep>()
        val stepsArray = leg?.optJSONArray("steps")
        if (stepsArray != null) {
            for (i in 0 until stepsArray.length()) {
                val stepJson = stepsArray.getJSONObject(i)
                
                // Extract instruction (remove HTML tags)
                val htmlInstruction = stepJson.optString("html_instructions", "")
                val instruction = htmlInstruction
                    .replace(Regex("<[^>]*>"), " ")  // Remove HTML tags
                    .replace(Regex("\\s+"), " ")     // Normalize whitespace
                    .trim()
                
                // Extract distance and duration
                val stepDistance = stepJson.optJSONObject("distance")?.optInt("value", 0) ?: 0
                val stepDuration = stepJson.optJSONObject("duration")?.optInt("value", 0) ?: 0
                
                // Extract maneuver (turn-left, turn-right, etc.)
                val maneuver = stepJson.optString("maneuver", "straight")
                
                // Extract start/end locations
                val startLoc = stepJson.optJSONObject("start_location")
                val endLoc = stepJson.optJSONObject("end_location")
                
                val startLatLng = LatLng(
                    startLoc?.optDouble("lat", 0.0) ?: 0.0,
                    startLoc?.optDouble("lng", 0.0) ?: 0.0
                )
                val endLatLng = LatLng(
                    endLoc?.optDouble("lat", 0.0) ?: 0.0,
                    endLoc?.optDouble("lng", 0.0) ?: 0.0
                )
                
                navigationSteps.add(
                    NavigationStep(
                        instruction = instruction,
                        distanceMeters = stepDistance,
                        durationSeconds = stepDuration,
                        maneuver = maneuver,
                        startLocation = startLatLng,
                        endLocation = endLatLng
                    )
                )
            }
            Log.d("ParkingNavigation", "Parsed ${navigationSteps.size} navigation steps")
        }

        val overviewPolyline = route
            .optJSONObject("overview_polyline")
            ?.optString("points", "")
            ?: ""

        if (overviewPolyline.isNotEmpty()) {
            val decoded = decodePolyline(overviewPolyline)
            Log.d("ParkingNavigation", "Directions API: decoded ${decoded.size} points, ${durationSec}s, ${distanceMeters}m")
            DirectionsResult(
                points = decoded,
                durationMinutes = (durationSec / 60.0).toInt().coerceAtLeast(1),
                distanceKm = distanceMeters / 1000.0,
                steps = navigationSteps
            )
        } else {
            Log.w("ParkingNavigation", "Directions API: empty polyline")
            fallback
        }
    } catch (e: Exception) {
        Log.e("ParkingNavigation", "Directions API failed: ${e.javaClass.simpleName}: ${e.message}")
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

/** Calculate distance between two LatLng points using Haversine formula (returns meters) */
private fun calculateDistance(from: LatLng, to: LatLng): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(to.latitude - from.latitude)
    val dLng = Math.toRadians(to.longitude - from.longitude)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
            sin(dLng / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}


// ═══════════════════════════════════════════════════════════════════
// TopBar — Police-style header with icon badge
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingTopBar(
    colors: CityFluxColors,
    totalSpots: Int,
    totalAvailable: Int,
    isLoading: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AccentParking.copy(alpha = 0.15f),
                        AccentParking.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Glowing icon badge
                Box(contentAlignment = Alignment.Center) {
                    // Outer glow
                    val infiniteTransition = rememberInfiniteTransition(label = "parking_glow")
                    val glowAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "glow"
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentParking.copy(alpha = glowAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentParking, AccentParking.copy(alpha = 0.8f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LocalParking,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column {
                    Text(
                        "Nearby Parking",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ParkingPulsingDot(color = AccentGreen, size = 6.dp)
                        Text(
                            if (isLoading) "Scanning nearby areas..."
                            else "Live Tracking · $totalAvailable slots free",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Live badge
            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = AccentGreen.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ParkingPulsingDot(color = AccentGreen, size = 8.dp)
                    Text(
                        "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ParkingPulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ═══════════════════════════════════════════════════════════════════
// Stats Strip — Glass-morphism mini stat cards
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingStatMiniCard(
    label: String,
    value: String,
    icon: ImageVector,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Report Illegal Parking Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportIllegalParkingDialog(
    userLatLng: LatLng?,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Double Parking") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val violationTypes = listOf(
        "Double Parking", "No Parking Zone", "Blocking Driveway",
        "Blocking Fire Hydrant", "Handicap Zone Violation", "Expired Meter", "Other"
    )

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        shape = RoundedCornerShape(CornerRadius.XLarge),
        containerColor = colors.cardBackground,
        title = {
            if (submitted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report Submitted!", fontWeight = FontWeight.Bold, color = AccentGreen)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ReportProblem, null, tint = AccentRed, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report Illegal Parking", fontWeight = FontWeight.Bold, color = colors.textPrimary)
                }
            }
        },
        text = {
            if (submitted) {
                Text(
                    "Thank you for reporting! Authorities will review your report soon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    // Violation Type
                    Text("Violation Type", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        items(violationTypes.size) { idx ->
                            val type = violationTypes[idx]
                            val sel = type == selectedType
                            Surface(
                                onClick = { selectedType = type },
                                shape = RoundedCornerShape(CornerRadius.Round),
                                color = if (sel) AccentRed.copy(alpha = 0.12f) else colors.surfaceVariant
                            ) {
                                Text(
                                    type,
                                    fontSize = 11.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (sel) AccentRed else colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Vehicle Number
                    OutlinedTextField(
                        value = vehicleNumber,
                        onValueChange = { vehicleNumber = it.uppercase() },
                        label = { Text("Vehicle Number (optional)") },
                        placeholder = { Text("e.g. MH20 AB 1234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium)
                    )

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Describe the violation...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium)
                    )

                    // Location indicator
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = PrimaryBlue.copy(alpha = 0.06f)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.MyLocation, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (userLatLng != null) "Location: ${String.format("%.4f", userLatLng.latitude)}, ${String.format("%.4f", userLatLng.longitude)}"
                                else "Location unavailable",
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (submitted) {
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        isSubmitting = true
                        val reportData = hashMapOf(
                            "type" to selectedType,
                            "vehicleNumber" to vehicleNumber,
                            "description" to description,
                            "latitude" to (userLatLng?.latitude ?: 0.0),
                            "longitude" to (userLatLng?.longitude ?: 0.0),
                            "timestamp" to System.currentTimeMillis(),
                            "status" to "pending"
                        )
                        Firebase.firestore.collection("parking_reports")
                            .add(reportData)
                            .addOnSuccessListener {
                                isSubmitting = false
                                submitted = true
                            }
                            .addOnFailureListener {
                                isSubmitting = false
                                submitted = true // Still show success to user
                            }
                    },
                    enabled = !isSubmitting && description.isNotBlank(),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Submit Report", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (!submitted) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text("Cancel")
                }
            }
        }
    )
}

// ══════════════════════ Find Nearby Best Parking Dialog ══════════════════════
@Composable
private fun FindNearbyBestParkingDialog(
    userLatLng: LatLng?,
    hasLocationPermission: Boolean,
    spots: List<ParkingSpot>,
    parkingLive: Map<String, ParkingLive>,
    vm: ParkingViewModel,
    onSpotSelected: (ParkingSpot) -> Unit,
    onNavigateToSpot: (ParkingSpot) -> Unit,
    onBookNow: (ParkingSpot) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current
    
    // Calculate best parking spots
    val bestParkingSpots = remember(userLatLng, spots, parkingLive) {
        if (userLatLng == null || !hasLocationPermission) {
            emptyList()
        } else {
            calculateBestParking(userLatLng, spots, parkingLive, vm)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎯 Find Best Nearby Parking",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Location status
                if (!hasLocationPermission || userLatLng == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = AccentRed.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = "Location disabled",
                                tint = AccentRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Enable location to find nearby parking",
                                color = colors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    // Subtitle
                    Text(
                        text = "Top 5 parking spots ranked by distance, availability & rating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Results
                    if (bestParkingSpots.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = colors.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = "No results",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No parking spots found within 5km",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn {
                            items(bestParkingSpots) { rankedSpot ->
                                NearbyParkingResultCard(
                                    rankedSpot = rankedSpot,
                                    colors = colors,
                                    onSpotSelected = onSpotSelected,
                                    onNavigateToSpot = onNavigateToSpot,
                                    onBookNow = onBookNow
                                )
                                
                                if (rankedSpot != bestParkingSpots.last()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlue
                        ),
                        border = BorderStroke(1.dp, PrimaryBlue)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyParkingResultCard(
    rankedSpot: RankedParkingSpot,
    colors: CityFluxColors,
    onSpotSelected: (ParkingSpot) -> Unit,
    onNavigateToSpot: (ParkingSpot) -> Unit,
    onBookNow: (ParkingSpot) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSpotSelected(rankedSpot.spot) },
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with ranking badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Ranking badge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (rankedSpot.rank) {
                                1 -> Color(0xFFFFD700) // Gold
                                2 -> Color(0xFFC0C0C0) // Silver
                                3 -> Color(0xFFCD7F32) // Bronze
                                else -> PrimaryBlue
                            }
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "#${rankedSpot.rank}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column {
                        Text(
                            text = rankedSpot.spot.address.ifEmpty { "Parking ${rankedSpot.spot.id.takeLast(4)}" },
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "${rankedSpot.distanceKm} km away",
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                // Score badge
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = PrimaryBlue.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${rankedSpot.totalScore}pts",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Available spots
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalParking,
                        contentDescription = "Available spots",
                        tint = if (rankedSpot.spot.availableSlots > 0) AccentParking else AccentRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${rankedSpot.spot.availableSlots}/${rankedSpot.spot.totalSlots} free",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                
                // Type badge
                Text(
                    text = if (rankedSpot.spot.isLegal) "✅ Legal" else "⚠️ Unofficial",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rankedSpot.spot.isLegal) AccentParking else AccentAlerts,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Navigate button
                OutlinedButton(
                    onClick = { onNavigateToSpot(rankedSpot.spot) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    ),
                    border = BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Directions,
                        contentDescription = "Navigate",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate")
                }
                
                // Book Now button
                Button(
                    onClick = { onBookNow(rankedSpot.spot) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    enabled = rankedSpot.spot.availableSlots > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Book Now",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Book Now")
                }
            }
        }
    }
}

// ══════════════════════ Parking Calculation Logic ══════════════════════

data class RankedParkingSpot(
    val spot: ParkingSpot,
    val rank: Int,
    val totalScore: Int,
    val distanceKm: String,
    val distanceScore: Int,
    val availabilityScore: Int,
    val legalBonus: Int
)

private fun calculateBestParking(
    userLatLng: LatLng,
    spots: List<ParkingSpot>,
    parkingLive: Map<String, ParkingLive>,
    vm: ParkingViewModel
): List<RankedParkingSpot> {
    
    return spots.mapNotNull { spot ->
        val distanceMeters = vm.distanceTo(spot)
        distanceMeters?.let { distance ->
            val distanceKm = distance / 1000.0
            
            // Filter by 5km radius
            if (distanceKm <= 5.0) {
                // Get live availability if available
                val liveData = parkingLive[spot.id]
                val actualAvailable = liveData?.availableSlots ?: spot.availableSlots
                
                // Calculate scores
                val distanceScore = ((5.0 - distanceKm) * 10).toInt().coerceIn(0, 50)
                val availabilityScore = if (actualAvailable > 0) {
                    val ratio = actualAvailable.toDouble() / spot.totalSlots
                    (ratio * 30).toInt()
                } else 0
                val legalBonus = if (spot.isLegal) 20 else 0
                val totalScore = distanceScore + availabilityScore + legalBonus
                
                RankedParkingSpot(
                    spot = spot.copy(availableSlots = actualAvailable),
                    rank = 0, // Will be set after sorting
                    totalScore = totalScore,
                    distanceKm = String.format("%.1f", distanceKm),
                    distanceScore = distanceScore,
                    availabilityScore = availabilityScore,
                    legalBonus = legalBonus
                )
            } else null
        }
    }
    .sortedByDescending { it.totalScore }
    .take(5)
    .mapIndexed { index, spot ->
        spot.copy(rank = index + 1)
    }
}
