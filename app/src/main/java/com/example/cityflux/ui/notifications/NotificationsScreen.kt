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
            // ══════════════════════ Header ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Spacing.XLarge, end = Spacing.XLarge,
                        top = Spacing.Large, bottom = Spacing.Small
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Alerts & Notifications",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(Spacing.Small))
                            Surface(
                                shape = CircleShape,
                                color = AccentRed,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (unreadCount > 99) "99+" else unreadCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (state.isLoading) "Loading alerts..."
                        else "${state.notifications.size} alerts · $unreadCount unread",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }

                // Mark all as read
                if (unreadCount > 0) {
                    TextButton(
                        onClick = {
                            try { Firebase.analytics.logEvent("mark_all_read_clicked", null) } catch (_: Exception) {}
                            vm.markAllAsRead()
                            snackbarMsg = "All marked as read"
                        }
                    ) {
                        Icon(
                            Icons.Outlined.DoneAll, null,
                            tint = PrimaryBlue, modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Mark all read",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue
                        )
                    }
                }
            }

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
                        val pinned = state.notifications.filter { it.pinned }
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
                                    onClick = {
                                        try { Firebase.analytics.logEvent("notification_clicked", null) } catch (_: Exception) {}
                                        vm.markAsRead(notification.id)
                                        snackbarMsg = "Marked as read"
                                        if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                            onNavigateToMap(notification.latitude, notification.longitude)
                                        }
                                    },
                                    onDelete = {
                                        vm.deleteNotification(notification.id)
                                        snackbarMsg = "Notification deleted"
                                    },
                                    onTogglePin = { vm.togglePin(notification.id) }
                                )
                            }
                        }

                        // All others
                        val others = state.notifications.filter { !it.pinned }
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
                                onClick = {
                                    try { Firebase.analytics.logEvent("notification_clicked", null) } catch (_: Exception) {}
                                    vm.markAsRead(notification.id)
                                    snackbarMsg = "Marked as read"
                                    if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                        onNavigateToMap(notification.latitude, notification.longitude)
                                    }
                                },
                                onDelete = {
                                    vm.deleteNotification(notification.id)
                                    snackbarMsg = "Notification deleted"
                                },
                                onTogglePin = { vm.togglePin(notification.id) }
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
// Swipeable Notification Card
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableNotificationCard(
    notification: Notification,
    colors: CityFluxColors,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 200f

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
                    onClick = onClick,
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (notification.pinned) {
                                    Icon(
                                        Icons.Filled.PushPin, "Pinned",
                                        tint = AccentAlerts,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        // Bottom row: time + location indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Timestamp
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

                            // Location available indicator
                            if (notification.latitude != 0.0 && notification.longitude != 0.0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.LocationOn, null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(
                                        "View on map",
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
