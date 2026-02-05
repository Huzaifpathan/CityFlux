package com.example.cityflux.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onReportSubmitted: () -> Unit
) {

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var issueType by remember { mutableStateOf("Traffic") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val issueTypes = listOf("Traffic", "Parking", "Garbage", "Road Damage", "Other")

    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            text = "Report an Issue",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Issue Title") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Issue Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 🔽 Issue Type Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = issueType,
                onValueChange = {},
                readOnly = true,
                label = { Text("Issue Type") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                issueTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            issueType = type
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Button(
            onClick = {
                if (title.isBlank() || description.isBlank()) {
                    error = "All fields are required"
                    return@Button
                }

                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    error = "User not logged in"
                    return@Button
                }

                loading = true
                error = ""

                val issueData = hashMapOf(
                    "title" to title,
                    "description" to description,
                    "type" to issueType,
                    "userId" to userId,
                    "timestamp" to System.currentTimeMillis() // 🔥 IMPORTANT CHANGE
                )

                FirebaseFirestore.getInstance()
                    .collection("issues")
                    .add(issueData)
                    .addOnSuccessListener {
                        loading = false
                        onReportSubmitted() // 🔥 MUST BE CALLED
                    }
                    .addOnFailureListener { e ->
                        loading = false
                        error = e.message ?: "Failed to submit issue"
                    }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text("Submit Issue")
            }
        }

    }
}
