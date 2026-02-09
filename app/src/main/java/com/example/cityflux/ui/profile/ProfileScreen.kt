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

    // ── Image Picker ──
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { vm.uploadProfileImage(it) }
    }

    // ── Permission checks ──
    var locationGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        notificationGranted = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
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
}


// ═══════════════════════════════════════════════════════════════════
// PROFILE HEADER — Hero section with avatar, name, badge
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeader(
    user: com.example.cityflux.model.User?,
    isUploadingImage: Boolean,
    uploadProgress: Float,
    onImageClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PrimaryBlue.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                )
            )
            .padding(vertical = Spacing.XXLarge, horizontal = Spacing.XLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ── Profile Image ──
            Box(contentAlignment = Alignment.Center) {
                val hasImage = !user?.profileImageUrl.isNullOrEmpty()

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .shadow(8.dp, CircleShape, ambientColor = PrimaryBlue.copy(alpha = 0.2f))
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
                        modifier = Modifier.size(104.dp),
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
