package com.example.cityflux.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.model.Issue
import com.google.firebase.firestore.FirebaseFirestore

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

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A237E), Color(0xFF0D47A1))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(16.dp)
    ) {

        Text(
            text = "Admin Dashboard",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            issues.isEmpty() -> {
                Text("No issues reported yet", color = Color.White)
            }
            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(issues) { issue ->
                        AdminIssueCard(issue)
                    }
                }
            }
        }
    }
}
