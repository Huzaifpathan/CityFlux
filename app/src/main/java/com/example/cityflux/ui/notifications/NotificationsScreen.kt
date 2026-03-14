package com.example.cityflux.ui.notifications

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cityflux.model.Notification
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import android.content.Intent
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicTextField
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════
// NotificationsScreen — Production-grade alerts & notifications
// with real-time Firestore data, swipe-to-delete, pin, mark read
// ═══════════════════════════════════════════════════════════════════

@Composable
fun NotificationsScreen(
    onNavigateToMap: (lat: Double, lng: Double) -> Unit = { _, _ -> },
    vm: NotificationsViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val state by vm.uiState.collectAsState()
    val unreadCount by vm.unreadCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Analytics ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("notifications_opened", null) } catch (_: Exception) {}
    }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailable(context) }

    // ── Snackbar for actions ──
    var snackbarMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            snackbarMsg = null
        }
    }

    // ── Feature 3/4/5/6/8: Search, Filters, Expand, Preferences ──
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("All") }
    var selectedTime by remember { mutableStateOf("All Time") }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showPreferencesDialog by remember { mutableStateOf(false) }

    // Feature 8: Alert preferences from SharedPreferences
    val prefs = remember { context.getSharedPreferences("alert_prefs", Context.MODE_PRIVATE) }
    var enabledTypes by remember {
        mutableStateOf(
            mapOf(
                "traffic" to prefs.getBoolean("traffic", true),
                "parking" to prefs.getBoolean("parking", true),
                "accident" to prefs.getBoolean("accident", true),
                "emergency" to prefs.getBoolean("emergency", true),
                "weather" to prefs.getBoolean("weather", true),
                "general" to prefs.getBoolean("general", true)
            )
        )
    }

    // ── Filter logic: search → type → time → preferences ──
    val filteredNotifications = remember(state.notifications, searchQuery, selectedType, selectedTime, enabledTypes) {
        state.notifications
            .filter { n -> enabledTypes[n.type.lowercase()] != false }
            .filter { n ->
                if (searchQuery.isBlank()) true
                else n.title.contains(searchQuery, ignoreCase = true) ||
                        n.message.contains(searchQuery, ignoreCase = true)
            }
            .filter { n ->
                if (selectedType == "All") true
                else n.type.equals(selectedType, ignoreCase = true)
            }
            .filter { n ->
                if (selectedTime == "All Time") true
                else {
                    val ts = n.timestamp?.toDate()?.time ?: return@filter false
                    val now = System.currentTimeMillis()
                    when (selectedTime) {
                        "Today" -> {
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, 0)
                            cal.set(Calendar.MINUTE, 0)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            ts >= cal.timeInMillis
                        }
                        "This Week" -> ts >= now - 7L * 24 * 60 * 60 * 1000
                        "This Month" -> ts >= now - 30L * 24 * 60 * 60 * 1000
                        else -> true
                    }
                }
            }
    }

    // Feature 8: Preferences dialog
    if (showPreferencesDialog) {
        AlertPreferencesDialog(
            enabledTypes = enabledTypes,
            onDismiss = { showPreferencesDialog = false },
            onSave = { newTypes ->
                enabledTypes = newTypes
                prefs.edit().apply {
                    newTypes.forEach { (key, value) -> putBoolean(key, value) }
                    apply()
                }
                showPreferencesDialog = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // ══════════════════════ Feature 1: Premium Header ══════════════════════
            PremiumNotificationsTopBar(
                colors = colors,
                unreadCount = unreadCount,
                totalCount = state.notifications.size,
                isLoading = state.isLoading,
                onMarkAllRead = {
                    try { Firebase.analytics.logEvent("mark_all_read_clicked", null) } catch (_: Exception) {}
                    vm.markAllAsRead()
                    snackbarMsg = "All marked as read"
                },
                onOpenPreferences = { showPreferencesDialog = true }
            )

            // ══════════════════════ Loading indicator ══════════════════════
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

            // ══════════════════════ Offline Banner ══════════════════════
            AnimatedVisibility(
                visible = isOffline || state.isOffline,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = AccentRed.copy(alpha = 0.95f)
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

            // ══════════════════════ Feature 2: Stats Strip ══════════════════════
            if (state.notifications.isNotEmpty()) {
                AlertStatsStrip(
                    total = state.notifications.size,
                    unread = unreadCount,
                    pinned = state.notifications.count { it.pinned },
                    today = state.notifications.count { n ->
                        val ts = n.timestamp?.toDate()?.time ?: return@count false
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        ts >= cal.timeInMillis
                    }
                )
            }

            // ══════════════════════ Feature 3: Search Bar ══════════════════════
            if (state.notifications.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = colors.inputBackground
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Search, null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(Spacing.Small))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.textPrimary
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search alerts...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textTertiary
                                    )
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Filled.Close, "Clear", tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ══════════════════════ Feature 4: Type Filter Chips ══════════════════════
            if (state.notifications.isNotEmpty()) {
                val typeFilters = listOf("All", "Traffic", "Parking", "Accident", "Emergency", "Weather", "General")
                LazyRow(
                    modifier = Modifier.padding(vertical = Spacing.Small),
                    contentPadding = PaddingValues(horizontal = Spacing.XLarge),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    items(typeFilters.size) { index ->
                        val filter = typeFilters[index]
                        val isSelected = selectedType == filter
                        val chipColor = if (filter == "All") PrimaryBlue else getTypeColor(filter)
                        Surface(
                            onClick = { selectedType = filter },
                            shape = RoundedCornerShape(CornerRadius.Round),
                            color = if (isSelected) chipColor.copy(alpha = 0.15f)
                            else colors.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                filter,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chipColor else colors.textSecondary
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Feature 5: Time Filter Chips ══════════════════════
            if (state.notifications.isNotEmpty()) {
                val timeFilters = listOf("All Time", "Today", "This Week", "This Month")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge, vertical = Spacing.XSmall),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    timeFilters.forEach { filter ->
                        val isSelected = selectedTime == filter
                        Surface(
                            onClick = { selectedTime = filter },
                            shape = RoundedCornerShape(CornerRadius.Round),
                            color = if (isSelected) PrimaryBlue.copy(alpha = 0.15f)
                            else colors.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                filter,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PrimaryBlue else colors.textTertiary
                            )
                        }
                    }
                }
            }

            // ══════════════════════ Content ══════════════════════
            when {
                state.isLoading && state.notifications.isEmpty() -> {
                    // Shimmer loading
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.XLarge,
                            vertical = Spacing.Small
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                    ) {
                        items(6) { ShimmerNotificationCard() }
                    }
                }

                state.error != null && state.notifications.isEmpty() -> {
                    // Error state
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.ErrorOutline, null,
                                modifier = Modifier.size(56.dp),
                                tint = colors.textTertiary
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            Text(
                                state.error ?: "Something went wrong",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            OutlinedButton(
                                onClick = { vm.retry() },
                                shape = RoundedCornerShape(CornerRadius.Round)
                            ) { Text("Retry") }
                        }
                    }
                }

                state.notifications.isEmpty() -> {
                    // Empty state
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.NotificationsNone, null,
                                modifier = Modifier.size(64.dp),
                                tint = colors.textTertiary
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            Text(
                                "No alerts yet",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                            Text(
                                "You'll be notified about traffic, parking & safety alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }

                filteredNotifications.isEmpty() -> {
                    // No results for current filters
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.FilterList, null,
                                modifier = Modifier.size(48.dp),
                                tint = colors.textTertiary
                            )
                            Spacer(Modifier.height(Spacing.Medium))
                            Text(
                                "No matching alerts",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                            Text(
                                "Try adjusting your filters or search",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = Spacing.XLarge,
                            vertical = Spacing.Small
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                    ) {
                        // Pinned section
                        val pinned = filteredNotifications.filter { it.pinned }
                        if (pinned.isNotEmpty()) {
                            item {
                                Text(
                                    "PINNED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textTertiary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(vertical = Spacing.XSmall)
                                )
                            }
                            items(pinned, key = { "pinned_${it.id}" }) { notification ->
                                SwipeableNotificationCard(
                                    notification = notification,
                                    colors = colors,
                                    isExpanded = expandedId == notification.id,
                                    onExpandToggle = {
                                        try { Firebase.analytics.logEvent("notification_clicked", null) } catch (_: Exception) {}
                                        expandedId = if (expandedId == notification.id) null else notification.id
                                    },
                                    onMarkRead = {
                                        vm.markAsRead(notification.id)
                                        snackbarMsg = "Marked as read"
                                    },
                                    onDelete = {
                                        vm.deleteNotification(notification.id)
                                        snackbarMsg = "Notification deleted"
                                    },
                                    onTogglePin = { vm.togglePin(notification.id) },
                                    onNavigateToMap = onNavigateToMap
                                )
                            }
                        }

                        // All others
                        val others = filteredNotifications.filter { !it.pinned }
                        if (others.isNotEmpty() && pinned.isNotEmpty()) {
                            item {
                                Text(
                                    "ALL ALERTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textTertiary,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(
                                        top = Spacing.Medium,
                                        bottom = Spacing.XSmall
                                    )
                                )
                            }
                        }
                        items(others, key = { it.id }) { notification ->
                            SwipeableNotificationCard(
                                notification = notification,
                                colors = colors,
                                isExpanded = expandedId == notification.id,
                                onExpandToggle = {
                                    try { Firebase.analytics.logEvent("notification_clicked", null) } catch (_: Exception) {}
                                    expandedId = if (expandedId == notification.id) null else notification.id
                                },
                                onMarkRead = {
                                    vm.markAsRead(notification.id)
                                    snackbarMsg = "Marked as read"
                                },
                                onDelete = {
                                    vm.deleteNotification(notification.id)
                                    snackbarMsg = "Notification deleted"
                                },
                                onTogglePin = { vm.togglePin(notification.id) },
                                onNavigateToMap = onNavigateToMap
                            )
                        }

                        // Bottom spacer
                        item { Spacer(Modifier.height(Spacing.Section)) }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Swipeable Expandable Notification Card (Features 6, 7, 9, 10)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableNotificationCard(
    notification: Notification,
    colors: CityFluxColors,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
    onNavigateToMap: (lat: Double, lng: Double) -> Unit
) {
    val context = LocalContext.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 200f
    var showMap by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Delete background (right swipe reveals)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .clip(RoundedCornerShape(CornerRadius.Large))
                .background(AccentRed.copy(alpha = 0.9f))
                .padding(horizontal = Spacing.XLarge),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Delete, "Delete",
                tint = Color.White, modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(Spacing.Small))
            Text("Delete", color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        // Foreground card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -swipeThreshold) {
                                onDelete()
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f }
                    ) { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-300f, 0f)
                    }
                }
                .shadow(
                    if (!notification.read) 4.dp else 2.dp,
                    RoundedCornerShape(CornerRadius.Large),
                    ambientColor = colors.cardShadow,
                    spotColor = colors.cardShadowMedium
                )
                .combinedClickable(
                    onClick = onExpandToggle,
                    onLongClick = onTogglePin
                ),
            shape = RoundedCornerShape(CornerRadius.Large),
            colors = CardDefaults.cardColors(
                containerColor = if (!notification.read)
                    colors.cardBackground
                else
                    colors.surfaceVariant.copy(alpha = 0.7f)
            )
        ) {
            Column {
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    // Left accent bar
                    val typeColor = getTypeColor(notification.type)
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(typeColor)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Medium),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Type icon
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            color = typeColor.copy(alpha = 0.1f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    getTypeIcon(notification.type), null,
                                    tint = typeColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(Spacing.Medium))

                        // Content
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    notification.title.ifBlank { "Alert" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (!notification.read) FontWeight.Bold else FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (notification.pinned) {
                                        Icon(
                                            Icons.Filled.PushPin, "Pinned",
                                            tint = AccentAlerts,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    // Feature 9: Priority badge
                                    val priorityColor = getPriorityColor(notification.priority)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = priorityColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            getPriorityLabel(notification.priority),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = priorityColor,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            fontSize = 8.sp
                                        )
                                    }
                                    // Read/Unread badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (!notification.read)
                                            PrimaryBlue.copy(alpha = 0.12f)
                                        else
                                            colors.surfaceVariant
                                    ) {
                                        Text(
                                            if (!notification.read) "New" else "Read",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (!notification.read) PrimaryBlue else colors.textTertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            if (notification.message.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    notification.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                    overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            // Bottom row: time + location indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Schedule, null,
                                        tint = colors.textTertiary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        formatTimeAgo(notification),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textTertiary
                                    )
                                }

                                if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.LocationOn, null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            "Has location",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Feature 6: Expanded View ──
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = Spacing.XLarge,
                            end = Spacing.Medium,
                            bottom = Spacing.Medium
                        )
                    ) {
                        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                        Spacer(Modifier.height(Spacing.Small))

                        // Full timestamp
                        val fullTime = notification.timestamp?.let { ts ->
                            try {
                                val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                                sdf.format(ts.toDate()) + " · " + formatTimeAgo(notification)
                            } catch (_: Exception) { formatTimeAgo(notification) }
                        } ?: "Just now"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CalendarToday, null, tint = colors.textTertiary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(fullTime, fontSize = 10.sp, color = colors.textTertiary)
                        }

                        Spacer(Modifier.height(Spacing.Medium))

                        // Action buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                        ) {
                            // Mark Read button
                            if (!notification.read) {
                                Surface(
                                    onClick = onMarkRead,
                                    shape = RoundedCornerShape(CornerRadius.Round),
                                    color = PrimaryBlue.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.DoneAll, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                        Text("Mark Read", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                    }
                                }
                            }

                            // Pin/Unpin button
                            Surface(
                                onClick = onTogglePin,
                                shape = RoundedCornerShape(CornerRadius.Round),
                                color = AccentAlerts.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (notification.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                        null, tint = AccentAlerts, modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        if (notification.pinned) "Unpin" else "Pin",
                                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentAlerts
                                    )
                                }
                            }

                            // Feature 10: Share button
                            Surface(
                                onClick = {
                                    val shareText = buildString {
                                        append("⚠\uFE0F CityFlux Alert\n\n")
                                        append("Title: ${notification.title}\n")
                                        append("Type: ${notification.type}\n")
                                        append("Message: ${notification.message}\n")
                                        append("Time: ${notification.timestamp?.let {
                                            try { SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(it.toDate()) } catch (_: Exception) { "Unknown" }
                                        } ?: "Unknown"}")
                                        if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                            append("\nLocation: ${notification.latitude},${notification.longitude}")
                                        }
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, notification.title)
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Alert"))
                                },
                                shape = RoundedCornerShape(CornerRadius.Round),
                                color = AccentGreen.copy(alpha = 0.1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Outlined.Share, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                    Text("Share", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen)
                                }
                            }

                            // View on Map button (if has location)
                            if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                Surface(
                                    onClick = { onNavigateToMap(notification.latitude, notification.longitude) },
                                    shape = RoundedCornerShape(CornerRadius.Round),
                                    color = PrimaryBlue.copy(alpha = 0.1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Outlined.Map, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                        Text("Map", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                                    }
                                }
                            }
                        }

                        // Feature 7: In-app map
                        if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                            Spacer(Modifier.height(Spacing.Small))
                            Surface(
                                onClick = { showMap = !showMap },
                                shape = RoundedCornerShape(CornerRadius.Round),
                                color = colors.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (showMap) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        null, tint = colors.textSecondary, modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        if (showMap) "Hide Map" else "Show Map",
                                        fontSize = 10.sp, color = colors.textSecondary
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showMap,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                val alertPos = LatLng(notification.latitude, notification.longitude)
                                val cameraState = rememberCameraPositionState {
                                    position = CameraPosition.fromLatLngZoom(alertPos, 15f)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .padding(top = Spacing.Small)
                                        .clip(RoundedCornerShape(CornerRadius.Medium))
                                ) {
                                    GoogleMap(
                                        modifier = Modifier.fillMaxSize(),
                                        cameraPositionState = cameraState,
                                        uiSettings = MapUiSettings(
                                            zoomControlsEnabled = false,
                                            scrollGesturesEnabled = false,
                                            zoomGesturesEnabled = false,
                                            tiltGesturesEnabled = false,
                                            rotationGesturesEnabled = false
                                        )
                                    ) {
                                        Marker(
                                            state = MarkerState(position = alertPos),
                                            title = notification.title
                                        )
                                    }
                                    // Type badge overlay
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(Spacing.Small),
                                        shape = RoundedCornerShape(CornerRadius.Small),
                                        color = getTypeColor(notification.type).copy(alpha = 0.9f)
                                    ) {
                                        Text(
                                            notification.type.replaceFirstChar { it.uppercase() },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Expand/collapse hint
                        Spacer(Modifier.height(Spacing.Small))
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                "Collapse",
                                tint = colors.textTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Collapsed expand hint
                if (!isExpanded) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            "Expand",
                            tint = colors.textTertiary.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Shimmer Notification Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerNotificationCard() {
    val colors = MaterialTheme.cityFluxColors
    val shimmerColors = listOf(
        colors.surfaceVariant,
        colors.surfaceVariant.copy(alpha = 0.5f),
        colors.surfaceVariant
    )
    val transition = rememberInfiniteTransition(label = "notif_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "notif_shimmer_translate"
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
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(brush)
            )
            Spacer(Modifier.width(Spacing.Medium))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        Modifier
                            .width(130.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(
                        Modifier
                            .width(70.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                    Box(
                        Modifier
                            .width(80.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush)
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════

private fun getTypeColor(type: String): Color = when (type.lowercase()) {
    "traffic" -> AccentTraffic
    "parking" -> AccentParking
    "accident" -> AccentRed
    "emergency" -> AccentRed
    "weather" -> AccentAlerts
    else -> PrimaryBlue
}

private fun getTypeIcon(type: String): ImageVector = when (type.lowercase()) {
    "traffic" -> Icons.Outlined.Traffic
    "parking" -> Icons.Outlined.LocalParking
    "accident" -> Icons.Outlined.Warning
    "emergency" -> Icons.Outlined.LocalHospital
    "weather" -> Icons.Outlined.Cloud
    else -> Icons.Outlined.Notifications
}

private fun formatTimeAgo(notification: Notification): String {
    val ts = notification.timestamp ?: return "Just now"
    return try {
        DateUtils.getRelativeTimeSpanString(
            ts.toDate().time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    } catch (_: Exception) {
        "Just now"
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}


// ═══════════════════════════════════════════════════════════════════
// Feature 1: Premium Header — Gradient with pulsing icon & LIVE badge
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PremiumNotificationsTopBar(
    colors: CityFluxColors,
    unreadCount: Int,
    totalCount: Int,
    isLoading: Boolean,
    onMarkAllRead: () -> Unit,
    onOpenPreferences: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AccentAlerts.copy(alpha = 0.15f),
                        AccentAlerts.copy(alpha = 0.03f),
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
                // Glowing animated bell icon
                Box(contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "alert_glow")
                    val glowAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alert_glow_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentAlerts.copy(alpha = glowAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentAlerts, AccentAlerts.copy(alpha = 0.8f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Notifications, null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column {
                    Text(
                        "Alerts & Notifications",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AlertPulsingDot(color = AccentGreen, size = 6.dp)
                        Text(
                            if (isLoading) "Loading alerts..."
                            else "$totalCount alerts · $unreadCount unread",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Right side: LIVE badge + settings + mark read
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // LIVE badge
                    Surface(
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = AccentGreen.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AlertPulsingDot(color = AccentGreen, size = 8.dp)
                            Text(
                                "LIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    // Feature 8: Settings icon
                    Surface(
                        onClick = onOpenPreferences,
                        shape = CircleShape,
                        color = colors.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Settings, "Preferences",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                // Mark all as read button
                if (unreadCount > 0) {
                    Surface(
                        onClick = onMarkAllRead,
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = PrimaryBlue.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DoneAll, null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Mark read",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue
                            )
                        }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// AlertPulsingDot — Animated green dot
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AlertPulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "adot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "adotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}


// ═══════════════════════════════════════════════════════════════════
// Feature 2: Stats Strip — 4 mini stat cards
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AlertStatsStrip(
    total: Int,
    unread: Int,
    pinned: Int,
    today: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        AlertStatMiniCard("Total", "$total", Icons.Outlined.Description, PrimaryBlue, Modifier.weight(1f))
        AlertStatMiniCard("Unread", "$unread", Icons.Outlined.MarkEmailUnread, AccentRed, Modifier.weight(1f))
        AlertStatMiniCard("Pinned", "$pinned", Icons.Outlined.PushPin, AccentAlerts, Modifier.weight(1f))
        AlertStatMiniCard("Today", "$today", Icons.Outlined.Today, AccentGreen, Modifier.weight(1f))
    }
}

@Composable
private fun AlertStatMiniCard(
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
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary, maxLines = 1)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Feature 8: Alert Preferences Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AlertPreferencesDialog(
    enabledTypes: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Boolean>) -> Unit
) {
    val mutableTypes = remember { mutableStateOf(enabledTypes.toMutableMap()) }
    val categories = listOf(
        Triple("traffic", "Traffic Alerts", Icons.Outlined.Traffic),
        Triple("parking", "Parking Alerts", Icons.Outlined.LocalParking),
        Triple("accident", "Accident Alerts", Icons.Outlined.Warning),
        Triple("emergency", "Emergency Alerts", Icons.Outlined.LocalHospital),
        Triple("weather", "Weather Alerts", Icons.Outlined.Cloud),
        Triple("general", "General Alerts", Icons.Outlined.Notifications)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, tint = AccentAlerts, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Spacing.Small))
                Text("Alert Preferences", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Choose which alerts to display",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.cityFluxColors.textSecondary
                )
                Spacer(Modifier.height(Spacing.Small))
                categories.forEach { (key, label, icon) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                        ) {
                            Icon(
                                icon, null,
                                tint = getTypeColor(key),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(label, fontSize = 13.sp)
                        }
                        Switch(
                            checked = mutableTypes.value[key] ?: true,
                            onCheckedChange = { checked ->
                                mutableTypes.value = mutableTypes.value.toMutableMap().apply {
                                    this[key] = checked
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = getTypeColor(key)
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mutableTypes.value) }) {
                Text("Save", fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// ═══════════════════════════════════════════════════════════════════
// Feature 9: Priority Helpers
// ═══════════════════════════════════════════════════════════════════

private fun getPriorityColor(priority: String): Color = when (priority.lowercase()) {
    "critical" -> AccentRed
    "high" -> AccentOrange
    "medium" -> PrimaryBlue
    "low" -> AccentGreen
    else -> PrimaryBlue
}

private fun getPriorityLabel(priority: String): String = when (priority.lowercase()) {
    "critical" -> "CRITICAL"
    "high" -> "HIGH"
    "medium" -> "MED"
    "low" -> "LOW"
    else -> "MED"
}
