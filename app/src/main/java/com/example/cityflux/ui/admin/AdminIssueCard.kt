package com.example.cityflux.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminIssueCard(issue: Report) {

    val db = FirebaseFirestore.getInstance()
    var expanded by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf(issue.status) }

    val statusOptions = listOf("Pending", "In Progress", "Resolved")
    
    // Get accent color based on issue type
    val accentColor = when (issue.type.lowercase()) {
        "traffic_violation" -> AccentTraffic
        "illegal_parking" -> AccentParking
        "road_damage" -> AccentIssues
        "accident" -> AccentRed
        "hawker" -> AccentOrange
        else -> AccentAlerts
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {
        Row {
            // Colored accent bar on left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            
            Column(modifier = Modifier.padding(18.dp)) {

                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = issue.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Type: ${issue.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    StatusChip(status = selectedStatus)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Status Dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedStatus,
                        onValueChange = {},
                        readOnly = true,
                        label = { 
                            Text(
                                "Update Status",
                                style = MaterialTheme.typography.bodySmall
                            ) 
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = InputBorderFocused,
                            unfocusedBorderColor = InputBorder,
                            focusedLabelColor = PrimaryBlue,
                            unfocusedLabelColor = TextSecondary
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        statusOptions.forEach { status ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        status,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    ) 
                                },
                                onClick = {
                                    selectedStatus = status
                                    expanded = false

                                    // Update Firestore
                                    db.collection("reports")
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
}
