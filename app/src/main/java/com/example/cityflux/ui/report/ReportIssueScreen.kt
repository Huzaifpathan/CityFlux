package com.example.cityflux.ui.report

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// ReportIssueScreen — Production-grade citizen report submission
// with "New Report" / "My Reports" tab toggle
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    @Suppress("UNUSED_PARAMETER") onReportSubmitted: () -> Unit,
    vm: ReportViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Analytics ──
    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("report_opened", null) } catch (_: Exception) {}
    }

    // ── Show snackbar ──
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.clearSnackbar()
        }
    }

    // ── Location Permission ──
    var hasLocation by remember { mutableStateOf(false) }
    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocation = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }
    LaunchedEffect(Unit) {
        locationPermLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // ── Fetch GPS ──
    LaunchedEffect(hasLocation) {
        if (!hasLocation) return@LaunchedEffect
        try {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            val loc = fused.lastLocation.await()
            loc?.let { vm.setLocation(it.latitude, it.longitude) }
        } catch (_: Exception) {}
    }

    // ── Camera setup ──
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            cameraImageUri?.let { vm.addPhoto(it) }
            try { Firebase.analytics.logEvent("photo_captured", null) } catch (_: Exception) {}
        }
    }

    // ── Camera permission ──
    var hasCameraPermission by remember { mutableStateOf(false) }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            val uri = createTempImageUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // ── Gallery picker ──
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            vm.addPhoto(it)
            try { Firebase.analytics.logEvent("photo_captured", null) } catch (_: Exception) {}
        }
    }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailable(context) }

    // ── Stats counts ──
    val pendingCount = state.myReports.count { it.status.lowercase() == "pending" }
    val inProgressCount = state.myReports.count { it.status.lowercase() == "in progress" }
    val resolvedCount = state.myReports.count { it.status.lowercase() == "resolved" }

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
            // ══════════════════════ Header (Premium) ══════════════════════
            ReportTopBar(
                colors = colors,
                activeTab = state.activeTab,
                myReportsCount = state.myReports.size,
                pendingCount = pendingCount,
                inProgressCount = inProgressCount,
                resolvedCount = resolvedCount
            )

            // ══════════════════════ Stats Strip ══════════════════════
            AnimatedVisibility(visible = state.activeTab == ReportViewModel.ReportTab.MY_REPORTS) {
                ReportStatsStrip(
                    total = state.myReports.size,
                    pending = pendingCount,
                    inProgress = inProgressCount,
                    resolved = resolvedCount
                )
            }

            // ══════════════════════ Tab Toggle ══════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                ReportTabButton(
                    label = "New Report",
                    icon = Icons.Outlined.AddCircleOutline,
                    isSelected = state.activeTab == ReportViewModel.ReportTab.NEW_REPORT,
                    onClick = { vm.setActiveTab(ReportViewModel.ReportTab.NEW_REPORT) },
                    modifier = Modifier.weight(1f)
                )
                ReportTabButton(
                    label = "My Reports",
                    icon = Icons.Outlined.History,
                    isSelected = state.activeTab == ReportViewModel.ReportTab.MY_REPORTS,
                    onClick = { vm.setActiveTab(ReportViewModel.ReportTab.MY_REPORTS) },
                    badge = state.myReports.size,
                    modifier = Modifier.weight(1f)
                )
            }

            // ══════════════════════ Upload Progress ══════════════════════
            AnimatedVisibility(visible = state.isSubmitting) {
                Column(modifier = Modifier.padding(horizontal = Spacing.XLarge)) {
                    LinearProgressIndicator(
                        progress = { state.uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = PrimaryBlue,
                        trackColor = PrimaryBlue.copy(alpha = 0.1f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            state.uploadProgress < 0.7f -> "Uploading photo..."
                            state.uploadProgress < 0.9f -> "Saving report..."
                            else -> "Almost done..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary
                    )
                }
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
                            "No internet — reports can't be submitted",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // ══════════════════════ Content ══════════════════════
            Crossfade(
                targetState = state.activeTab,
                animationSpec = tween(300),
                label = "report_tab_toggle"
            ) { tab ->
                when (tab) {
                    ReportViewModel.ReportTab.NEW_REPORT -> {
                        NewReportContent(
                            state = state,
                            colors = colors,
                            isOffline = isOffline,
                            onTypeSelected = { vm.setIssueType(it) },
                            onDescriptionChanged = { vm.setDescription(it) },
                            onCameraClick = {
                                if (hasCameraPermission) {
                                    val uri = createTempImageUri(context)
                                    cameraImageUri = uri
                                    cameraLauncher.launch(uri)
                                } else {
                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            onRemovePhoto = { vm.removePhoto(it) },
                            onRefreshLocation = {
                                if (hasLocation) {
                                    try {
                                        val fused = LocationServices.getFusedLocationProviderClient(context)
                                        fused.lastLocation.addOnSuccessListener { loc ->
                                            loc?.let { vm.setLocation(it.latitude, it.longitude) }
                                        }
                                    } catch (_: Exception) {}
                                } else {
                                    locationPermLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    )
                                }
                            },
                            onAnonymousChanged = { vm.setAnonymous(it) },
                            onPriorityChanged = { vm.setPriority(it) },
                            onSubmit = {
                                try { Firebase.analytics.logEvent("report_submitted", null) } catch (_: Exception) {}
                                vm.submitReport()
                            }
                        )
                    }
                    ReportViewModel.ReportTab.MY_REPORTS -> {
                        MyReportsContent(
                            state = state,
                            colors = colors,
                            onRetry = { vm.retryReports() },
                            onDeleteReport = { vm.deleteReport(it) },
                            onUpvoteReport = { vm.upvoteReport(it) },
                            onSendChatMessage = { reportId, message -> vm.sendChatMessage(reportId, message) }
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Tab Toggle Button
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    badge: Int = 0,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) PrimaryBlue else MaterialTheme.cityFluxColors.surfaceVariant
    val contentColor = if (isSelected) Color.White else MaterialTheme.cityFluxColors.textSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CornerRadius.Round),
        color = bgColor,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            if (badge > 0 && !isSelected) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = CircleShape,
                    color = AccentRed,
                    modifier = Modifier.size(18.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// New Report Content
// ═══════════════════════════════════════════════════════════════════

@SuppressLint("MissingPermission")
@Composable
private fun NewReportContent(
    state: ReportViewModel.ReportUiState,
    colors: CityFluxColors,
    isOffline: Boolean,
    onTypeSelected: (ReportViewModel.IssueType) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onRefreshLocation: () -> Unit,
    onAnonymousChanged: (Boolean) -> Unit,
    onPriorityChanged: (ReportViewModel.Priority) -> Unit,
    onSubmit: () -> Unit
) {
    // ── Draft persistence (Feature 8) ──
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("report_draft", Context.MODE_PRIVATE) }
    var draftSaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val desc = prefs.getString("description", "") ?: ""
        val type = prefs.getString("type", "") ?: ""
        val priority = prefs.getString("priority", "") ?: ""
        val anon = prefs.getBoolean("isAnonymous", false)
        if (desc.isNotBlank()) onDescriptionChanged(desc)
        if (type.isNotBlank()) {
            try { onTypeSelected(ReportViewModel.IssueType.valueOf(type)) } catch (_: Exception) {}
        }
        if (priority.isNotBlank()) {
            try { onPriorityChanged(ReportViewModel.Priority.valueOf(priority)) } catch (_: Exception) {}
        }
        onAnonymousChanged(anon)
    }

    LaunchedEffect(state.description, state.selectedType, state.selectedPriority, state.isAnonymous) {
        prefs.edit()
            .putString("description", state.description)
            .putString("type", state.selectedType?.name ?: "")
            .putString("priority", state.selectedPriority.name)
            .putBoolean("isAnonymous", state.isAnonymous)
            .apply()
        draftSaved = state.description.isNotBlank()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.XLarge, end = Spacing.XLarge,
            top = Spacing.Small, bottom = Spacing.Section
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large)
    ) {
        // ── Reporting Tips (Feature 9) ──
        item {
            var tipsExpanded by remember { mutableStateOf(false) }
            Surface(
                onClick = { tipsExpanded = !tipsExpanded },
                shape = RoundedCornerShape(CornerRadius.Large),
                color = PrimaryBlue.copy(alpha = 0.06f)
            ) {
                Column(modifier = Modifier.padding(Spacing.Large)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", fontSize = 16.sp)
                            Spacer(Modifier.width(Spacing.Small))
                            Text(
                                "Tips for a good report",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Icon(
                            if (tipsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            null, tint = colors.textTertiary, modifier = Modifier.size(20.dp)
                        )
                    }
                    AnimatedVisibility(visible = tipsExpanded) {
                        Column(modifier = Modifier.padding(top = Spacing.Small)) {
                            listOf(
                                "📷 Take clear, well-lit photos of the issue",
                                "📍 Include the exact location for faster response",
                                "📝 Describe what you see in detail",
                                "⏰ Report immediately for best results",
                                "🔢 Include any relevant numbers (license plates, etc.)"
                            ).forEach { tip ->
                                Text(
                                    tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Draft Saved Indicator (Feature 8) ──
        if (draftSaved) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Save, null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Draft saved", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ── Issue Type Selector ──
        item {
            SectionLabel("What's the issue?")
            Spacer(Modifier.height(Spacing.Small))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                items(ReportViewModel.IssueType.entries.toList()) { type ->
                    IssueTypeCard(
                        type = type,
                        isSelected = state.selectedType == type,
                        onClick = { onTypeSelected(type) }
                    )
                }
            }
        }

        // ── Priority Selector (Feature 10) ──
        item {
            SectionLabel("Priority Level")
            Spacer(Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                ReportViewModel.Priority.entries.forEach { priority ->
                    val chipColor = when (priority) {
                        ReportViewModel.Priority.LOW -> AccentGreen
                        ReportViewModel.Priority.MEDIUM -> PrimaryBlue
                        ReportViewModel.Priority.HIGH -> AccentOrange
                        ReportViewModel.Priority.CRITICAL -> AccentRed
                    }
                    val isSelected = state.selectedPriority == priority
                    Surface(
                        onClick = { onPriorityChanged(priority) },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        color = if (isSelected) chipColor.copy(alpha = 0.15f) else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 0.dp,
                            if (isSelected) chipColor else Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                priority.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chipColor else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── Photo Section (Feature 2: Multiple Photos) ──
        item {
            SectionLabel("Attach photos (up to 5)")
            Spacer(Modifier.height(Spacing.Small))
            if (state.photoUris.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    itemsIndexed(state.photoUris) { index, uri ->
                        Box(modifier = Modifier.size(80.dp)) {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = "Photo ${index + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(CornerRadius.Small)),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                onClick = { onRemovePhoto(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(22.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                    if (state.photoUris.size < 5) {
                        item {
                            Card(
                                modifier = Modifier.size(80.dp),
                                shape = RoundedCornerShape(CornerRadius.Small),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, colors.divider)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    IconButton(onClick = onCameraClick, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.CameraAlt, "Camera", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = onGalleryClick, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.PhotoLibrary, "Gallery", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                PhotoCaptureCard(
                    colors = colors,
                    onCameraClick = onCameraClick,
                    onGalleryClick = onGalleryClick
                )
            }
        }

        // ── Location Section ──
        item {
            SectionLabel("Location")
            Spacer(Modifier.height(Spacing.Small))
            LocationCard(
                hasLocation = state.hasLocation,
                latitude = state.latitude,
                longitude = state.longitude,
                colors = colors,
                onRefresh = onRefreshLocation
            )
        }

        // ── Description ──
        item {
            SectionLabel("Describe the issue")
            Spacer(Modifier.height(Spacing.Small))
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChanged,
                placeholder = {
                    Text("What happened? Provide details...", color = colors.textTertiary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = colors.divider,
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant,
                    cursorColor = PrimaryBlue
                )
            )
        }

        // ── Anonymous Toggle (Feature 7) ──
        item {
            Surface(
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = colors.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(Spacing.Large)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Shield, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(Spacing.Small))
                            Text(
                                "Report Anonymously",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }
                        Switch(
                            checked = state.isAnonymous,
                            onCheckedChange = onAnonymousChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryBlue
                            )
                        )
                    }
                    AnimatedVisibility(visible = state.isAnonymous) {
                        Text(
                            "Your identity will be hidden from public view",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = Spacing.Small)
                        )
                    }
                }
            }
        }

        // ── Submit + Error ──
        item {
            // Error retry
            AnimatedVisibility(visible = state.submitError != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.Medium),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = AccentRed.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.ErrorOutline, null,
                            tint = AccentRed, modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.Small))
                        Text(
                            state.submitError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onSubmit) {
                            Text("Retry", fontWeight = FontWeight.Bold, color = AccentRed)
                        }
                    }
                }
            }

            // Submit button
            Button(
                onClick = {
                    prefs.edit().clear().apply()
                    draftSaved = false
                    onSubmit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(CornerRadius.Round),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    disabledContainerColor = PrimaryBlue.copy(alpha = 0.4f)
                ),
                enabled = !state.isSubmitting && !isOffline
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text("Submitting...", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.Small))
                    Text("Submit Report", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Issue Type Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun IssueTypeCard(
    type: ReportViewModel.IssueType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val (icon, accent) = remember(type) {
        when (type) {
            ReportViewModel.IssueType.ILLEGAL_PARKING -> Icons.Outlined.LocalParking to AccentRed
            ReportViewModel.IssueType.ACCIDENT -> Icons.Outlined.Warning to AccentAlerts
            ReportViewModel.IssueType.HAWKERS -> Icons.Outlined.Store to AccentOrange
            ReportViewModel.IssueType.ROAD_DAMAGE -> Icons.Outlined.Construction to AccentIssues
            ReportViewModel.IssueType.TRAFFIC_VIOLATION -> Icons.Outlined.ReportProblem to AccentTraffic
            ReportViewModel.IssueType.OTHER -> Icons.Outlined.MoreHoriz to colors.textSecondary
        }
    }

    val borderColor = if (isSelected) accent else colors.divider
    val bgColor = if (isSelected) accent.copy(alpha = 0.08f) else colors.cardBackground

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(100.dp)
            .shadow(
                if (isSelected) 4.dp else 1.dp,
                RoundedCornerShape(CornerRadius.Large),
                ambientColor = if (isSelected) accent.copy(alpha = 0.3f) else colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = accent.copy(alpha = if (isSelected) 0.15f else 0.08f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon, null,
                        tint = if (isSelected) accent else colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Small))
            Text(
                type.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accent else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Photo Capture Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PhotoCaptureCard(
    colors: CityFluxColors,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 2.dp,
            brush = androidx.compose.ui.graphics.SolidColor(colors.divider)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.XLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = PrimaryBlue.copy(alpha = 0.1f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CameraAlt, "Camera",
                        tint = PrimaryBlue, modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                "Capture evidence",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Take a photo or select from gallery",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
            Spacer(Modifier.height(Spacing.Large))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                OutlinedButton(
                    onClick = onCameraClick,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    border = BorderStroke(1.dp, PrimaryBlue)
                ) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera", fontWeight = FontWeight.SemiBold, color = PrimaryBlue, fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    border = BorderStroke(1.dp, colors.divider)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(16.dp), tint = colors.textSecondary)
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery", fontWeight = FontWeight.SemiBold, color = colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Photo Preview Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PhotoPreviewCard(
    uri: Uri,
    colors: CityFluxColors,
    onRetake: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Box {
            Image(
                painter = rememberAsyncImagePainter(uri),
                contentDescription = "Selected photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(CornerRadius.Large)),
                contentScale = ContentScale.Crop
            )
            // Retake overlay button
            Surface(
                onClick = onRetake,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.Small),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Replay, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Retake", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // Green check badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Spacing.Small),
                shape = RoundedCornerShape(CornerRadius.Round),
                color = AccentParking
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Photo attached", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Location Card with mini map
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun LocationCard(
    hasLocation: Boolean,
    latitude: Double,
    longitude: Double,
    colors: CityFluxColors,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column {
            // Mini map preview
            if (hasLocation) {
                val latLng = LatLng(latitude, longitude)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(latLng, 16f)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(topStart = CornerRadius.Large, topEnd = CornerRadius.Large))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = false,
                            scrollGesturesEnabled = false,
                            zoomGesturesEnabled = false,
                            rotationGesturesEnabled = false,
                            tiltGesturesEnabled = false,
                            compassEnabled = false,
                            myLocationButtonEnabled = false,
                            mapToolbarEnabled = false
                        )
                    ) {
                        Marker(
                            state = MarkerState(position = latLng),
                            title = "Report location"
                        )
                    }
                }
            }

            // Location info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Large),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = if (hasLocation) AccentParking.copy(alpha = 0.1f) else AccentAlerts.copy(alpha = 0.1f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.LocationOn, "Location",
                            tint = if (hasLocation) AccentParking else AccentAlerts,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (hasLocation) "Location captured" else "Location required",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        if (hasLocation) "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                        else "Tap refresh to get GPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, "Refresh location", tint = PrimaryBlue)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// My Reports Content
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MyReportsContent(
    state: ReportViewModel.ReportUiState,
    colors: CityFluxColors,
    onRetry: () -> Unit,
    onDeleteReport: (String) -> Unit,
    onUpvoteReport: (String) -> Unit,
    onSendChatMessage: (String, String) -> Unit
) {
    // ── Local filters ──
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }
    var sortMode by remember { mutableStateOf("Newest") }
    var showSortMenu by remember { mutableStateOf(false) }

    // ── Detail dialog state ──
    var selectedReport by remember { mutableStateOf<Report?>(null) }

    // ── Filtered reports ──
    val filteredReports = remember(state.myReports, searchQuery, selectedStatus, selectedType, sortMode) {
        state.myReports
            .filter { report ->
                val matchSearch = searchQuery.isBlank() ||
                    report.description.contains(searchQuery, ignoreCase = true) ||
                    report.title.contains(searchQuery, ignoreCase = true) ||
                    report.type.contains(searchQuery, ignoreCase = true)
                val matchStatus = selectedStatus == "All" || report.status.equals(selectedStatus, ignoreCase = true)
                val matchType = selectedType == "All" || report.type == selectedType
                matchSearch && matchStatus && matchType
            }
            .let { list ->
                when (sortMode) {
                    "Oldest" -> list.sortedBy { it.timestamp }
                    "Type" -> list.sortedBy { it.type }
                    "Status" -> list.sortedBy { it.status }
                    else -> list.sortedByDescending { it.timestamp }
                }
            }
    }

    // ── Full-Screen Report Detail Dialog ──
    selectedReport?.let { report ->
        ReportFullDetailDialog(
            report = report,
            colors = colors,
            onDismiss = { selectedReport = null },
            onDeleteReport = onDeleteReport,
            onUpvoteReport = onUpvoteReport,
            onSendChatMessage = onSendChatMessage
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search Bar ──
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
            shape = RoundedCornerShape(CornerRadius.Large),
            color = colors.surfaceVariant.copy(alpha = 0.7f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Search reports...", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, "Clear", tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // ── Status Tabs ──
        val statusTabs = listOf(
            "All" to state.myReports.size,
            "Pending" to state.myReports.count { it.status.lowercase() == "pending" },
            "In Progress" to state.myReports.count { it.status.lowercase() == "in progress" },
            "Resolved" to state.myReports.count { it.status.lowercase() == "resolved" }
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            items(statusTabs.size) { idx ->
                val (label, count) = statusTabs[idx]
                val isSel = selectedStatus == label
                val chipColor = when (label) {
                    "Pending" -> AccentIssues
                    "In Progress" -> AccentAlerts
                    "Resolved" -> AccentGreen
                    else -> PrimaryBlue
                }
                Surface(
                    onClick = { selectedStatus = label },
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = if (isSel) chipColor.copy(alpha = 0.15f) else colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) chipColor else colors.textSecondary
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) chipColor.copy(alpha = 0.2f) else colors.textTertiary.copy(alpha = 0.15f),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$count", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    color = if (isSel) chipColor else colors.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Type Filter Chips ──
        val typeChips = listOf(
            "All" to null,
            "Parking" to "illegal_parking",
            "Accident" to "accident",
            "Hawkers" to "hawker",
            "Road Damage" to "road_damage",
            "Traffic" to "traffic_violation",
            "Other" to "other"
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            items(typeChips.size) { idx ->
                val (label, typeVal) = typeChips[idx]
                val isSel = selectedType == (typeVal ?: "All")
                val chipColor = when (typeVal) {
                    "illegal_parking" -> AccentRed
                    "accident" -> AccentAlerts
                    "hawker" -> AccentOrange
                    "road_damage" -> AccentIssues
                    "traffic_violation" -> AccentTraffic
                    "other" -> Color.Gray
                    else -> PrimaryBlue
                }
                Surface(
                    onClick = { selectedType = typeVal ?: "All" },
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = if (isSel) chipColor.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier.height(26.dp)
                ) {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) chipColor else colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // ── Sort + Results Count ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.XLarge, vertical = Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filteredReports.size} reports",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
            Box {
                Surface(
                    onClick = { showSortMenu = !showSortMenu },
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = colors.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.Sort, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                        Text(sortMode, fontSize = 11.sp, color = colors.textSecondary)
                    }
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf("Newest", "Oldest", "Type", "Status").forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode, fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { sortMode = mode; showSortMenu = false },
                            leadingIcon = if (mode == sortMode) {
                                { Icon(Icons.Filled.Check, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }
        }

        // ── Content ──
        when {
            state.isLoadingReports -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.XLarge, vertical = Spacing.Small),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) { items(4) { ShimmerReportCard() } }
            }
            state.reportsError != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, null, modifier = Modifier.size(56.dp), tint = colors.textTertiary)
                        Spacer(Modifier.height(Spacing.Medium))
                        Text(state.reportsError, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                        Spacer(Modifier.height(Spacing.Medium))
                        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(CornerRadius.Round)) { Text("Retry") }
                    }
                }
            }
            filteredReports.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.FolderOff, null, modifier = Modifier.size(64.dp), tint = colors.textTertiary)
                        Spacer(Modifier.height(Spacing.Medium))
                        Text(
                            if (state.myReports.isEmpty()) "No reports yet" else "No matching reports",
                            style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary
                        )
                        Text(
                            if (state.myReports.isEmpty()) "Submit your first report to see it here" else "Try adjusting your filters",
                            style = MaterialTheme.typography.bodySmall, color = colors.textTertiary
                        )
                        if (searchQuery.isNotBlank() || selectedStatus != "All" || selectedType != "All") {
                            Spacer(Modifier.height(Spacing.Medium))
                            OutlinedButton(
                                onClick = { searchQuery = ""; selectedStatus = "All"; selectedType = "All" },
                                shape = RoundedCornerShape(CornerRadius.Round)
                            ) { Text("Clear Filters") }
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.XLarge, end = Spacing.XLarge,
                        top = Spacing.Small, bottom = Spacing.Section
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    itemsIndexed(filteredReports, key = { _, report -> report.id }) { _, report ->
                        CompactReportListCard(
                            report = report,
                            colors = colors,
                            onClick = { selectedReport = report }
                        )
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Compact Report List Card — Clean summary, opens full detail on tap
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun CompactReportListCard(
    report: Report,
    colors: CityFluxColors,
    onClick: () -> Unit
) {
    val statusColor = when (report.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentAlerts
        "rejected" -> AccentRed
        else -> AccentIssues
    }
    val typeIcon = reportTypeIconFor(report.type)
    val typeColor = reportTypeColorFor(report.type)
    val typeLabel = reportTypeLabelFor(report.type)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                4.dp,
                RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent gradient bar
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(typeColor, statusColor)))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail or type icon
                if (report.imageUrl.isNotBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(report.imageUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = typeColor.copy(alpha = 0.1f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                Spacer(Modifier.width(Spacing.Medium))

                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (report.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = report.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        report.timestamp?.let { ts ->
                            Text(
                                formatTimestamp(ts),
                                fontSize = 10.sp,
                                color = colors.textTertiary
                            )
                        }
                        if (report.upvoteCount > 0) {
                            Text(" · ", fontSize = 10.sp, color = colors.textTertiary)
                            Icon(Icons.Outlined.ThumbUp, null, tint = colors.textTertiary, modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${report.upvoteCount}", fontSize = 10.sp, color = colors.textTertiary)
                        }
                    }
                }

                Spacer(Modifier.width(Spacing.Small))

                // Status + chevron
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            report.status,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
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

// ═══════════════════════════════════════════════════════════════════
// Full Detail Dialog — Opens when tapping a report card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportFullDetailDialog(
    report: Report,
    colors: CityFluxColors,
    onDismiss: () -> Unit,
    onDeleteReport: (String) -> Unit,
    onUpvoteReport: (String) -> Unit,
    onSendChatMessage: (String, String) -> Unit
) {
    val context = LocalContext.current
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val hasUpvoted = currentUid.isNotBlank() && report.upvotedBy.contains(currentUid)
    var showDeleteDialog by remember { mutableStateOf(false) }

    val statusColor = when (report.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentAlerts
        "rejected" -> AccentRed
        else -> AccentIssues
    }
    val typeIcon = reportTypeIconFor(report.type)
    val typeColor = reportTypeColorFor(report.type)
    val typeLabel = reportTypeLabelFor(report.type)

    // Delete confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Report?", fontWeight = FontWeight.Bold) },
            text = { Text("This action cannot be undone. Are you sure you want to delete this report?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteReport(report.id)
                    onDismiss()
                }) { Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .shadow(16.dp, RoundedCornerShape(CornerRadius.XLarge), ambientColor = colors.cardShadow),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(typeColor.copy(alpha = 0.12f), Color.Transparent)
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
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(CornerRadius.Medium),
                                color = typeColor.copy(alpha = 0.15f)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(26.dp))
                                }
                            }
                            Spacer(Modifier.width(Spacing.Medium))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    typeLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = typeColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = statusColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            report.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (report.status.lowercase() == "pending") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AccentRed.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                "NEEDS ACTION",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentRed,
                                                letterSpacing = 0.5.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (report.title.isNotBlank()) {
                            Spacer(Modifier.height(Spacing.Medium))
                            Text(
                                report.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                        }
                    }
                }

                // ── Scrollable content ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.Large)
                ) {
                    // Photo
                    if (report.imageUrl.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.Small))
                        Card(
                            shape = RoundedCornerShape(CornerRadius.Large),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(report.imageUrl),
                                contentDescription = "Report photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Description
                    if (report.description.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.XLarge))
                        Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(Modifier.height(Spacing.Small))
                        Text(
                            report.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            lineHeight = 22.sp
                        )
                    }

                    Spacer(Modifier.height(Spacing.XLarge))
                    HorizontalDivider(color = colors.surfaceVariant, thickness = 0.5.dp)
                    Spacer(Modifier.height(Spacing.XLarge))

                    // Details grid
                    Text("Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.Medium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
                    ) {
                        DetailMetaChip(
                            icon = Icons.Outlined.Tag,
                            label = "ID",
                            value = report.id.take(8) + "...",
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetaChip(
                            icon = Icons.Outlined.Schedule,
                            label = "Time",
                            value = report.timestamp?.let { formatTimestamp(it) } ?: "Unknown",
                            modifier = Modifier.weight(1f)
                        )
                        DetailMetaChip(
                            icon = Icons.Outlined.FlagCircle,
                            label = "Status",
                            value = report.status,
                            color = statusColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(Spacing.XLarge))

                    // Status Timeline
                    StatusTimeline(report = report, colors = colors)

                    Spacer(Modifier.height(Spacing.XLarge))

                    // Location card with map
                    if (report.latitude != 0.0 && report.longitude != 0.0) {
                        val reportLatLng = LatLng(report.latitude, report.longitude)
                        val cameraState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(reportLatLng, 16f)
                        }

                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            color = PrimaryBlue.copy(alpha = 0.06f)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.Small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.LocationOn, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${"%.4f".format(report.latitude)}, ${"%.4f".format(report.longitude)}",
                                        fontSize = 11.sp, color = colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(bottomStart = CornerRadius.Medium, bottomEnd = CornerRadius.Medium))
                                ) {
                                    GoogleMap(
                                        modifier = Modifier.fillMaxSize(),
                                        cameraPositionState = cameraState,
                                        uiSettings = MapUiSettings(
                                            zoomControlsEnabled = true,
                                            scrollGesturesEnabled = true,
                                            zoomGesturesEnabled = true,
                                            rotationGesturesEnabled = false,
                                            tiltGesturesEnabled = false,
                                            compassEnabled = false,
                                            myLocationButtonEnabled = false,
                                            mapToolbarEnabled = false
                                        )
                                    ) {
                                        Marker(
                                            state = MarkerState(position = reportLatLng),
                                            title = typeLabel,
                                            snippet = report.description.take(50)
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp),
                                        shape = RoundedCornerShape(CornerRadius.Round),
                                        color = typeColor.copy(alpha = 0.9f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(typeIcon, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(typeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Spacing.XLarge))
                    }

                    // Chat section
                    ReportChatSection(
                        reportId = report.id,
                        colors = colors,
                        onSendMessage = { message -> onSendChatMessage(report.id, message) }
                    )

                    Spacer(Modifier.height(Spacing.Medium))
                }

                // ── Bottom action bar ──
                HorizontalDivider(color = colors.surfaceVariant, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
                ) {
                    // Share
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                append("CityFlux Report\n")
                                append("Type: $typeLabel\n")
                                append("Description: ${report.description}\n")
                                append("Location: ${report.latitude}, ${report.longitude}\n")
                                append("Status: ${report.status}")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(CornerRadius.Round),
                        border = BorderStroke(1.dp, colors.divider)
                    ) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(16.dp), tint = colors.textSecondary)
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontSize = 12.sp, color = colors.textSecondary)
                    }

                    // Upvote
                    OutlinedButton(
                        onClick = { onUpvoteReport(report.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(CornerRadius.Round),
                        border = BorderStroke(1.dp, if (hasUpvoted) PrimaryBlue else colors.divider)
                    ) {
                        Icon(
                            if (hasUpvoted) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            null, Modifier.size(16.dp),
                            tint = if (hasUpvoted) PrimaryBlue else colors.textSecondary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Upvote${if (report.upvoteCount > 0) " (${report.upvoteCount})" else ""}",
                            fontSize = 12.sp,
                            color = if (hasUpvoted) PrimaryBlue else colors.textSecondary
                        )
                    }

                    // Delete (only for pending)
                    if (report.status.lowercase() == "pending") {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(AccentRed.copy(alpha = 0.08f), RoundedCornerShape(CornerRadius.Round))
                        ) {
                            Icon(Icons.Outlined.Delete, "Delete", tint = AccentRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetaChip(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color = MaterialTheme.cityFluxColors.textSecondary,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Small),
        color = colors.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, fontSize = 8.sp, color = colors.textTertiary)
        }
    }
}

private fun reportTypeIconFor(type: String): ImageVector = when (type) {
    "illegal_parking" -> Icons.Outlined.LocalParking
    "accident" -> Icons.Outlined.Warning
    "hawker" -> Icons.Outlined.Store
    "road_damage" -> Icons.Outlined.Construction
    "traffic_violation" -> Icons.Outlined.ReportProblem
    else -> Icons.Outlined.MoreHoriz
}

private fun reportTypeColorFor(type: String): Color = when (type) {
    "illegal_parking" -> AccentRed
    "accident" -> AccentAlerts
    "hawker" -> AccentOrange
    "road_damage" -> AccentIssues
    "traffic_violation" -> AccentTraffic
    else -> Color.Gray
}

private fun reportTypeLabelFor(type: String): String = when (type) {
    "illegal_parking" -> "Illegal Parking"
    "accident" -> "Accident"
    "hawker" -> "Hawkers"
    "road_damage" -> "Road Damage"
    "traffic_violation" -> "Traffic Violation"
    else -> "Other"
}

// ═══════════════════════════════════════════════════════════════════
// Report Chat Section — Police-citizen messages from subcollection
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportChatSection(reportId: String, colors: CityFluxColors, onSendMessage: (String) -> Unit) {
    var chatMessages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }

    LaunchedEffect(reportId) {
        isLoading = true
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("reports").document(reportId).collection("chat")
            .orderBy("timestamp")
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                chatMessages = snapshot?.documents?.mapNotNull { doc ->
                    doc.data
                } ?: emptyList()
                isLoading = false
            }
    }

    if (isLoading) {
        // skip
    } else if (chatMessages.isNotEmpty()) {
        Column {
            Text(
                "Updates & Messages",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(Spacing.Small))
            chatMessages.forEach { msg ->
                val sender = (msg["sender"] as? String) ?: "System"
                val text = (msg["message"] as? String) ?: ""
                val isPolice = sender.lowercase().contains("police") || sender.lowercase().contains("officer")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(CornerRadius.Small),
                    color = if (isPolice) PrimaryBlue.copy(alpha = 0.06f) else AccentGreen.copy(alpha = 0.06f)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.Small),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (isPolice) Icons.Outlined.Shield else Icons.Outlined.Person,
                            null,
                            tint = if (isPolice) PrimaryBlue else AccentGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                sender,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPolice) PrimaryBlue else AccentGreen
                            )
                            Text(text, fontSize = 11.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.Small))
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Small),
            shape = RoundedCornerShape(CornerRadius.Small),
            color = colors.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("No updates yet — authorities will respond here", fontSize = 10.sp, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(Spacing.Small))
    }

    // Chat input (Feature 1)
    Surface(
        shape = RoundedCornerShape(CornerRadius.Round),
        color = colors.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Small, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = chatInput,
                onValueChange = { chatInput = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary),
                decorationBox = { inner ->
                    if (chatInput.isEmpty()) {
                        Text("Type a message...", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                    }
                    inner()
                }
            )
            IconButton(
                onClick = {
                    if (chatInput.isNotBlank()) {
                        onSendMessage(chatInput.trim())
                        chatInput = ""
                    }
                },
                modifier = Modifier.size(32.dp),
                enabled = chatInput.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    "Send",
                    tint = if (chatInput.isNotBlank()) PrimaryBlue else colors.textTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.Small))
}


// ═══════════════════════════════════════════════════════════════════
// Status Timeline (Feature 3) — Order-tracking style vertical timeline
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatusTimeline(report: Report, colors: CityFluxColors) {
    val steps = listOf("Submitted", "Assigned", "In Progress", "Resolved")
    val currentStep = when (report.status.lowercase()) {
        "resolved" -> 4
        "in progress" -> 3
        else -> if (report.assignedTo.isNotBlank()) 2 else 1
    }

    Surface(
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = colors.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Text(
                "Status Timeline",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(Spacing.Small))
            steps.forEachIndexed { index, step ->
                val stepNumber = index + 1
                val isCompleted = stepNumber <= currentStep
                val isCurrent = stepNumber == currentStep
                val stepColor = when {
                    isCompleted -> AccentGreen
                    else -> colors.textTertiary.copy(alpha = 0.4f)
                }

                Row(verticalAlignment = Alignment.Top) {
                    // Circle + connecting line
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = CircleShape,
                            color = if (isCompleted) stepColor else Color.Transparent,
                            border = BorderStroke(2.dp, stepColor)
                        ) {
                            if (isCompleted) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        if (index < steps.size - 1) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(24.dp)
                                    .background(if (stepNumber < currentStep) AccentGreen else colors.textTertiary.copy(alpha = 0.2f))
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.Small))
                    Column {
                        Text(
                            step,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCompleted) colors.textPrimary else colors.textTertiary
                        )
                        if (isCurrent && report.timestamp != null) {
                            Text(
                                formatTimestamp(report.timestamp),
                                fontSize = 9.sp,
                                color = colors.textTertiary
                            )
                        }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Shimmer Report Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerReportCard() {
    val colors = MaterialTheme.cityFluxColors
    val shimmerColors = listOf(
        colors.surfaceVariant,
        colors.surfaceVariant.copy(alpha = 0.5f),
        colors.surfaceVariant
    )
    val transition = rememberInfiniteTransition(label = "report_shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "report_shimmer_translate"
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
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(CornerRadius.Small))
                        .background(brush)
                )
                Spacer(Modifier.width(Spacing.Small))
                Box(
                    Modifier
                        .width(100.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .width(60.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush)
                )
            }
            Spacer(Modifier.height(Spacing.Medium))
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(brush)
            )
            Spacer(Modifier.height(Spacing.Medium))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
                    .background(brush)
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Section Label
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    val colors = MaterialTheme.cityFluxColors
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = colors.textPrimary
    )
}


// ═══════════════════════════════════════════════════════════════════
// Utilities
// ═══════════════════════════════════════════════════════════════════

private fun createTempImageUri(context: Context): Uri {
    val photoDir = File(context.cacheDir, "photos")
    photoDir.mkdirs()
    val photoFile = File(photoDir, "report_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(timestamp.toDate())
    } catch (_: Exception) {
        "Just now"
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}


// ═══════════════════════════════════════════════════════════════════
// TopBar — Police-style header with icon badge
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportTopBar(
    colors: CityFluxColors,
    activeTab: ReportViewModel.ReportTab,
    myReportsCount: Int,
    pendingCount: Int,
    inProgressCount: Int,
    resolvedCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AccentIssues.copy(alpha = 0.15f),
                        AccentIssues.copy(alpha = 0.03f),
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
                // Glowing icon
                Box(contentAlignment = Alignment.Center) {
                    val infiniteTransition = rememberInfiniteTransition(label = "report_glow")
                    val glowAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.2f,
                        targetValue = 0.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "glow"
                    )
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AccentIssues.copy(alpha = glowAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentIssues, AccentIssues.copy(alpha = 0.8f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.ReportProblem,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.Medium))
                Column {
                    Text(
                        "Report an Issue",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReportPulsingDot(color = AccentGreen, size = 6.dp)
                        Text(
                            when (activeTab) {
                                ReportViewModel.ReportTab.NEW_REPORT -> "Help keep your city safe"
                                ReportViewModel.ReportTab.MY_REPORTS -> "$myReportsCount reports · $pendingCount pending"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

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
                    ReportPulsingDot(color = AccentGreen, size = 8.dp)
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
private fun ReportPulsingDot(color: Color, size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "rdot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "rdotAlpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}


// ═══════════════════════════════════════════════════════════════════
// Stats Strip — 4 stat cards + resolution progress bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ReportStatsStrip(
    total: Int,
    pending: Int,
    inProgress: Int,
    resolved: Int
) {
    val colors = MaterialTheme.cityFluxColors
    Column(modifier = Modifier.padding(horizontal = Spacing.XLarge, vertical = Spacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
        ) {
            ReportStatMiniCard("Total", "$total", Icons.Outlined.Description, PrimaryBlue, Modifier.weight(1f))
            ReportStatMiniCard("Pending", "$pending", Icons.Outlined.HourglassTop, AccentIssues, Modifier.weight(1f))
            ReportStatMiniCard("Active", "$inProgress", Icons.Outlined.Engineering, AccentAlerts, Modifier.weight(1f))
            ReportStatMiniCard("Done", "$resolved", Icons.Outlined.CheckCircle, AccentGreen, Modifier.weight(1f))
        }
        // Resolution progress bar
        if (total > 0) {
            Spacer(Modifier.height(Spacing.Small))
            val resolvedFraction = resolved.toFloat() / total
            val inProgressFraction = inProgress.toFloat() / total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Resolution", fontSize = 9.sp, color = colors.textTertiary)
                Text("${(resolvedFraction * 100).toInt()}% resolved", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            }
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceVariant)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (resolvedFraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .weight(resolvedFraction.coerceAtLeast(0.01f))
                                .background(AccentGreen)
                        )
                    }
                    if (inProgressFraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .weight(inProgressFraction.coerceAtLeast(0.01f))
                                .background(AccentAlerts)
                        )
                    }
                    val pendingFraction = 1f - resolvedFraction - inProgressFraction
                    if (pendingFraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .weight(pendingFraction.coerceAtLeast(0.01f))
                                .background(Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatMiniCard(
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
