package com.example.cityflux.ui.police

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Issue
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun PoliceIssueCard(issue: Issue) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(issue.title, style = MaterialTheme.typography.titleMedium)
            Text(issue.description)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Type: ${issue.type}")
            Text("Status: ${issue.status}")

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                Button(onClick = {
                    FirebaseFirestore.getInstance()
                        .collection("issues")
                        .document(issue.id)
                        .update("status", "In Progress")
                }) {
                    Text("In Progress")
                }

                Button(onClick = {
                    FirebaseFirestore.getInstance()
                        .collection("issues")
                        .document(issue.id)
                        .update("status", "Resolved")
                }) {
                    Text("Resolved")
                }
            }
        }
    }
}
