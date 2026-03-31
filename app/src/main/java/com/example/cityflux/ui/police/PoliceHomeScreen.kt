package com.example.cityflux.ui.police

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.location.Location
import com.example.cityflux.R
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.toParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.model.LocationUtils
import com.example.cityflux.ui.dashboard.CityFluxAIScreen
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp

// ═══════════════════════════════════════════════════════════════════
// Police Home Screen — Premium Command Center Dashboard
// Live data from Firebase + modern UI matching citizen theme
// ═══════════════════════════════════════════════════════════════════

@Composable
fun PoliceHomeScreen(
    onNavigateToTab: (PoliceTab) -> Unit,
    onNavigateToParkingWithFilter: (String) -> Unit = {},
    geminiApiKey: String = ""
) {
    val colors = MaterialTheme.cityFluxColors
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    
    // AI Screen visibility state
    var showAIScreen by remember { mutableStateOf(false) }
    
    // Show AI Screen overlay
    if (showAIScreen) {
        CityFluxAIScreen(
            onClose = { showAIScreen = false },
            apiKey = geminiApiKey,
            userType = "police"
        )
        return
    }

    // ── User profile ──
    var userName by remember { mutableStateOf<String?>(null) }
    var userLoading by remember { mutableStateOf(true) }
    // ── Police working location for proximity filtering ──
    var policeLat by remember { mutableStateOf(0.0) }
    var policeLon by remember { mutableStateOf(0.0) }
    var policeAreaName by remember { mutableStateOf("") }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userName = doc.getString("name") ?: "Officer"
                policeLat = doc.getDouble("workingLatitude") ?: 0.0
                policeLon = doc.getDouble("workingLongitude") ?: 0.0
                policeAreaName = doc.getString("workingAreaName") ?: ""
                userLoading = false
            }
            .addOnFailureListener {
                userName = auth.currentUser?.displayName ?: "Officer"
                userLoading = false
            }
    }

    // ── Real-time traffic from RTDB with dummy data fallback ──
    val trafficMapRaw by RealtimeDbService.observeTraffic()
        .collectAsState(initial = emptyMap())
    
    // Add dummy data if empty for demonstration
    val trafficMap = remember(trafficMapRaw) {
        if (trafficMapRaw.isEmpty()) {
            // Dummy traffic data for demo
            mapOf(
                "MG_Road_Zone1" to TrafficStatus("HIGH", System.currentTimeMillis()),
                "Brigade_Road" to TrafficStatus("HIGH", System.currentTimeMillis()),
                "Residency_Road" to TrafficStatus("MEDIUM", System.currentTimeMillis()),
                "JC_Road" to TrafficStatus("MEDIUM", System.currentTimeMillis()),
                "Commercial_St" to TrafficStatus("MEDIUM", System.currentTimeMillis()),
                "Malleshwaram_Main" to TrafficStatus("LOW", System.currentTimeMillis()),
                "Koramangala_4th" to TrafficStatus("LOW", System.currentTimeMillis()),
                "Indiranagar_100ft" to TrafficStatus("LOW", System.currentTimeMillis())
            )
        } else trafficMapRaw
    }

    // ── Real-time parking from RTDB ──
    val parkingMap by RealtimeDbService.observeParkingLive()
        .collectAsState(initial = emptyMap())
        
    // ── Parking spots from Firestore ──
    var parkingSpots by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }
    var parkingSpotsLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        firestore.collection("parking")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    parkingSpotsLoading = false
                    return@addSnapshotListener
                }
                parkingSpots = snapshot?.documents?.mapNotNull { doc ->
                    doc.toParkingSpot()
                } ?: emptyList()
                parkingSpotsLoading = false
            }
    }
    
    // ── Bookings from Firestore (for assigned areas) ──
    var allBookings by remember { mutableStateOf<List<PoliceBookingData>>(emptyList()) }
    var bookingsLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        firestore.collection("bookings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { bookingsLoading = false; return@addSnapshotListener }
                allBookings = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        PoliceBookingData(
                            id = doc.id,
                            parkingSpotId = doc.getString("parkingSpotId") ?: "",
                            parkingSpotName = doc.getString("parkingSpotName") ?: "",
                            userName = doc.getString("userName") ?: "",
                            vehicleNumber = doc.getString("vehicleNumber") ?: "",
                            vehicleType = doc.getString("vehicleType") ?: "CAR",
                            status = doc.getString("status") ?: "PENDING"
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                bookingsLoading = false
            }
    }

    // ── All reports from Firestore (real-time) ──
    var allReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var reportsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        firestore.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportsLoading = false; return@addSnapshotListener }
                allReports = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                reportsLoading = false
            }
    }

    // ── Unread notifications ──
    var unreadCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("users").document(uid)
            .collection("notifications")
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, _ ->
                unreadCount = snap?.size() ?: 0
            }
    }

    // ── Real-time parking violations from Firestore (police-specific) ──
    var allViolations by remember { mutableStateOf<List<ParkingViolationSummary>>(emptyList()) }
    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("parking_violations")
            .whereEqualTo("officerId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                allViolations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ParkingViolationSummary(
                            actionType = doc.getString("actionType") ?: "warning",
                            status = doc.getString("status") ?: "Active",
                            vehicleInfo = doc.getString("vehicleInfo") ?: "",
                            violationType = doc.getString("violationType") ?: "",
                            address = doc.getString("address") ?: "",
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
            }
    }

    // ── Derived: violation stats ──
    val activeViolations = remember(allViolations) {
        allViolations.count { it.status.equals("Active", true) }
    }
    val clearedViolations = remember(allViolations) {
        allViolations.count { it.status.equals("Cleared", true) }
    }
    val warningCount = remember(allViolations) {
        allViolations.count { it.actionType.equals("warning", true) }
    }
    val fineCount = remember(allViolations) {
        allViolations.count { it.actionType.equals("fine", true) }
    }
    val towCount = remember(allViolations) {
        allViolations.count { it.actionType.equals("tow", true) }
    }

    // ── Proximity-filtered reports (within 4 km of police working location) ──
    val nearbyReports = remember(allReports, policeLat, policeLon) {
        if (policeLat == 0.0 && policeLon == 0.0) allReports
        else allReports.filter {
            LocationUtils.isWithinRadius(policeLat, policeLon, it.latitude, it.longitude)
        }
    }

    // ── Derived Stats (area-specific) ──
    val totalComplaints = nearbyReports.size
    val pendingReports = remember(nearbyReports) {
        nearbyReports.filter {
            it.status.equals("Pending", true) || it.status.equals("submitted", true)
        }
    }
    val inProgressReports = remember(nearbyReports) {
        nearbyReports.filter {
            it.status.equals("In Progress", true) || it.status.equals("in_progress", true)
        }
    }
    val resolvedReports = remember(nearbyReports) {
        nearbyReports.filter { it.status.equals("Resolved", true) }
    }

    // ── Derived: congestion breakdown ──
    val highTrafficZones = remember(trafficMap) {
        trafficMap.entries.filter { it.value.congestionLevel.equals("HIGH", true) }
    }
    val mediumTrafficZones = remember(trafficMap) {
        trafficMap.entries.filter { it.value.congestionLevel.equals("MEDIUM", true) }
    }
    val worstLevel = remember(trafficMap) {
        when {
            trafficMap.values.any { it.congestionLevel.equals("HIGH", true) } -> "HIGH"
            trafficMap.values.any { it.congestionLevel.equals("MEDIUM", true) } -> "MEDIUM"
            trafficMap.isNotEmpty() -> "LOW"
            else -> null
        }
    }

    // ── Parking stats ──
    val totalSlots = remember(parkingMap) { parkingMap.values.sumOf { it.availableSlots } }

    // Recent 5 parking violations by this officer
    val recentViolations = remember(allViolations) { allViolations.take(5) }

    // Recent 5 reports (from nearby area)
    val recentReports = remember(nearbyReports) { nearbyReports.take(5) }
    
    // ── Nearby parking areas (within 4km of police working location) ──
    val nearbyParkingAreas = remember(parkingSpots, parkingMap, policeLat, policeLon) {
        if (policeLat == 0.0 && policeLon == 0.0) {
            // Show all if location not set
            parkingSpots.map { spot ->
                val live = parkingMap[spot.id]
                NearbyParkingAreaItem(
                    id = spot.id,
                    name = spot.address.ifBlank { spot.id.replace("_", " ").replaceFirstChar { c -> c.uppercase() } },
                    availableSlots = live?.availableSlots ?: spot.availableSlots,
                    totalSlots = live?.totalSlots ?: spot.totalSlots,
                    isLegal = spot.isLegal,
                    distanceKm = 0f,
                    latitude = spot.location?.latitude ?: 0.0,
                    longitude = spot.location?.longitude ?: 0.0,
                    parkingType = spot.parkingType,
                    ratePerHour = spot.ratePerHour
                )
            }.take(5)
        } else {
            parkingSpots.mapNotNull { spot ->
                val loc = spot.location ?: return@mapNotNull null
                val results = FloatArray(1)
                Location.distanceBetween(policeLat, policeLon, loc.latitude, loc.longitude, results)
                val distanceKm = results[0] / 1000f
                if (distanceKm > 4f) return@mapNotNull null
                
                val live = parkingMap[spot.id]
                NearbyParkingAreaItem(
                    id = spot.id,
                    name = spot.address.ifBlank { spot.id.replace("_", " ").replaceFirstChar { c -> c.uppercase() } },
                    availableSlots = live?.availableSlots ?: spot.availableSlots,
                    totalSlots = live?.totalSlots ?: spot.totalSlots,
                    isLegal = spot.isLegal,
                    distanceKm = distanceKm,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    parkingType = spot.parkingType,
                    ratePerHour = spot.ratePerHour
                )
            }.sortedBy { it.distanceKm }
        }
    }
    
    // ── Bookings in nearby areas ──
    val nearbyBookings = remember(allBookings, nearbyParkingAreas) {
        val nearbyIds = nearbyParkingAreas.map { it.id }.toSet()
        allBookings.filter { nearbyIds.contains(it.parkingSpotId) }
    }
    
    // ── Bookings grouped by parking area ──
    val bookingsByArea = remember(nearbyBookings) {
        nearbyBookings.groupBy { it.parkingSpotId }
    }

    // ── UI ──
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ──────── HEADER ────────
        PoliceHomeTopBar(
            userName = userName,
            userLoading = userLoading,
            unreadCount = unreadCount,
            areaName = policeAreaName,
            onAIClick = { showAIScreen = true },
            onNotificationClick = { onNavigateToTab(PoliceTab.REPORTS) },
            onProfileClick = { onNavigateToTab(PoliceTab.PROFILE) }
        )

        Column(
            modifier = Modifier.padding(horizontal = Spacing.XLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.Large)
        ) {

            // ──────── CRITICAL ALERTS BANNER ────────
            CriticalAlertsBanner(
                worstLevel = worstLevel,
                highZoneCount = highTrafficZones.size,
                pendingCount = pendingReports.size
            )

            // ──────── LIVE CONGESTION ACTIVITY (compact card) ────────
            PoliceSectionHeader(
                title = "Live Congestion",
                icon = Icons.Outlined.Traffic,
                actionLabel = "View Map",
                onAction = { onNavigateToTab(PoliceTab.CONGESTION) }
            )

            LiveCongestionActivityCard(
                totalRoads = trafficMap.size,
                highCount = highTrafficZones.size,
                mediumCount = mediumTrafficZones.size,
                lowCount = trafficMap.size - highTrafficZones.size - mediumTrafficZones.size,
                isLoading = trafficMap.isEmpty()
            )
            
            // ──────── ASSIGNED PARKING AREAS (within 4km) ────────
            PoliceSectionHeader(
                title = "Assigned Parking Areas",
                icon = Icons.Outlined.LocalParking,
                actionLabel = "View All",
                onAction = { onNavigateToParkingWithFilter("All Parking") }
            )
            
            AssignedParkingAreasCard(
                parkingAreas = nearbyParkingAreas,
                bookingsByArea = bookingsByArea,
                totalBookings = nearbyBookings.size,
                isLoading = parkingSpotsLoading,
                onViewDetails = { onNavigateToParkingWithFilter("All Parking") },
                onViewBookings = { onNavigateToParkingWithFilter("Parking Bookings") }
            )

            // ──────── HIGH-TRAFFIC ZONES (premium grid) ────────
            PoliceSectionHeader(
                title = "High Traffic Zones",
                icon = Icons.Outlined.LocationOn
            )

            HighTrafficZonesGrid(
                highZones = highTrafficZones,
                mediumZones = mediumTrafficZones
            )

            // ──────── QUICK STATISTICS (chart cards) ────────
            PoliceSectionHeader(
                title = "Quick Statistics",
                icon = Icons.Outlined.Analytics
            )

            // Reports pie chart card
            PieChartCard(
                title = "Reports Overview",
                icon = Icons.Outlined.Assignment,
                accentColor = PrimaryBlue,
                items = listOf(
                    ChartItem("Pending", pendingReports.size, AccentOrange),
                    ChartItem("In Progress", inProgressReports.size, PrimaryBlue),
                    ChartItem("Resolved", resolvedReports.size, AccentGreen)
                ),
                total = totalComplaints,
                centerLabel = "Reports"
            )

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Parking control pie chart card (police-specific violations)
            PieChartCard(
                title = "My Violations",
                icon = Icons.Outlined.LocalParking,
                accentColor = AccentParking,
                items = listOf(
                    ChartItem("Active", activeViolations, AccentRed),
                    ChartItem("Cleared", clearedViolations, AccentGreen),
                    ChartItem("Warnings", warningCount, AccentOrange),
                    ChartItem("Fines", fineCount, PrimaryBlue),
                    ChartItem("Towed", towCount, Color(0xFF8B5CF6))
                ),
                total = allViolations.size,
                centerLabel = "Violations"
            )

            // ──────── RECENT PARKING VIOLATIONS (police-specific) ────────
            PoliceSectionHeader(
                title = "My Recent Violations",
                icon = Icons.Outlined.LocalParking,
                actionLabel = "View All",
                onAction = { onNavigateToTab(PoliceTab.PARKING) }
            )

            if (recentViolations.isEmpty()) {
                PoliceEmptyCard(
                    icon = Icons.Outlined.LocalParking,
                    message = "No violations issued yet"
                )
            } else {
                RecentViolationsCard(violations = recentViolations)
            }

            // ──────── QUICK ACTIONS (expanded) ────────
            PoliceSectionHeader(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn
            )

            // Row 1: Congestion Reports, Parking Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PremiumActionButton(
                    label = "Congestion\nReports",
                    icon = Icons.Outlined.Traffic,
                    accentColor = AccentTraffic,
                    badge = highTrafficZones.size.let { if (it > 0) "$it" else null },
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.CONGESTION) }
                )
                PremiumActionButton(
                    label = "Parking\nControl",
                    icon = Icons.Outlined.LocalParking,
                    accentColor = AccentParking,
                    badge = null,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.PARKING) }
                )
            }

            // Row 2: Reports, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PremiumActionButton(
                    label = "View\nReports",
                    icon = Icons.Outlined.Assignment,
                    accentColor = AccentIssues,
                    badge = pendingReports.size.let { if (it > 0) "$it" else null },
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.REPORTS) }
                )
                PremiumActionButton(
                    label = "Action\nStatus",
                    icon = Icons.Outlined.TaskAlt,
                    accentColor = AccentGreen,
                    badge = null,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.ACTIONS) }
                )
            }

            // ──────── RECENT ACTIVITY (premium timeline) ────────
            PoliceSectionHeader(
                title = "Recent Activity",
                icon = Icons.Outlined.History,
                actionLabel = "View All",
                onAction = { onNavigateToTab(PoliceTab.REPORTS) }
            )

            if (recentReports.isEmpty() && !reportsLoading) {
                PoliceEmptyCard(
                    icon = Icons.Outlined.Inbox,
                    message = "No recent activity"
                )
            } else if (reportsLoading) {
                repeat(3) { PoliceShimmerCard() }
            } else {
                RecentActivityTimeline(reports = recentReports)
            }

            Spacer(modifier = Modifier.height(Spacing.XXLarge))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── TOP BAR ─────────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceHomeTopBar(
    userName: String?,
    userLoading: Boolean,
    unreadCount: Int,
    areaName: String = "",
    onAIClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    // Premium gradient header card - cleaner design
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = PrimaryBlue.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E40AF),
                            Color(0xFF3B82F6),
                            Color(0xFF60A5FA)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(800f, 400f)
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Officer shield badge
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name & Status - takes remaining space
                Column(modifier = Modifier.weight(1f)) {
                    if (userLoading) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    } else {
                        Text(
                            text = "Welcome, Officer",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            letterSpacing = 0.3.sp
                        )
                        Text(
                            text = userName ?: "Officer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Location + Status row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (areaName.isNotBlank()) areaName else "City Patrol",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // On Duty badge
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentGreen.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .background(AccentGreen, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "On Duty",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action buttons - fixed width
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // AI Assistant button
                    Surface(
                        onClick = onAIClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ai_assistant),
                                contentDescription = "CityFlux AI",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Notification bell
                    Box {
                        Surface(
                            onClick = onNotificationClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (unreadCount > 0) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-2).dp),
                                shape = CircleShape,
                                color = AccentRed
                            ) {
                                Text(
                                    text = if (unreadCount > 9) "9+" else "$unreadCount",
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Profile
                    Surface(
                        onClick = onProfileClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── CRITICAL ALERTS BANNER ──────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CriticalAlertsBanner(
    worstLevel: String?,
    highZoneCount: Int,
    pendingCount: Int
) {
    val hasCritical = worstLevel == "HIGH" || pendingCount >= 5

    val bgGradient: Brush
    val iconTint: Color
    val titleText: String
    val subtitleText: String
    val bannerIcon: ImageVector

    if (hasCritical) {
        bgGradient = Brush.horizontalGradient(listOf(AccentRed, Color(0xFFDC2626)))
        iconTint = Color.White
        titleText = "Critical Alert"
        bannerIcon = Icons.Filled.Warning
        subtitleText = buildString {
            if (worstLevel == "HIGH") append("$highZoneCount high-congestion zone(s)")
            if (worstLevel == "HIGH" && pendingCount > 0) append(" • ")
            if (pendingCount > 0) append("$pendingCount pending reports")
        }
    } else if (worstLevel == "MEDIUM" || pendingCount > 0) {
        bgGradient = Brush.horizontalGradient(listOf(AccentOrange, Color(0xFFF97316)))
        iconTint = Color.White
        titleText = "Attention Required"
        bannerIcon = Icons.Filled.Info
        subtitleText = buildString {
            if (worstLevel == "MEDIUM") append("Moderate congestion detected")
            if (pendingCount in 1..4) {
                if (worstLevel == "MEDIUM") append(" • ")
                append("$pendingCount pending report(s)")
            }
        }
    } else {
        bgGradient = Brush.horizontalGradient(listOf(AccentGreen, Color(0xFF059669)))
        iconTint = Color.White
        titleText = "All Clear"
        bannerIcon = Icons.Filled.CheckCircle
        subtitleText = "No critical alerts — situation normal"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = if (hasCritical) AccentRed.copy(alpha = 0.3f) else Color.Transparent
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgGradient)
                .padding(Spacing.Large)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                    if (subtitleText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = iconTint.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── LIVE CONGESTION ACTIVITY CARD (compact premium) ─────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveCongestionActivityCard(
    totalRoads: Int,
    highCount: Int,
    mediumCount: Int,
    lowCount: Int,
    isLoading: Boolean
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = PrimaryBlue.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Header row with live indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PulsingDot(color = AccentGreen, size = 8.dp)
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen,
                        letterSpacing = 1.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = PrimaryBlue.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = if (isLoading) "..." else "$totalRoads Roads",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(3) { PoliceShimmerBox(width = 80.dp, height = 56.dp) }
                }
            } else {
                // Segmented congestion bar with rounded ends
                if (totalRoads > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.surfaceVariant)
                    ) {
                        if (highCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(highCount.toFloat().coerceAtLeast(0.1f))
                                    .fillMaxHeight()
                                    .background(AccentRed)
                            )
                        }
                        if (mediumCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(mediumCount.toFloat().coerceAtLeast(0.1f))
                                    .fillMaxHeight()
                                    .background(AccentOrange)
                            )
                        }
                        if (lowCount > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(lowCount.toFloat().coerceAtLeast(0.1f))
                                    .fillMaxHeight()
                                    .background(AccentGreen)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.Large))
                }

                // Level indicators with mini bar charts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CongestionMiniStat(
                        label = "Critical",
                        count = highCount,
                        color = AccentRed,
                        icon = Icons.Filled.Warning
                    )
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(52.dp)
                            .background(colors.divider)
                    )
                    CongestionMiniStat(
                        label = "Moderate",
                        count = mediumCount,
                        color = AccentOrange,
                        icon = Icons.Filled.RemoveCircle
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(52.dp)
                            .background(colors.divider)
                    )
                    CongestionMiniStat(
                        label = "Clear",
                        count = lowCount,
                        color = AccentGreen,
                        icon = Icons.Filled.CheckCircle
                    )
                }
            }
        }
    }
}

@Composable
private fun CongestionMiniStat(
    label: String,
    count: Int,
    color: Color,
    icon: ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cityFluxColors.textSecondary
        )
    }
}

@Composable
private fun PulsingDot(color: Color, size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(size * scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

// ═══════════════════════════════════════════════════════════════════
// ── HIGH TRAFFIC ZONES GRID (premium glassmorphism-style) ───────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HighTrafficZonesGrid(
    highZones: List<Map.Entry<String, TrafficStatus>>,
    mediumZones: List<Map.Entry<String, TrafficStatus>>
) {
    val colors = MaterialTheme.cityFluxColors

    if (highZones.isEmpty() && mediumZones.isEmpty()) {
        // Empty state — stylish
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AccentGreen.copy(alpha = 0.08f),
                                AccentGreen.copy(alpha = 0.03f)
                            )
                        )
                    )
                    .padding(Spacing.XLarge),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    Text(
                        text = "All Zones Clear",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No congestion detected across the city",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    val allZones = (highZones + mediumZones).take(6)

    // 2-column grid
    val chunked = allZones.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                rowItems.forEach { (roadId, status) ->
                    val level = status.congestionLevel.uppercase()
                    val zoneColor = if (level == "HIGH") AccentRed else AccentOrange
                    val levelTag = if (level == "HIGH") "HEAVY" else "MODERATE"
                    val zoneIcon = if (level == "HIGH") Icons.Filled.Warning else Icons.Filled.RemoveCircle

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .shadow(
                                elevation = 4.dp,
                                shape = RoundedCornerShape(CornerRadius.Large),
                                ambientColor = zoneColor.copy(alpha = 0.15f)
                            ),
                        shape = RoundedCornerShape(CornerRadius.Large),
                        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                        border = BorderStroke(1.dp, zoneColor.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.Medium)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(zoneColor.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = zoneIcon,
                                        contentDescription = null,
                                        tint = zoneColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (level == "HIGH") {
                                    PulsingDot(color = zoneColor, size = 8.dp)
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.Small))
                            Text(
                                text = roadId.replace("_", ", "),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = zoneColor.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = levelTag,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = zoneColor,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
                // Fill empty space if odd number
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── DATA CLASSES ────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

private data class ChartItem(val label: String, val value: Int, val color: Color)
private data class ParkingViolationSummary(
    val actionType: String,
    val status: String,
    val vehicleInfo: String = "",
    val violationType: String = "",
    val address: String = "",
    val timestamp: Timestamp? = null
)

// ═══════════════════════════════════════════════════════════════════
// ── PIE CHART STATISTICS CARD ───────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PieChartCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    items: List<ChartItem>,
    total: Int,
    centerLabel: String
) {
    val colors = MaterialTheme.cityFluxColors
    val nonZeroItems = items.filter { it.value > 0 }
    val sweepTotal = nonZeroItems.sumOf { it.value }.coerceAtLeast(1)

    // Animate the pie chart sweep
    var animationPlayed by remember { mutableStateOf(false) }
    val animateProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "pieAnim"
    )
    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = accentColor.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Card header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accentColor.copy(alpha = 0.15f), accentColor.copy(alpha = 0.05f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.XLarge))

            // Pie chart + legend side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut pie chart
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(130.dp)) {
                        val strokeWidth = 22.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2f
                        val topLeft = Offset(
                            (size.width - 2 * radius) / 2f,
                            (size.height - 2 * radius) / 2f
                        )
                        val arcSize = Size(radius * 2, radius * 2)
                        val gapAngle = if (nonZeroItems.size > 1) 4f else 0f
                        val totalGap = gapAngle * nonZeroItems.size
                        val availableSweep = (360f - totalGap) * animateProgress.value

                        if (nonZeroItems.isEmpty()) {
                            // Empty state ring
                            drawArc(
                                color = Color.LightGray.copy(alpha = 0.3f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        } else {
                            var startAngle = -90f
                            nonZeroItems.forEach { item ->
                                val sweep = (item.value.toFloat() / sweepTotal) * availableSweep
                                drawArc(
                                    color = item.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweep + gapAngle
                            }
                        }
                    }

                    // Center text
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$total",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = centerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.XLarge))

                // Legend items with values
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(item.color)
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${item.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                if (total > 0) {
                                    val pct = (item.value * 100f / total).toInt()
                                    Text(
                                        text = "${pct}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = item.color,
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
}

// ═══════════════════════════════════════════════════════════════════
// ── PREMIUM ACTION BUTTON (with optional badge) ─────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PremiumActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        onClick = onClick,
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = accentColor.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle gradient accent at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(accentColor.copy(alpha = 0.6f), accentColor.copy(alpha = 0.1f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.XLarge, bottom = Spacing.Large, start = Spacing.Large, end = Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accentColor.copy(alpha = 0.15f),
                                        accentColor.copy(alpha = 0.05f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    // Badge
                    if (badge != null) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp),
                            shape = CircleShape,
                            color = AccentRed,
                            shadowElevation = 2.dp
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── RECENT ACTIVITY TIMELINE (premium vertical timeline) ────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecentActivityTimeline(reports: List<Report>) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            reports.forEachIndexed { index, report ->
                val statusColor = when (report.status.lowercase()) {
                    "resolved" -> AccentGreen
                    "in_progress", "in progress" -> PrimaryBlue
                    else -> AccentOrange
                }
                val statusIcon = when (report.status.lowercase()) {
                    "resolved" -> Icons.Filled.CheckCircle
                    "in_progress", "in progress" -> Icons.Filled.Autorenew
                    else -> Icons.Filled.Schedule
                }
                val typeLabel = report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
                val timeAgo = report.timestamp?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.toDate().time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } ?: ""
                val isLast = index == reports.lastIndex

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Timeline track
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp)
                    ) {
                        // Dot
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Connecting line
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(statusColor.copy(alpha = 0.3f), colors.divider)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.Medium))

                    // Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else Spacing.Medium)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = report.title.ifBlank { typeLabel },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusColor.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = report.status.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surfaceVariant
                            ) {
                                Text(
                                    text = typeLabel,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                    fontSize = 9.sp
                                )
                            }
                            Text(
                                text = "•",
                                color = colors.textTertiary,
                                fontSize = 8.sp
                            )
                            Text(
                                text = timeAgo,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                fontSize = 10.sp
                            )
                        }
                        if (report.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = report.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── RECENT PARKING VIOLATIONS CARD (police-specific timeline) ───
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecentViolationsCard(violations: List<ParkingViolationSummary>) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = AccentParking.copy(alpha = 0.12f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            violations.forEachIndexed { index, violation ->
                val statusColor = if (violation.status.equals("Active", true)) AccentRed else AccentGreen
                val actionColor = when (violation.actionType.lowercase()) {
                    "fine" -> PrimaryBlue
                    "tow" -> Color(0xFF8B5CF6)
                    else -> AccentOrange
                }
                val actionIcon = when (violation.actionType.lowercase()) {
                    "fine" -> Icons.Filled.Receipt
                    "tow" -> Icons.Filled.LocalShipping
                    else -> Icons.Filled.Warning
                }
                val typeLabel = violation.violationType.replace("_", " ")
                    .replaceFirstChar { it.uppercase() }
                val timeAgo = violation.timestamp?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it.toDate().time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } ?: ""
                val isLast = index == violations.lastIndex

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Timeline track
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(actionColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (!isLast) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(48.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(actionColor.copy(alpha = 0.3f), colors.divider)
                                        )
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.Medium))

                    // Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (isLast) 0.dp else Spacing.Medium)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = violation.vehicleInfo.ifBlank { typeLabel },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusColor.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = violation.status,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = actionColor.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = violation.actionType.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor,
                                    fontSize = 9.sp
                                )
                            }
                            if (violation.address.isNotBlank()) {
                                Text(
                                    text = "•",
                                    color = colors.textTertiary,
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = violation.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (timeAgo.isNotBlank()) {
                                Text(
                                    text = "•",
                                    color = colors.textTertiary,
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = timeAgo,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── SECTION HEADER ──────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceSectionHeader(
    title: String,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── EMPTY STATE / SHIMMER ───────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceEmptyCard(icon: ImageVector, message: String) {
    val colors = MaterialTheme.cityFluxColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.XLarge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(Spacing.Medium))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
private fun PoliceShimmerCard() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
    }
}

@Composable
private fun PoliceShimmerBox(width: Dp, height: Dp) {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "shimmerBox")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "shimmerBoxTranslate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 100f, 0f),
        end = Offset(translateAnim, 0f)
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(brush)
    )
}

// ═══════════════════════════════════════════════════════════════════
// ── DATA CLASSES ────────────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

private data class NearbyParkingAreaItem(
    val id: String,
    val name: String,
    val availableSlots: Int,
    val totalSlots: Int,
    val isLegal: Boolean,
    val distanceKm: Float,
    val latitude: Double,
    val longitude: Double,
    val parkingType: String = "free", // "free" or "paid"
    val ratePerHour: Int = 0
)

private data class PoliceBookingData(
    val id: String = "",
    val parkingSpotId: String = "",
    val parkingSpotName: String = "",
    val userName: String = "",
    val vehicleNumber: String = "",
    val vehicleType: String = "",
    val status: String = ""
)

// ═══════════════════════════════════════════════════════════════════
// ── ASSIGNED PARKING AREAS CARD ─────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AssignedParkingAreasCard(
    parkingAreas: List<NearbyParkingAreaItem>,
    bookingsByArea: Map<String, List<PoliceBookingData>>,
    totalBookings: Int,
    isLoading: Boolean,
    onViewDetails: () -> Unit,
    onViewBookings: () -> Unit = {}
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = AccentParking.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            // Top accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentParking, AccentParking.copy(alpha = 0.6f), AccentParking)
                        )
                    )
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header - simplified layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentParking.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalParking,
                            contentDescription = null,
                            tint = AccentParking,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    
                    // Title & subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Nearby Parking Areas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "Within 4km of your working area",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    
                    // Badge - compact
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AccentParking.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentParking, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${parkingAreas.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentParking
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.Medium))
                
                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ParkingStatMini(
                        label = "Total Areas",
                        value = parkingAreas.size.toString(),
                        color = AccentParking
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(colors.divider)
                    )
                    ParkingStatMini(
                        label = "Available",
                        value = parkingAreas.sumOf { it.availableSlots }.toString(),
                        color = AccentGreen
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(colors.divider)
                    )
                    ParkingStatMini(
                        label = "Bookings",
                        value = totalBookings.toString(),
                        color = PrimaryBlue
                    )
                }
                
                Spacer(modifier = Modifier.height(Spacing.Medium))
                
                // Parking areas list
                if (isLoading) {
                    repeat(3) {
                        PoliceShimmerBox(width = 300.dp, height = 48.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else if (parkingAreas.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.Large),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.LocationOff,
                                null,
                                tint = colors.textTertiary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No parking areas found nearby",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                } else {
                    parkingAreas.take(5).forEachIndexed { index, area ->
                        val bookingsCount = bookingsByArea[area.id]?.size ?: 0
                        ParkingAreaRow(
                            area = area,
                            bookingsCount = bookingsCount,
                            rank = index + 1
                        )
                        if (index < parkingAreas.lastIndex && index < 4) {
                            HorizontalDivider(
                                color = colors.divider,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.Medium))
                
                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // View All Parking Button
                    OutlinedButton(
                        onClick = onViewDetails,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(CornerRadius.Large),
                        border = BorderStroke(1.5.dp, AccentParking),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentParking)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalParking,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "All Parking",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                    
                    // View Bookings Button
                    Button(
                        onClick = onViewBookings,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(CornerRadius.Large),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ConfirmationNumber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Bookings",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParkingStatMini(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cityFluxColors.textSecondary
        )
    }
}

@Composable
private fun ParkingAreaRow(
    area: NearbyParkingAreaItem,
    bookingsCount: Int,
    rank: Int
) {
    val colors = MaterialTheme.cityFluxColors
    val available = area.availableSlots
    val total = area.totalSlots
    val occupancyPercent = if (total > 0) ((total - available).toFloat() / total * 100).toInt() else 0
    
    val statusColor = when {
        available == 0 -> AccentRed
        available <= 5 -> AccentOrange
        else -> AccentGreen
    }
    
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> AccentParking.copy(alpha = 0.7f)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (rank <= 3) rankColor.copy(alpha = 0.15f)
                    else AccentParking.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) rankColor else AccentParking
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Parking info
        Column(modifier = Modifier.weight(1f)) {
            // First row: Parking name
            Text(
                text = area.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Second row: Distance and Availability
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Distance
                if (area.distanceKm > 0) {
                    Text(
                        text = String.format("%.1f km", area.distanceKm),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    Text("•", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
                
                // Availability
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$available/$total free",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Text("•", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                
                // Free/Paid badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (area.parkingType == "paid") AccentOrange.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f)
                ) {
                    Text(
                        if (area.parkingType == "paid") "₹${area.ratePerHour}/hr" else "FREE",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (area.parkingType == "paid") AccentOrange else AccentGreen,
                        fontSize = 10.sp
                    )
                }
                
                // Legal status
                Text(
                    text = if (area.isLegal) "✅" else "⚠️",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        
        // Bookings count badge
        if (bookingsCount > 0) {
            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = PrimaryBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "$bookingsCount booked",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    fontSize = 10.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Progress indicator
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(occupancyPercent / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            occupancyPercent >= 90 -> AccentRed
                            occupancyPercent >= 70 -> AccentOrange
                            else -> AccentGreen
                        }
                    )
            )
        }
    }
}
