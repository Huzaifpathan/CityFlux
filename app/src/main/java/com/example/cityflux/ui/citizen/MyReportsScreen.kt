package com.example.cityflux.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

data class CitizenIssue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val status: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReportsScreen() {

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val colors = MaterialTheme.cityFluxColors

    var issues by remember { mutableStateOf(listOf<CitizenIssue>()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {

        val uid = auth.currentUser?.uid ?: return@DisposableEffect onDispose { }

        val listener: ListenerRegistration =
            firestore.collection("issues")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        issues = snapshot.documents.map {
                            CitizenIssue(
                                id = it.id,
                                title = it.getString("title") ?: "",
                                description = it.getString("description") ?: "",
                                imageUrl = it.getString("imageUrl") ?: "",
                                status = it.getString("status") ?: "Pending"
                            )
                        }
                    }
                    loading = false
                }

        onDispose {
            listener.remove()
        }
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "My Reports",
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
                title = "Your Reports",
                subtitle = "Track the status of issues you've reported"
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
                issues.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No reports yet",
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
                                CitizenIssueCard(issue)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CitizenIssueCard(issue: CitizenIssue) {
    
    val colors = MaterialTheme.cityFluxColors
    
    // Determine accent color based on status
    val accentColor = when (issue.status.lowercase()) {
        "resolved" -> AccentGreen
        "in progress" -> AccentOrange
        else -> AccentIssues
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow,
                spotColor = colors.cardShadowMedium
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.cardBackground
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Colored accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(accentColor)
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(Spacing.Small))
                    
                    StatusChip(status = issue.status)
                }
                
                Spacer(modifier = Modifier.height(Spacing.Small))
                
                Text(
                    text = issue.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2
                )

                if (issue.imageUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Spacing.Medium))
                    AsyncImage(
                        model = issue.imageUrl,
                        contentDescription = "Report image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(CornerRadius.Medium)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
