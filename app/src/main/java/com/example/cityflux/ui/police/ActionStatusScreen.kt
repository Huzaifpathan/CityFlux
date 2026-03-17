package com.example.cityflux.ui.police

import android.Manifest
import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.cityflux.model.Report
import com.example.cityflux.model.LocationUtils
import com.example.cityflux.ui.theme.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


// ═══════════════════════════════════════════════════════════════════
// ActionStatusScreen — Police Actions Command Center
// My assigned cases, status timeline, chat with citizen,
// upload proof after action, daily action reports
// ═══════════════════════════════════════════════════════════════════


// ── Chat Message Model ──
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "",  // "police" | "citizen"
    val message: String = "",
    val imageUrl: String = "",
    val timestamp: Timestamp? = null
)

// ── Action Proof Model ──
data class ActionProof(
    val id: String = "",
    val reportId: String = "",
    val officerId: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val actionTaken: String = "",
    val timestamp: Timestamp? = null
)


// ═══════════════════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════════════════

class ActionStatusViewModel : ViewModel() {

    companion object { private const val TAG = "ActionStatusVM" }

    data class ActionState(
        val isLoading: Boolean = true,
        val allReports: List<Report> = emptyList(),
        val error: String? = null
    ) {
        val assignedCases get() = allReports.filter {
            it.status.equals("In Progress", true) || it.status.equals("in_progress", true)
        }
        val pendingCases get() = allReports.filter {
            it.status.equals("Pending", true) || it.status.equals("submitted", true)
        }
        val resolvedCases get() = allReports.filter { it.status.equals("Resolved", true) }
        val totalCases get() = allReports.size
        val todayCases: Int get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val startOfDay = cal.time
            return allReports.count { r ->
                r.timestamp?.toDate()?.after(startOfDay) == true
            }
        }
    }

    private val _uiState = MutableStateFlow(ActionState())
    val uiState: StateFlow<ActionState> = _uiState.asStateFlow()

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init { observeReports() }

    // Load all reports; proximity filtering done in composable
    private fun observeReports() {
        firestore.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Reports error", err)
                    _uiState.update { it.copy(error = "Failed to load reports", isLoading = false) }
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _uiState.update { it.copy(allReports = list, isLoading = false) }
            }
    }

    fun updateReportStatus(reportId: String, newStatus: String, onDone: () -> Unit = {}) {
        firestore.collection("reports").document(reportId)
            .update("status", newStatus)
            .addOnSuccessListener { onDone() }
            .addOnFailureListener { Log.e(TAG, "Status update failed", it) }
    }

    fun sendChatMessage(
        reportId: String, message: String, imageUri: Uri?,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val user = auth.currentUser ?: run { onError("Not signed in"); return }
        viewModelScope.launch {
            try {
                val msgId = UUID.randomUUID().toString()
                var imgUrl = ""
                imageUri?.let { uri ->
                    val ref = storage.reference.child("chat_images/$reportId/$msgId.jpg")
                    ref.putFile(uri).await()
                    imgUrl = ref.downloadUrl.await().toString()
                }
                val data = hashMapOf(
                    "senderId" to user.uid,
                    "senderName" to (user.displayName ?: "Officer"),
                    "senderRole" to "police",
                    "message" to message,
                    "imageUrl" to imgUrl,
                    "timestamp" to Timestamp.now()
                )
                firestore.collection("reports").document(reportId)
                    .collection("chat").document(msgId).set(data).await()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Send message failed", e)
                onError("Failed to send: ${e.localizedMessage}")
            }
        }
    }

    fun uploadProof(
        reportId: String, description: String, actionTaken: String, photoUri: Uri?,
        onSuccess: () -> Unit, onError: (String) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run { onError("Not signed in"); return }
        viewModelScope.launch {
            try {
                val proofId = UUID.randomUUID().toString()
                var imgUrl = ""
                photoUri?.let { uri ->
                    val ref = storage.reference.child("proofs/$reportId/$proofId.jpg")
                    ref.putFile(uri).await()
                    imgUrl = ref.downloadUrl.await().toString()
                }
                val data = hashMapOf(
                    "reportId" to reportId,
                    "officerId" to uid,
                    "description" to description,
                    "actionTaken" to actionTaken,
                    "imageUrl" to imgUrl,
                    "timestamp" to Timestamp.now()
                )
                firestore.collection("reports").document(reportId)
                    .collection("proofs").document(proofId).set(data).await()
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Upload proof failed", e)
                onError("Failed to upload: ${e.localizedMessage}")
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Main Screen
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionStatusScreen(
    onBack: () -> Unit,
    initialReportId: String? = null,
    onReportHandled: () -> Unit = {},
    vm: ActionStatusViewModel = viewModel()
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors
    val state by vm.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // ── Police working location for proximity filtering ──
    var policeLat by remember { mutableStateOf(0.0) }
    var policeLon by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                policeLat = doc.getDouble("workingLatitude") ?: 0.0
                policeLon = doc.getDouble("workingLongitude") ?: 0.0
            }
    }

    // Proximity-filtered reports within 4 km, excluding resolved
    val nearbyReports = remember(state.allReports, policeLat, policeLon) {
        val proxFiltered = if (policeLat == 0.0 && policeLon == 0.0) state.allReports
        else state.allReports.filter {
            LocationUtils.isWithinRadius(policeLat, policeLon, it.latitude, it.longitude)
        }
        // Only show pending and in-progress (not resolved)
        proxFiltered.filter {
            !it.status.equals("Resolved", true)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCase by remember { mutableStateOf<Report?>(null) }
    var showChatFor by remember { mutableStateOf<Report?>(null) }
    var showProofFor by remember { mutableStateOf<Report?>(null) }
    var showDailyReport by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var dismissUrgencyBanner by remember { mutableStateOf(false) }

    // Auto-select report if navigated from Reports screen
    LaunchedEffect(initialReportId, nearbyReports) {
        if (initialReportId != null && nearbyReports.isNotEmpty()) {
            val targetReport = nearbyReports.find { it.id == initialReportId }
            if (targetReport != null) {
                selectedCase = targetReport
                selectedTab = when {
                    targetReport.status.equals("In Progress", true) ||
                    targetReport.status.equals("in_progress", true) -> 0
                    targetReport.status.equals("Pending", true) ||
                    targetReport.status.equals("submitted", true) -> 1
                    else -> 2
                }
                onReportHandled()
            }
        }
    }

    // Derived lists from nearby (non-resolved) reports
    val assignedCases = remember(nearbyReports) {
        nearbyReports.filter {
            it.status.equals("In Progress", true) || it.status.equals("in_progress", true)
        }
    }
    val pendingCases = remember(nearbyReports) {
        nearbyReports.filter {
            it.status.equals("Pending", true) || it.status.equals("submitted", true)
        }
    }

    val tabs = listOf("In Progress", "Pending", "All")

    val displayedReports = remember(nearbyReports, selectedTab, searchQuery, selectedTypeFilter, assignedCases, pendingCases) {
        var list = when (selectedTab) {
            0 -> assignedCases
            1 -> pendingCases
            else -> nearbyReports
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.type.lowercase().replace("_", " ").contains(q)
            }
        }
        if (selectedTypeFilter != "All") {
            val typeMapping = mapOf(
                "Parking" to "illegal_parking",
                "Accident" to "accident",
                "Hawker" to "hawker",
                "Road" to "road_damage",
                "Traffic" to "traffic_violation"
            )
            val mappedType = typeMapping[selectedTypeFilter]
            list = if (mappedType != null) {
                list.filter { it.type.equals(mappedType, true) }
            } else {
                val knownTypes = typeMapping.values.toSet()
                list.filter { it.type.lowercase() !in knownTypes }
            }
        }
        list
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ══════════════════════ Top Header ══════════════════════
            ActionTopBar(
                colors = colors,
                todayCasesCount = nearbyReports.count { r ->
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                    r.timestamp?.toDate()?.after(cal.time) == true
                },
                onDailyReport = { showDailyReport = true },
                onBack = onBack
            )

            // ══════════════════════ Urgency Banner ══════════════════════
            val urgentCount = nearbyReports.count { it.type.equals("accident", true) }
            if (urgentCount > 0 && !dismissUrgencyBanner) {
                UrgencyBanner(
                    urgentCount = urgentCount,
                    onDismiss = { dismissUrgencyBanner = true }
                )
            }

            // ══════════════════════ Stats ══════════════════════
            ActionStatsRow(
                inProgressCount = assignedCases.size,
                pendingCount = pendingCases.size,
                totalCount = nearbyReports.size,
                resolvedCount = state.resolvedCases.size
            )

            // ══════════════════════ Search ══════════════════════
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
                placeholder = { Text("Search cases...", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, "Clear", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
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

            // ══════════════════════ Type Filters ══════════════════════
            val typeFilters = listOf("All", "Parking", "Accident", "Hawker", "Road", "Traffic", "Other")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.XSmall),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                items(typeFilters) { filter ->
                    val isSelected = selectedTypeFilter == filter
                    val chipColor = when (filter) {
                        "Parking" -> AccentAlerts
                        "Accident" -> AccentRed
                        "Hawker" -> AccentOrange
                        "Road" -> Color(0xFFEF4444)
                        "Traffic" -> PrimaryBlue
                        "Other" -> colors.textTertiary
                        else -> PrimaryBlue
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTypeFilter = filter },
                        label = {
                            Text(
                                filter,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(CornerRadius.Round),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipColor.copy(alpha = 0.15f),
                            selectedLabelColor = chipColor
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.cardBorder.copy(alpha = 0.3f),
                            selectedBorderColor = chipColor.copy(alpha = 0.5f),
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            // ══════════════════════ Tab Row ══════════════════════
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = PrimaryBlue,
                edgePadding = Spacing.Large,
                divider = {},
                indicator = @Composable { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        val currentTabPosition = tabPositions[selectedTab]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = currentTabPosition.left)
                                .width(currentTabPosition.width)
                                .height(3.dp)
                                .background(PrimaryBlue, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    val count = when (index) {
                        0 -> assignedCases.size
                        1 -> pendingCases.size
                        else -> nearbyReports.size
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
                                if (count > 0) {
                                    Surface(shape = CircleShape, color = if (selectedTab == index) PrimaryBlue.copy(alpha = 0.15f) else colors.textTertiary.copy(alpha = 0.1f), modifier = Modifier.size(20.dp)) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(count.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == index) PrimaryBlue else colors.textTertiary)
                                        }
                                    }
                                }
                            }
                        },
                        unselectedContentColor = colors.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Small))

            // ══════════════════════ Content ══════════════════════
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingSpinner()
                            Spacer(Modifier.height(12.dp))
                            Text("Loading cases...", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                        }
                    }
                }

                displayedReports.isEmpty() -> {
                    val emptyIcon = when (selectedTab) {
                        0 -> Icons.Outlined.Engineering
                        1 -> Icons.Outlined.CheckCircle
                        else -> Icons.Outlined.Inbox
                    }
                    val emptyTitle = when (selectedTab) {
                        0 -> "No Active Cases"
                        1 -> "All Caught Up!"
                        else -> "No Cases Nearby"
                    }
                    val emptySubtitle = when (selectedTab) {
                        0 -> "Pick up pending cases to start working"
                        1 -> "No pending cases in your area"
                        else -> "Cases within your patrol area will appear here"
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(emptyIcon, null, tint = colors.textTertiary.copy(alpha = 0.3f), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(emptyTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                            Text(emptySubtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(
                            start = Spacing.Large, end = Spacing.Large,
                            top = Spacing.Small, bottom = 80.dp
                        )
                    ) {
                        itemsIndexed(displayedReports, key = { _, r -> r.id }) { index, report ->
                            SlideUpFadeIn(visible = true, delay = staggeredDelay(index)) {
                                ActionCompactCard(
                                    report = report,
                                    onClick = { selectedCase = report },
                                    colors = colors
                                )
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════ Action Detail Dialog ══════════════════════
        if (selectedCase != null) {
            ActionDetailDialog(
                report = selectedCase!!,
                onDismiss = { selectedCase = null },
                onChat = { showChatFor = selectedCase },
                onUploadProof = { showProofFor = selectedCase },
                onUpdateStatus = { newStatus ->
                    vm.updateReportStatus(selectedCase!!.id, newStatus) {
                        Toast.makeText(context, "Status updated to $newStatus", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = colors
            )
        }

        // ══════════════════════ Chat Dialog ══════════════════════
        if (showChatFor != null) {
            ChatDialog(
                report = showChatFor!!,
                onDismiss = { showChatFor = null },
                vm = vm,
                context = context
            )
        }

        // ══════════════════════ Proof Dialog ══════════════════════
        if (showProofFor != null) {
            UploadProofDialog(
                report = showProofFor!!,
                onDismiss = { showProofFor = null },
                vm = vm,
                context = context
            )
        }

        // ══════════════════════ Daily Report Dialog ══════════════════════
        if (showDailyReport) {
            DailyReportDialog(
                state = state,
                onDismiss = { showDailyReport = false }
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Top Bar
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ActionTopBar(
    colors: CityFluxColors,
    todayCasesCount: Int,
    onDailyReport: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0F172A), Color(0xFF1E3A5F))
                )
            )
            .statusBarsPadding()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            // Shield icon
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(PrimaryBlue.copy(alpha = 0.4f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Icon(
                    Icons.Filled.Shield, null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            // Title — takes remaining space
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Command Center",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActionLiveDot()
                    Text(
                        "$todayCasesCount active · On Duty",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            // Daily Report button
            FilledTonalButton(
                onClick = onDailyReport,
                shape = RoundedCornerShape(CornerRadius.Round),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Outlined.Summarize, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Report", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Stats Row
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ActionStatsRow(
    inProgressCount: Int,
    pendingCount: Int,
    totalCount: Int,
    resolvedCount: Int
) {
    val allKnown = totalCount + resolvedCount
    val resolutionRate = if (allKnown > 0) (resolvedCount * 100f / allKnown) else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Large, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionStatChip("In Progress", inProgressCount.toString(), AccentOrange, Icons.Outlined.PlayCircle, Modifier.weight(1f))
        ActionStatChip("Pending", pendingCount.toString(), AccentAlerts, Icons.Outlined.Schedule, Modifier.weight(1f))
        ActionStatChip("Total", totalCount.toString(), PrimaryBlue, Icons.Outlined.FolderOpen, Modifier.weight(1f))
        // Resolution rate chip with mini ring
        val tc = MaterialTheme.cityFluxColors
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(CornerRadius.Medium),
            color = AccentGreen.copy(alpha = 0.08f)
        ) {
            Column(
                Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val sweep = (resolutionRate / 100f) * 360f
                        drawArc(
                            color = AccentGreen.copy(alpha = 0.2f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = AccentGreen,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        "${resolutionRate.toInt()}%",
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("Resolved", fontSize = 10.sp, color = tc.textTertiary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ActionStatChip(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier) {
    val tc = MaterialTheme.cityFluxColors
    Surface(modifier = modifier, shape = RoundedCornerShape(CornerRadius.Medium), color = color.copy(alpha = 0.08f)) {
        Column(Modifier.padding(vertical = 8.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = tc.textTertiary, fontWeight = FontWeight.Medium)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Action Compact Card
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCompactCard(
    report: Report,
    onClick: () -> Unit,
    colors: CityFluxColors
) {
    val typeLabel = report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
    val statusColor = when (report.status.lowercase()) {
        "pending", "submitted" -> AccentAlerts
        "in progress", "in_progress" -> AccentOrange
        "resolved" -> AccentGreen
        else -> colors.textTertiary
    }
    val typeColor = when (report.type.lowercase()) {
        "illegal_parking" -> AccentAlerts
        "accident" -> AccentRed
        "hawker" -> AccentOrange
        "road_damage" -> Color(0xFFEF4444)
        "traffic_violation" -> PrimaryBlue
        else -> colors.textTertiary
    }
    val timeAgo = report.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    } ?: "Unknown"

    val ageMs = report.timestamp?.let { System.currentTimeMillis() - it.toDate().time } ?: 0L
    val priority = when {
        report.type.equals("accident", true) -> "URGENT"
        (report.status.equals("Pending", true) || report.status.equals("submitted", true)) && ageMs > 24 * 60 * 60 * 1000 -> "HIGH"
        report.status.equals("In Progress", true) || report.status.equals("in_progress", true) -> "MEDIUM"
        else -> "NORMAL"
    }
    val priorityColor = when (priority) {
        "URGENT" -> AccentRed
        "HIGH" -> AccentOrange
        "MEDIUM" -> AccentAlerts
        else -> colors.textTertiary
    }
    val isUrgent = priority == "URGENT"

    val urgencyTransition = rememberInfiniteTransition(label = "urgencyPulseCompact")
    val pulseAlpha by urgencyTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isUrgent) 0.6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "compactPulseAlpha"
    )

    val cardBorder = if (isUrgent) BorderStroke(2.dp, AccentRed.copy(alpha = pulseAlpha)) else null

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
        border = cardBorder
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent bar
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(statusColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type icon
                Surface(Modifier.size(44.dp), shape = RoundedCornerShape(CornerRadius.Medium), color = typeColor.copy(alpha = 0.1f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            when (report.type.lowercase()) {
                                "illegal_parking" -> Icons.Outlined.LocalParking
                                "accident" -> Icons.Outlined.CarCrash
                                "hawker" -> Icons.Outlined.Store
                                "road_damage" -> Icons.Outlined.Warning
                                "traffic_violation" -> Icons.Outlined.Traffic
                                else -> Icons.Outlined.Report
                            },
                            null, tint = typeColor, modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        report.title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (report.description.isNotBlank()) {
                        Text(
                            report.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.1f)) {
                            Text(typeLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = typeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text("· $timeAgo", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (priority != "NORMAL") {
                        Surface(shape = RoundedCornerShape(4.dp), color = priorityColor.copy(alpha = 0.15f)) {
                            Text(
                                priority,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(CornerRadius.Small), color = statusColor.copy(alpha = 0.12f)) {
                        Text(report.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = statusColor)
                    }
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = "View details",
                        tint = colors.textTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Action Detail Dialog — Full-screen detail view
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ActionDetailDialog(
    report: Report,
    onDismiss: () -> Unit,
    onChat: () -> Unit,
    onUploadProof: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    colors: CityFluxColors
) {
    val typeLabel = report.type.replace("_", " ").replaceFirstChar { it.uppercase() }
    val statusColor = when (report.status.lowercase()) {
        "pending", "submitted" -> AccentAlerts
        "in progress", "in_progress" -> AccentOrange
        "resolved" -> AccentGreen
        else -> colors.textTertiary
    }
    val typeColor = when (report.type.lowercase()) {
        "illegal_parking" -> AccentAlerts
        "accident" -> AccentRed
        "hawker" -> AccentOrange
        "road_damage" -> Color(0xFFEF4444)
        "traffic_violation" -> PrimaryBlue
        else -> colors.textTertiary
    }
    val typeIcon = when (report.type.lowercase()) {
        "illegal_parking" -> Icons.Outlined.LocalParking
        "accident" -> Icons.Outlined.CarCrash
        "hawker" -> Icons.Outlined.Store
        "road_damage" -> Icons.Outlined.Warning
        "traffic_violation" -> Icons.Outlined.Traffic
        else -> Icons.Outlined.Report
    }
    val timeAgo = report.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    } ?: "Unknown"
    val formattedTime = report.timestamp?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it.toDate())
    } ?: "Unknown"

    val ageMs = report.timestamp?.let { System.currentTimeMillis() - it.toDate().time } ?: 0L
    val isPending = report.status.equals("Pending", true) || report.status.equals("submitted", true)
    val isInProgress = report.status.equals("In Progress", true) || report.status.equals("in_progress", true)
    val priority = when {
        report.type.equals("accident", true) -> "URGENT"
        isPending && ageMs > 24 * 60 * 60 * 1000 -> "HIGH"
        isInProgress -> "MEDIUM"
        else -> "NORMAL"
    }
    val priorityColor = when (priority) {
        "URGENT" -> AccentRed
        "HIGH" -> AccentOrange
        "MEDIUM" -> AccentAlerts
        else -> colors.textTertiary
    }
    val currentStatus = report.status.lowercase()

    // Proof data
    var proofs by remember { mutableStateOf<List<ActionProof>>(emptyList()) }
    DisposableEffect(report.id) {
        val reg = FirebaseFirestore.getInstance()
            .collection("reports").document(report.id)
            .collection("proofs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                proofs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ActionProof::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Gradient Header ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(typeColor.copy(alpha = 0.15f), Color.Transparent)
                            )
                        )
                        .padding(Spacing.Large)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(Modifier.size(44.dp), shape = RoundedCornerShape(CornerRadius.Medium), color = typeColor.copy(alpha = 0.1f)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(24.dp))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    report.title.ifBlank { typeLabel },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colors.textTertiary.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(Icons.Filled.Close, "Close", tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(shape = RoundedCornerShape(CornerRadius.Small), color = statusColor.copy(alpha = 0.12f)) {
                                Text(report.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = statusColor)
                            }
                            if (priority != "NORMAL") {
                                Surface(shape = RoundedCornerShape(4.dp), color = priorityColor.copy(alpha = 0.15f)) {
                                    Text(
                                        priority,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = priorityColor,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.1f)) {
                                Text(typeLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = typeColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }

                // ── Scrollable Content ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.Large)
                ) {
                    // Photo
                    if (report.imageUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(CornerRadius.Medium))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(report.imageUrl).crossfade(true).build(),
                                contentDescription = "Report image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))))
                            )
                        }
                        Spacer(Modifier.height(Spacing.Medium))
                    }

                    // Description
                    if (report.description.isNotBlank()) {
                        Text("Description", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                        Spacer(Modifier.height(Spacing.Small))
                        Text(report.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, lineHeight = 18.sp)
                        Spacer(Modifier.height(Spacing.Large))
                    }

                    // Details
                    Text("Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.Small))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = colors.textTertiary.copy(alpha = 0.04f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionDetailRow(icon = Icons.Outlined.Tag, label = "Case ID", value = report.id.take(8) + "...", colors = colors)
                            ActionDetailRow(icon = Icons.Outlined.PriorityHigh, label = "Priority", value = priority, colors = colors)
                            ActionDetailRow(icon = Icons.Outlined.AccessTime, label = "Time", value = formattedTime, colors = colors)
                            ActionDetailRow(icon = Icons.Outlined.Schedule, label = "Ago", value = timeAgo, colors = colors)
                            if (report.latitude != 0.0 && report.longitude != 0.0) {
                                ActionDetailRow(icon = Icons.Outlined.LocationOn, label = "Location", value = "%.4f, %.4f".format(report.latitude, report.longitude), colors = colors)
                            }
                        }
                    }

                    Spacer(Modifier.height(Spacing.Large))

                    // Status Timeline
                    Text("Status Timeline", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.Small))
                    StatusTimeline(currentStatus = report.status, colors = colors)

                    Spacer(Modifier.height(Spacing.Large))

                    // Proof mini cards
                    if (proofs.isNotEmpty()) {
                        Text("Evidence & Proof (${proofs.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentGreen)
                        Spacer(Modifier.height(Spacing.Small))
                        proofs.forEach { proof ->
                            ProofMiniCard(proof = proof, colors = colors)
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    Spacer(Modifier.height(Spacing.Medium))
                }

                // ── Bottom Action Bar ──
                HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chat button
                    OutlinedButton(
                        onClick = onChat,
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PrimaryBlue.copy(alpha = 0.06f))
                    ) {
                        Icon(Icons.Outlined.Chat, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                    }

                    // Upload Proof button
                    OutlinedButton(
                        onClick = onUploadProof,
                        modifier = Modifier.height(42.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = AccentGreen.copy(alpha = 0.06f))
                    ) {
                        Icon(Icons.Outlined.CameraAlt, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Proof", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AccentGreen)
                    }

                    Spacer(Modifier.weight(1f))

                    // Status update buttons
                    if (currentStatus == "pending" || currentStatus == "submitted") {
                        Button(
                            onClick = { onUpdateStatus("In Progress") },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Take", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextOnPrimary)
                        }
                    }
                    if (currentStatus != "resolved") {
                        Button(
                            onClick = { onUpdateStatus("Resolved") },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resolve", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextOnPrimary)
                        }
                    }
                    if (currentStatus == "resolved") {
                        OutlinedButton(
                            onClick = { onUpdateStatus("In Progress") },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            border = BorderStroke(1.dp, AccentOrange),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Outlined.Replay, null, tint = AccentOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reopen", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    colors: CityFluxColors
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, fontWeight = FontWeight.Medium, modifier = Modifier.width(70.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


// ═══════════════════════════════════════════════════════════════════
// Status Timeline — Visual step-by-step progress
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatusTimeline(currentStatus: String, colors: CityFluxColors) {
    val steps = listOf(
        TimelineStep("Reported", Icons.Outlined.Flag, "Case reported by citizen"),
        TimelineStep("In Progress", Icons.Outlined.Engineering, "Officer assigned & working"),
        TimelineStep("Resolved", Icons.Outlined.CheckCircle, "Action completed")
    )
    val currentIndex = when (currentStatus.lowercase()) {
        "pending", "submitted" -> 0
        "in progress", "in_progress" -> 1
        "resolved" -> 2
        else -> 0
    }

    // Pulse animation for the current step
    val pulseTransition = rememberInfiniteTransition(label = "timelinePulse")
    val pulseGlow by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timelinePulseGlow"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timelinePulseScale"
    )

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { index, step ->
            val isCompleted = index <= currentIndex
            val isCurrent = index == currentIndex
            val stepColor = when {
                isCompleted && isCurrent -> when (index) {
                    0 -> AccentAlerts; 1 -> AccentOrange; 2 -> AccentGreen; else -> PrimaryBlue
                }
                isCompleted -> AccentGreen
                else -> colors.textTertiary.copy(alpha = 0.3f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Circle + line
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        // Pulsing glow for current step
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp * pulseScale)
                                    .clip(CircleShape)
                                    .background(stepColor.copy(alpha = pulseGlow * 0.4f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 28.dp else 22.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCompleted) stepColor
                                    else colors.textTertiary.copy(alpha = 0.1f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted && !isCurrent) {
                                Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            } else if (isCurrent) {
                                Icon(step.icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            } else {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(colors.textTertiary.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }
                    if (index < steps.lastIndex) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .background(
                                    if (index < currentIndex) Brush.verticalGradient(
                                        listOf(AccentGreen, AccentGreen.copy(alpha = 0.6f))
                                    )
                                    else Brush.verticalGradient(
                                        listOf(
                                            colors.textTertiary.copy(alpha = 0.15f),
                                            colors.textTertiary.copy(alpha = 0.15f)
                                        )
                                    )
                                )
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(top = if (isCurrent) 4.dp else 1.dp)) {
                    Text(
                        step.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCompleted) colors.textPrimary else colors.textTertiary
                    )
                    if (isCurrent) {
                        Text(step.desc, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

private data class TimelineStep(val label: String, val icon: ImageVector, val desc: String)


@Composable
private fun ActionQuickButton(
    icon: ImageVector, label: String, color: Color,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(CornerRadius.Medium),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = 0.06f))
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}


// ═══════════════════════════════════════════════════════════════════
// Chat Dialog — Police/Citizen chat per report
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDialog(
    report: Report,
    onDismiss: () -> Unit,
    vm: ActionStatusViewModel,
    context: Context
) {
    val colors = MaterialTheme.cityFluxColors
    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Camera for chat image
    var chatPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) chatPhotoUri = cameraImageUri
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createActionTempUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { chatPhotoUri = it }
    }

    // Listen to chat
    DisposableEffect(report.id) {
        val registration = FirebaseFirestore.getInstance()
            .collection("reports").document(report.id)
            .collection("chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                messages = snap?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    ChatMessage(
                        id = doc.id,
                        senderId = data["senderId"] as? String ?: "",
                        senderName = data["senderName"] as? String ?: data["sender"] as? String ?: "",
                        senderRole = data["senderRole"] as? String ?: "",
                        message = data["message"] as? String ?: "",
                        imageUrl = data["imageUrl"] as? String ?: "",
                        timestamp = data["timestamp"] as? Timestamp
                    )
                } ?: emptyList()
            }
        onDispose { registration.remove() }
    }

    // Auto scroll
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = CornerRadius.XLarge, topEnd = CornerRadius.XLarge),
                    color = PrimaryBlue
                ) {
                    Row(
                        Modifier.padding(Spacing.Large),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Chat, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Case Chat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    report.title.ifBlank { report.type.replace("_", " ") },
                                    style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Messages
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Forum, null, tint = colors.textTertiary.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("No messages yet", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                                    Text("Send a message to the citizen", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                    items(messages, key = { it.id }) { msg ->
                        val isMe = msg.senderId == currentUid
                        ChatBubble(msg = msg, isMe = isMe, colors = colors)
                    }
                }

                // Photo preview
                if (chatPhotoUri != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = chatPhotoUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Photo attached", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { chatPhotoUri = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, "Remove", tint = AccentRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.15f))

                // Input
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Camera button
                    IconButton(
                        onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.CameraAlt, "Camera", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                    }
                    // Gallery button
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Outlined.Image, "Gallery", tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                    }

                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type message...", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary) },
                        singleLine = false, maxLines = 3,
                        shape = RoundedCornerShape(CornerRadius.Round),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.cardBackground,
                            unfocusedContainerColor = colors.textTertiary.copy(alpha = 0.04f),
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = PrimaryBlue
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textPrimary)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isBlank() && chatPhotoUri == null) return@IconButton
                            isSending = true
                            vm.sendChatMessage(
                                reportId = report.id,
                                message = inputText.trim(),
                                imageUri = chatPhotoUri,
                                onSuccess = {
                                    inputText = ""; chatPhotoUri = null; isSending = false
                                },
                                onError = { msg ->
                                    isSending = false
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(PrimaryBlue, CircleShape),
                        enabled = !isSending && (inputText.isNotBlank() || chatPhotoUri != null)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, isMe: Boolean, colors: CityFluxColors) {
    val bubbleColor = if (isMe) PrimaryBlue else colors.textTertiary.copy(alpha = 0.08f)
    val textColor = if (isMe) Color.White else colors.textPrimary
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (isMe) 16.dp else 4.dp,
        bottomEnd = if (isMe) 4.dp else 16.dp
    )
    val timeStr = msg.timestamp?.let {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it.toDate())
    } ?: ""
    // If senderRole is empty, infer: if isMe → police, else → citizen
    val effectiveRole = msg.senderRole.ifBlank { if (isMe) "police" else "citizen" }
    val roleColor = when (effectiveRole) {
        "police" -> PrimaryBlue; "citizen" -> AccentGreen; else -> colors.textTertiary
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        // Sender label
        if (!isMe) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(roleColor)
                )
                Text(
                    msg.senderName.ifBlank { effectiveRole.replaceFirstChar { it.uppercase() } },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = roleColor
                )
            }
        }

        Surface(
            shape = shape, color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(msg.imageUrl).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (msg.message.isNotBlank()) Spacer(Modifier.height(4.dp))
                }
                if (msg.message.isNotBlank()) {
                    Text(msg.message, style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMe) Color.White.copy(alpha = 0.6f) else colors.textTertiary,
                    modifier = Modifier.align(Alignment.End),
                    fontSize = 9.sp
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Upload Proof Dialog
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadProofDialog(
    report: Report,
    onDismiss: () -> Unit,
    vm: ActionStatusViewModel,
    context: Context
) {
    val colors = MaterialTheme.cityFluxColors
    var description by remember { mutableStateOf("") }
    var actionTaken by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var proofs by remember { mutableStateOf<List<ActionProof>>(emptyList()) }

    // Camera
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraImageUri != null) photoUri = cameraImageUri
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createActionTempUri(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoUri = it }
    }

    // Existing proofs
    DisposableEffect(report.id) {
        val reg = FirebaseFirestore.getInstance()
            .collection("reports").document(report.id)
            .collection("proofs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                proofs = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(ActionProof::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
        onDispose { reg.remove() }
    }

    val actionOptions = listOf(
        "Verbal Warning", "Written Notice", "Fine Issued",
        "Removed Obstruction", "Towed Vehicle", "Directed Traffic",
        "Filed FIR", "Area Cleared", "Other"
    )

    Dialog(onDismissRequest = { if (!isSubmitting) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.Large)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Upload Proof", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(
                                report.title.ifBlank { report.type.replace("_", " ") },
                                style = MaterialTheme.typography.labelSmall, color = colors.textTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { if (!isSubmitting) onDismiss() }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Existing proofs
                if (proofs.isNotEmpty()) {
                    Text("Previous Proofs (${proofs.size})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.Small))
                    proofs.forEach { proof ->
                        ProofMiniCard(proof = proof, colors = colors)
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(Spacing.Medium))
                    HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.15f))
                    Spacer(Modifier.height(Spacing.Medium))
                }

                Text("Add New Proof", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentGreen)
                Spacer(Modifier.height(Spacing.Small))

                // Action taken
                Text("Action Taken", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(actionOptions) { option ->
                        val isSel = actionTaken == option
                        FilterChip(
                            selected = isSel,
                            onClick = { actionTaken = option },
                            label = { Text(option, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                            shape = RoundedCornerShape(CornerRadius.Round),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentGreen.copy(alpha = 0.15f),
                                selectedLabelColor = AccentGreen
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = colors.cardBorder.copy(alpha = 0.3f),
                                selectedBorderColor = AccentGreen.copy(alpha = 0.5f),
                                enabled = true, selected = isSel
                            )
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                // Description
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description / Details") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, cursorColor = AccentGreen)
                )

                Spacer(Modifier.height(Spacing.Medium))

                // Photo
                Text("Evidence Photo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                if (photoUri != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(photoUri).crossfade(true).build(),
                            contentDescription = "Proof",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { photoUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Outlined.CameraAlt, null, Modifier.size(18.dp), tint = AccentGreen)
                            Spacer(Modifier.width(6.dp))
                            Text("Camera", color = AccentGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            border = BorderStroke(1.dp, colors.cardBorder.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp), tint = colors.textSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Gallery", color = colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.XLarge))

                // Submit
                Button(
                    onClick = {
                        if (actionTaken.isBlank()) {
                            Toast.makeText(context, "Please select action taken", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        vm.uploadProof(
                            reportId = report.id,
                            description = description,
                            actionTaken = actionTaken,
                            photoUri = photoUri,
                            onSuccess = {
                                isSubmitting = false
                                Toast.makeText(context, "Proof uploaded!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = { msg ->
                                isSubmitting = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    enabled = !isSubmitting,
                    elevation = ButtonDefaults.buttonElevation(4.dp, 0.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading...", fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Submit Proof", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofMiniCard(proof: ActionProof, colors: CityFluxColors) {
    val timeStr = proof.timestamp?.let {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(it.toDate())
    } ?: ""
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CornerRadius.Medium),
        color = AccentGreen.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, AccentGreen.copy(alpha = 0.15f))
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (proof.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = proof.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Surface(Modifier.size(40.dp), shape = RoundedCornerShape(6.dp), color = AccentGreen.copy(alpha = 0.12f)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(proof.actionTaken.ifBlank { "Action Proof" }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                if (proof.description.isNotBlank()) {
                    Text(proof.description, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary, fontSize = 9.sp)
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Daily Action Report Dialog
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DailyReportDialog(
    state: ActionStatusViewModel.ActionState,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    val today = dateFormat.format(Date())

    // Compute today's stats
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
    val startOfDay = cal.time

    val todayReports = state.allReports.filter { r ->
        r.timestamp?.toDate()?.after(startOfDay) == true
    }
    val todayPending = todayReports.count { it.status.equals("Pending", true) || it.status.equals("submitted", true) }
    val todayInProgress = todayReports.count { it.status.equals("In Progress", true) || it.status.equals("in_progress", true) }
    val todayResolved = todayReports.count { it.status.equals("Resolved", true) }

    // Type breakdown
    val typeBreakdown = todayReports.groupBy { it.type }.mapValues { it.value.size }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CornerRadius.XLarge),
            colors = CardDefaults.cardColors(containerColor = colors.cardBackground),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.Large)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(CornerRadius.Medium), color = AccentAlerts.copy(alpha = 0.1f)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Summarize, null, tint = AccentAlerts, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Daily Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                            Text(today, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Close", tint = colors.textTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Summary bar
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = PrimaryBlue.copy(alpha = 0.06f)
                ) {
                    Column(Modifier.padding(Spacing.Large), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(todayReports.size.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        Text("Total Cases Today", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Status breakdown
                Text("Status Breakdown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.Small))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyStatBox("Pending", todayPending, AccentAlerts, Modifier.weight(1f))
                    DailyStatBox("In Progress", todayInProgress, AccentOrange, Modifier.weight(1f))
                    DailyStatBox("Resolved", todayResolved, AccentGreen, Modifier.weight(1f))
                }

                if (typeBreakdown.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.Large))
                    Text("Type Breakdown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(Spacing.Small))

                    typeBreakdown.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                        val label = type.replace("_", " ").replaceFirstChar { it.uppercase() }
                        val typeColor = when (type.lowercase()) {
                            "illegal_parking" -> AccentAlerts
                            "accident" -> AccentRed
                            "hawker" -> AccentOrange
                            "road_damage" -> Color(0xFFEF4444)
                            "traffic_violation" -> PrimaryBlue
                            else -> colors.textTertiary
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(typeColor)
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                            }
                            Surface(shape = RoundedCornerShape(4.dp), color = typeColor.copy(alpha = 0.12f)) {
                                Text(count.toString(), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = typeColor)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Overall totals
                HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.15f))
                Spacer(Modifier.height(Spacing.Medium))
                Text("Overall Statistics", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(Spacing.Small))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DailyStatBox("All Cases", state.totalCases, PrimaryBlue, Modifier.weight(1f))
                    DailyStatBox("Active", state.assignedCases.size, AccentOrange, Modifier.weight(1f))
                    DailyStatBox("Done", state.resolvedCases.size, AccentGreen, Modifier.weight(1f))
                }

                Spacer(Modifier.height(Spacing.Large))

                // Resolution rate ring chart
                val resolutionRate = if (state.totalCases > 0) (state.resolvedCases.size * 100f / state.totalCases) else 0f
                val animatedRate by animateFloatAsState(
                    targetValue = resolutionRate / 100f,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "ringAnimation"
                )
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Medium),
                    color = AccentGreen.copy(alpha = 0.06f)
                ) {
                    Column(Modifier.padding(Spacing.Large), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Resolution Rate", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                        Spacer(Modifier.height(Spacing.Medium))
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                val strokeWidth = 12.dp.toPx()
                                drawArc(
                                    color = AccentGreen.copy(alpha = 0.15f),
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = AccentGreen,
                                    startAngle = -90f,
                                    sweepAngle = animatedRate * 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "%.1f%%".format(resolutionRate),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen
                                )
                                Text(
                                    "${state.resolvedCases.size}/${state.totalCases}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Close Report", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DailyStatBox(label: String, value: Int, color: Color, modifier: Modifier) {
    val tc = MaterialTheme.cityFluxColors
    Surface(modifier = modifier, shape = RoundedCornerShape(CornerRadius.Medium), color = color.copy(alpha = 0.08f)) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = tc.textTertiary, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}


// ═══════════════════════════════════════════════════════════════════
// Live Dot + Utility
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ActionLiveDot() {
    val inf = rememberInfiniteTransition(label = "live")
    val alpha by inf.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    val ringScale by inf.animateFloat(
        initialValue = 1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "liveRingScale"
    )
    val ringAlpha by inf.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "liveRingAlpha"
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
        // Outer pulsing ring
        Box(
            Modifier
                .size(8.dp * ringScale)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = ringAlpha))
        )
        // Solid dot
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = alpha))
        )
    }
}

@Composable
private fun UrgencyBanner(urgentCount: Int, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(AccentRed, Color(0xFFDC2626))
                )
            )
            .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(
                    "$urgentCount urgent case${if (urgentCount != 1) "s" else ""} require immediate attention",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun InfoGridCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    colors: CityFluxColors
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CornerRadius.Small),
        color = colors.cardBorder.copy(alpha = 0.06f)
    ) {
        Row(
            Modifier.padding(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            Column {
                Text(label, fontSize = 9.sp, color = colors.textTertiary, fontWeight = FontWeight.Medium)
                Text(
                    value,
                    fontSize = 11.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun createActionTempUri(context: Context): Uri {
    val dir = File(context.cacheDir, "action_photos")
    dir.mkdirs()
    val file = File(dir, "action_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
