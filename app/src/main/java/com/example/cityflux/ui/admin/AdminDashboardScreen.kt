package com.example.cityflux.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class Issue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val status: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen() {

    val firestore = FirebaseFirestore.getInstance()
    var issues by remember { mutableStateOf(listOf<Issue>()) }
    val colors = MaterialTheme.cityFluxColors

    // Real-time listener
    DisposableEffect(Unit) {
        val listener: ListenerRegistration =
            firestore.collection("issues")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        issues = snapshot.documents.map {
                            Issue(
                                id = it.id,
                                title = it.getString("title") ?: "",
                                description = it.getString("description") ?: "",
                                imageUrl = it.getString("imageUrl") ?: "",
                                status = it.getString("status") ?: "Pending"
                            )
                        }
                    }
                }

        onDispose {
            listener.remove()
        }
    }

    CleanBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.XLarge)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {

            Spacer(modifier = Modifier.height(Spacing.Large))

            ScreenHeader(
                title = "Admin Dashboard",
                subtitle = "Manage all reported issues"
            )

            Spacer(modifier = Modifier.height(Spacing.XLarge))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                items(issues) { issue ->
                    AdminIssueCard(issue)
                }
            }
        }
    }
}

@Composable
fun AdminIssueCard(issue: Issue) {

    val firestore = FirebaseFirestore.getInstance()
    var loading by remember { mutableStateOf(false) }
    val colors = MaterialTheme.cityFluxColors
    
    val accentColor = when (issue.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentOrange
        else -> AccentRed
    }

    IssueCard(accentColor = accentColor) {
        Text(
            issue.title, 
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(Spacing.XSmall))
        
        Text(
            issue.description, 
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        if (issue.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = issue.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(CornerRadius.Medium))
            )
            Spacer(modifier = Modifier.height(Spacing.Medium))
        }

        StatusChip(status = issue.status)

        Spacer(modifier = Modifier.height(Spacing.Medium))

        if (issue.status.lowercase() != "resolved") {
            PrimaryButton(
                text = "Mark as Resolved",
                onClick = {
                    loading = true
                    firestore.collection("issues")
                        .document(issue.id)
                        .update("status", "Resolved")
                        .addOnSuccessListener {
                            loading = false
                        }
                        .addOnFailureListener {
                            loading = false
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                loading = loading
            )
        }
    }
}
