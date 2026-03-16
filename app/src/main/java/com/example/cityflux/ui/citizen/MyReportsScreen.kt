package com.example.cityflux.ui.citizen

import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// MyReportsScreen — Citizen Reports Management Center
// Premium UI with search, filter chips, sort options,
// stats strip, expandable cards, photo viewer, and animations.
// ═══════════════════════════════════════════════════════════════════

private enum class CitizenSortMode(val label: String, val icon: ImageVector) {
    NEWEST("Newest", Icons.Outlined.Schedule),
    OLDEST("Oldest", Icons.Outlined.History),
    STATUS("By Status", Icons.Outlined.FilterList),
    TYPE("By Type", Icons.Outlined.Category)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen() {

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val colors = MaterialTheme.cityFluxColors

    // ── Data State ──
    var issues by remember { mutableStateOf(listOf<Report>()) }
    var loading by remember { mutableStateOf(true) }

    // ── Search, Filter, Sort State ──
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    var sortMode by remember { mutableStateOf(CitizenSortMode.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    // ── Detail Sheet / Photo Dialog ──
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    var photoDialogUrl by remember { mutableStateOf<String?>(null) }

    // ── Firestore Listener ──
    DisposableEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@DisposableEffect onDispose { }

        val listener: ListenerRegistration =
            firestore.collection("reports")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        issues = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Report::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                    loading = false
                }

        onDispose { listener.remove() }
    }

    // ── Computed: filtered + sorted list ──
    val statusFilters = listOf("All", "Pending", "In Progress", "Resolved", "Rejected")

    val filteredReports = remember(issues, searchQuery, selectedStatus, sortMode) {
        var list = issues

        // Status filter
        if (selectedStatus != "All") {
            list = list.filter { it.status.equals(selectedStatus, ignoreCase = true) }
        }

        // Search filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.type.lowercase().contains(q)
            }
        }

        // Sort
        when (sortMode) {
            CitizenSortMode.NEWEST -> list.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
            CitizenSortMode.OLDEST -> list.sortedBy { it.timestamp?.toDate()?.time ?: 0L }
            CitizenSortMode.STATUS -> list.sortedBy {
                when (it.status.lowercase()) {
                    "pending" -> 0; "in progress" -> 1; "resolved" -> 2; else -> 3
                }
            }
            CitizenSortMode.TYPE -> list.sortedBy { it.type }
        }
    }

    // ── Stats ──
    val totalReports = issues.size
    val pendingReports = issues.count { it.status.equals("Pending", true) }
    val inProgressReports = issues.count { it.status.equals("In Progress", true) }
    val resolvedReports = issues.count { it.status.equals("Resolved", true) }

    // ── Photo Viewer Dialog ──
    photoDialogUrl?.let { url ->
        Dialog(onDismissRequest = { photoDialogUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CornerRadius.XLarge))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                    contentDescription = "Report photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(CornerRadius.XLarge)),
                    contentScale = ContentScale.Fit
                )
                // Close button
                IconButton(
                    onClick = { photoDialogUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // ── Report Detail Full-Screen Dialog ──
    selectedReport?.let { report ->
        ReportDetailDialog(
            report = report,
            onDismiss = { selectedReport = null },
            onViewPhoto = { photoDialogUrl = it },
            onShare = {
                val shareText = buildString {
                    append("📋 Report: ${report.title}\n")
                    append("📝 ${report.description}\n")
                    append("📊 Status: ${report.status}\n")
                    append("🏷️ Type: ${report.type}\n")
                    append("— Shared via CityFlux")
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Report"))
            },
            colors = colors
        )
    }

    // ── Main UI ──
    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "My Reports",
                showNotification = true,
                showProfile = true
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // ══════════════ 1. Stats Strip ══════════════
            MyReportsStatsStrip(
                total = totalReports,
                pending = pendingReports,
                inProgress = inProgressReports,
                resolved = resolvedReports,
                colors = colors
            )

            // ══════════════ 2. Search Bar ══════════════
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search reports...", color = colors.textTertiary, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, "Search", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = RoundedCornerShape(CornerRadius.Large),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = colors.surfaceVariant,
                    focusedContainerColor = colors.cardBackground,
                    unfocusedContainerColor = colors.cardBackground
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .height(52.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary)
            )

            Spacer(Modifier.height(Spacing.Medium))

            // ══════════════ 3. Filter Chips + Sort ══════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter chips (scrollable)
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(statusFilters) { status ->
                        val isSelected = selectedStatus == status
                        val chipColor = when (status) {
                            "Pending" -> AccentIssues
                            "In Progress" -> AccentOrange
                            "Resolved" -> AccentGreen
                            "Rejected" -> AccentRed
                            else -> PrimaryBlue
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedStatus = status },
                            label = {
                                Text(
                                    status,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.15f),
                                selectedLabelColor = chipColor,
                                selectedLeadingIconColor = chipColor,
                                containerColor = colors.cardBackground,
                                labelColor = colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = colors.surfaceVariant,
                                selectedBorderColor = chipColor.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(CornerRadius.Round)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Sort button
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (sortMode != CitizenSortMode.NEWEST) PrimaryBlue.copy(alpha = 0.1f)
                                else colors.cardBackground,
                                RoundedCornerShape(CornerRadius.Medium)
                            )
                    ) {
                        Icon(
                            Icons.Outlined.Sort, "Sort",
                            tint = if (sortMode != CitizenSortMode.NEWEST) PrimaryBlue else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        CitizenSortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(mode.icon, null, modifier = Modifier.size(16.dp),
                                            tint = if (sortMode == mode) PrimaryBlue else colors.textSecondary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            mode.label,
                                            fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortMode == mode) PrimaryBlue else colors.textPrimary
                                        )
                                    }
                                },
                                onClick = { sortMode = mode; showSortMenu = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Small))

            // Result count
            Text(
                "${filteredReports.size} report${if (filteredReports.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = Spacing.Large)
            )

            Spacer(Modifier.height(Spacing.Medium))

            // ══════════════ Content ══════════════
            when {
                loading -> {
                    // Shimmer loading placeholders
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = Spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(bottom = Spacing.XXLarge)
                    ) {
                        items(4) {
                            ShimmerReportCard(colors)
                        }
                    }
                }
                filteredReports.isEmpty() -> {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XXLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                if (searchQuery.isNotBlank() || selectedStatus != "All")
                                    Icons.Outlined.SearchOff else Icons.Outlined.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = colors.textTertiary.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(Spacing.Large))
                            Text(
                                if (searchQuery.isNotBlank() || selectedStatus != "All")
                                    "No matching reports" else "No reports yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                            Spacer(Modifier.height(Spacing.Small))
                            Text(
                                if (searchQuery.isNotBlank() || selectedStatus != "All")
                                    "Try adjusting your search or filters"
                                else "Report an issue to get started!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textTertiary,
                                textAlign = TextAlign.Center
                            )
                            if (searchQuery.isNotBlank() || selectedStatus != "All") {
                                Spacer(Modifier.height(Spacing.Large))
                                OutlinedButton(onClick = {
                                    searchQuery = ""
                                    selectedStatus = "All"
                                    sortMode = CitizenSortMode.NEWEST
                                }) {
                                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Clear Filters")
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.padding(horizontal = Spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(bottom = Spacing.XXLarge)
                    ) {
                        itemsIndexed(filteredReports, key = { _, r -> r.id }) { index, report ->
                            SlideUpFadeIn(
                                visible = true,
                                delay = staggeredDelay(index)
                            ) {
                                CompactReportCard(
                                    report = report,
                                    onClick = { selectedReport = report },
                                    colors = colors
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
// Stats Strip — Total / Pending / Active / Resolved + Resolution Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MyReportsStatsStrip(
    total: Int,
    pending: Int,
    inProgress: Int,
    resolved: Int,
    colors: CityFluxColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MyStatsChip("Total", total.toString(), PrimaryBlue, Icons.Outlined.Assignment, Modifier.weight(1f))
            MyStatsChip("Pending", pending.toString(), AccentIssues, Icons.Outlined.Schedule, Modifier.weight(1f))
            MyStatsChip("Active", inProgress.toString(), AccentOrange, Icons.Outlined.Autorenew, Modifier.weight(1f))
            MyStatsChip("Done", resolved.toString(), AccentGreen, Icons.Outlined.CheckCircle, Modifier.weight(1f))
        }

        // Resolution progress bar
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Resolution",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceVariant)
                ) {
                    val resolvedFraction = resolved.toFloat() / total.toFloat()
                    val inProgressFraction = inProgress.toFloat() / total.toFloat()
                    Row(modifier = Modifier.fillMaxHeight()) {
                        if (resolved > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(resolvedFraction)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(AccentGreen, AccentGreen.copy(alpha = 0.7f))
                                        )
                                    )
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxHeight()) {
                        if (resolved > 0) Spacer(Modifier.fillMaxWidth(resolvedFraction))
                        if (inProgress > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(
                                        if (resolvedFraction < 1f)
                                            (inProgressFraction / (1f - resolvedFraction)).coerceAtMost(1f)
                                        else 0f
                                    )
                                    .background(AccentOrange.copy(alpha = 0.7f))
                            )
                        }
                    }
                }
                val pct = ((resolved * 100f) / total).toInt()
                Text(
                    "$pct%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (pct >= 70) AccentGreen else if (pct >= 40) AccentOrange else AccentRed,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MyStatsChip(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val themeColors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = themeColors.textTertiary, fontWeight = FontWeight.Medium)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Compact Report Card — Summary only, tap to open detail
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CompactReportCard(
    report: Report,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    val accentColor = when (report.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentOrange
        "rejected" -> AccentRed
        else -> AccentIssues
    }

    val typeIcon = reportTypeIcon(report.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        onClick = onClick
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or type icon
                if (report.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(report.imageUrl).crossfade(true).build(),
                        contentDescription = "Report image",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(accentColor.copy(alpha = 0.1f), RoundedCornerShape(CornerRadius.Medium)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = accentColor, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.width(Spacing.Medium))

                // Text info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = report.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                        report.timestamp?.let { ts ->
                            Text(
                                " · ",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                            Text(
                                DateUtils.getRelativeTimeSpanString(
                                    ts.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                                ).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.width(Spacing.Small))

                // Status + chevron
                Column(horizontalAlignment = Alignment.End) {
                    StatusChip(status = report.status)
                    Spacer(Modifier.height(4.dp))
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "View details",
                        tint = colors.textTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun reportTypeIcon(type: String): ImageVector {
    return when (type.lowercase()) {
        "illegal_parking" -> Icons.Outlined.LocalParking
        "accident" -> Icons.Outlined.CarCrash
        "pothole", "road_damage" -> Icons.Outlined.Warning
        "traffic_signal", "traffic_violation" -> Icons.Outlined.Traffic
        "streetlight" -> Icons.Outlined.LightMode
        "garbage", "waste" -> Icons.Outlined.Delete
        else -> Icons.Outlined.Report
    }
}

// ═══════════════════════════════════════════════════════════════════
// Report Detail Full-Screen Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportDetailDialog(
    report: Report,
    onDismiss: () -> Unit,
    onViewPhoto: (String) -> Unit,
    onShare: () -> Unit,
    colors: CityFluxColors
) {
    val accentColor = when (report.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentOrange
        "rejected" -> AccentRed
        else -> AccentIssues
    }
    val typeIcon = reportTypeIcon(report.type)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(CornerRadius.XLarge),
                    ambientColor = colors.cardShadow
                ),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header with close button ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                        .padding(Spacing.Large)
                ) {
                    // Close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(32.dp)
                            .background(colors.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(CornerRadius.Medium)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(typeIcon, null, tint = accentColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(Spacing.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = report.type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                StatusChip(status = report.status)
                            }
                        }

                        Spacer(Modifier.height(Spacing.Medium))

                        Text(
                            text = report.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }

                // ── Scrollable content ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.Large)
                ) {
                    // Images gallery
                    val allImages = buildList {
                        if (report.imageUrl.isNotEmpty()) add(report.imageUrl)
                        addAll(report.imageUrls.filter { it.isNotEmpty() && it != report.imageUrl })
                    }
                    if (allImages.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.Medium))
                        if (allImages.size == 1) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(allImages[0]).crossfade(true).build(),
                                contentDescription = "Report photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(CornerRadius.Large))
                                    .clickable { onViewPhoto(allImages[0]) },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                                items(allImages) { url ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(url).crossfade(true).build(),
                                        contentDescription = "Photo",
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(CornerRadius.Medium))
                                            .clickable { onViewPhoto(url) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.XLarge))

                    // Description section
                    Text(
                        "Description",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(Spacing.Small))
                    Text(
                        text = report.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        lineHeight = 22.sp
                    )

                    Spacer(Modifier.height(Spacing.XLarge))
                    HorizontalDivider(color = colors.surfaceVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(Spacing.XLarge))

                    // Details grid
                    Text(
                        "Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(Spacing.Medium))

                    // Priority
                    DetailRow(
                        icon = Icons.Outlined.Flag,
                        label = "Priority",
                        value = report.priority.replaceFirstChar { it.uppercase() },
                        valueColor = when (report.priority.lowercase()) {
                            "high" -> AccentRed; "medium" -> AccentOrange; else -> AccentGreen
                        },
                        colors = colors
                    )

                    // Date
                    report.timestamp?.let { ts ->
                        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        DetailRow(
                            icon = Icons.Outlined.CalendarToday,
                            label = "Reported on",
                            value = fmt.format(ts.toDate()),
                            colors = colors
                        )
                    }

                    // Location
                    if (report.latitude != 0.0 && report.longitude != 0.0) {
                        DetailRow(
                            icon = Icons.Outlined.LocationOn,
                            label = "Location",
                            value = "%.5f, %.5f".format(report.latitude, report.longitude),
                            colors = colors
                        )
                    }

                    // Upvotes
                    if (report.upvoteCount > 0) {
                        DetailRow(
                            icon = Icons.Outlined.ThumbUp,
                            label = "Upvotes",
                            value = "${report.upvoteCount}",
                            valueColor = PrimaryBlue,
                            colors = colors
                        )
                    }

                    // Anonymous
                    if (report.isAnonymous) {
                        DetailRow(
                            icon = Icons.Outlined.VisibilityOff,
                            label = "Visibility",
                            value = "Anonymous",
                            colors = colors
                        )
                    }

                    Spacer(Modifier.height(Spacing.XLarge))
                }

                // ── Bottom action bar ──
                HorizontalDivider(color = colors.surfaceVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        border = BorderStroke(1.dp, colors.inputBorder)
                    ) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    colors: CityFluxColors,
    valueColor: Color = colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(Spacing.Medium))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Shimmer Loading Placeholder
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerReportCard(colors: CityFluxColors) {
    val shimmerAnim = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmerAnim.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shimmerAlpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.textTertiary.copy(alpha = alpha))
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.textTertiary.copy(alpha = alpha))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.textTertiary.copy(alpha = alpha))
                    )
                }
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.textTertiary.copy(alpha = alpha))
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.textTertiary.copy(alpha = alpha))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.textTertiary.copy(alpha = alpha))
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(colors.textTertiary.copy(alpha = alpha))
            )
        }
    }
}

// Keep old composable for backward compatibility
@Composable
fun CitizenIssueCard(issue: Report) {
    val colors = MaterialTheme.cityFluxColors
    val accentColor = when (issue.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentOrange
        else -> AccentIssues
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(accentColor)
            )
            Column(modifier = Modifier.padding(Spacing.Large)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    StatusChip(status = issue.status)
                }
                Spacer(modifier = Modifier.height(Spacing.Small))
                Text(
                    text = issue.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2
                )
                if (issue.imageUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    AsyncImage(
                        model = issue.imageUrl,
                        contentDescription = "Report image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
