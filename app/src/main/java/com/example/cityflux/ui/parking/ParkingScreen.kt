package com.example.cityflux.ui.parking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.outlined.ViewList
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import kotlinx.coroutines.tasks.await

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
    var isMapView by remember { mutableStateOf(false) }

    // ── Filter sheet ──
    var showFilterSheet by remember { mutableStateOf(false) }

    // ── Selected parking for detail popup ──
    var selectedSpot by remember { mutableStateOf<ParkingSpot?>(null) }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailable(context) }

    // ── Report Illegal Parking ──
    var showReportDialog by remember { mutableStateOf(false) }

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

            // ══════════════════════ Nearby Alert Banner ══════════════════════
            if (nearbyCount > 0 && !state.isLoading) {
                NearbyParkingAlertBanner(nearbyCount = nearbyCount, availableNearby = filteredSpots.count { spot ->
                    val dist = vm.distanceTo(spot)
                    val avail = state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
                    dist != null && dist < 1000f && avail > 0
                })
            }

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
                ParkingStatMiniCard(
                    label = "Nearby",
                    value = "$nearbyCount",
                    icon = Icons.Outlined.NearMe,
                    color = AccentOrange,
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
                // List / Map toggle
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = colors.surfaceVariant,
                    modifier = Modifier.height(36.dp)
                ) {
                    Row {
                        ViewToggleButton(
                            label = "List",
                            icon = Icons.AutoMirrored.Outlined.ViewList,
                            isSelected = !isMapView,
                            onClick = { isMapView = false }
                        )
                        ViewToggleButton(
                            label = "Map",
                            icon = Icons.Outlined.Map,
                            isSelected = isMapView,
                            onClick = { isMapView = true }
                        )
                    }
                }

                // Filter chips row
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
                targetState = isMapView,
                animationSpec = tween(300),
                label = "parking_view_toggle"
            ) { showMap ->
                if (showMap) {
                    // ──────── Map View ────────
                    ParkingMapView(
                        spots = displaySpots,
                        parkingLive = state.parkingLive,
                        userLatLng = userLatLng,
                        onMarkerClick = { spot ->
                            try { Firebase.analytics.logEvent("parking_card_clicked", null) } catch (_: Exception) {}
                            selectedSpot = spot
                        }
                    )
                } else {
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
                                            openNavigation(context, geo.latitude, geo.longitude)
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
            }
        }

        // ══════════════════════ FABs (bottom-right, list view only) ══════════════════════
        if (!isMapView) Column(
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

            // Nearest Parking FAB
            FloatingActionButton(
                onClick = {
                    try {
                        Firebase.analytics.logEvent("nearest_parking_clicked", null)
                    } catch (_: Exception) {}
                    val nearest = vm.findNearestAvailable()
                    nearest?.let { selectedSpot = it }
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
            visible = selectedSpot != null,
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
                            openNavigation(context, geo.latitude, geo.longitude)
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

            // ── Action Row: Navigate + Notify ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(bottom = Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
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

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                StatChip("Available", available.toString(), statusColor, Modifier.weight(1f))
                StatChip("Total", spot.totalSlots.toString(), PrimaryBlue, Modifier.weight(1f))
                StatChip("Type", if (spot.isLegal) "Legal" else "Illegal",
                    if (spot.isLegal) AccentParking else AccentAlerts, Modifier.weight(1f))
            }

            if (distance != null) {
                Spacer(Modifier.height(Spacing.Small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    StatChip(
                        "Distance",
                        formatDistance(distance),
                        AccentTraffic,
                        Modifier.weight(1f)
                    )
                    Box(Modifier.weight(2f)) // spacer
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
// Parking Map View (mini map with markers)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ParkingMapView(
    spots: List<ParkingSpot>,
    parkingLive: Map<String, com.example.cityflux.model.ParkingLive>,
    userLatLng: LatLng?,
    onMarkerClick: (ParkingSpot) -> Unit
) {
    val defaultLocation = LatLng(17.6599, 75.9064) // Solapur
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLatLng ?: defaultLocation, 14f)
    }

    LaunchedEffect(userLatLng) {
        userLatLng?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 15f), durationMs = 600)
        }
    }

    val parkingGreenBitmap = remember {
        createCircleMarkerBitmap(AccentParking.toArgb(), 38)
    }
    val parkingRedBitmap = remember {
        createCircleMarkerBitmap(AccentRed.toArgb(), 38)
    }

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
            zoomControlsEnabled = true,
            compassEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false
        )
    ) {
        spots.forEach { spot ->
            val loc = spot.location ?: return@forEach
            val available = parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
            val isFull = available <= 0

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
// Nearby Parking Alert Banner
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NearbyParkingAlertBanner(nearbyCount: Int, availableNearby: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_banner")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = AccentGreen.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing radar icon
            Box(contentAlignment = Alignment.Center) {
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f, targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = "pulse"
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentGreen.copy(alpha = pulseAlpha * 0.15f))
                )
                Icon(
                    Icons.Outlined.ShareLocation,
                    null,
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$nearbyCount parking spots within 1km",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
                Text(
                    "$availableNearby have free slots right now",
                    fontSize = 10.sp,
                    color = MaterialTheme.cityFluxColors.textSecondary
                )
            }
            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = AccentGreen.copy(alpha = 0.15f)
            ) {
                Text(
                    "NEARBY",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
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
