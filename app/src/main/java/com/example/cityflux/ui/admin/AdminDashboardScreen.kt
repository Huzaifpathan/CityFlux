package com.example.cityflux.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen() {

    val firestore = FirebaseFirestore.getInstance()
    var issues by remember { mutableStateOf(listOf<Report>()) }
    val colors = MaterialTheme.cityFluxColors

    // Real-time listener
    DisposableEffect(Unit) {
        val listener: ListenerRegistration =
            firestore.collection("reports")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        issues = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Report::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                null
                            }
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
                items(issues) { report ->
                    AdminIssueCard(report)
                }
            }
        }
    }
}
