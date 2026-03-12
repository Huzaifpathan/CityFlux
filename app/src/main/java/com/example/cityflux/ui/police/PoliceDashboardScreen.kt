package com.example.cityflux.ui.police

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Report
import com.example.cityflux.model.LocationUtils
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliceDashboardScreen() {

    var allIssues by remember { mutableStateOf<List<Report>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    val colors = MaterialTheme.cityFluxColors

    // ── Police working location ──
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

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("reports")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error = true
                    loading = false
                    return@addSnapshotListener
                }

                allIssues = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Report::class.java)?.copy(id = doc.id)
                        } catch (ex: Exception) {
                            null
                        }
                    } ?: emptyList()

                loading = false
            }
    }

    // Proximity-filtered issues (within 4 km)
    val issues = remember(allIssues, policeLat, policeLon) {
        if (policeLat == 0.0 && policeLon == 0.0) allIssues
        else allIssues.filter {
            LocationUtils.isWithinRadius(policeLat, policeLon, it.latitude, it.longitude)
        }
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Police Dashboard",
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
                .padding(horizontal = Spacing.XLarge)
        ) {

            Spacer(modifier = Modifier.height(Spacing.Large))

            ScreenHeader(
                title = "Active Issues",
                subtitle = "Monitor and resolve city issues"
            )

            Spacer(modifier = Modifier.height(Spacing.XLarge))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingSpinner()
                    }
                }
                error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Error loading issues",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AccentRed
                        )
                    }
                }
                issues.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No issues available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                        contentPadding = PaddingValues(bottom = Spacing.XXLarge)
                    ) {
                        itemsIndexed(issues) { index, issue ->
                            SlideUpFadeIn(
                                visible = true,
                                delay = staggeredDelay(index)
                            ) {
                                PoliceIssueCard(issue)
                            }
                        }
                    }
                }
            }
        }
    }
}
