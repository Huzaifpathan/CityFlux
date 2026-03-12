package com.example.cityflux.ui.police

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userName = doc.getString("name") ?: "Officer"
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

    // ── Derived Stats ──
    val totalComplaints = allReports.size
    val pendingReports = remember(allReports) {
        allReports.filter {
            it.status.equals("Pending", true) || it.status.equals("submitted", true)
        }
    }
    val inProgressReports = remember(allReports) {
        allReports.filter {
            it.status.equals("In Progress", true) || it.status.equals("in_progress", true)
        }
    }
    val resolvedReports = remember(allReports) {
        allReports.filter { it.status.equals("Resolved", true) }
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

    // ── Type breakdown ──
    val typeBreakdown = remember(allReports) {
        allReports.groupBy { it.type.lowercase() }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
    }

    // ── Parking stats ──
    val totalSlots = remember(parkingMap) { parkingMap.values.sumOf { it.availableSlots } }

    // Recent 5 reports
    val recentReports = remember(allReports) { allReports.take(5) }

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

            // ──────── LIVE CONGESTION SUMMARY ────────
            PoliceSectionHeader(
                title = "Live Congestion",
                icon = Icons.Outlined.Traffic
            )

            LiveCongestionSummary(
                totalRoads = trafficMap.size,
                highCount = highTrafficZones.size,
                mediumCount = mediumTrafficZones.size,
                lowCount = trafficMap.size - highTrafficZones.size - mediumTrafficZones.size,
                isLoading = trafficMap.isEmpty()
            )

            // ── High traffic zones carousel ──
            if (highTrafficZones.isNotEmpty() || mediumTrafficZones.isNotEmpty()) {
                val topZones = (highTrafficZones + mediumTrafficZones).take(8)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Spacing.Medium)
                ) {
                    items(topZones) { (roadId, status) ->
                        TrafficZoneChip(roadId = roadId, status = status)
                    }
                }
            }

            // ──────── QUICK STATS ────────
            PoliceSectionHeader(
                title = "Quick Stats",
                icon = Icons.Outlined.Analytics
            )

            // Primary stat row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                AnimatedStatCard(
                    title = "Active\nComplaints",
                    value = totalComplaints.toString(),
                    icon = Icons.Outlined.Report,
                    accentColor = AccentTraffic,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    title = "Pending\nReports",
                    value = pendingReports.size.toString(),
                    icon = Icons.Outlined.Pending,
                    accentColor = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            // Secondary stat row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                AnimatedStatCard(
                    title = "In\nProgress",
                    value = inProgressReports.size.toString(),
                    icon = Icons.Outlined.Autorenew,
                    accentColor = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                AnimatedStatCard(
                    title = "Resolved",
                    value = resolvedReports.size.toString(),
                    icon = Icons.Outlined.CheckCircle,
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            // ──────── COMPLAINTS BY TYPE ────────
            if (typeBreakdown.isNotEmpty()) {
                PoliceSectionHeader(
                    title = "Complaints by Type",
                    icon = Icons.Outlined.PieChart
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Spacing.Medium)
                ) {
                    items(typeBreakdown) { (type, count) ->
                        ComplaintTypeChip(type = type, count = count)
                    }
                }
            }

            // ──────── HIGH-TRAFFIC ZONES ────────
            PoliceSectionHeader(
                title = "High-Traffic Zones",
                icon = Icons.Outlined.LocationOn
            )

            if (highTrafficZones.isEmpty() && mediumTrafficZones.isEmpty()) {
                PoliceEmptyCard(
                    icon = Icons.Outlined.CheckCircle,
                    message = "All zones clear — no congestion detected"
                )
            } else {
                HighTrafficZoneCards(
                    highZones = highTrafficZones,
                    mediumZones = mediumTrafficZones
                )
            }

            // ──────── PENDING REPORTS ────────
            PoliceSectionHeader(
                title = "Pending Reports",
                icon = Icons.Outlined.Assignment,
                actionLabel = "View All",
                onAction = { onNavigateToTab(PoliceTab.REPORTS) }
            )

            if (reportsLoading) {
                repeat(3) { PoliceShimmerCard() }
            } else if (pendingReports.isEmpty()) {
                PoliceEmptyCard(
                    icon = Icons.Outlined.Verified,
                    message = "No pending reports — great work!"
                )
            } else {
                pendingReports.take(5).forEach { report ->
                    PendingReportCard(report = report)
                    Spacer(modifier = Modifier.height(Spacing.Small))
                }
            }

            // ──────── QUICK ACTION BUTTONS ────────
            PoliceSectionHeader(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PoliceActionButton(
                    label = "View Reports",
                    icon = Icons.Outlined.Assignment,
                    accentColor = AccentIssues,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.REPORTS) }
                )
                PoliceActionButton(
                    label = "View Issues",
                    icon = Icons.Outlined.ReportProblem,
                    accentColor = AccentTraffic,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.CONGESTION) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                PoliceActionButton(
                    label = "Mark Action",
                    icon = Icons.Outlined.TaskAlt,
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.ACTIONS) }
                )
                PoliceActionButton(
                    label = "Parking",
                    icon = Icons.Outlined.LocalParking,
                    accentColor = AccentParking,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(PoliceTab.PARKING) }
                )
            }

            // ──────── RECENT ACTIVITY FEED ────────
            PoliceSectionHeader(
                title = "Recent Activity",
                icon = Icons.Outlined.History
            )

            if (recentReports.isEmpty() && !reportsLoading) {
                PoliceEmptyCard(
                    icon = Icons.Outlined.Inbox,
                    message = "No recent activity"
                )
            } else {
                recentReports.forEach { report ->
                    RecentActivityCard(report = report)
                    Spacer(modifier = Modifier.height(Spacing.Small))
                }
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
                    text = "City Traffic Control · Live Operations",
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
// ── LIVE CONGESTION SUMMARY ─────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LiveCongestionSummary(
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
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Roads Monitored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = PrimaryBlue.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (isLoading) "..." else "$totalRoads roads",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
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
                    repeat(3) {
                        PoliceShimmerBox(width = 80.dp, height = 60.dp)
                    }
                }
            } else {
                // Congestion progress bar
                if (totalRoads > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
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

                // Level breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CongestionLevelIndicator(label = "High", count = highCount, color = AccentRed)
                    CongestionLevelIndicator(label = "Medium", count = mediumCount, color = AccentOrange)
                    CongestionLevelIndicator(label = "Low", count = lowCount, color = AccentGreen)
                }
            }
        }
    }
}

@Composable
private fun CongestionLevelIndicator(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cityFluxColors.textSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── TRAFFIC ZONE CHIP ───────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TrafficZoneChip(roadId: String, status: TrafficStatus) {
    val level = status.congestionLevel.uppercase()
    val chipColor = when (level) {
        "HIGH" -> AccentRed
        "MEDIUM" -> AccentOrange
        else -> AccentGreen
    }
    val levelLabel = when (level) {
        "HIGH" -> "Heavy"
        "MEDIUM" -> "Moderate"
        else -> "Clear"
    }

    Card(
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = chipColor.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (level == "HIGH") {
                PulsingDot(color = chipColor, size = 10.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(chipColor)
                )
            }
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
                    text = levelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = chipColor.copy(alpha = 0.75f)
                )
            }
        }
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
// ── ANIMATED STAT CARD ──────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AnimatedStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = accentColor.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Medium))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── COMPLAINT TYPE CHIP ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ComplaintTypeChip(type: String, count: Int) {
    val accentColor = when (type) {
        "traffic_violation" -> AccentTraffic
        "illegal_parking" -> AccentParking
        "road_damage" -> AccentIssues
        "accident" -> AccentRed
        "hawker" -> AccentOrange
        else -> AccentAlerts
    }
    val typeIcon = when (type) {
        "traffic_violation" -> Icons.Outlined.Traffic
        "illegal_parking" -> Icons.Outlined.LocalParking
        "road_damage" -> Icons.Outlined.Construction
        "accident" -> Icons.Outlined.CarCrash
        "hawker" -> Icons.Outlined.Store
        else -> Icons.Outlined.Report
    }
    val label = type.replace("_", " ").replaceFirstChar { it.uppercase() }

    Card(
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = typeIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
                Text(
                    text = "$count report${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── HIGH TRAFFIC ZONE CARDS ─────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HighTrafficZoneCards(
    highZones: List<Map.Entry<String, TrafficStatus>>,
    mediumZones: List<Map.Entry<String, TrafficStatus>>
) {
    val colors = MaterialTheme.cityFluxColors
    val allZones = (highZones + mediumZones).take(4)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        allZones.forEach { (roadId, status) ->
            val level = status.congestionLevel.uppercase()
            val zoneColor = if (level == "HIGH") AccentRed else AccentOrange
            val levelTag = if (level == "HIGH") "HEAVY" else "MODERATE"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        ambientColor = colors.cardShadow
                    ),
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(zoneColor)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = zoneColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = roadId.replace("_", ", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Small),
                            color = zoneColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = levelTag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = zoneColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── PENDING REPORT CARD ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PendingReportCard(report: Report) {
    val colors = MaterialTheme.cityFluxColors

    val accentColor = when (report.type.lowercase()) {
        "traffic_violation" -> AccentTraffic
        "illegal_parking" -> AccentParking
        "road_damage" -> AccentIssues
        "accident" -> AccentRed
        "hawker" -> AccentOrange
        else -> AccentAlerts
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
                    .background(accentColor)
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
                        color = accentColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = typeLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
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
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (report.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(AccentOrange)
                    )
                    Text(
                        text = "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentOrange
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── QUICK ACTION BUTTON ─────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        onClick = onClick,
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = accentColor.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            Spacer(modifier = Modifier.height(Spacing.Medium))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ── RECENT ACTIVITY CARD ────────────────────────────────────────
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecentActivityCard(report: Report) {
    val colors = MaterialTheme.cityFluxColors

    val statusColor = when (report.status.lowercase()) {
        "resolved" -> AccentGreen
        "in_progress", "in progress" -> AccentTraffic
        else -> AccentOrange
    }
    val statusIcon = when (report.status.lowercase()) {
        "resolved" -> Icons.Outlined.CheckCircle
        "in_progress", "in progress" -> Icons.Outlined.Autorenew
        else -> Icons.Outlined.Schedule
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
                elevation = 2.dp,
                shape = RoundedCornerShape(CornerRadius.Medium),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(Spacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.title.ifBlank { typeLabel },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$typeLabel • $timeAgo",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    maxLines = 1
                )
            }

            Surface(
                shape = RoundedCornerShape(CornerRadius.Small),
                color = statusColor.copy(alpha = 0.10f)
            ) {
                Text(
                    text = report.status.replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
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
