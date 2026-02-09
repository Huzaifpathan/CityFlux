package com.example.cityflux.ui.report

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import com.google.firebase.ktx.Firebase
import com.google.maps.android.compose.*
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
            vm.setPhoto(cameraImageUri)
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
            vm.setPhoto(it)
            try { Firebase.analytics.logEvent("photo_captured", null) } catch (_: Exception) {}
        }
    }

    // ── Connectivity ──
    val isOffline = remember { !isNetworkAvailable(context) }

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
            Column(
                modifier = Modifier.padding(
                    start = Spacing.XLarge, end = Spacing.XLarge,
                    top = Spacing.Large, bottom = Spacing.Small
                )
            ) {
                Text(
                    "Report an Issue",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Help keep your city safe and smooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
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
                            onRetakeClick = { vm.setPhoto(null) },
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
                            onRetry = { vm.retryReports() }
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
    onRetakeClick: () -> Unit,
    onRefreshLocation: () -> Unit,
    onSubmit: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.XLarge, end = Spacing.XLarge,
            top = Spacing.Small, bottom = Spacing.Section
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large)
    ) {
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

        // ── Photo Section ──
        item {
            SectionLabel("Attach a photo")
            Spacer(Modifier.height(Spacing.Small))
            if (state.photoUri != null) {
                // Preview with retake
                PhotoPreviewCard(
                    uri = state.photoUri,
                    colors = colors,
                    onRetake = onRetakeClick
                )
            } else {
                // Camera + Gallery buttons
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
                onClick = onSubmit,
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
                    Icon(Icons.Outlined.Send, null, Modifier.size(20.dp))
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
    onRetry: () -> Unit
) {
    when {
        state.isLoadingReports -> {
            // Shimmer loading
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.XLarge, end = Spacing.XLarge,
                    top = Spacing.Small, bottom = Spacing.Section
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                items(4) { ShimmerReportCard() }
            }
        }

        state.reportsError != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.ErrorOutline, null,
                        modifier = Modifier.size(56.dp), tint = colors.textTertiary
                    )
                    Spacer(Modifier.height(Spacing.Medium))
                    Text(
                        state.reportsError,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(Spacing.Medium))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(CornerRadius.Round)
                    ) { Text("Retry") }
                }
            }
        }

        state.myReports.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FolderOff, null,
                        modifier = Modifier.size(64.dp), tint = colors.textTertiary
                    )
                    Spacer(Modifier.height(Spacing.Medium))
                    Text(
                        "No reports yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary
                    )
                    Text(
                        "Submit your first report to see it here",
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
                    start = Spacing.XLarge, end = Spacing.XLarge,
                    top = Spacing.Small, bottom = Spacing.Section
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                itemsIndexed(state.myReports, key = { _, report -> report.id }) { _, report ->
                    MyReportCard(report = report, colors = colors)
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// My Report Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun MyReportCard(report: Report, colors: CityFluxColors) {
    val statusColor = when (report.status.lowercase()) {
        "resolved" -> AccentParking
        "in progress" -> AccentAlerts
        else -> AccentIssues
    }
    val typeIcon = when (report.type) {
        "illegal_parking" -> Icons.Outlined.LocalParking
        "accident" -> Icons.Outlined.Warning
        "hawker" -> Icons.Outlined.Store
        "road_damage" -> Icons.Outlined.Construction
        "traffic_violation" -> Icons.Outlined.ReportProblem
        else -> Icons.Outlined.MoreHoriz
    }
    val typeLabel = when (report.type) {
        "illegal_parking" -> "Illegal Parking"
        "accident" -> "Accident"
        "hawker" -> "Hawkers"
        "road_damage" -> "Road Damage"
        "traffic_violation" -> "Traffic Violation"
        else -> "Other"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                4.dp, RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow, spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )

            Column(modifier = Modifier.padding(Spacing.Large)) {
                // Header: type + status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = RoundedCornerShape(CornerRadius.Small),
                            color = statusColor.copy(alpha = 0.1f)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(typeIcon, null, tint = statusColor, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.width(Spacing.Small))
                        Text(
                            typeLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                    }
                    // Status badge
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
                }

                // Description
                if (report.description.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.Small))
                    Text(
                        report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Meta: location + time
                Spacer(Modifier.height(Spacing.Small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Location
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn, null,
                            tint = colors.textTertiary, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "${"%.3f".format(report.latitude)}, ${"%.3f".format(report.longitude)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary
                        )
                    }
                    // Time
                    report.timestamp?.let { ts ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Schedule, null,
                                tint = colors.textTertiary, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                formatTimestamp(ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary
                            )
                        }
                    }
                }

                // Photo thumbnail
                if (report.imageUrl.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.Small))
                    Image(
                        painter = rememberAsyncImagePainter(report.imageUrl),
                        contentDescription = "Report photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium)),
                        contentScale = ContentScale.Crop
                    )
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
