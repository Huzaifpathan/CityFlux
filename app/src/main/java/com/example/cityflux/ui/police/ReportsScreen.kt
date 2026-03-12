package com.example.cityflux.ui.police

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// ReportsScreen — Police Citizen Complaints Management Center
// Professional UI with search, multi-filter, type categories,
// photo viewer, location view, status marking, sort options,
// summary stats, and expandable report detail cards.
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onTakeAction: (Report) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.cityFluxColors

    // ── Data State ──
    var allReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    // ── Filter / Search / Sort State ──
    var selectedStatus by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    // ── Dialog State ──
    var photoDialogUrl by remember { mutableStateOf<String?>(null) }
    var expandedReportId by remember { mutableStateOf<String?>(null) }

    val statusFilters = listOf("All", "Pending", "In Progress", "Resolved")
    val typeFilters = listOf(
        TypeFilter("All", Icons.Outlined.Dashboard, PrimaryBlue),
        TypeFilter("illegal_parking", Icons.Outlined.LocalParking, AccentAlerts),
        TypeFilter("hawker", Icons.Outlined.Store, AccentOrange),
        TypeFilter("accident", Icons.Outlined.CarCrash, AccentRed),
        TypeFilter("road_damage", Icons.Outlined.Warning, AccentIssues),
        TypeFilter("traffic_violation", Icons.Outlined.Traffic, AccentTraffic),
        TypeFilter("other", Icons.Outlined.MoreHoriz, Color(0xFF6B7280))
    )

    // ── Firestore Listener ──
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error = true; loading = false; return@addSnapshotListener
                }
                allReports = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Report::class.java)?.copy(id = doc.id)
                    } catch (_: Exception) { null }
                } ?: emptyList()
                loading = false
            }
    }

    // ── Computed Filtered & Sorted List ──
    val filteredReports = remember(allReports, selectedStatus, selectedType, searchQuery, sortMode) {
        var list = allReports

        // Status filter
        list = when (selectedStatus) {
            "Pending" -> list.filter {
                it.status.equals("pending", true) || it.status.equals("submitted", true)
            }
            "In Progress" -> list.filter {
                it.status.equals("in_progress", true) || it.status.equals("in progress", true)
            }
            "Resolved" -> list.filter { it.status.equals("resolved", true) }
            else -> list
        }

        // Type filter
        if (selectedType != "All") {
            list = list.filter { it.type.equals(selectedType, true) }
        }

        // Search (by title, description, type)
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.type.lowercase().replace("_", " ").contains(q)
            }
        }

        // Sort
        when (sortMode) {
            SortMode.NEWEST -> list.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
            SortMode.OLDEST -> list.sortedBy { it.timestamp?.toDate()?.time ?: 0L }
            SortMode.TYPE -> list.sortedBy { it.type }
            SortMode.STATUS -> list.sortedBy {
                when (it.status.lowercase()) {
                    "pending", "submitted" -> 0
                    "in progress", "in_progress" -> 1
                    "resolved" -> 2
                    else -> 3
                }
            }
        }
    }

    // ── Stats ──
    val totalCount = allReports.size
    val pendingCount = allReports.count {
        it.status.equals("pending", true) || it.status.equals("submitted", true)
    }
    val inProgressCount = allReports.count {
        it.status.equals("in progress", true) || it.status.equals("in_progress", true)
    }
    val resolvedCount = allReports.count { it.status.equals("resolved", true) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ══════════════════════ Top Header Bar ══════════════════════
            ReportsTopBar(colors = colors)

            // ══════════════════════ Stats Summary Strip ══════════════════════
            ReportsStatsStrip(
                total = totalCount,
                pending = pendingCount,
                inProgress = inProgressCount,
                resolved = resolvedCount,
                colors = colors
            )

            // ══════════════════════ Search Bar ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search reports...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.cardBackground,
                        unfocusedContainerColor = colors.cardBackground,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = colors.cardBorder.copy(alpha = 0.3f),
                        cursorColor = PrimaryBlue
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                // Sort button
                Box {
                    FloatingActionButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        containerColor = colors.cardBackground,
                        elevation = FloatingActionButtonDefaults.elevation(2.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Sort,
                            contentDescription = "Sort",
                            tint = if (sortMode != SortMode.NEWEST) PrimaryBlue else colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            mode.icon,
                                            null,
                                            tint = if (sortMode == mode) PrimaryBlue else colors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
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

            // ══════════════════════ Type Category Chips ══════════════════════
            LazyRow(
                modifier = Modifier.padding(vertical = Spacing.Small),
                contentPadding = PaddingValues(horizontal = Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(typeFilters) { filter ->
                    val isSelected = selectedType == filter.type
                    ReportTypeCategoryChip(
                        filter = filter,
                        isSelected = isSelected,
                        count = if (filter.type == "All") allReports.size
                        else allReports.count { it.type.equals(filter.type, true) },
                        onClick = { selectedType = filter.type }
                    )
                }
            }

            // ══════════════════════ Status Filter Tabs ══════════════════════
            ScrollableTabRow(
                selectedTabIndex = statusFilters.indexOf(selectedStatus),
                edgePadding = Spacing.Large,
                containerColor = Color.Transparent,
                contentColor = PrimaryBlue,
                divider = {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colors.cardBorder.copy(alpha = 0.15f)
                    )
                },
                indicator = {
                    TabRowDefaults.SecondaryIndicator(
                        height = 3.dp,
                        color = PrimaryBlue
                    )
                }
            ) {
                statusFilters.forEachIndexed { index, filter ->
                    val count = when (filter) {
                        "Pending" -> pendingCount
                        "In Progress" -> inProgressCount
                        "Resolved" -> resolvedCount
                        else -> totalCount
                    }
                    Tab(
                        selected = selectedStatus == filter,
                        onClick = { selectedStatus = filter },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = filter,
                                    fontWeight = if (selectedStatus == filter) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (selectedStatus == filter) PrimaryBlue.copy(alpha = 0.15f)
                                        else colors.textTertiary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                count.toString(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selectedStatus == filter) PrimaryBlue else colors.textTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        selectedContentColor = PrimaryBlue,
                        unselectedContentColor = colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Small))

            // ══════════════════════ Results Count ══════════════════════
            Row(
                modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${filteredReports.size} reports",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                if (searchQuery.isNotBlank() || selectedType != "All" || selectedStatus != "All") {
                    Surface(
                        onClick = {
                            searchQuery = ""; selectedType = "All"; selectedStatus = "All"
                            sortMode = SortMode.NEWEST
                        },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                "Clear filters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Report List ══════════════════════
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingSpinner()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading reports...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }

                error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                null,
                                tint = AccentRed.copy(alpha = 0.6f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Failed to load reports",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                "Check connection and try again",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                filteredReports.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.SearchOff,
                                null,
                                tint = colors.textTertiary.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No reports found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                if (searchQuery.isNotBlank()) "Try different search terms"
                                else "No ${selectedStatus.lowercase()} reports",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(
                            start = Spacing.Large,
                            end = Spacing.Large,
                            top = Spacing.Small,
                            bottom = Spacing.XXLarge + 40.dp
                        )
                    ) {
                        itemsIndexed(
                            items = filteredReports,
                            key = { _, report -> report.id }
                        ) { index, report ->
                            SlideUpFadeIn(
                                visible = true,
                                delay = staggeredDelay(index)
                            ) {
                                PremiumReportCard(
                                    report = report,
                                    isExpanded = expandedReportId == report.id,
                                    onToggleExpand = {
                                        expandedReportId = if (expandedReportId == report.id) null else report.id
                                    },
                                    onViewPhoto = { url -> photoDialogUrl = url },
                                    onViewLocation = { lat, lng ->
                                        try {
                                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                        } catch (_: Exception) {
                                            val webUri = Uri.parse("https://www.google.com/maps?q=$lat,$lng")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                        }
                                    },
                                    onTakeAction = { onTakeAction(report) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════ Photo Viewer Dialog ══════════════════════
        if (photoDialogUrl != null) {
            Dialog(onDismissRequest = { photoDialogUrl = null }) {
                Card(
                    shape = RoundedCornerShape(CornerRadius.XLarge),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column {
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(photoDialogUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Report photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = CornerRadius.XLarge,
                                            topEnd = CornerRadius.XLarge
                                        )
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            // Close button
                            IconButton(
                                onClick = { photoDialogUrl = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        // Open in gallery
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(photoDialogUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.OpenInNew,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Open Full Image", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

    }
}


// ═══════════════════════════════════════════════════════════════════
// Top Bar — Police shield badge + title
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportsTopBar(colors: CityFluxColors) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = PrimaryBlue.copy(alpha = 0.1f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Assignment,
                            null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "Reports Center",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        "Citizen complaints management",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            // Live indicator
            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = AccentGreen.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PulsingDot(color = AccentGreen, size = 8.dp)
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
private fun PulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}


// ═══════════════════════════════════════════════════════════════════
// Stats Summary Strip
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportsStatsStrip(
    total: Int,
    pending: Int,
    inProgress: Int,
    resolved: Int,
    colors: CityFluxColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatsChipMini("Total", total.toString(), PrimaryBlue, Modifier.weight(1f))
        StatsChipMini("Pending", pending.toString(), AccentRed, Modifier.weight(1f))
        StatsChipMini("Active", inProgress.toString(), AccentOrange, Modifier.weight(1f))
        StatsChipMini("Done", resolved.toString(), AccentGreen, Modifier.weight(1f))
    }
}

@Composable
private fun StatsChipMini(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val themeColors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 10.sp,
                color = themeColors.textTertiary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Type Category Chip
// ═══════════════════════════════════════════════════════════════════

private data class TypeFilter(
    val type: String,
    val icon: ImageVector,
    val color: Color
) {
    val displayName: String
        get() = when (type) {
            "All" -> "All"
            "illegal_parking" -> "Illegal Parking"
            "hawker" -> "Hawkers"
            "accident" -> "Accidents"
            "road_damage" -> "Road Damage"
            "traffic_violation" -> "Traffic"
            "other" -> "Other"
            else -> type.replace("_", " ").replaceFirstChar { it.uppercase() }
        }
}

@Composable
private fun ReportTypeCategoryChip(
    filter: TypeFilter,
    isSelected: Boolean,
    count: Int,
    onClick: () -> Unit
) {
    val themeColors = MaterialTheme.cityFluxColors
    val bgColor = if (isSelected) filter.color.copy(alpha = 0.15f)
    else themeColors.cardBackground
    val contentColor = if (isSelected) filter.color else themeColors.textSecondary
    val borderColor = if (isSelected) filter.color.copy(alpha = 0.4f)
    else themeColors.cardBorder.copy(alpha = 0.2f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                filter.icon,
                null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                filter.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
            if (count > 0 && isSelected) {
                Surface(
                    shape = CircleShape,
                    color = filter.color.copy(alpha = 0.2f),
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            count.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = filter.color
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Premium Report Card
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumReportCard(
    report: Report,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onViewPhoto: (String) -> Unit,
    onViewLocation: (Double, Double) -> Unit,
    onTakeAction: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val accentColor = when (report.type.lowercase()) {
        "accident" -> AccentRed
        "illegal_parking" -> AccentAlerts
        "hawker" -> AccentOrange
        "road_damage" -> AccentIssues
        "traffic_violation" -> AccentTraffic
        else -> PrimaryBlue
    }
    val typeLabel = report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
    val typeIcon = when (report.type.lowercase()) {
        "accident" -> Icons.Outlined.CarCrash
        "illegal_parking" -> Icons.Outlined.LocalParking
        "hawker" -> Icons.Outlined.Store
        "road_damage" -> Icons.Outlined.Warning
        "traffic_violation" -> Icons.Outlined.Traffic
        else -> Icons.Outlined.ReportProblem
    }
    val timeAgo = report.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(
            it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: "Unknown"

    Card(
        onClick = onToggleExpand,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isExpanded) 8.dp else 4.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = if (isExpanded) BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)) else null
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = CornerRadius.Large,
                            bottomStart = CornerRadius.Large
                        )
                    )
                    .background(accentColor)
            )

            Column(modifier = Modifier.padding(Spacing.Large)) {
                // ── Header Row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type icon badge
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = accentColor.copy(alpha = 0.1f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                typeIcon,
                                null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            report.title.ifBlank { typeLabel },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentColor.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                "· $timeAgo",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }

                    // Status chip
                    StatusChip(status = report.status)
                }

                // ── Description ──
                if (report.description.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.Medium))
                    Text(
                        report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = if (isExpanded) 10 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ── Thumbnail Preview ──
                if (report.imageUrl.isNotBlank() && !isExpanded) {
                    Spacer(Modifier.height(Spacing.Small))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(report.imageUrl).crossfade(true).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Icon(
                            Icons.Outlined.Photo,
                            null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Photo attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }

                // ── Location Preview (always) ──
                if (report.latitude != 0.0 && report.longitude != 0.0 && !isExpanded) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "%.4f, %.4f".format(report.latitude, report.longitude),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                }

                // ══════════════════════ Expanded Section ══════════════════════
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(Spacing.Medium))
                        HorizontalDivider(
                            color = colors.cardBorder.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )
                        Spacer(Modifier.height(Spacing.Medium))

                        // ── Photo (Expanded) ──
                        if (report.imageUrl.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(CornerRadius.Medium))
                                    .clickable { onViewPhoto(report.imageUrl) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(report.imageUrl).crossfade(true).build(),
                                    contentDescription = "Report photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Overlay: tap to enlarge
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.4f)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(8.dp),
                                        shape = RoundedCornerShape(CornerRadius.Small),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.ZoomIn,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "Tap to enlarge",
                                                fontSize = 10.sp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(Spacing.Medium))
                        }

                        // ── Location Card ──
                        if (report.latitude != 0.0 && report.longitude != 0.0) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(CornerRadius.Medium),
                                color = AccentTraffic.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, AccentTraffic.copy(alpha = 0.15f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            null,
                                            tint = AccentTraffic,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                "Location",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.textTertiary
                                            )
                                            Text(
                                                "%.5f, %.5f".format(report.latitude, report.longitude),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                        }
                                    }
                                    FilledTonalButton(
                                        onClick = { onViewLocation(report.latitude, report.longitude) },
                                        shape = RoundedCornerShape(CornerRadius.Round),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = AccentTraffic.copy(alpha = 0.15f),
                                            contentColor = AccentTraffic
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.OpenInNew,
                                            null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Map",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(Spacing.Medium))
                        }

                        // ── Report Meta Info ──
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            color = colors.textTertiary.copy(alpha = 0.04f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                MetaInfoItem("Report ID", report.id.take(8) + "...")
                                MetaInfoItem("Time", timeAgo)
                                MetaInfoItem("Status", report.status)
                            }
                        }

                        Spacer(Modifier.height(Spacing.Large))

                        // ── In Progress / Resolved: Show Proofs & Chat ──
                        if (report.status.equals("Resolved", true) ||
                            report.status.equals("In Progress", true) ||
                            report.status.equals("in_progress", true)
                        ) {
                            ResolvedProofsSection(reportId = report.id, onViewPhoto = onViewPhoto)
                            Spacer(Modifier.height(Spacing.Medium))
                            ResolvedChatSection(reportId = report.id)
                        }

                        // ── Take Action Button (only for non-resolved) ──
                        val isResolved = report.status.equals("Resolved", true)
                        val isInProgress = report.status.equals("In Progress", true) ||
                                report.status.equals("in_progress", true)

                        if (!isResolved) {
                            Spacer(Modifier.height(Spacing.Medium))

                            Button(
                                onClick = onTakeAction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(CornerRadius.Medium),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isInProgress) PrimaryBlue else AccentOrange
                                ),
                                elevation = ButtonDefaults.buttonElevation(2.dp, 0.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.OpenInNew,
                                    null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isInProgress) "Continue Action" else "Take Action",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOnPrimary
                                )
                            }
                        }
                    }
                }

                // ── Expand indicator ──
                if (!isExpanded) {
                    Spacer(Modifier.height(Spacing.Small))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ExpandMore,
                            "Expand",
                            tint = colors.textTertiary.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun MetaInfoItem(label: String, value: String) {
    val colors = MaterialTheme.cityFluxColors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            fontSize = 10.sp
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// Resolved Proofs Section — shows uploaded proof images/videos
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ResolvedProofsSection(reportId: String, onViewPhoto: (String) -> Unit) {
    val colors = MaterialTheme.cityFluxColors
    var proofs by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(reportId) {
        val listener = FirebaseFirestore.getInstance()
            .collection("reports").document(reportId)
            .collection("proofs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                loading = false
                if (snap != null) {
                    proofs = snap.documents.mapNotNull { it.data }
                }
            }
        onDispose { listener.remove() }
    }

    if (loading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Small),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PrimaryBlue
            )
            Spacer(Modifier.width(8.dp))
            Text("Loading proofs…", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        return
    }

    if (proofs.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = AccentGreen.copy(alpha = 0.12f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Evidence & Proof (${proofs.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = AccentGreen
            )
        }

        Spacer(Modifier.height(8.dp))

        proofs.forEach { proof ->
            val imageUrl = proof["imageUrl"] as? String
            val description = proof["description"] as? String ?: ""
            val actionTaken = proof["actionTaken"] as? String ?: ""
            val timestamp = proof["timestamp"] as? Timestamp

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(CornerRadius.Small),
                color = AccentGreen.copy(alpha = 0.06f),
                border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.Small),
                    verticalAlignment = Alignment.Top
                ) {
                    if (!imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl).crossfade(true).build(),
                            contentDescription = "Proof",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onViewPhoto(imageUrl) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(Spacing.Small))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        if (actionTaken.isNotBlank()) {
                            Text(
                                actionTaken,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (description.isNotBlank()) {
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (timestamp != null) {
                            Text(
                                DateUtils.getRelativeTimeSpanString(
                                    timestamp.toDate().time,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString(),
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


// ═══════════════════════════════════════════════════════════════════
// Resolved Chat Section — shows police-citizen chat history
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ResolvedChatSection(reportId: String) {
    val colors = MaterialTheme.cityFluxColors
    var messages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(reportId) {
        val listener = FirebaseFirestore.getInstance()
            .collection("reports").document(reportId)
            .collection("chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                loading = false
                if (snap != null) {
                    messages = snap.documents.mapNotNull { it.data }
                }
            }
        onDispose { listener.remove() }
    }

    if (loading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Small),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PrimaryBlue
            )
            Spacer(Modifier.width(8.dp))
            Text("Loading chat…", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        return
    }

    if (messages.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = PrimaryBlue.copy(alpha = 0.12f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Chat, null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "Chat History (${messages.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
        }

        Spacer(Modifier.height(8.dp))

        // Chat bubbles (show last 5, collapsed)
        val displayMessages = if (messages.size > 5) messages.takeLast(5) else messages
        var showAll by remember { mutableStateOf(false) }
        val toShow = if (showAll) messages else displayMessages

        if (messages.size > 5 && !showAll) {
            TextButton(
                onClick = { showAll = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Show all ${messages.size} messages",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue
                )
            }
        }

        toShow.forEach { msg ->
            val senderRole = (msg["senderRole"] as? String) ?: "user"
            val senderName = (msg["senderName"] as? String) ?: if (senderRole == "police") "Officer" else "Citizen"
            val text = (msg["message"] as? String) ?: ""
            val imageUrl = msg["imageUrl"] as? String
            val timestamp = msg["timestamp"] as? Timestamp
            val isPolice = senderRole.equals("police", true)

            val bubbleColor = if (isPolice) PrimaryBlue.copy(alpha = 0.08f) else colors.surfaceVariant
            val bubbleBorder = if (isPolice) PrimaryBlue.copy(alpha = 0.18f) else colors.divider
            val align = if (isPolice) Alignment.End else Alignment.Start

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalAlignment = align
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.85f),
                    shape = RoundedCornerShape(
                        topStart = 12.dp, topEnd = 12.dp,
                        bottomStart = if (isPolice) 12.dp else 4.dp,
                        bottomEnd = if (isPolice) 4.dp else 12.dp
                    ),
                    color = bubbleColor,
                    border = BorderStroke(0.5.dp, bubbleBorder)
                ) {
                    Column(modifier = Modifier.padding(Spacing.Small)) {
                        Text(
                            senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPolice) PrimaryBlue else AccentOrange,
                            fontSize = 10.sp
                        )

                        if (!imageUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl).crossfade(true).build(),
                                contentDescription = "Chat image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        if (text.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textPrimary
                            )
                        }

                        if (timestamp != null) {
                            Text(
                                DateUtils.getRelativeTimeSpanString(
                                    timestamp.toDate().time,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS
                                ).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                                fontSize = 9.sp,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }

        if (showAll && messages.size > 5) {
            TextButton(
                onClick = { showAll = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Show less",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryBlue
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Sort Mode Enum
// ═══════════════════════════════════════════════════════════════════

private enum class SortMode(
    val label: String,
    val icon: ImageVector
) {
    NEWEST("Newest First", Icons.Outlined.Schedule),
    OLDEST("Oldest First", Icons.Outlined.History),
    TYPE("By Type", Icons.Outlined.Category),
    STATUS("By Status", Icons.Outlined.FilterList)
}
