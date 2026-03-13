package com.example.cityflux.ui.dashboard

import android.text.format.DateUtils
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
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase

// ═══════════════════════════════════════════════════════════════════
// Citizen Home Content — Premium Modern Dashboard
// All data from Firebase — zero hardcoded/dummy data
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

    // ── Real-time parking from RTDB ──
    val parkingMap by RealtimeDbService.observeParkingLive()
        .collectAsState(initial = emptyMap())

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
    val worstLevel = remember(trafficMap) {
        when {
            trafficMap.values.any { it.congestionLevel.equals("HIGH", true) } -> "HIGH"
            trafficMap.values.any { it.congestionLevel.equals("MEDIUM", true) } -> "MEDIUM"
            trafficMap.isNotEmpty() -> "LOW"
            else -> null
        }
    }

    // ── Derived: total available parking slots ──
    val totalSlots = remember(parkingMap) {
        parkingMap.values.sumOf { it.availableSlots }
    }
    val parkingCount = parkingMap.size

    // ── Derived: top congested roads (sorted by level) ──
    val congestedRoads = remember(trafficMap) {
        trafficMap.entries
            .sortedByDescending { entry ->
                when (entry.value.congestionLevel.uppercase()) {
                    "HIGH" -> 3; "MEDIUM" -> 2; else -> 1
                }
            }
            .take(6)
    }

    // ── Derived: congestion breakdown ──
    val highTrafficZones = remember(trafficMap) {
        trafficMap.entries.filter { it.value.congestionLevel.equals("HIGH", true) }
    }
    val mediumTrafficZones = remember(trafficMap) {
        trafficMap.entries.filter { it.value.congestionLevel.equals("MEDIUM", true) }
    }
    val lowTrafficCount = trafficMap.size - highTrafficZones.size - mediumTrafficZones.size

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

    // ── UI ──
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
                totalRoads = trafficMap.size,
                highCount = highTrafficZones.size,
                mediumCount = mediumTrafficZones.size,
                lowCount = lowTrafficCount,
                isLoading = trafficMap.isEmpty()
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

            // ──────── PARKING AVAILABILITY CARDS ────────
            if (parkingMap.isNotEmpty()) {
                SectionHeader(
                    title = "Parking Availability",
                    icon = Icons.Outlined.LocalParking,
                    actionLabel = "View All",
                    onAction = {
                        try { Firebase.analytics.logEvent("find_parking_clicked", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.PARKING)
                    }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Spacing.Medium)
                ) {
                    items(parkingMap.entries.toList()) { (id, live) ->
                        ParkingMiniCard(parkingId = id, live = live)
                    }
                }
            }

            // ──────── QUICK ACTIONS (premium with badges) ────────
            SectionHeader(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PremiumActionButton(
                    label = "Traffic\nMap",
                    icon = Icons.Outlined.Traffic,
                    accentColor = AccentTraffic,
                    badge = highTrafficZones.size.let { if (it > 0) "$it" else null },
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try { Firebase.analytics.logEvent("map_opened_from_home", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.MAP)
                    }
                )
                PremiumActionButton(
                    label = "Find\nParking",
                    icon = Icons.Outlined.LocalParking,
                    accentColor = AccentParking,
                    badge = null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try { Firebase.analytics.logEvent("find_parking_clicked", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.PARKING)
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PremiumActionButton(
                    label = "Report\nIssue",
                    icon = Icons.Outlined.ReportProblem,
                    accentColor = AccentIssues,
                    badge = null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try { Firebase.analytics.logEvent("report_issue_clicked", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.REPORT)
                    }
                )
                PremiumActionButton(
                    label = "My\nAlerts",
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    accentColor = AccentAlerts,
                    badge = myPendingReports.size.let { if (it > 0) "$it" else null },
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(CitizenTab.ALERTS) }
                )
            }

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Large),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // City badge icon (matching police shield style)
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
                imageVector = Icons.Filled.LocationCity,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(Spacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            if (userLoading) {
                CitizenShimmerBox(width = 140.dp, height = 16.dp)
                Spacer(modifier = Modifier.height(4.dp))
                CitizenShimmerBox(width = 100.dp, height = 12.dp)
            } else {
                Text(
                    text = "Hello, ${userName ?: "Citizen"} 👋",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Smart City Dashboard",
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

// ═══════════════════════════════════════════════════════════════════
// ── LIVE CONGESTION STATS CARD ──────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveCongestionCard(
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
                    repeat(3) { ShimmerBox(width = 80.dp, height = 56.dp) }
                }
            } else {
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
// ── PIE CHART STATISTICS CARD ───────────────────────────────────
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
