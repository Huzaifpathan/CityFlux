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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.data.RealtimeDbService
import com.example.cityflux.model.ParkingLive
import com.example.cityflux.model.Report
import com.example.cityflux.model.TrafficStatus
import com.example.cityflux.model.LocationUtils
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
    onNavigateToTab: (PoliceTab) -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

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

    // ── Real-time traffic from RTDB ──
    val trafficMap by RealtimeDbService.observeTraffic()
        .collectAsState(initial = emptyMap())

    // ── Real-time parking from RTDB ──
    val parkingMap by RealtimeDbService.observeParkingLive()
        .collectAsState(initial = emptyMap())

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
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shield badge icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(PrimaryBlue, GradientBright)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            if (userLoading) {
                PoliceShimmerBox(width = 140.dp, height = 16.dp)
                Spacer(modifier = Modifier.height(4.dp))
                PoliceShimmerBox(width = 100.dp, height = 12.dp)
            } else {
                Text(
                    text = "Officer ${userName ?: "On Duty"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = if (areaName.isNotBlank()) "$areaName · Live Operations"
                           else "City Traffic Control · Live Operations",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        // Notification bell
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge(containerColor = AccentRed, contentColor = Color.White) {
                        Text(
                            if (unreadCount > 9) "9+" else "$unreadCount",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        ) {
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = "Notifications",
                    tint = colors.textSecondary
                )
            }
        }

        // Profile
        IconButton(onClick = onProfileClick) {
            Icon(
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = "Profile",
                tint = colors.textSecondary
            )
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
                        color = PrimaryBlue
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
