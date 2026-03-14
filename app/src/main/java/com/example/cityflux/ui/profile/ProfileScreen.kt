package com.example.cityflux.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import android.text.format.DateUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Production-grade ProfileScreen with:
 * - Hero header with circular profile image (Firebase Storage)
 * - Real-time Firestore profile data
 * - Report statistics
 * - Saved places from Firestore sub-collection with swipe-to-delete
 * - Permissions & Access toggles (location, notifications)
 * - App Settings (dark mode toggle, clear cache, version)
 * - Secure logout with FCM token cleanup
 * - Shimmer loading, offline banner, edit profile dialog
 * - Firebase Analytics events
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToMap: (Double, Double) -> Unit = { _, _ -> },
    vm: ProfileViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Analytics ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("profile_opened", null) } catch (_: Exception) {}
    }

    // ── Snackbar ──
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearSnackbar()
        }
    }

    // ── Edit Profile Dialog state ──
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    // ── Logout Confirmation Dialog ──
    var showLogoutDialog by remember { mutableStateOf(false) }

    // ── Help Center Dialog ──
    var showHelpCenter by remember { mutableStateOf(false) }

    // ── Image Picker ──
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.uploadProfileImage(it) }
    }

    // ── Permission checks ──
    var locationGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(false) }

    // ── Settings state (SharedPreferences) ──
    val prefs = remember { context.getSharedPreferences("profile_settings", android.content.Context.MODE_PRIVATE) }
    var alertSoundsEnabled by remember { mutableStateOf(prefs.getBoolean("alert_sounds", true)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("vibration", true)) }

    LaunchedEffect(Unit) {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        notificationGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        cameraGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.cardBackground,
                    contentColor = colors.textPrimary,
                    actionColor = PrimaryBlue,
                    shape = RoundedCornerShape(CornerRadius.Medium)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                // ═══════════════ Shimmer Loading ═══════════════
                ShimmerProfileContent()
            } else if (state.error != null && state.user == null) {
                // ═══════════════ Error State ═══════════════
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Section),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(Spacing.Large))
                    Text(
                        text = state.error ?: "Something went wrong",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.XLarge))
                    PrimaryButton(
                        text = "Retry",
                        onClick = { vm.retry() },
                        modifier = Modifier.width(160.dp)
                    )
                }
            } else {
                // ═══════════════ Profile Content ═══════════════
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                ) {
                    // ── Offline Banner ──
                    AnimatedVisibility(
                        visible = state.isOffline,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = AccentAlerts.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.WifiOff,
                                    contentDescription = null,
                                    tint = AccentAlerts,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(Spacing.Small))
                                Text(
                                    "No internet connection",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentAlerts,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // ═══════════════════════════════════════════════
                    // 1) HERO PROFILE HEADER
                    // ═══════════════════════════════════════════════
                    ProfileHeader(
                        user = state.user,
                        isUploadingImage = state.isUploadingImage,
                        uploadProgress = state.uploadProgress,
                        onImageClick = {
                            try { Firebase.analytics.logEvent("edit_profile_clicked", null) } catch (_: Exception) {}
                            imagePickerLauncher.launch("image/*")
                        },
                        onEditClick = {
                            try { Firebase.analytics.logEvent("edit_profile_clicked", null) } catch (_: Exception) {}
                            editName = state.user?.name ?: ""
                            editPhone = state.user?.phone ?: ""
                            showEditDialog = true
                        }
                    )

                    Spacer(Modifier.height(Spacing.Medium))

                    // ═══════════════════════════════════════════════
                    // 2) STATS ROW
                    // ═══════════════════════════════════════════════
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.XLarge),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                    ) {
                        ProfileStatCard(
                            label = "Total",
                            value = state.totalReports.toString(),
                            color = PrimaryBlue,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileStatCard(
                            label = "Resolved",
                            value = state.resolvedReports.toString(),
                            color = AccentParking,
                            modifier = Modifier.weight(1f)
                        )
                        ProfileStatCard(
                            label = "Pending",
                            value = state.pendingReports.toString(),
                            color = AccentAlerts,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 2b) WEEKLY ACTIVITY CHART
                    // ═══════════════════════════════════════════════
                    WeeklyActivityChart(weeklyStats = state.weeklyStats)

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 2c) TODAY'S SNAPSHOT
                    // ═══════════════════════════════════════════════
                    TodaySnapshot(
                        todayReports = state.todayReports,
                        todayResolved = state.todayResolved,
                        citizenScore = state.citizenScore
                    )

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 2d) QUICK ACTIONS
                    // ═══════════════════════════════════════════════
                    QuickActionsSection(
                        context = context,
                        onHelpCenter = { showHelpCenter = true }
                    )

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 3) SAVED PLACES CARD
                    // ═══════════════════════════════════════════════
                    if (state.savedPlaces.isNotEmpty()) {
                        ProfileSection(title = "Saved Places", icon = Icons.Outlined.Bookmark) {
                            state.savedPlaces.forEach { place ->
                                SavedPlaceItem(
                                    place = place,
                                    onTap = {
                                        try {
                                            val bundle = android.os.Bundle().apply {
                                                putString("place_name", place.name)
                                            }
                                            Firebase.analytics.logEvent("saved_place_clicked", bundle)
                                        } catch (_: Exception) {}
                                        onNavigateToMap(place.latitude, place.longitude)
                                    },
                                    onDelete = { vm.deleteSavedPlace(place.id) }
                                )
                            }
                        }
                        Spacer(Modifier.height(Spacing.XXLarge))
                    }

                    // ═══════════════════════════════════════════════
                    // 3b) RECENT ACTIVITY
                    // ═══════════════════════════════════════════════
                    RecentActivitySection(activities = state.recentActivities)

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 3c) ACHIEVEMENTS
                    // ═══════════════════════════════════════════════
                    AchievementsSection(citizenScore = state.citizenScore)

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 3d) EMERGENCY CONTACTS
                    // ═══════════════════════════════════════════════
                    EmergencyContactsSection(context = context)

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 4) PERMISSIONS & ACCESS
                    // ═══════════════════════════════════════════════
                    ProfileSection(title = "Permissions & Access", icon = Icons.Outlined.Security) {
                        PermissionToggleRow(
                            title = "Location Access",
                            description = if (locationGranted) "Granted" else "Denied",
                            icon = Icons.Outlined.LocationOn,
                            isGranted = locationGranted,
                            onToggle = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        PermissionToggleRow(
                            title = "Notifications",
                            description = if (notificationGranted) "Granted" else "Denied",
                            icon = Icons.Outlined.Notifications,
                            isGranted = notificationGranted,
                            onToggle = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        PermissionToggleRow(
                            title = "Camera",
                            description = if (cameraGranted) "Granted" else "Denied",
                            icon = Icons.Outlined.CameraAlt,
                            isGranted = cameraGranted,
                            onToggle = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 5) APP SETTINGS
                    // ═══════════════════════════════════════════════
                    ProfileSection(title = "App Settings", icon = Icons.Outlined.Settings) {
                        SettingsInfoRow(
                            icon = Icons.Outlined.DarkMode,
                            title = "Dark Mode",
                            trailing = {
                                Text(
                                    text = if (colors.isDark) "On" else "Off",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        SettingsInfoRow(
                            icon = Icons.Outlined.VolumeUp,
                            title = "Alert Sounds",
                            trailing = {
                                Switch(
                                    checked = alertSoundsEnabled,
                                    onCheckedChange = {
                                        alertSoundsEnabled = it
                                        prefs.edit().putBoolean("alert_sounds", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        SettingsInfoRow(
                            icon = Icons.Outlined.Vibration,
                            title = "Vibration",
                            trailing = {
                                Switch(
                                    checked = vibrationEnabled,
                                    onCheckedChange = {
                                        vibrationEnabled = it
                                        prefs.edit().putBoolean("vibration", it).apply()
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue)
                                )
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        SettingsInfoRow(
                            icon = Icons.Outlined.DeleteSweep,
                            title = "Clear Cache",
                            trailing = {
                                TextButton(
                                    onClick = {
                                        try {
                                            context.cacheDir.deleteRecursively()
                                            vm.clearSnackbar()
                                        } catch (_: Exception) {}
                                        // Trigger snackbar via ViewModel pattern
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "Clear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        )

                        HorizontalDivider(
                            color = colors.divider,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = Spacing.Small)
                        )

                        SettingsInfoRow(
                            icon = Icons.Outlined.Info,
                            title = "App Version",
                            trailing = {
                                val versionName = try {
                                    context.packageManager
                                        .getPackageInfo(context.packageName, 0).versionName
                                } catch (_: Exception) { "1.0.0" }
                                Text(
                                    text = versionName ?: "1.0.0",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textTertiary
                                )
                            }
                        )
                    }

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 5b) DATA & STORAGE
                    // ═══════════════════════════════════════════════
                    DataStorageSection(
                        context = context,
                        snackbarHostState = snackbarHostState,
                        state = state
                    )

                    Spacer(Modifier.height(Spacing.XXLarge))

                    // ═══════════════════════════════════════════════
                    // 5c) APP INFO
                    // ═══════════════════════════════════════════════
                    AppInfoCard(
                        context = context,
                        snackbarHostState = snackbarHostState
                    )

                    Spacer(Modifier.height(Spacing.Section))

                    // ═══════════════════════════════════════════════
                    // 6) LOGOUT
                    // ═══════════════════════════════════════════════
                    Column(
                        modifier = Modifier.padding(horizontal = Spacing.XLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                try { Firebase.analytics.logEvent("logout_clicked", null) } catch (_: Exception) {}
                                showLogoutDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentRed.copy(alpha = 0.1f),
                                contentColor = AccentRed
                            ),
                            elevation = ButtonDefaults.buttonElevation(0.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(Spacing.Small))
                            Text(
                                "Sign Out",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(Modifier.height(Spacing.Medium))

                        Text(
                            text = "Logged in securely via Firebase.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }

                    Spacer(Modifier.height(Spacing.Section))
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // EDIT PROFILE DIALOG
    // ═══════════════════════════════════════════════════════
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            title = {
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    AppTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = "Full Name"
                    )
                    AppTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = "Phone Number"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.updateProfile(editName, editPhone)
                        showEditDialog = false
                    }
                ) {
                    Text(
                        "Save",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(
                        "Cancel",
                        color = colors.textSecondary
                    )
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════
    // LOGOUT CONFIRMATION DIALOG
    // ═══════════════════════════════════════════════════════
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            icon = {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Sign Out",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    "Are you sure you want to sign out? Your FCM token will be removed and you'll need to sign in again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        vm.logout(onLogout)
                    }
                ) {
                    Text(
                        "Sign Out",
                        color = AccentRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        "Cancel",
                        color = colors.textSecondary
                    )
                }
            }
        )
    }

    // ═══════════════════════════════════════════════════════
    // HELP CENTER DIALOG
    // ═══════════════════════════════════════════════════════
    if (showHelpCenter) {
        AlertDialog(
            onDismissRequest = { showHelpCenter = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            icon = {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Help Center",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    HelpItem(
                        question = "How do I report an issue?",
                        answer = "Go to the Report tab, tap '+' button, select the issue type, add photos and description, then submit."
                    )
                    HelpItem(
                        question = "How do I track my report?",
                        answer = "In the Report tab, tap 'My Reports' to see all your submissions with real-time status updates."
                    )
                    HelpItem(
                        question = "How does Citizen Score work?",
                        answer = "You earn 10 points for each report submitted and 25 bonus points when a report gets resolved. Unlock badges as you reach milestones!"
                    )
                    HelpItem(
                        question = "How do I find parking?",
                        answer = "Use the Parking tab to see nearby available spots with real-time occupancy data and navigation."
                    )
                    HelpItem(
                        question = "What are alerts?",
                        answer = "Alerts notify you about traffic updates, emergencies, and city announcements in your area. Customize them in Alert Preferences."
                    )
                    HelpItem(
                        question = "How to contact support?",
                        answer = "Email us at support@cityflux.app or use the Emergency Contacts section for urgent matters."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpCenter = false }) {
                    Text(
                        "Got it",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

@Composable
private fun ProfileHeader(
    user: com.example.cityflux.model.User?,
    isUploadingImage: Boolean,
    uploadProgress: Float,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "header_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    val activeBadgeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_badge_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PrimaryBlue.copy(alpha = 0.15f),
                        PrimaryBlue.copy(alpha = 0.03f),
                        Color.Transparent
                    )
                )
            )
            .padding(vertical = Spacing.XXLarge, horizontal = Spacing.XLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ── Profile Image with Glow Ring ──
            Box(contentAlignment = Alignment.Center) {
                val hasImage = !user?.profileImageUrl.isNullOrEmpty()

                // Glowing outer ring
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .clip(CircleShape)
                        .border(3.dp, PrimaryBlue.copy(alpha = glowAlpha), CircleShape)
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(12.dp, CircleShape, ambientColor = PrimaryBlue.copy(alpha = 0.3f))
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.1f))
                        .border(2.5.dp, PrimaryBlue.copy(alpha = 0.4f), CircleShape)
                        .clickable { onImageClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (hasImage) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(user?.profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = (user?.name ?: "U").take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }

                // Upload progress ring
                if (isUploadingImage) {
                    CircularProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.size(118.dp),
                        color = PrimaryBlue,
                        strokeWidth = 3.dp,
                        trackColor = PrimaryBlue.copy(alpha = 0.1f)
                    )
                }

                // Camera badge
                if (!isUploadingImage) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                            .border(2.dp, colors.cardBackground, CircleShape)
                            .clickable { onImageClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Change photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // ACTIVE pulsing green badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = 4.dp),
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = AccentGreen.copy(alpha = activeBadgeAlpha)
                ) {
                    Text(
                        text = "ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // ── Name ──
            Text(
                text = user?.name?.ifEmpty { "User" } ?: "User",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(Spacing.XSmall))

            // ── Role Badge ──
            Surface(
                shape = RoundedCornerShape(CornerRadius.Round),
                color = PrimaryBlue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = (user?.role ?: "Citizen").replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            // ── Contact Info ──
            if (!user?.email.isNullOrEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.XSmall))
                    Text(
                        text = user?.email ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            if (!user?.phone.isNullOrEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.XSmall))
                    Text(
                        text = user?.phone ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            // ── Member since ──
            user?.createdAt?.let { ts ->
                Spacer(Modifier.height(Spacing.Small))
                val formatted = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(ts.toDate())
                Text(
                    text = "Member since $formatted",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            // ── Edit Profile Button ──
            OutlinedButton(
                onClick = onEditClick,
                shape = RoundedCornerShape(CornerRadius.Round),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(Spacing.XSmall))
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// PROFILE SECTION — Reusable card wrapper for sections
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Column(modifier = Modifier.padding(horizontal = Spacing.XLarge)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Spacing.Medium)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }

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
            Column(
                modifier = Modifier.padding(Spacing.Large),
                content = content
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// STAT CARD
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ProfileStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Medium, horizontal = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// SAVED PLACE ITEM — swipe-to-delete
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPlaceItem(
    place: com.example.cityflux.model.SavedPlace,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else false
        },
        positionalThreshold = { it * 0.3f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(AccentRed.copy(alpha = 0.15f))
                    .padding(end = Spacing.Large),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = AccentRed,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(vertical = Spacing.XSmall)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadius.Medium))
                .clickable { onTap() },
            color = colors.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(CornerRadius.Medium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Location icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(CornerRadius.Small))
                        .background(PrimaryBlue.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(Spacing.Medium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name.ifEmpty { "Saved Location" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (place.address.isNotEmpty()) {
                        Text(
                            text = place.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// PERMISSION TOGGLE ROW
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PermissionToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(CornerRadius.Small))
                .background(
                    if (isGranted) AccentParking.copy(alpha = 0.08f)
                    else AccentRed.copy(alpha = 0.08f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isGranted) AccentParking else AccentRed,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(Spacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isGranted) AccentParking else AccentRed
            )
        }

        Surface(
            shape = RoundedCornerShape(CornerRadius.Small),
            color = if (isGranted) AccentParking.copy(alpha = 0.12f)
            else AccentRed.copy(alpha = 0.12f)
        ) {
            Text(
                text = if (isGranted) "Granted" else "Denied",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isGranted) AccentParking else AccentRed,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// SETTINGS INFO ROW
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SettingsInfoRow(
    icon: ImageVector,
    title: String,
    trailing: @Composable () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(CornerRadius.Small))
                .background(PrimaryBlue.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(Spacing.Medium))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        trailing()
    }
}


// ═══════════════════════════════════════════════════════════════════
// SHIMMER PROFILE — Full-page skeleton
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerProfileContent() {
    val colors = MaterialTheme.cityFluxColors
    val shimmerColors = listOf(
        colors.surfaceVariant,
        colors.surfaceVariant.copy(alpha = 0.5f),
        colors.surfaceVariant
    )
    val transition = rememberInfiniteTransition(label = "profile_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "profile_shimmer_translate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(vertical = Spacing.XXLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar shimmer
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(Modifier.height(Spacing.Medium))

        // Name shimmer
        Box(
            Modifier
                .width(150.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )
        Spacer(Modifier.height(Spacing.Small))

        // Role badge shimmer
        Box(
            Modifier
                .width(80.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(CornerRadius.Round))
                .background(brush)
        )
        Spacer(Modifier.height(Spacing.Small))

        // Email shimmer
        Box(
            Modifier
                .width(180.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(brush)
        )

        Spacer(Modifier.height(Spacing.XXLarge))

        // Stats row shimmer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
        ) {
            repeat(3) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.Medium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier
                                .width(40.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .width(50.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.XXLarge))

        // Section shimmer cards
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small)
                    .height(80.dp),
                shape = RoundedCornerShape(CornerRadius.Large),
                colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.Large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(CornerRadius.Small))
                            .background(brush)
                    )
                    Spacer(Modifier.width(Spacing.Medium))
                    Column {
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush)
                        )
                        Spacer(Modifier.height(6.dp))
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

        Spacer(Modifier.height(Spacing.Section))

        // Logout button shimmer
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge)
                .height(52.dp)
                .clip(RoundedCornerShape(CornerRadius.Medium))
                .background(brush)
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// WEEKLY ACTIVITY CHART — Bar chart for last 7 days
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun WeeklyActivityChart(weeklyStats: List<Int>) {
    val colors = MaterialTheme.cityFluxColors
    val stats = if (weeklyStats.size >= 7) weeklyStats.takeLast(7)
                else weeklyStats + List(7 - weeklyStats.size) { 0 }
    val maxValue = stats.maxOrNull()?.coerceAtLeast(1) ?: 1

    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val labels = (0..6).map { i ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, i - 6)
        dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
    }

    ProfileSection(title = "Weekly Activity", icon = Icons.Outlined.BarChart) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.XSmall),
            verticalAlignment = Alignment.Bottom
        ) {
            stats.forEachIndexed { index, value ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    val fraction = value.toFloat() / maxValue
                    val animatedHeight by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(600),
                        label = "bar_$index"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height((100 * animatedHeight).dp.coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(PrimaryBlue)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = labels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// TODAY'S SNAPSHOT — 3 mini stat cards
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun TodaySnapshot(todayReports: Int, todayResolved: Int, citizenScore: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XLarge),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        MiniStatCard("Today's\nReports", todayReports.toString(), PrimaryBlue, Icons.Outlined.Description, Modifier.weight(1f))
        MiniStatCard("Resolved\nToday", todayResolved.toString(), AccentGreen, Icons.Outlined.CheckCircle, Modifier.weight(1f))
        MiniStatCard("Citizen\nScore", citizenScore.toString(), AccentOrange, Icons.Outlined.Star, Modifier.weight(1f))
    }
}

@Composable
private fun MiniStatCard(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    Card(
        modifier = modifier.shadow(
            2.dp,
            RoundedCornerShape(CornerRadius.Medium),
            ambientColor = colors.cardShadow
        ),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                fontSize = 9.sp
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// QUICK ACTIONS — SOS, Emergency, Help Center
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsSection(
    context: android.content.Context,
    onHelpCenter: () -> Unit
) {
    ProfileSection(title = "Quick Actions", icon = Icons.Outlined.Speed) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickActionButton("SOS", Icons.Outlined.Phone, AccentRed) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
            }
            QuickActionButton("Emergency", Icons.Outlined.LocalHospital, AccentOrange) {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:100")))
            }
            QuickActionButton("Help Center", Icons.Outlined.HelpOutline, PrimaryBlue) {
                onHelpCenter()
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(Spacing.XSmall))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.cityFluxColors.textSecondary
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// RECENT ACTIVITY — Timeline layout
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RecentActivitySection(activities: List<ProfileViewModel.RecentActivity>) {
    ProfileSection(title = "Recent Activity", icon = Icons.Outlined.History) {
        if (activities.isEmpty()) {
            Text(
                "No recent activity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.cityFluxColors.textTertiary,
                modifier = Modifier.padding(vertical = Spacing.Medium)
            )
        } else {
            val items = activities.take(5)
            items.forEachIndexed { index, activity ->
                RecentActivityItem(
                    activity = activity,
                    isLast = index == items.lastIndex
                )
            }
        }
    }
}

@Composable
private fun RecentActivityItem(activity: ProfileViewModel.RecentActivity, isLast: Boolean) {
    val colors = MaterialTheme.cityFluxColors
    val statusColor = when (activity.status.lowercase()) {
        "pending" -> AccentOrange
        "in progress", "in_progress" -> PrimaryBlue
        "resolved" -> AccentGreen
        else -> colors.textTertiary
    }
    val typeIcon = when (activity.type.lowercase()) {
        "traffic" -> Icons.Outlined.DirectionsCar
        "parking" -> Icons.Outlined.LocalParking
        "issue", "issues" -> Icons.Outlined.Warning
        else -> Icons.Outlined.Description
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.XSmall)
    ) {
        // Timeline dot + line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(colors.divider)
                )
            }
        }
        Spacer(Modifier.width(Spacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(Spacing.XSmall))
                Text(
                    activity.title.ifEmpty { "Report" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        activity.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.Small))
                activity.timestamp?.let { ts ->
                    val relativeTime = DateUtils.getRelativeTimeSpanString(
                        ts.toDate().time,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE
                    )
                    Text(
                        relativeTime.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// ACHIEVEMENTS — Gamification badges
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AchievementsSection(citizenScore: Int) {
    val colors = MaterialTheme.cityFluxColors

    data class Badge(val emoji: String, val name: String, val threshold: Int)

    val badges = listOf(
        Badge("\uD83C\uDF31", "First Report", 10),
        Badge("\uD83D\uDD25", "Active Citizen", 100),
        Badge("\u2B50", "Star Reporter", 250),
        Badge("\uD83C\uDFC5", "City Guardian", 500),
        Badge("\uD83D\uDC51", "Legend", 1000)
    )

    val nextBadge = badges.firstOrNull { it.threshold > citizenScore }
    val progress = if (nextBadge != null) {
        val prevThreshold = badges.lastOrNull { it.threshold <= citizenScore }?.threshold ?: 0
        (citizenScore - prevThreshold).toFloat() / (nextBadge.threshold - prevThreshold)
    } else 1f

    ProfileSection(title = "Achievements", icon = Icons.Outlined.EmojiEvents) {
        // Score display
        Text(
            text = "\uD83C\uDFC6 $citizenScore points",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(bottom = Spacing.Medium)
        )

        // Progress to next badge
        if (nextBadge != null) {
            Text(
                text = "Next: ${nextBadge.name} (${nextBadge.threshold} pts)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
            Spacer(Modifier.height(Spacing.XSmall))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryBlue,
                trackColor = colors.surfaceVariant
            )
            Spacer(Modifier.height(Spacing.Medium))
        }

        // Badge list
        badges.forEach { badge ->
            val unlocked = citizenScore >= badge.threshold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.XSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(badge.emoji, fontSize = 24.sp)
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        badge.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (unlocked) colors.textPrimary else colors.textTertiary
                    )
                    Text(
                        "${badge.threshold} pts",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = if (unlocked) AccentGreen.copy(alpha = 0.12f) else colors.surfaceVariant
                ) {
                    Text(
                        if (unlocked) "Unlocked" else "Locked",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (unlocked) AccentGreen else colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// EMERGENCY CONTACTS — Dial emergency numbers
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EmergencyContactsSection(context: android.content.Context) {
    val colors = MaterialTheme.cityFluxColors

    data class EmergencyContact(val name: String, val number: String, val icon: ImageVector)

    val contacts = listOf(
        EmergencyContact("Police Control", "100", Icons.Outlined.Security),
        EmergencyContact("Fire Brigade", "101", Icons.Outlined.Whatshot),
        EmergencyContact("Ambulance", "102", Icons.Outlined.LocalHospital),
        EmergencyContact("Emergency", "112", Icons.Outlined.Phone)
    )

    ProfileSection(title = "Emergency Contacts", icon = Icons.Outlined.Phone) {
        contacts.forEachIndexed { index, contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(CornerRadius.Small))
                        .background(AccentRed.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        contact.icon,
                        contentDescription = null,
                        tint = AccentRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary
                    )
                    Text(
                        contact.number,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
                IconButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                    )
                }) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = "Call ${contact.name}",
                        tint = AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (index < contacts.lastIndex) {
                HorizontalDivider(
                    color = colors.divider,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = Spacing.Small)
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// DATA & STORAGE — Cache management & export
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DataStorageSection(
    context: android.content.Context,
    snackbarHostState: SnackbarHostState,
    state: ProfileViewModel.ProfileUiState
) {
    val colors = MaterialTheme.cityFluxColors
    val scope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        cacheSize = calculateDirSize(context.cacheDir)
    }

    val cacheMB = cacheSize / (1024f * 1024f)
    val maxCacheMB = 100f

    ProfileSection(title = "Data & Storage", icon = Icons.Outlined.Storage) {
        // Cache size display
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cache",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
                Text(
                    "%.1f MB used".format(cacheMB),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary
                )
            }
        }
        Spacer(Modifier.height(Spacing.Small))
        LinearProgressIndicator(
            progress = { (cacheMB / maxCacheMB).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (cacheMB > 50) AccentOrange else PrimaryBlue,
            trackColor = colors.surfaceVariant
        )

        Spacer(Modifier.height(Spacing.Medium))
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

        // Clear Cache
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        context.cacheDir.deleteRecursively()
                        cacheSize = 0L
                    } catch (_: Exception) {
                    }
                    scope.launch { snackbarHostState.showSnackbar("Cache cleared") }
                }
                .padding(vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.DeleteSweep,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Medium))
            Text(
                "Clear Cache",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }

        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

        // Export My Data — real user data
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val user = state.user
                    val exportData = buildString {
                        appendLine("═══════════════════════════════════")
                        appendLine("   CityFlux — My Data Export")
                        appendLine("═══════════════════════════════════")
                        appendLine()
                        appendLine("👤 Profile")
                        appendLine("   Name: ${user?.name ?: "N/A"}")
                        appendLine("   Email: ${user?.email ?: "N/A"}")
                        appendLine("   Phone: ${user?.phone ?: "N/A"}")
                        appendLine("   Role: ${user?.role ?: "Citizen"}")
                        user?.createdAt?.let {
                            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            appendLine("   Member since: ${fmt.format(it.toDate())}")
                        }
                        appendLine()
                        appendLine("📊 Report Statistics")
                        appendLine("   Total Reports: ${state.totalReports}")
                        appendLine("   Resolved: ${state.resolvedReports}")
                        appendLine("   In Progress: ${state.inProgressReports}")
                        appendLine("   Pending: ${state.pendingReports}")
                        appendLine()
                        appendLine("📈 Today's Activity")
                        appendLine("   Reports Today: ${state.todayReports}")
                        appendLine("   Resolved Today: ${state.todayResolved}")
                        appendLine()
                        appendLine("🏆 Citizen Score: ${state.citizenScore} points")
                        appendLine()
                        appendLine("📅 Weekly Activity (last 7 days)")
                        val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        state.weeklyStats.takeLast(7).forEachIndexed { i, count ->
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.DAY_OF_YEAR, i - 6)
                            val day = dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
                            appendLine("   $day: $count reports")
                        }
                        appendLine()
                        if (state.savedPlaces.isNotEmpty()) {
                            appendLine("📍 Saved Places (${state.savedPlaces.size})")
                            state.savedPlaces.forEach { place ->
                                appendLine("   • ${place.name.ifEmpty { "Unnamed" }} — ${place.address}")
                            }
                            appendLine()
                        }
                        if (state.recentActivities.isNotEmpty()) {
                            appendLine("📋 Recent Activity")
                            state.recentActivities.forEach { activity ->
                                appendLine("   • ${activity.title} — ${activity.status}")
                            }
                            appendLine()
                        }
                        appendLine("───────────────────────────────────")
                        appendLine("Exported from CityFlux App")
                        val now = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(Date())
                        appendLine("Date: $now")
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "CityFlux — My Data Export")
                        putExtra(Intent.EXTRA_TEXT, exportData)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export Data"))
                }
                .padding(vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.Medium))
            Text(
                "Export My Data",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }
    }
}

private fun calculateDirSize(dir: java.io.File): Long {
    var size = 0L
    dir.listFiles()?.forEach { file ->
        size += if (file.isDirectory) calculateDirSize(file) else file.length()
    }
    return size
}


// ═══════════════════════════════════════════════════════════════════
// APP INFO CARD — Version, policies, rating
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AppInfoCard(
    context: android.content.Context,
    snackbarHostState: SnackbarHostState
) {
    val colors = MaterialTheme.cityFluxColors
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showTermsOfService by remember { mutableStateOf(false) }
    var showRateApp by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableIntStateOf(0) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    ProfileSection(title = "App Info", icon = Icons.Outlined.Info) {
        SettingsInfoRow(
            icon = Icons.Outlined.Info,
            title = "Version",
            trailing = {
                Text(
                    versionName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary
                )
            }
        )

        HorizontalDivider(
            color = colors.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = Spacing.Small)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPrivacyPolicy = true }
                .padding(vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.Medium))
            Text("Privacy Policy", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }

        HorizontalDivider(
            color = colors.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = Spacing.Small)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showTermsOfService = true }
                .padding(vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.Medium))
            Text("Terms of Service", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }

        HorizontalDivider(
            color = colors.divider,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = Spacing.Small)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    selectedRating = 0
                    showRateApp = true
                }
                .padding(vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Star, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.Medium))
            Text("Rate App", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(Spacing.Medium))

        Text(
            "\u00A9 2026 CityFlux. All rights reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // ── Privacy Policy Dialog ──
    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            icon = { Icon(Icons.Outlined.Security, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp)) },
            title = {
                Text("Privacy Policy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val sections = listOf(
                        "1. Information We Collect" to "CityFlux collects your name, email, phone number, and location data to provide city services. Report data including photos, descriptions, and GPS coordinates are stored to process your submissions.",
                        "2. How We Use Your Data" to "Your data is used to: process issue reports, send relevant city alerts, display nearby parking availability, calculate your citizen score, and improve our services. We never sell your personal data to third parties.",
                        "3. Location Data" to "We collect location data only when the app is in use, to show nearby services, enable map features, and tag report locations accurately. You can revoke location access anytime from device settings.",
                        "4. Data Storage & Security" to "All data is stored securely on Firebase (Google Cloud) servers with encryption at rest and in transit. Profile images are stored in Firebase Storage with secure access rules.",
                        "5. Data Retention" to "Your account data is retained as long as your account is active. Reports are kept for city records. You can export or delete your data anytime from the Profile > Data & Storage section.",
                        "6. Third-Party Services" to "We use Google Maps for navigation, Firebase for authentication and storage, and Open-Meteo for weather data. Each service has its own privacy policy.",
                        "7. Your Rights" to "You have the right to: access your data (Export My Data), correct your information (Edit Profile), delete your account, and opt out of notifications (Alert Preferences).",
                        "8. Contact Us" to "For privacy concerns, contact us at privacy@cityflux.app"
                    )
                    sections.forEach { (title, body) ->
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        Spacer(Modifier.height(Spacing.Medium))
                    }
                    Text("Last updated: March 2026", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) {
                    Text("Close", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Terms of Service Dialog ──
    if (showTermsOfService) {
        AlertDialog(
            onDismissRequest = { showTermsOfService = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            icon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp)) },
            title = {
                Text("Terms of Service", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val sections = listOf(
                        "1. Acceptance of Terms" to "By using CityFlux, you agree to these Terms of Service. If you do not agree, please discontinue use of the application.",
                        "2. User Accounts" to "You must provide accurate information during registration. You are responsible for maintaining the security of your account credentials. One account per person is allowed.",
                        "3. Acceptable Use" to "You agree to: submit truthful and accurate reports, not misuse the SOS or emergency features, not upload inappropriate or offensive content, and not attempt to manipulate the citizen score system.",
                        "4. Report Submissions" to "Reports you submit become part of the city's public record system. City authorities may act on, reassign, or close reports at their discretion. False reports may result in account suspension.",
                        "5. Citizen Score & Badges" to "Citizen scores and badges are awarded based on your participation. They are for engagement purposes and do not guarantee any privileges or rewards beyond the app.",
                        "6. Content Ownership" to "You retain ownership of photos and content you submit. By submitting, you grant CityFlux a license to use this content for processing reports and improving city services.",
                        "7. Service Availability" to "CityFlux is provided \"as-is\". We strive for uptime but do not guarantee uninterrupted service. Features may be modified or discontinued with notice.",
                        "8. Termination" to "We reserve the right to suspend or terminate accounts that violate these terms. You may delete your account at any time from profile settings.",
                        "9. Limitation of Liability" to "CityFlux is not liable for actions taken or not taken by city authorities based on submitted reports. Emergency situations should always be reported via official emergency services (112).",
                        "10. Contact" to "For questions about these terms, email legal@cityflux.app"
                    )
                    sections.forEach { (title, body) ->
                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        Spacer(Modifier.height(Spacing.Medium))
                    }
                    Text("Last updated: March 2026", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsOfService = false }) {
                    Text("Close", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Rate App Dialog ──
    if (showRateApp) {
        val ratingColor = when (selectedRating) {
            1 -> AccentRed
            2 -> AccentOrange
            3 -> AccentYellow
            4 -> AccentGreen
            5 -> PrimaryBlue
            else -> colors.textTertiary
        }
        val ratingLabel = when (selectedRating) {
            1 -> "Poor"
            2 -> "Fair"
            3 -> "Good"
            4 -> "Very Good"
            5 -> "Excellent"
            else -> "Tap a star to rate"
        }
        AlertDialog(
            onDismissRequest = { showRateApp = false },
            containerColor = colors.cardBackground,
            shape = RoundedCornerShape(CornerRadius.XLarge),
            icon = { Icon(Icons.Filled.Star, contentDescription = null, tint = ratingColor, modifier = Modifier.size(40.dp)) },
            title = {
                Text("Rate CityFlux", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Enjoying CityFlux? Your rating helps us improve and reach more citizens!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(Spacing.Large))
                    // Interactive star rating (1-5)
                    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        repeat(5) { index ->
                            val star = index + 1
                            IconButton(onClick = { selectedRating = star }) {
                                Icon(
                                    imageVector = if (star <= selectedRating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Rate $star",
                                    tint = if (star <= selectedRating) ratingColor else colors.textTertiary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.Medium))
                    Text(
                        if (selectedRating > 0) "$selectedRating/5 - $ratingLabel" else ratingLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ratingColor
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRateApp = false
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
                    } catch (_: Exception) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                    }
                }, enabled = selectedRating > 0) {
                    Text("Rate on Play Store", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRateApp = false }) {
                    Text("Maybe Later", color = colors.textSecondary)
                }
            }
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// HELP ITEM — FAQ row for Help Center dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun HelpItem(question: String, answer: String) {
    val colors = MaterialTheme.cityFluxColors
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Medium))
            .background(colors.surfaceVariant.copy(alpha = 0.5f))
            .clickable { expanded = !expanded }
            .padding(Spacing.Medium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.QuestionAnswer,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.Small, start = 26.dp)
            )
        }
    }
}
