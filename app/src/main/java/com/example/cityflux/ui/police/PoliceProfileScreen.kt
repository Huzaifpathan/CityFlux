package com.example.cityflux.ui.police

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.model.User
import com.example.cityflux.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════
// Police Officer Data Model
// ═══════════════════════════════════════════════════════════════════

data class PoliceOfficer(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val profileImageUrl: String = "",
    val badgeId: String = "",
    val rank: String = "Constable",
    val department: String = "Traffic Division",
    val station: String = "",
    val dutyArea: String = "",
    val joinDate: Timestamp? = null,
    val isOnDuty: Boolean = false,
    val currentShift: String = "Day", // Day, Night, Rotating
    val todayDutyHours: Float = 0f,
    val totalCasesHandled: Int = 0,
    val casesThisMonth: Int = 0,
    val avgResponseTime: Int = 0, // in minutes
    val citizenRating: Float = 0f,
    val lastActiveAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)

// ═══════════════════════════════════════════════════════════════════
// Police Profile ViewModel
// ═══════════════════════════════════════════════════════════════════

class PoliceProfileViewModel : ViewModel() {

    companion object {
        private const val TAG = "PoliceProfileVM"
    }

    data class UiState(
        val isLoading: Boolean = true,
        val officer: PoliceOfficer? = null,
        val casesAssignedToday: Int = 0,
        val casesResolvedToday: Int = 0,
        val pendingCases: Int = 0,
        val weeklyStats: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
        val isUploadingImage: Boolean = false,
        val uploadProgress: Float = 0f,
        val error: String? = null,
        val isOffline: Boolean = false,
        val snackbarMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var profileListener: ListenerRegistration? = null
    private var statsListener: ListenerRegistration? = null

    init {
        loadOfficerProfile()
        loadCaseStatistics()
    }

    private fun loadOfficerProfile() {
        val uid = auth.currentUser?.uid ?: return

        profileListener?.remove()
        profileListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to load profile", isOffline = true) }
                    return@addSnapshotListener
                }

                if (snap != null && snap.exists()) {
                    val data = snap.data ?: return@addSnapshotListener

                    val officer = PoliceOfficer(
                        uid = uid,
                        name = data["name"] as? String ?: "",
                        email = data["email"] as? String ?: auth.currentUser?.email ?: "",
                        phone = data["phone"] as? String ?: "",
                        profileImageUrl = data["profileImageUrl"] as? String ?: "",
                        badgeId = data["badgeId"] as? String ?: "PO-${uid.take(6).uppercase()}",
                        rank = data["rank"] as? String ?: "Constable",
                        department = data["department"] as? String ?: "Traffic Division",
                        station = data["station"] as? String ?: "Central Station",
                        dutyArea = data["dutyArea"] as? String ?: "Downtown District",
                        joinDate = data["joinDate"] as? Timestamp ?: data["createdAt"] as? Timestamp,
                        isOnDuty = data["isOnDuty"] as? Boolean ?: false,
                        currentShift = data["currentShift"] as? String ?: "Day",
                        todayDutyHours = (data["todayDutyHours"] as? Number)?.toFloat() ?: 0f,
                        totalCasesHandled = (data["totalCasesHandled"] as? Number)?.toInt() ?: 0,
                        casesThisMonth = (data["casesThisMonth"] as? Number)?.toInt() ?: 0,
                        avgResponseTime = (data["avgResponseTime"] as? Number)?.toInt() ?: 12,
                        citizenRating = (data["citizenRating"] as? Number)?.toFloat() ?: 4.5f,
                        lastActiveAt = data["lastActiveAt"] as? Timestamp,
                        createdAt = data["createdAt"] as? Timestamp
                    )

                    _uiState.update { it.copy(isLoading = false, officer = officer, error = null, isOffline = false) }
                }
            }
    }

    private fun loadCaseStatistics() {
        val uid = auth.currentUser?.uid ?: return

        // Today's date range
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val todayStart = Timestamp(calendar.time)

        statsListener?.remove()
        statsListener = firestore.collection("reports")
            .whereEqualTo("assignedTo", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val allCases = snap.documents
                    val todayCases = allCases.filter { doc ->
                        val ts = doc.getTimestamp("timestamp")
                        ts != null && ts >= todayStart
                    }
                    val resolvedToday = todayCases.count {
                        it.getString("status")?.equals("Resolved", true) == true
                    }
                    val pending = allCases.count {
                        val status = it.getString("status") ?: ""
                        !status.equals("Resolved", true)
                    }

                    // Generate weekly mock data (in production, calculate from real data)
                    val weekly = (0..6).map { (5..15).random() }

                    _uiState.update {
                        it.copy(
                            casesAssignedToday = todayCases.size,
                            casesResolvedToday = resolvedToday,
                            pendingCases = pending,
                            weeklyStats = weekly
                        )
                    }
                }
            }
    }

    fun toggleDutyStatus() {
        val uid = auth.currentUser?.uid ?: return
        val current = _uiState.value.officer?.isOnDuty ?: false

        firestore.collection("users").document(uid)
            .update("isOnDuty", !current)
            .addOnSuccessListener {
                _uiState.update { it.copy(snackbarMessage = if (!current) "You are now On Duty" else "You are now Off Duty") }
            }
    }

    fun updateShift(shift: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("currentShift", shift)
    }

    fun uploadProfileImage(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        _uiState.update { it.copy(isUploadingImage = true, uploadProgress = 0f) }

        val ref = storage.reference.child("profile_images/$uid.jpg")

        viewModelScope.launch {
            try {
                ref.putFile(uri)
                    .addOnProgressListener { task ->
                        val progress = (100.0 * task.bytesTransferred / task.totalByteCount).toFloat() / 100f
                        _uiState.update { it.copy(uploadProgress = progress) }
                    }
                    .await()

                val downloadUrl = ref.downloadUrl.await().toString()
                firestore.collection("users").document(uid)
                    .update("profileImageUrl", downloadUrl)
                    .await()

                _uiState.update {
                    it.copy(
                        isUploadingImage = false,
                        snackbarMessage = "Profile photo updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploadingImage = false,
                        snackbarMessage = "Upload failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateProfile(name: String, phone: String) {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .update(mapOf("name" to name, "phone" to phone))
            .addOnSuccessListener {
                _uiState.update { it.copy(snackbarMessage = "Profile updated") }
            }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Remove FCM token
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    try {
                        val token = FirebaseMessaging.getInstance().token.await()
                        firestore.collection("users").document(uid)
                            .update("fcmToken", null, "isOnDuty", false)
                            .await()
                    } catch (_: Exception) {}
                }

                auth.signOut()
                onComplete()
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Logout failed") }
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    override fun onCleared() {
        profileListener?.remove()
        statsListener?.remove()
        super.onCleared()
    }
}

// ═══════════════════════════════════════════════════════════════════
// Police Profile Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliceProfileScreen(
    onLogout: () -> Unit,
    vm: PoliceProfileViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    val colors = MaterialTheme.cityFluxColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Snackbar
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearSnackbar()
        }
    }

    // Dialog states
    var showEditDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSOSDialog by remember { mutableStateOf(false) }
    var showShiftDialog by remember { mutableStateOf(false) }

    // Permission states
    var locationGranted by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(false) }

    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.uploadProfileImage(it) } }

    // Check permissions
    LaunchedEffect(Unit) {
        locationGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        cameraGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
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
    ) { padding ->

        if (state.isLoading) {
            ShimmerPoliceProfile()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // ═══════════════════════ Hero Header ═══════════════════════
                item {
                    PoliceHeroHeader(
                        officer = state.officer,
                        isUploadingImage = state.isUploadingImage,
                        uploadProgress = state.uploadProgress,
                        onEditPhoto = { imagePickerLauncher.launch("image/*") },
                        onEditProfile = { showEditDialog = true }
                    )
                }

                // ═══════════════════════ Duty Status Card ═══════════════════════
                item {
                    DutyStatusCard(
                        officer = state.officer,
                        onToggleDuty = { vm.toggleDutyStatus() },
                        onChangeShift = { showShiftDialog = true }
                    )
                }

                // ═══════════════════════ Performance Stats ═══════════════════════
                item {
                    PerformanceStatsCard(
                        officer = state.officer,
                        casesAssignedToday = state.casesAssignedToday,
                        casesResolvedToday = state.casesResolvedToday,
                        pendingCases = state.pendingCases,
                        weeklyStats = state.weeklyStats
                    )
                }

                // ═══════════════════════ Quick Actions ═══════════════════════
                item {
                    QuickActionsCard(
                        onSOS = { showSOSDialog = true },
                        onRadio = { Toast.makeText(context, "Radio channel opened", Toast.LENGTH_SHORT).show() },
                        onBackup = { Toast.makeText(context, "Backup request sent", Toast.LENGTH_SHORT).show() }
                    )
                }

                // ═══════════════════════ Permissions Status ═══════════════════════
                item {
                    PermissionsCard(
                        locationGranted = locationGranted,
                        cameraGranted = cameraGranted,
                        notificationGranted = notificationGranted,
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                // ═══════════════════════ App Settings ═══════════════════════
                item {
                    AppSettingsCard(colors = colors)
                }

                // ═══════════════════════ Account Section ═══════════════════════
                item {
                    AccountCard(
                        onEditProfile = { showEditDialog = true },
                        onSupport = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:support@cityflux.app"))
                            context.startActivity(intent)
                        },
                        onLogout = { showLogoutDialog = true }
                    )
                }

                // ═══════════════════════ App Info ═══════════════════════
                item {
                    AppInfoCard(context = context)
                }
            }
        }
    }

    // ═══════════════════════ Dialogs ═══════════════════════

    // Edit Profile Dialog
    if (showEditDialog) {
        EditProfileDialog(
            officer = state.officer,
            onDismiss = { showEditDialog = false },
            onSave = { name, phone ->
                vm.updateProfile(name, phone)
                showEditDialog = false
            }
        )
    }

    // Logout Confirmation
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = AccentRed.copy(alpha = 0.12f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Logout, null, tint = AccentRed, modifier = Modifier.size(24.dp))
                    }
                }
            },
            title = { Text("Sign Out?", fontWeight = FontWeight.Bold) },
            text = { Text("You will be signed out and your duty status will be set to Off Duty.", color = colors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { vm.logout { onLogout() } },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(CornerRadius.XLarge)
        )
    }

    // SOS Dialog
    if (showSOSDialog) {
        AlertDialog(
            onDismissRequest = { showSOSDialog = false },
            icon = {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    color = AccentRed
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            },
            title = { Text("Emergency SOS", fontWeight = FontWeight.Bold, color = AccentRed) },
            text = {
                Text(
                    "This will alert all nearby units and dispatch. Only use in real emergencies.",
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "SOS Alert Sent!", Toast.LENGTH_LONG).show()
                        showSOSDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SEND SOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSOSDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(CornerRadius.XLarge)
        )
    }

    // Shift Selection Dialog
    if (showShiftDialog) {
        ShiftSelectionDialog(
            currentShift = state.officer?.currentShift ?: "Day",
            onDismiss = { showShiftDialog = false },
            onSelect = { shift ->
                vm.updateShift(shift)
                showShiftDialog = false
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Hero Header Component
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PoliceHeroHeader(
    officer: PoliceOfficer?,
    isUploadingImage: Boolean,
    uploadProgress: Float,
    onEditPhoto: () -> Unit,
    onEditProfile: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        PrimaryBlue,
                        PrimaryBlue.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(top = Spacing.XLarge, bottom = Spacing.Section)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Image with Rank Badge
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(colors.cardBackground)
                        .border(4.dp, Color.White, CircleShape)
                        .clickable { onEditPhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploadingImage) {
                        CircularProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.size(48.dp),
                            color = PrimaryBlue,
                            strokeWidth = 3.dp
                        )
                    } else if (officer?.profileImageUrl?.isNotBlank() == true) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(officer.profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.Person,
                            null,
                            tint = colors.textTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Camera button
                Surface(
                    modifier = Modifier
                        .size(34.dp)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .clickable { onEditPhoto() },
                    shape = CircleShape,
                    color = PrimaryBlue,
                    shadowElevation = 4.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Name & Rank
            Text(
                officer?.name?.ifBlank { "Officer" } ?: "Officer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Rank Badge
            Surface(
                modifier = Modifier.padding(top = 6.dp),
                shape = RoundedCornerShape(CornerRadius.Round),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Star,
                        null,
                        tint = AccentOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        officer?.rank ?: "Constable",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Info chips row
            Row(
                modifier = Modifier.padding(horizontal = Spacing.Large),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoChip(
                    icon = Icons.Outlined.Badge,
                    label = officer?.badgeId ?: "N/A"
                )
                InfoChip(
                    icon = Icons.Outlined.LocationOn,
                    label = officer?.dutyArea ?: "N/A"
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            // Department & Station
            Text(
                "${officer?.department ?: ""} • ${officer?.station ?: ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )

            // Join date
            officer?.joinDate?.let { ts ->
                val joinDateStr = SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(ts.toDate())
                Text(
                    "Since $joinDateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.Round),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Duty Status Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DutyStatusCard(
    officer: PoliceOfficer?,
    onToggleDuty: () -> Unit,
    onChangeShift: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val isOnDuty = officer?.isOnDuty ?: false
    val statusColor = if (isOnDuty) AccentGreen else AccentRed

    // Pulsing animation for on-duty
    val infiniteTransition = rememberInfiniteTransition(label = "duty_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing status indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnDuty) statusColor.copy(alpha = pulseAlpha)
                                else statusColor
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            if (isOnDuty) "On Duty" else "Off Duty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            "${officer?.currentShift ?: "Day"} Shift",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }

                Switch(
                    checked = isOnDuty,
                    onCheckedChange = { onToggleDuty() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = AccentGreen,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = colors.inputBorder,
                        uncheckedThumbColor = colors.textTertiary
                    )
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Duty info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DutyInfoItem(
                    label = "Today's Hours",
                    value = String.format("%.1fh", officer?.todayDutyHours ?: 0f),
                    icon = Icons.Outlined.AccessTime
                )
                DutyInfoItem(
                    label = "Shift",
                    value = officer?.currentShift ?: "Day",
                    icon = Icons.Outlined.WbSunny,
                    onClick = onChangeShift
                )
                DutyInfoItem(
                    label = "Last Active",
                    value = officer?.lastActiveAt?.let { ts ->
                        android.text.format.DateUtils.getRelativeTimeSpanString(
                            ts.toDate().time,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS
                        ).toString()
                    } ?: "N/A",
                    icon = Icons.Outlined.History
                )
            }
        }
    }
}

@Composable
private fun DutyInfoItem(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.cityFluxColors

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(CornerRadius.Small))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            fontSize = 10.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Performance Stats Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PerformanceStatsCard(
    officer: PoliceOfficer?,
    casesAssignedToday: Int,
    casesResolvedToday: Int,
    pendingCases: Int,
    weeklyStats: List<Int>
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Analytics,
                        null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                // Rating badge
                Surface(
                    shape = RoundedCornerShape(CornerRadius.Round),
                    color = AccentOrange.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            null,
                            tint = AccentOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            String.format("%.1f", officer?.citizenRating ?: 4.5f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBox(
                    value = casesAssignedToday.toString(),
                    label = "Today",
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = casesResolvedToday.toString(),
                    label = "Resolved",
                    color = AccentGreen,
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    value = pendingCases.toString(),
                    label = "Pending",
                    color = AccentOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.Large))

            // Weekly chart (simple bar)
            Text(
                "This Week",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                val maxVal = (weeklyStats.maxOrNull() ?: 1).coerceAtLeast(1)

                weeklyStats.forEachIndexed { index, value ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        val height = (value.toFloat() / maxVal * 40).dp.coerceAtLeast(4.dp)

                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(height)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (index == Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2)
                                        PrimaryBlue
                                    else
                                        PrimaryBlue.copy(alpha = 0.3f)
                                )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            days[index],
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Bottom stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MiniStat(
                    label = "Total Cases",
                    value = officer?.totalCasesHandled?.toString() ?: "0",
                    icon = Icons.Outlined.Folder
                )
                MiniStat(
                    label = "Avg Response",
                    value = "${officer?.avgResponseTime ?: 12} min",
                    icon = Icons.Outlined.Speed
                )
                MiniStat(
                    label = "This Month",
                    value = officer?.casesThisMonth?.toString() ?: "0",
                    icon = Icons.Outlined.CalendarMonth
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    icon: ImageVector
) {
    val colors = MaterialTheme.cityFluxColors

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontSize = 9.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Quick Actions Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsCard(
    onSOS: () -> Unit,
    onRadio: () -> Unit,
    onBackup: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FlashOn,
                    null,
                    tint = AccentOrange,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SOS Button
                QuickActionButton(
                    icon = Icons.Filled.Warning,
                    label = "SOS",
                    color = AccentRed,
                    onClick = onSOS,
                    modifier = Modifier.weight(1f)
                )

                // Radio Button
                QuickActionButton(
                    icon = Icons.Filled.SettingsInputAntenna,
                    label = "Radio",
                    color = PrimaryBlue,
                    onClick = onRadio,
                    modifier = Modifier.weight(1f)
                )

                // Backup Button
                QuickActionButton(
                    icon = Icons.Filled.Groups,
                    label = "Backup",
                    color = AccentOrange,
                    onClick = onBackup,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.12f),
            contentColor = color
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Permissions Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun PermissionsCard(
    locationGranted: Boolean,
    cameraGranted: Boolean,
    notificationGranted: Boolean,
    onOpenSettings: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Security,
                        null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                TextButton(onClick = onOpenSettings) {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.OpenInNew,
                        null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Small))

            PermissionRow(
                icon = Icons.Outlined.LocationOn,
                name = "Location",
                description = "Required for duty tracking",
                isGranted = locationGranted
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            PermissionRow(
                icon = Icons.Outlined.CameraAlt,
                name = "Camera",
                description = "Required for evidence capture",
                isGranted = cameraGranted
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            PermissionRow(
                icon = Icons.Outlined.Notifications,
                name = "Notifications",
                description = "Required for alerts",
                isGranted = notificationGranted
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    name: String,
    description: String,
    isGranted: Boolean
) {
    val colors = MaterialTheme.cityFluxColors
    val statusColor = if (isGranted) AccentGreen else AccentRed

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = statusColor, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }

        Surface(
            shape = RoundedCornerShape(CornerRadius.Small),
            color = statusColor.copy(alpha = 0.12f)
        ) {
            Text(
                if (isGranted) "Granted" else "Denied",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// App Settings Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AppSettingsCard(colors: CityFluxColors) {
    var darkModeEnabled by remember { mutableStateOf(false) }
    var alertSoundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Settings,
                    null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "App Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            SettingToggleRow(
                icon = Icons.Outlined.DarkMode,
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = darkModeEnabled,
                onCheckedChange = { darkModeEnabled = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            SettingToggleRow(
                icon = Icons.Outlined.VolumeUp,
                title = "Alert Sounds",
                subtitle = "Play sounds for new alerts",
                checked = alertSoundEnabled,
                onCheckedChange = { alertSoundEnabled = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            SettingToggleRow(
                icon = Icons.Outlined.Vibration,
                title = "Vibration",
                subtitle = "Vibrate for notifications",
                checked = vibrationEnabled,
                onCheckedChange = { vibrationEnabled = it }
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaryBlue,
                checkedThumbColor = Color.White
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Account Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AccountCard(
    onEditProfile: () -> Unit,
    onSupport: () -> Unit,
    onLogout: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(modifier = Modifier.padding(Spacing.Large)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ManageAccounts,
                    null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Spacer(Modifier.height(Spacing.Medium))

            AccountActionRow(
                icon = Icons.Outlined.Edit,
                title = "Edit Profile",
                onClick = onEditProfile
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            AccountActionRow(
                icon = Icons.Outlined.HeadsetMic,
                title = "Help & Support",
                onClick = onSupport
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = colors.divider)

            AccountActionRow(
                icon = Icons.Outlined.Logout,
                title = "Sign Out",
                tint = AccentRed,
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.cityFluxColors.textSecondary,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius.Small))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))

        Spacer(Modifier.width(12.dp))

        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (tint == AccentRed) AccentRed else colors.textPrimary,
            modifier = Modifier.weight(1f)
        )

        Icon(
            Icons.Outlined.ChevronRight,
            null,
            tint = colors.textTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// App Info Card
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun AppInfoCard(context: android.content.Context) {
    val colors = MaterialTheme.cityFluxColors

    val versionName = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: Exception) { "1.0.0" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small)
            .shadow(6.dp, RoundedCornerShape(CornerRadius.Large)),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App logo placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = PrimaryBlue.copy(alpha = 0.12f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Shield,
                        null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "CityFlux Police",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                "Version $versionName",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )

            Spacer(Modifier.height(Spacing.Medium))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cityflux.app/privacy"))
                    context.startActivity(intent)
                }) {
                    Text("Privacy Policy", style = MaterialTheme.typography.labelSmall)
                }

                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://cityflux.app/terms"))
                    context.startActivity(intent)
                }) {
                    Text("Terms of Service", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                "© 2026 CityFlux. All rights reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                fontSize = 10.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Edit Profile Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun EditProfileDialog(
    officer: PoliceOfficer?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(officer?.name ?: "") }
    var phone by remember { mutableStateOf(officer?.phone ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.cityFluxColors.cardBackground)
        ) {
            Column(modifier = Modifier.padding(Spacing.XLarge)) {
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(Spacing.Large))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Medium)
                )

                Spacer(Modifier.height(Spacing.Medium))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(CornerRadius.Medium)
                )

                Spacer(Modifier.height(Spacing.XLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name, phone) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Shift Selection Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShiftSelectionDialog(
    currentShift: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val shifts = listOf(
        Triple("Day", Icons.Outlined.WbSunny, "6:00 AM - 2:00 PM"),
        Triple("Evening", Icons.Outlined.WbTwilight, "2:00 PM - 10:00 PM"),
        Triple("Night", Icons.Outlined.NightsStay, "10:00 PM - 6:00 AM"),
        Triple("Rotating", Icons.Outlined.Autorenew, "Variable hours")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Shift", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                shifts.forEach { (name, icon, time) ->
                    val isSelected = name == currentShift
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium))
                            .clickable { onSelect(name) },
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = if (isSelected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, PrimaryBlue) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                null,
                                tint = if (isSelected) PrimaryBlue else MaterialTheme.cityFluxColors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) PrimaryBlue else MaterialTheme.cityFluxColors.textPrimary
                                )
                                Text(
                                    time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.cityFluxColors.textTertiary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(CornerRadius.XLarge)
    )
}

// ═══════════════════════════════════════════════════════════════════
// Shimmer Loading
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ShimmerPoliceProfile() {
    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.3f),
        Color.LightGray.copy(alpha = 0.1f),
        Color.LightGray.copy(alpha = 0.3f)
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 500, 0f),
        end = Offset(translateAnim, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.Large)
    ) {
        // Hero shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(CornerRadius.Large))
                .background(brush)
        )

        Spacer(Modifier.height(Spacing.Large))

        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(CornerRadius.Large))
                    .background(brush)
            )
            Spacer(Modifier.height(Spacing.Medium))
        }
    }
}
