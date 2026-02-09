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

    // ── Filtered & sorted list ──
    val filteredSpots = remember(state) { vm.getFilteredSpots(state) }

    // ── Totals for header ──
    val totalSpots = filteredSpots.size
    val totalAvailable = filteredSpots.sumOf { spot ->
        state.parkingLive[spot.id]?.availableSlots ?: spot.availableSlots
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ══════════════════════ Header ══════════════════════
            Column(
                modifier = Modifier.padding(
                    start = Spacing.XLarge, end = Spacing.XLarge,
                    top = Spacing.Large, bottom = Spacing.Small
                )
            ) {
                Text(
                    "Nearby Parking",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (state.isLoading) "Finding parking spots..."
                    else "$totalSpots spots · $totalAvailable slots available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
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
                        spots = filteredSpots,
                        parkingLive = state.parkingLive,
                        userLatLng = userLatLng,
                        onMarkerClick = { spot ->
                            try { Firebase.analytics.logEvent("parking_card_clicked", null) } catch (_: Exception) {}
                            selectedSpot = spot
                        }
                    )
                } else {
                    // ──────── List View ────────
                    if (state.isLoading && filteredSpots.isEmpty()) {
                        // Shimmer loading
                        Column(
                            modifier = Modifier.padding(horizontal = Spacing.XLarge),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Spacer(Modifier.height(Spacing.Small))
                            repeat(5) { ShimmerParkingCard() }
                        }
                    } else if (filteredSpots.isEmpty()) {
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
                            items(filteredSpots, key = { it.id }) { spot ->
                                ParkingCard(
                                    spot = spot,
                                    available = state.parkingLive[spot.id]?.availableSlots
                                        ?: spot.availableSlots,
                                    distance = vm.distanceTo(spot),
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

        // ══════════════════════ FABs (bottom-right) ══════════════════════
        if (!isMapView) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.Medium, bottom = Spacing.Large)
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
                            Text(
                                formatDistance(distance),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
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

                // Slot count badge
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
    val defaultLocation = LatLng(19.0760, 72.8777)
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
