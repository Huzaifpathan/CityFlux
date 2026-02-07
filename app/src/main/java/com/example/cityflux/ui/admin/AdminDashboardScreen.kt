package com.example.cityflux.ui.admin

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Issue
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen() {

    val db = FirebaseFirestore.getInstance()
    val issues = remember { mutableStateListOf<Issue>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("issues")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, _ ->
                issues.clear()
                snapshot?.documents?.forEach { doc ->
                    val issue = doc.toObject(Issue::class.java)
                    if (issue != null) {
                        issues.add(issue.copy(id = doc.id))
                    }
                }
                loading = false
            }
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Admin Dashboard",
                showNotification = true,
                showProfile = true
            )
        },
        containerColor = SurfaceWhite
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Manage Issues",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Review and update reported issues",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            when {
                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingSpinner()
                    }
                }
                issues.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No issues reported yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(issues) { index, issue ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(300, delayMillis = index * 50)) +
                                        slideInVertically(
                                            initialOffsetY = { it / 6 },
                                            animationSpec = tween(300, delayMillis = index * 50)
                                        )
                            ) {
                                AdminIssueCard(issue)
                            }
                        }
                    }
                }
            }
        }
    }
}
