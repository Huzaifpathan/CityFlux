package com.example.cityflux.ui.dashboard

import android.content.Intent
import android.location.Location
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.toParkingSpot
import com.example.cityflux.model.Report
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════
// Citizen Home Content — Premium Modern Dashboard
// Live-first dashboard with graceful fallback demo traffic when backend is empty
// ═══════════════════════════════════════════════════════════════════

@Composable
fun CitizenHomeContent(
    onNavigateToTab: (CitizenTab) -> Unit,
    onProfileClick: () -> Unit = {}
) {
    @Suppress("UNUSED_VARIABLE")
    val colors = MaterialTheme.cityFluxColors
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    // ── Analytics: log once per screen open ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("home_opened", null) } catch (_: Exception) {}
    }

    // ── User profile from Firestore ──
    var userName by remember { mutableStateOf<String?>(null) }
    var userLoading by remember { mutableStateOf(true) }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userName = doc.getString("name") ?: "Citizen"
                userLoading = false
            }
            .addOnFailureListener {
                userName = auth.currentUser?.displayName ?: "Citizen"
                userLoading = false
            }
    }

    // ── Real-time traffic from RTDB ──
    val trafficMap by RealtimeDbService.observeTraffic()
        .collectAsState(initial = emptyMap())
    val dummyTrafficMap = remember { citizenTrafficDemoData() }
    val effectiveTrafficMap = if (trafficMap.isEmpty()) dummyTrafficMap else trafficMap

    // ── Real-time parking from RTDB ──
    val parkingMap by RealtimeDbService.observeParkingLive()
        .collectAsState(initial = emptyMap())
    var parkingSpots by remember { mutableStateOf<List<ParkingSpot>>(emptyList()) }

    // ── User location (default Aurangabad) ──
    var userLatitude by remember { mutableDoubleStateOf(19.8762) }
    var userLongitude by remember { mutableDoubleStateOf(75.3433) }

    // ── Nearby incidents from Firestore (latest 5) ──
    var recentReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var reportsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        firestore.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { reportsLoading = false; return@addSnapshotListener }
                recentReports = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                reportsLoading = false
            }
    }

    // ── My reports from Firestore (current user's reports) ──
    var myReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var myReportsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("reports")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { myReportsLoading = false; return@addSnapshotListener }
                myReports = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                myReportsLoading = false
            }
    }

    // ── Unread notifications count ──
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

    // ── Derived: worst congestion level ──
    val worstLevel = remember(effectiveTrafficMap) {
        when {
            effectiveTrafficMap.values.any { it.congestionLevel.equals("HIGH", true) } -> "HIGH"
            effectiveTrafficMap.values.any { it.congestionLevel.equals("MEDIUM", true) } -> "MEDIUM"
            effectiveTrafficMap.isNotEmpty() -> "LOW"
            else -> null
        }
    }

    // ── Derived: congestion breakdown ──
    val highTrafficZones = remember(effectiveTrafficMap) {
        effectiveTrafficMap.entries.filter { it.value.congestionLevel.equals("HIGH", true) }
    }
    val mediumTrafficZones = remember(effectiveTrafficMap) {
        effectiveTrafficMap.entries.filter { it.value.congestionLevel.equals("MEDIUM", true) }
    }
    val lowTrafficCount = effectiveTrafficMap.size - highTrafficZones.size - mediumTrafficZones.size

    // ── Derived: nearest parking list from Firestore parking spots + live availability ──
    val nearbyParkingItems = remember(parkingSpots, parkingMap, userLatitude, userLongitude) {
        parkingSpots.map { spot ->
            val live = parkingMap[spot.id]
            val available = live?.availableSlots ?: spot.availableSlots
            val total = live?.totalSlots ?: spot.totalSlots
            val distanceMeters = spot.location?.let { geo ->
                val results = FloatArray(1)
                Location.distanceBetween(
                    userLatitude, userLongitude,
                    geo.latitude, geo.longitude,
                    results
                )
                results[0]
            }
            NearbyParkingItem(
                id = spot.id,
                address = spot.address.ifBlank { spot.id.replace("_", " ").replaceFirstChar { it.uppercase() } },
                availableSlots = available,
                totalSlots = total,
                distanceMeters = distanceMeters,
                isLegal = spot.isLegal,
                rank = 0,
                totalScore = 0,
                distanceKmText = ""
            )
        }
            .mapNotNull { item ->
                val distance = item.distanceMeters ?: return@mapNotNull null
                val distanceKm = distance / 1000.0
                if (distanceKm > 5.0) return@mapNotNull null
                val distanceScore = ((5.0 - distanceKm) * 10).toInt().coerceIn(0, 50)
                val availabilityScore = if (item.totalSlots > 0 && item.availableSlots > 0) {
                    ((item.availableSlots.toDouble() / item.totalSlots) * 30).toInt()
                } else 0
                val legalBonus = if (item.isLegal) 20 else 0
                val totalScore = distanceScore + availabilityScore + legalBonus
                item.copy(
                    totalScore = totalScore,
                    distanceKmText = String.format("%.1f", distanceKm)
                )
            }
            .sortedByDescending { it.totalScore }
            .take(5)
            .mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    // ── Derived: my report stats ──
    val myPendingReports = remember(myReports) {
        myReports.filter {
            it.status.equals("Pending", true) || it.status.equals("submitted", true)
        }
    }
    val myInProgressReports = remember(myReports) {
        myReports.filter {
            it.status.equals("In Progress", true) || it.status.equals("in_progress", true)
        }
    }
    val myResolvedReports = remember(myReports) {
        myReports.filter { it.status.equals("Resolved", true) }
    }

    // ── Community Announcements from Firestore ──
    var announcements by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    LaunchedEffect(Unit) {
        firestore.collection("announcements")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                announcements = snap?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    data + ("id" to doc.id)
                } ?: emptyList()
            }
    }

    // ── Weather data from Open-Meteo (free, no API key) ──
    var weatherTemp by remember { mutableStateOf<Double?>(null) }
    var weatherCode by remember { mutableIntStateOf(-1) }
    var weatherWind by remember { mutableStateOf<Double?>(null) }
    var weatherLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    userLatitude = loc.latitude
                    userLongitude = loc.longitude
                }
            }
        } catch (_: SecurityException) {
            // keep Aurangabad fallback
        }
    }

    LaunchedEffect(userLatitude, userLongitude) {
        try {
            withContext(Dispatchers.IO) {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$userLatitude&longitude=$userLongitude&current_weather=true"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                try {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val current = json.getJSONObject("current_weather")
                    weatherTemp = current.getDouble("temperature")
                    weatherCode = current.getInt("weathercode")
                    weatherWind = current.getDouble("windspeed")
                } finally {
                    conn.disconnect()
                }
            }
        } catch (_: Exception) { }
        weatherLoading = false
    }

    DisposableEffect(Unit) {
        val listener = firestore.collection("parking")
            .addSnapshotListener { snapshot, _ ->
                parkingSpots = snapshot?.documents?.mapNotNull { doc ->
                    doc.toParkingSpot()
                } ?: emptyList()
            }
        onDispose { listener.remove() }
    }

    // ── My Vehicle Violations from Firestore ──
    var userVehicles by remember { mutableStateOf<List<String>>(emptyList()) }
    var myViolations by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var violationsLoading by remember { mutableStateOf(true) }
    var selectedViolation by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Load user's vehicle numbers
    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("users").document(uid)
            .addSnapshotListener { doc, _ ->
                @Suppress("UNCHECKED_CAST")
                userVehicles = (doc?.get("vehicleNumbers") as? List<String>) ?: emptyList()
            }
    }

    // Listen for violations matching user's vehicles
    DisposableEffect(userVehicles) {
        if (userVehicles.isEmpty()) {
            myViolations = emptyList()
            violationsLoading = false
            return@DisposableEffect onDispose { }
        }
        val listener = firestore.collection("parking_violations")
            .addSnapshotListener { snapshot, _ ->
                myViolations = snapshot?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val vehicleInfo = (data["vehicleInfo"] as? String) ?: return@mapNotNull null
                    if (userVehicles.any { it.equals(vehicleInfo, ignoreCase = true) }) {
                        data + ("id" to doc.id)
                    } else null
                }?.sortedByDescending {
                    (it["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                } ?: emptyList()
                violationsLoading = false

                // Auto-create notification for new violations
                val uid = auth.currentUser?.uid ?: return@addSnapshotListener
                myViolations.forEach { violation ->
                    val violationId = violation["id"] as? String ?: return@forEach
                    val actionType = (violation["actionType"] as? String ?: "fine").replaceFirstChar { it.uppercase() }
                    val vehicleNo = violation["vehicleInfo"] as? String ?: ""
                    val fineAmt = violation["fineAmount"] as? String ?: ""
                    val notifRef = firestore.collection("users").document(uid)
                        .collection("notifications").document("violation_$violationId")
                    notifRef.get().addOnSuccessListener { existing ->
                        if (!existing.exists()) {
                            notifRef.set(
                                hashMapOf(
                                    "title" to "⚠️ $actionType — $vehicleNo",
                                    "message" to if (fineAmt.isNotBlank()) "Fine: ₹$fineAmt" else "$actionType issued on your vehicle $vehicleNo",
                                    "type" to "parking",
                                    "priority" to if (actionType.lowercase() == "tow") "critical" else "high",
                                    "latitude" to (violation["latitude"] ?: 0.0),
                                    "longitude" to (violation["longitude"] ?: 0.0),
                                    "read" to false,
                                    "pinned" to false,
                                    "timestamp" to (violation["timestamp"] ?: com.google.firebase.Timestamp.now())
                                )
                            )
                        }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    val activeViolations = remember(myViolations) {
        myViolations.filter { (it["status"] as? String ?: "Active").equals("Active", true) }
    }
    val totalFines = remember(myViolations) {
        myViolations.sumOf { (it["fineAmount"] as? String)?.toDoubleOrNull()?.toInt() ?: 0 }
    }

    // ── SOS state ──
    var showSosDialog by remember { mutableStateOf(false) }
    var sosSending by remember { mutableStateOf(false) }

    // ── UI ──
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // ──────── TOP APP BAR ────────
            HomeTopBar(
                userName = userName,
                userLoading = userLoading,
                unreadCount = unreadCount,
                onNotificationClick = { onNavigateToTab(CitizenTab.ALERTS) },
                onProfileClick = onProfileClick
            )

            Column(
                modifier = Modifier.padding(horizontal = Spacing.XLarge),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large)
            ) {
                // ──────── CRITICAL ALERTS BANNER (upgraded) ────────
                CitizenAlertsBanner(
                worstLevel = worstLevel,
                highZoneCount = highTrafficZones.size,
                pendingCount = myPendingReports.size
            )

            // ──────── 📢 COMMUNITY ANNOUNCEMENTS ────────
            if (announcements.isNotEmpty()) {
                SectionHeader(
                    title = "Announcements",
                    icon = Icons.Outlined.Campaign
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Spacing.Medium)
                ) {
                    items(announcements) { announcement ->
                        AnnouncementCard(announcement = announcement)
                    }
                }
            }

            // ──────── 🌦️ WEATHER & TRAVEL CARD ────────
            WeatherCard(
                temp = weatherTemp,
                weatherCode = weatherCode,
                windSpeed = weatherWind,
                isLoading = weatherLoading,
                highTrafficCount = highTrafficZones.size
            )

            // ──────── LIVE CONGESTION STATS CARD ────────
            SectionHeader(
                title = "Live Congestion",
                icon = Icons.Outlined.Traffic,
                actionLabel = "View Map",
                onAction = {
                    try { Firebase.analytics.logEvent("map_opened_from_home", null) } catch (_: Exception) {}
                    onNavigateToTab(CitizenTab.MAP)
                }
            )

            LiveCongestionCard(
                totalRoads = effectiveTrafficMap.size,
                highCount = highTrafficZones.size,
                mediumCount = mediumTrafficZones.size,
                lowCount = lowTrafficCount,
                isLoading = effectiveTrafficMap.isEmpty(),
                isDemo = trafficMap.isEmpty()
            )

            // ──────── 🅿️ NEARBY BEST PARKING (Below Congestion) ────────
            SectionHeader(
                title = "Nearby Parking",
                icon = Icons.Outlined.LocalParking,
                actionLabel = "View All",
                onAction = {
                    try { Firebase.analytics.logEvent("find_parking_clicked", null) } catch (_: Exception) {}
                    onNavigateToTab(CitizenTab.PARKING)
                }
            )

            NearbyParkingBlueCard(
                nearbyParking = nearbyParkingItems,
                onFindParking = { onNavigateToTab(CitizenTab.PARKING) }
            )

            // ──────── HIGH TRAFFIC ZONES GRID ────────
            if (highTrafficZones.isNotEmpty() || mediumTrafficZones.isNotEmpty()) {
                SectionHeader(
                    title = "Traffic Zones",
                    icon = Icons.Outlined.LocationOn
                )

                CitizenTrafficZonesGrid(
                    highZones = highTrafficZones,
                    mediumZones = mediumTrafficZones
                )
            }

            // ──────── MY REPORTS PIE CHART ────────
            SectionHeader(
                title = "My Reports",
                icon = Icons.Outlined.Assignment
            )

            CitizenPieChartCard(
                title = "Reports Overview",
                icon = Icons.Outlined.Assignment,
                accentColor = PrimaryBlue,
                items = listOf(
                    ChartItem("Pending", myPendingReports.size, AccentOrange),
                    ChartItem("In Progress", myInProgressReports.size, PrimaryBlue),
                    ChartItem("Resolved", myResolvedReports.size, AccentGreen)
                ),
                total = myReports.size,
                centerLabel = "Reports"
            )

            // ──────── 🚗 MY VIOLATIONS CARD ────────
            if (myViolations.isNotEmpty()) {
                SectionHeader(
                    title = "My Violations",
                    icon = Icons.Outlined.Receipt
                )

                MyViolationsCard(
                    violations = myViolations,
                    activeCount = activeViolations.size,
                    totalFines = totalFines,
                    onViewDetail = { selectedViolation = it }
                )
            } else if (userVehicles.isEmpty()) {
                // Prompt to add vehicle
                SectionHeader(
                    title = "Vehicle Alerts",
                    icon = Icons.Outlined.DirectionsCar
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Large),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DirectionsCar, null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(Spacing.Medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Add your vehicle", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text("Register your vehicle number in Profile to get fine & violation alerts",
                                style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                        }
                    }
                }
            }

            // ──────── 🏥 NEARBY EMERGENCY SERVICES ────────
            SectionHeader(
                title = "Emergency Services",
                icon = Icons.Outlined.LocalHospital
            )

            NearbyEmergencyServicesCard()

            // ──────── QUICK ACTIONS (Modern Grid Design) ────────
            QuickActionsGrid(
                highTrafficCount = highTrafficZones.size,
                unreadAlerts = unreadCount,
                pendingReports = myPendingReports.size,
                newParkingAreas = nearbyParkingItems.size,
                onMapClick = {
                    try { Firebase.analytics.logEvent("map_opened_from_home", null) } catch (_: Exception) {}
                    onNavigateToTab(CitizenTab.MAP)
                },
                onParkingClick = {
                    try { Firebase.analytics.logEvent("find_parking_clicked", null) } catch (_: Exception) {}
                    onNavigateToTab(CitizenTab.PARKING)
                },
                onReportClick = {
                    try { Firebase.analytics.logEvent("report_issue_clicked", null) } catch (_: Exception) {}
                    onNavigateToTab(CitizenTab.REPORT)
                },
                onAlertsClick = { onNavigateToTab(CitizenTab.ALERTS) }
            )

            // ──────── RECENT ACTIVITY TIMELINE ────────
            SectionHeader(
                title = "Recent Activity",
                icon = Icons.Outlined.History,
                actionLabel = "View All",
                onAction = { onNavigateToTab(CitizenTab.ALERTS) }
            )

            if (reportsLoading) {
                repeat(3) { ShimmerCard() }
            } else if (recentReports.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Outlined.CheckCircle,
                    message = "No nearby incidents right now"
                )
            } else {
                RecentActivityTimeline(reports = recentReports)
            }

            Spacer(modifier = Modifier.height(Spacing.Large))
        }
    }

    // ──────── 🚗 VIOLATION DETAIL DIALOG ────────
    selectedViolation?.let { violation ->
        ViolationDetailDialog(
            violation = violation,
            onDismiss = { selectedViolation = null }
        )
    }

    // ──────── 🆘 SOS EMERGENCY FLOATING BUTTON ────────
    SosEmergencyFab(
        onClick = { showSosDialog = true }
    )

    // ──────── SOS CONFIRMATION DIALOG ────────
    if (showSosDialog) {
        AlertDialog(
            onDismissRequest = { if (!sosSending) showSosDialog = false },
            icon = {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "🆘 Send SOS Alert?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "This will send your live location and an emergency alert to nearby police officers immediately.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.cityFluxColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        sosSending = true
                        val uid = auth.currentUser?.uid ?: return@Button
                        try {
                            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                .addOnSuccessListener { loc ->
                                    val alert = hashMapOf(
                                        "userId" to uid,
                                        "userName" to (userName ?: "Citizen"),
                                        "latitude" to (loc?.latitude ?: 0.0),
                                        "longitude" to (loc?.longitude ?: 0.0),
                                        "timestamp" to com.google.firebase.Timestamp.now(),
                                        "status" to "active",
                                        "type" to "sos"
                                    )
                                    firestore.collection("sos_alerts").add(alert)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "🆘 SOS Alert Sent! Help is on the way.", Toast.LENGTH_LONG).show()
                                            sosSending = false
                                            showSosDialog = false
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(context, "Failed to send SOS. Try again.", Toast.LENGTH_SHORT).show()
                                            sosSending = false
                                        }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Could not get location. Check GPS.", Toast.LENGTH_SHORT).show()
                                    sosSending = false
                                }
                        } catch (_: SecurityException) {
                            Toast.makeText(context, "Location permission required.", Toast.LENGTH_SHORT).show()
                            sosSending = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    enabled = !sosSending
                ) {
                    if (sosSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (sosSending) "Sending..." else "Send SOS", color = Color.White)
                }
            },
            dismissButton = {
                if (!sosSending) {
                    TextButton(onClick = { showSosDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
    } // end Box
}

// ═══════════════════════════════════════════════════════════════════
// Sub-components
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HomeTopBar(
    userName: String?,
    userLoading: Boolean,
    unreadCount: Int,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val colors = MaterialTheme.cityFluxColors
    
    // Get time-based greeting
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
    
    val greetingEmoji = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "☀️"
            hour < 17 -> "🌤️"
            hour < 20 -> "🌅"
            else -> "🌙"
        }
    }

    // Subtle breathing animation for the card
    val infiniteTransition = rememberInfiniteTransition(label = "topBarBreath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // Clean, minimal welcome card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
            .scale(breathScale)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = PrimaryBlue.copy(alpha = 0.25f),
                spotColor = GradientBright.copy(alpha = 0.15f)
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
                            Color(0xFF1E3A5F),
                            Color(0xFF2E5A8F),
                            Color(0xFF1E4976)
                        ),
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                )
        ) {
            // Subtle pattern overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(100f, 50f),
                            radius = 300f
                        )
                    )
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clean avatar with gradient border
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF60A5FA),
                                    Color(0xFF3B82F6)
                                )
                            )
                        )
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E3A5F)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!userLoading && !userName.isNullOrBlank()) {
                        Text(
                            text = userName.first().uppercaseChar().toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Greeting & Name
                Column(modifier = Modifier.weight(1f)) {
                    if (userLoading) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                    } else {
                        // Time-based greeting
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$greeting $greetingEmoji",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = userName ?: "Citizen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.25.sp
                        )
                    }
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Notification button with badge
                    Box {
                        Surface(
                            onClick = onNotificationClick,
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.12f),
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
                                color = Color(0xFFEF4444),
                                shadowElevation = 2.dp
                            ) {
                                Text(
                                    text = if (unreadCount > 99) "99+" else "$unreadCount",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Profile/Settings button
                    Surface(
                        onClick = onProfileClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
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

@Composable
private fun LiveAlertBanner(worstLevel: String?) {
    // Kept for backward compatibility — unused now
}

// ═══════════════════════════════════════════════════════════════════
// ── CRITICAL ALERTS BANNER (citizen version) ────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CitizenAlertsBanner(
    worstLevel: String?,
    highZoneCount: Int,
    pendingCount: Int
) {
    val hasCritical = worstLevel == "HIGH"

    val bgGradient: Brush
    val iconTint: Color
    val titleText: String
    val subtitleText: String
    val bannerIcon: ImageVector

    if (hasCritical) {
        bgGradient = Brush.horizontalGradient(listOf(AccentRed, Color(0xFFDC2626)))
        iconTint = Color.White
        titleText = "⚠️  Heavy Traffic Alert"
        bannerIcon = Icons.Filled.Warning
        subtitleText = buildString {
            append("$highZoneCount high-congestion zone(s)")
            if (pendingCount > 0) append(" • $pendingCount pending report(s)")
        }
    } else if (worstLevel == "MEDIUM") {
        bgGradient = Brush.horizontalGradient(listOf(AccentOrange, Color(0xFFF97316)))
        iconTint = Color.White
        titleText = "⚡  Moderate Traffic"
        bannerIcon = Icons.Filled.Info
        subtitleText = buildString {
            append("Moderate congestion detected")
            if (pendingCount > 0) append(" • $pendingCount pending report(s)")
        }
    } else if (worstLevel == "LOW") {
        bgGradient = Brush.horizontalGradient(listOf(AccentGreen, Color(0xFF059669)))
        iconTint = Color.White
        titleText = "✅  Roads Clear"
        bannerIcon = Icons.Filled.CheckCircle
        subtitleText = "Smooth driving conditions"
    } else {
        bgGradient = Brush.horizontalGradient(listOf(PrimaryBlue, GradientBright))
        iconTint = Color.White
        titleText = "📡  Live Monitoring"
        bannerIcon = Icons.Filled.Sensors
        subtitleText = "Tracking traffic in real time"
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

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector? = null,
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
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
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

@Composable
private fun CongestionChip(roadId: String, status: TrafficStatus) {
    val level = status.congestionLevel.uppercase()
    val chipColor = when (level) {
        "HIGH" -> AccentRed
        "MEDIUM" -> AccentOrange
        else -> AccentGreen
    }
    val label = when (level) {
        "HIGH" -> "Heavy"
        "MEDIUM" -> "Moderate"
        else -> "Clear"
    }

    Card(
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = chipColor.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(chipColor)
            )
            Column {
                Text(
                    text = roadId.replace("_", ", "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chipColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ParkingMiniCard(parkingId: String, live: ParkingLive) {
    val colors = MaterialTheme.cityFluxColors
    val available = live.availableSlots
    val statusColor = when {
        available == 0 -> AccentRed
        available <= 5 -> AccentOrange
        else -> AccentParking
    }

    Card(
        modifier = Modifier
            .width(150.dp)
            .shadow(2.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.LocalParking,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = parkingId.replace("_", " ").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (available == 0) "Full" else "$available slots",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── NEARBY PARKING BLUE CARD (Top 5 with Blue+White Theme) ──────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun NearbyParkingBlueCard(
    nearbyParking: List<NearbyParkingItem>,
    onFindParking: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    // Calculate stats
    val availableSpots = nearbyParking.sumOf { it.availableSlots }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = PrimaryBlue.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            // Top blue accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryBlue, PrimaryBlue.copy(alpha = 0.6f), PrimaryBlue)
                        )
                    )
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalParking,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Top 5 parking spots",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Ranked by distance, availability & legal status",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    
                    // Live badge
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulsingDot(color = PrimaryBlue, size = 6.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.Medium))
                
                // Top 5 parking list
                if (nearbyParking.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.Large),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No parking spots found within 5km",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary
                        )
                    }
                } else {
                    nearbyParking.forEachIndexed { index, item ->
                        NearbyParkingRow(
                            item = item
                        )
                        if (index < nearbyParking.lastIndex) {
                            Divider(
                                color = colors.divider,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(Spacing.Medium))
                
                // Find More Button
                OutlinedButton(
                    onClick = onFindParking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    border = BorderStroke(1.5.dp, PrimaryBlue),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Find Best Nearby Parking",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyParkingRow(
    item: NearbyParkingItem
) {
    val colors = MaterialTheme.cityFluxColors
    val available = item.availableSlots
    val total = item.totalSlots
    val occupancyPercent = if (total > 0) ((total - available).toFloat() / total * 100).toInt() else 0
    
    val rankColor = when (item.rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> PrimaryBlue.copy(alpha = 0.7f)
    }
    
    val statusColor = when {
        available == 0 -> AccentRed
        available <= 5 -> AccentOrange
        else -> AccentGreen
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (item.rank <= 3) rankColor.copy(alpha = 0.15f) 
                    else PrimaryBlue.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${item.rank}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (item.rank <= 3) rankColor else PrimaryBlue
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Parking info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.address,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${item.distanceKmText} km away",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
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
                        text = "$available free",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "•",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
                Text(
                    text = "$total total",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
                Text(
                    text = if (item.isLegal) "✅ Legal" else "⚠️ Unofficial",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isLegal) AccentParking else AccentAlerts
                )
            }
        }
        
        // Progress indicator
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(occupancyPercent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            occupancyPercent > 80 -> AccentRed
                            occupancyPercent > 50 -> AccentOrange
                            else -> PrimaryBlue
                        }
                    )
            )
        }
    }
}

@Composable
private fun IncidentCard(report: Report) {
    val colors = MaterialTheme.cityFluxColors
    val typeColor = when (report.type) {
        "accident" -> AccentRed
        "illegal_parking" -> AccentParking
        "traffic_violation" -> AccentTraffic
        "road_damage" -> AccentOrange
        "hawker" -> AccentAlerts
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
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(typeColor)
            )
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
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Small),
                        color = typeColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = typeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = typeColor
                        )
                    }
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = report.title.ifBlank { typeLabel },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (report.description.isNotBlank()) {
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                StatusChip(status = report.status)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(icon: ImageVector, message: String) {
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

// ── Shimmer loading skeleton ──
@Composable
private fun ShimmerCard() {
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
private fun ShimmerBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
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

// Alias for consistency with police style
@Composable
private fun CitizenShimmerBox(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    ShimmerBox(width = width, height = height)
}

// ═══════════════════════════════════════════════════════════════════
// ── DATA CLASS FOR PIE CHART ────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

private data class ChartItem(val label: String, val value: Int, val color: Color)
private data class NearbyParkingItem(
    val id: String,
    val address: String,
    val availableSlots: Int,
    val totalSlots: Int,
    val distanceMeters: Float?,
    val isLegal: Boolean,
    val rank: Int,
    val totalScore: Int,
    val distanceKmText: String
)

private fun citizenTrafficDemoData(now: Long = System.currentTimeMillis()): Map<String, TrafficStatus> = mapOf(
    "jalna_road" to TrafficStatus("HIGH", now),
    "station_road" to TrafficStatus("MEDIUM", now),
    "kranti_chowk" to TrafficStatus("HIGH", now),
    "beed_bypass" to TrafficStatus("LOW", now),
    "cidco_n5" to TrafficStatus("MEDIUM", now),
    "adalat_road" to TrafficStatus("LOW", now)
)

// ═══════════════════════════════════════════════════════════════════
// ── LIVE CONGESTION STATS CARD (Blue+White Theme) ───────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveCongestionCard(
    totalRoads: Int,
    highCount: Int,
    mediumCount: Int,
    lowCount: Int,
    isLoading: Boolean,
    isDemo: Boolean
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = PrimaryBlue.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            // Top blue accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryBlue, PrimaryBlue.copy(alpha = 0.5f), PrimaryBlue)
                        )
                    )
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Traffic,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Traffic Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (isLoading) "Loading..." else "$totalRoads roads monitored",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            if (isDemo) {
                                Text(
                                    text = "Demo data",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    
                    // Live badge
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulsingDot(color = PrimaryBlue, size = 6.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        repeat(3) { ShimmerBox(width = 80.dp, height = 56.dp) }
                    }
                } else {
                    // Traffic Distribution Bar
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

                    // Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CongestionMiniStat(label = "Critical", count = highCount, color = AccentRed, icon = Icons.Filled.Warning)
                        Box(modifier = Modifier.width(1.dp).height(52.dp).background(colors.divider))
                        CongestionMiniStat(label = "Moderate", count = mediumCount, color = AccentOrange, icon = Icons.Filled.RemoveCircle)
                        Box(modifier = Modifier.width(1.dp).height(52.dp).background(colors.divider))
                        CongestionMiniStat(label = "Clear", count = lowCount, color = AccentGreen, icon = Icons.Filled.CheckCircle)
                    }
                }
            }
        }
    }
}

@Composable
private fun CongestionMiniStat(label: String, count: Int, color: Color, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
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
// ── HIGH TRAFFIC ZONES GRID (citizen version) ───────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CitizenTrafficZonesGrid(
    highZones: List<Map.Entry<String, TrafficStatus>>,
    mediumZones: List<Map.Entry<String, TrafficStatus>>
) {
    val colors = MaterialTheme.cityFluxColors
    val allZones = (highZones + mediumZones).take(6)

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
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── PIE CHART STATISTICS CARD (Blue+White Professional) ─────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CitizenPieChartCard(
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

    var animationPlayed by remember { mutableStateOf(false) }
    val animateProgress = animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "pieAnim"
    )
    LaunchedEffect(Unit) { animationPlayed = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = PrimaryBlue.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            // Top blue accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(PrimaryBlue, PrimaryBlue.copy(alpha = 0.5f), PrimaryBlue)
                        )
                    )
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PrimaryBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "$total total submitted",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    
                    // Total badge
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "$total",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                // Chart and Legend Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pie Chart
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val strokeWidth = 18.dp.toPx()
                            val radius = (size.minDimension - strokeWidth) / 2f
                            val topLeft = Offset(
                                (size.width - 2 * radius) / 2f,
                                (size.height - 2 * radius) / 2f
                            )
                            val arcSize = Size(radius * 2, radius * 2)
                            val gapAngle = if (nonZeroItems.size > 1) 6f else 0f
                            val totalGap = gapAngle * nonZeroItems.size
                            val availableSweep = (360f - totalGap) * animateProgress.value

                            if (nonZeroItems.isEmpty()) {
                                drawArc(
                                    color = PrimaryBlue.copy(alpha = 0.15f),
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

                        // Center icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Assignment,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(Spacing.Large))

                    // Legend items
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items.forEach { item ->
                            ReportLegendItem(
                                item = item,
                                total = total
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLegendItem(
    item: ChartItem,
    total: Int
) {
    val colors = MaterialTheme.cityFluxColors
    val pct = if (total > 0) (item.value * 100f / total).toInt() else 0
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator with icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(item.color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(item.color)
            )
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(item.color)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        // Count and percentage
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.value}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "$pct%",
                style = MaterialTheme.typography.labelSmall,
                color = item.color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── RECENT ACTIVITY TIMELINE ────────────────────────────────────
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp)
                    ) {
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
                            Text(text = "•", color = colors.textTertiary, fontSize = 8.sp)
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
// 🆘 SOS Emergency Floating Action Button
// ═══════════════════════════════════════════════════════════════════
// MY VIOLATIONS CARD — Shows fines/warnings matched to citizen's vehicles
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MyViolationsCard(
    violations: List<Map<String, Any>>,
    activeCount: Int,
    totalFines: Int,
    onViewDetail: (Map<String, Any>) -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.XLarge),
                ambientColor = colors.cardShadow,
                spotColor = AccentRed.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.XLarge),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active violations
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(AccentRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$activeCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Active", fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                            style = MaterialTheme.typography.titleSmall)
                        Text("Violation${if (activeCount != 1) "s" else ""}", color = colors.textTertiary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Total fines
                if (totalFines > 0) {
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = AccentRed.copy(alpha = 0.1f),
                        border = BorderStroke(0.5.dp, AccentRed.copy(alpha = 0.2f))
                    ) {
                        Text(
                            "₹$totalFines",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentRed
                        )
                    }
                }
            }

            if (violations.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.Medium))
                HorizontalDivider(color = colors.surfaceVariant, thickness = 0.5.dp)
                Spacer(Modifier.height(Spacing.Medium))
            }

            // Recent violations list (max 3)
            violations.take(3).forEachIndexed { idx, violation ->
                if (idx > 0) Spacer(Modifier.height(8.dp))
                val actionType = (violation["actionType"] as? String ?: "fine")
                val vehicleNo = violation["vehicleInfo"] as? String ?: ""
                val fineAmt = violation["fineAmount"] as? String ?: ""
                val status = violation["status"] as? String ?: "Active"
                val ts = violation["timestamp"] as? com.google.firebase.Timestamp
                val address = violation["address"] as? String ?: ""

                val actionColor = when (actionType.lowercase()) {
                    "tow" -> AccentRed
                    "fine" -> AccentOrange
                    else -> Color(0xFF6B7280)
                }
                val actionIcon = when (actionType.lowercase()) {
                    "tow" -> Icons.Outlined.Warning
                    "fine" -> Icons.Outlined.Receipt
                    else -> Icons.Outlined.Info
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadius.Medium))
                        .background(actionColor.copy(alpha = 0.05f))
                        .clickable { onViewDetail(violation) }
                        .padding(Spacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action type icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(actionColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(actionIcon, null, tint = actionColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                vehicleNo,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                letterSpacing = 1.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = actionColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    actionType.replaceFirstChar { it.uppercase() },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor
                                )
                            }
                        }
                        if (address.isNotBlank()) {
                            Text(
                                address,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ts?.let {
                            Text(
                                DateUtils.getRelativeTimeSpanString(
                                    it.toDate().time, System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString(),
                                fontSize = 10.sp,
                                color = colors.textTertiary
                            )
                        }
                    }
                    // Fine amount or status
                    Column(horizontalAlignment = Alignment.End) {
                        if (fineAmt.isNotBlank()) {
                            Text("₹$fineAmt", fontWeight = FontWeight.Bold, color = actionColor,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (status.equals("Active", true)) AccentRed else AccentGreen
                        )
                    }
                }
            }

            // View all if more than 3
            if (violations.size > 3) {
                Spacer(Modifier.height(Spacing.Small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "+${violations.size - 3} more violation${if (violations.size - 3 != 1) "s" else ""}",
                        color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// VIOLATION DETAIL DIALOG — Full details with photo, location, fine
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ViolationDetailDialog(
    violation: Map<String, Any>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors

    val actionType = (violation["actionType"] as? String ?: "fine")
    val vehicleNo = violation["vehicleInfo"] as? String ?: ""
    val fineAmt = violation["fineAmount"] as? String ?: ""
    val description = violation["description"] as? String ?: ""
    val address = violation["address"] as? String ?: ""
    val note = violation["note"] as? String ?: ""
    val imageUrl = violation["imageUrl"] as? String ?: ""
    val status = violation["status"] as? String ?: "Active"
    val violationType = violation["violationType"] as? String ?: ""
    val lat = (violation["latitude"] as? Number)?.toDouble() ?: 0.0
    val lng = (violation["longitude"] as? Number)?.toDouble() ?: 0.0
    val ts = violation["timestamp"] as? com.google.firebase.Timestamp

    val actionColor = when (actionType.lowercase()) {
        "tow" -> AccentRed
        "fine" -> AccentOrange
        else -> Color(0xFF6B7280)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.cardBackground,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(actionColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (actionType.lowercase()) {
                        "tow" -> Icons.Outlined.Warning
                        "fine" -> Icons.Outlined.Receipt
                        else -> Icons.Outlined.Info
                    },
                    null, tint = actionColor, modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${actionType.replaceFirstChar { it.uppercase() }} — $vehicleNo",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                if (fineAmt.isNotBlank()) {
                    Text(
                        "₹$fineAmt",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = actionColor
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Status badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = if (status.equals("Active", true)) AccentRed.copy(alpha = 0.1f)
                        else AccentGreen.copy(alpha = 0.1f)
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold,
                            color = if (status.equals("Active", true)) AccentRed else AccentGreen
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Evidence photo
                if (imageUrl.isNotBlank()) {
                    coil.compose.AsyncImage(
                        model = coil.request.ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                        contentDescription = "Evidence",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(CornerRadius.Large)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(Modifier.height(Spacing.Medium))
                }

                // Details
                if (violationType.isNotBlank()) {
                    ViolationInfoRow("Type", violationType.replace("_", " ").replaceFirstChar { it.uppercase() })
                }
                if (description.isNotBlank()) {
                    ViolationInfoRow("Description", description)
                }
                if (address.isNotBlank()) {
                    ViolationInfoRow("Location", address)
                }
                if (note.isNotBlank()) {
                    ViolationInfoRow("Officer Note", note)
                }
                ts?.let {
                    val fmt = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                    ViolationInfoRow("Date & Time", fmt.format(it.toDate()))
                }

                // Open in Maps
                if (lat != 0.0 && lng != 0.0) {
                    Spacer(Modifier.height(Spacing.Medium))
                    OutlinedButton(
                        onClick = {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Violation)")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium)
                    ) {
                        Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View on Map")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) { Text("Close") }
        }
    )
}

@Composable
private fun ViolationInfoRow(label: String, value: String) {
    val colors = MaterialTheme.cityFluxColors
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 10.sp, color = colors.textTertiary, fontWeight = FontWeight.Medium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
    }
}


// ═══════════════════════════════════════════════════════════════════
// 🆘 SOS Emergency Floating Action Button
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SosEmergencyFab(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = Spacing.XLarge, bottom = 24.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Glow ring
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(AccentRed.copy(alpha = glowAlpha))
        )
        // Actual button
        FloatingActionButton(
            onClick = onClick,
            containerColor = AccentRed,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Sos,
                contentDescription = "SOS Emergency",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 📢 Community Announcement Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AnnouncementCard(announcement: Map<String, Any>) {
    val colors = MaterialTheme.cityFluxColors
    val title = announcement["title"] as? String ?: "Announcement"
    val message = announcement["message"] as? String ?: ""
    val type = (announcement["type"] as? String ?: "info").lowercase()

    val (accentColor, bgAlpha, icon) = remember(type) {
        when (type) {
            "urgent" -> Triple(AccentRed, 0.08f, Icons.Filled.Error)
            "warning" -> Triple(AccentOrange, 0.08f, Icons.Filled.Warning)
            else -> Triple(PrimaryBlue, 0.06f, Icons.Filled.Info)
        }
    }

    val timestamp = announcement["timestamp"]
    val timeAgo = remember(timestamp) {
        when (timestamp) {
            is com.google.firebase.Timestamp ->
                DateUtils.getRelativeTimeSpanString(
                    timestamp.toDate().time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString()
            else -> ""
        }
    }

    Surface(
        modifier = Modifier.width(280.dp),
        shape = RoundedCornerShape(CornerRadius.Large),
        color = accentColor.copy(alpha = bgAlpha),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Small))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (timeAgo.isNotEmpty()) {
                        Text(
                            text = timeAgo,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = accentColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = type.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp
                    )
                }
            }
            if (message.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.Small))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 🌦️ Weather & Travel Card
// ═══════════════════════════════════════════════════════════════════

private fun weatherCodeToInfo(code: Int): Triple<String, ImageVector, String> {
    return when (code) {
        0 -> Triple("Clear Sky", Icons.Outlined.WbSunny, "Great driving conditions!")
        1, 2, 3 -> Triple("Partly Cloudy", Icons.Outlined.Cloud, "Good visibility. Drive safe.")
        45, 48 -> Triple("Foggy", Icons.Outlined.CloudQueue, "⚠️ Low visibility — drive slow, use fog lights.")
        51, 53, 55 -> Triple("Drizzle", Icons.Outlined.Grain, "Light rain — roads may be slippery.")
        61, 63, 65 -> Triple("Rainy", Icons.Outlined.Umbrella, "🌧️ Wet roads — expect slower traffic & delays.")
        66, 67 -> Triple("Freezing Rain", Icons.Outlined.AcUnit, "⚠️ Icy roads — avoid travel if possible.")
        71, 73, 75, 77 -> Triple("Snowfall", Icons.Outlined.AcUnit, "❄️ Snow — major traffic impact expected.")
        80, 81, 82 -> Triple("Rain Showers", Icons.Outlined.Umbrella, "🌧️ Heavy showers — poor visibility, delays likely.")
        85, 86 -> Triple("Snow Showers", Icons.Outlined.AcUnit, "❄️ Snow showers — roads dangerous.")
        95 -> Triple("Thunderstorm", Icons.Outlined.FlashOn, "⛈️ Thunderstorm — stay indoors if possible!")
        96, 99 -> Triple("Hail Storm", Icons.Outlined.FlashOn, "⛈️ Hail — avoid driving, seek shelter!")
        else -> Triple("Unknown", Icons.Outlined.Cloud, "Check local weather for updates.")
    }
}

@Composable
private fun WeatherCard(
    temp: Double?,
    weatherCode: Int,
    windSpeed: Double?,
    isLoading: Boolean,
    highTrafficCount: Int
) {
    val colors = MaterialTheme.cityFluxColors
    val (condition, weatherIcon, travelTip) = remember(weatherCode) {
        weatherCodeToInfo(weatherCode)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        color = colors.cardBackground,
        shadowElevation = 2.dp
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryBlue
                    )
                    Text(
                        "Loading weather...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary
                    )
                }
            }
        } else if (temp != null) {
            Column(modifier = Modifier.padding(Spacing.Large)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GradientSky.copy(alpha = 0.2f), PrimaryBlue.copy(alpha = 0.1f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = weatherIcon,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = condition,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Wind: ${windSpeed?.let { "%.0f".format(it) } ?: "-"} km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                    Text(
                        text = "${temp.let { "%.0f".format(it) }}°C",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.Medium))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = if (weatherCode in listOf(0, 1, 2, 3))
                        AccentGreen.copy(alpha = 0.08f)
                    else
                        AccentOrange.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DirectionsCar,
                            contentDescription = null,
                            tint = if (weatherCode in listOf(0, 1, 2, 3)) AccentGreen else AccentOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Small))
                        Text(
                            text = if (highTrafficCount > 0)
                                "$travelTip ($highTrafficCount congested zones)"
                            else travelTip,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 🏥 Nearby Emergency Services Card
// ═══════════════════════════════════════════════════════════════════

private data class EmergencyService(
    val name: String,
    val number: String,
    val icon: ImageVector,
    val color: Color,
    val mapsQuery: String
)

@Composable
private fun NearbyEmergencyServicesCard() {
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current

    val services = remember {
        listOf(
            EmergencyService("Emergency", "112", Icons.Filled.Sos, AccentRed, "emergency services"),
            EmergencyService("Police", "100", Icons.Outlined.LocalPolice, PrimaryBlue, "police station"),
            EmergencyService("Fire", "101", Icons.Outlined.LocalFireDepartment, AccentOrange, "fire station"),
            EmergencyService("Ambulance", "102", Icons.Outlined.LocalHospital, AccentGreen, "hospital")
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        color = colors.cardBackground,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Text(
                text = "Quick Dial",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                services.forEach { service ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${service.number}"))
                            context.startActivity(intent)
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(service.color.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = service.icon,
                                contentDescription = service.name,
                                tint = service.color,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = service.number,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = service.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))
            Divider(color = colors.divider)
            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "Find Nearby",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(Spacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                val nearbyItems = listOf(
                    Triple("🏥 Hospital", "hospital near me", AccentGreen),
                    Triple("🚔 Police", "police station near me", PrimaryBlue),
                    Triple("🚒 Fire Stn", "fire station near me", AccentOrange)
                )
                nearbyItems.forEach { (label, query, color) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}"))
                                    )
                                }
                            },
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = color.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.Medium),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Open Maps →",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ⚡ Quick Actions Grid - Modern Card Design with Live Badges
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsGrid(
    highTrafficCount: Int,
    unreadAlerts: Int,
    pendingReports: Int,
    newParkingAreas: Int,
    onMapClick: () -> Unit,
    onParkingClick: () -> Unit,
    onReportClick: () -> Unit,
    onAlertsClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.FlashOn,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
            // Total notification count
            val totalCount = highTrafficCount + unreadAlerts + pendingReports
            if (totalCount > 0) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentRed.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$totalCount new",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Action cards grid - 2x2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Traffic Map",
                subtitle = if (highTrafficCount > 0) "$highTrafficCount zones" else "View live",
                icon = Icons.Outlined.Traffic,
                accentColor = Color(0xFFEF4444),
                badgeCount = highTrafficCount,
                modifier = Modifier.weight(1f),
                onClick = onMapClick
            )
            QuickActionCard(
                title = "Find Parking",
                subtitle = if (newParkingAreas > 0) "$newParkingAreas nearby" else "Search",
                icon = Icons.Outlined.LocalParking,
                accentColor = Color(0xFF3B82F6),
                badgeCount = 0,
                modifier = Modifier.weight(1f),
                onClick = onParkingClick
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Report Issue",
                subtitle = "Submit now",
                icon = Icons.Outlined.ReportProblem,
                accentColor = Color(0xFFF59E0B),
                badgeCount = 0,
                modifier = Modifier.weight(1f),
                onClick = onReportClick
            )
            QuickActionCard(
                title = "My Alerts",
                subtitle = if (unreadAlerts > 0) "$unreadAlerts unread" else "All clear",
                icon = Icons.Outlined.Notifications,
                accentColor = Color(0xFF8B5CF6),
                badgeCount = unreadAlerts,
                modifier = Modifier.weight(1f),
                onClick = onAlertsClick
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        onClick = onClick,
        modifier = modifier
            .height(100.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = accentColor.copy(alpha = 0.15f),
                spotColor = accentColor.copy(alpha = 0.1f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Accent stripe on left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor, accentColor.copy(alpha = 0.5f))
                        )
                    )
            )
            
            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon container with gradient background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    accentColor.copy(alpha = 0.15f),
                                    accentColor.copy(alpha = 0.08f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Text content
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                }
                
                // Badge or arrow
                if (badgeCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor,
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = if (badgeCount > 99) "99+" else "$badgeCount",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.textTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
