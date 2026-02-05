package com.example.cityflux.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.model.Issue   // ✅ REQUIRED IMPORT
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminIssueCard(issue: Issue) {

    val db = FirebaseFirestore.getInstance()
    var expanded by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(issue.status) }

    val statusOptions = listOf("Pending", "In Progress", "Resolved")

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = issue.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = issue.description,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Type: ${issue.type}")

            Spacer(modifier = Modifier.height(10.dp))

            // 🔽 Status Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedStatus,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    statusOptions.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status) },
                            onClick = {
                                selectedStatus = status
                                expanded = false

                                // 🔥 Update Firestore
                                db.collection("issues")
                                    .document(issue.id)
                                    .update("status", status)
                            }
                        )
                    }
                }
            }
        }
    }
}
