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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            // ──────── LIVE ALERT BANNER ────────
            LiveAlertBanner(worstLevel = worstLevel)

            // ──────── DASHBOARD ACTION GRID ────────
            SectionHeader(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                QuickActionCard(
                    title = "Traffic",
                    subtitle = if (trafficMap.isEmpty()) "Loading..." else "${trafficMap.size} roads monitored",
                    icon = Icons.Outlined.Traffic,
                    accentColor = AccentTraffic,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try { Firebase.analytics.logEvent("map_opened_from_home", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.MAP)
                    }
                )
                QuickActionCard(
                    title = "Parking",
                    subtitle = if (parkingMap.isEmpty()) "Loading..."
                    else "$totalSlots slots across $parkingCount areas",
                    icon = Icons.Outlined.LocalParking,
                    accentColor = AccentParking,
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
                QuickActionCard(
                    title = "Report",
                    subtitle = "Report an issue",
                    icon = Icons.Outlined.ReportProblem,
                    accentColor = AccentIssues,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        try { Firebase.analytics.logEvent("report_issue_clicked", null) } catch (_: Exception) {}
                        onNavigateToTab(CitizenTab.REPORT)
                    }
                )
                QuickActionCard(
                    title = "My Reports",
                    subtitle = "Track your reports",
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    accentColor = AccentAlerts,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToTab(CitizenTab.ALERTS) }
                )
            }

            // ──────── TOP CONGESTED ROADS CAROUSEL ────────
            if (congestedRoads.isNotEmpty()) {
                SectionHeader(
                    title = "Top Congested Roads",
                    icon = Icons.Outlined.Traffic
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    contentPadding = PaddingValues(end = Spacing.Medium)
                ) {
                    items(congestedRoads) { (roadId, status) ->
                        CongestionChip(roadId = roadId, status = status)
                    }
                }
            }

            // ──────── PARKING AVAILABILITY CARDS ────────
            if (parkingMap.isNotEmpty()) {
                SectionHeader(
                    title = "Parking Availability",
                    icon = Icons.Outlined.LocalParking
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

            // ──────── NEARBY INCIDENTS ────────
            SectionHeader(
                title = "Nearby Incidents",
                icon = Icons.Outlined.Report
            )

            if (reportsLoading) {
                repeat(3) { ShimmerCard() }
            } else if (recentReports.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Outlined.CheckCircle,
                    message = "No nearby incidents right now"
                )
            } else {
                recentReports.forEach { report ->
                    IncidentCard(report = report)
                }
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
    val bgGradient: Brush
    val iconTint: Color
    val titleText: String
    val subtitleText: String
    val bannerIcon: ImageVector

    when (worstLevel) {
        "HIGH" -> {
            bgGradient = Brush.horizontalGradient(listOf(AccentRed, Color(0xFFDC2626)))
            iconTint = Color.White
            titleText = "⚠️  Heavy Traffic Alert"
            bannerIcon = Icons.Filled.Warning
            subtitleText = "Use alternate routes for faster travel"
        }
        "MEDIUM" -> {
            bgGradient = Brush.horizontalGradient(listOf(AccentOrange, Color(0xFFF97316)))
            iconTint = Color.White
            titleText = "⚡  Moderate Traffic"
            bannerIcon = Icons.Filled.Info
            subtitleText = "Plan your journey ahead"
        }
        "LOW" -> {
            bgGradient = Brush.horizontalGradient(listOf(AccentGreen, Color(0xFF059669)))
            iconTint = Color.White
            titleText = "✅  Roads Clear"
            bannerIcon = Icons.Filled.CheckCircle
            subtitleText = "Smooth driving conditions"
        }
        else -> {
            bgGradient = Brush.horizontalGradient(listOf(PrimaryBlue, GradientBright))
            iconTint = Color.White
            titleText = "📡  Live Monitoring"
            bannerIcon = Icons.Filled.Sensors
            subtitleText = "Tracking traffic in real time"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = if (worstLevel == "HIGH") AccentRed.copy(alpha = 0.3f) else Color.Transparent
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

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Medium))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
