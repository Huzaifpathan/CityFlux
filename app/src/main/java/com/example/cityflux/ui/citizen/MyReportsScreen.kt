package com.example.cityflux.ui.citizen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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

    var issues by remember { mutableStateOf(listOf<CitizenIssue>()) }

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
                }

        onDispose {
            listener.remove()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020C2B),
                        Color(0xFF031A3D),
                        Color(0xFF020C2B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {

            Text(
                text = "My Reports",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (issues.isEmpty()) {
                Text(
                    text = "No reports yet",
                    color = Color(0xFFB8C6FF)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(issues) { issue ->
                        CitizenIssueCard(issue)
                    }
                }
            }
        }
    }
}

@Composable
fun CitizenIssueCard(issue: CitizenIssue) {

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F1C3F).copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(issue.title, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Text(issue.description, color = Color(0xFFB8C6FF))

            Spacer(modifier = Modifier.height(8.dp))

            if (issue.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = issue.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Status: ${issue.status}",
                color = if (issue.status == "Resolved")
                    Color(0xFF4CAF50)
                else
                    Color(0xFFFF9800)
            )
        }
    }
}
