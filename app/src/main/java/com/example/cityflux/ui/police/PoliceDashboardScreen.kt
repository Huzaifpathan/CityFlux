package com.example.cityflux.ui.police

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Issue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliceDashboardScreen() {

    var issues by remember { mutableStateOf<List<Issue>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("issues")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error = true
                    loading = false
                    return@addSnapshotListener
                }

                issues = snapshot?.documents
                    ?.mapNotNull { doc ->
                        try {
                            doc.toObject(Issue::class.java)?.copy(id = doc.id)
                        } catch (ex: Exception) {
                            null   // 🔥 prevents crash
                        }
                    } ?: emptyList()

                loading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Police Dashboard") })
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CircularProgressIndicator()
                error -> Text("Error loading issues")
                issues.isEmpty() -> Text("No issues available")
                else -> LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(issues) { issue ->
                        PoliceIssueCard(issue)
                    }
                }
            }
        }
    }
}
