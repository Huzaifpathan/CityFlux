package com.example.cityflux.ui.police

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Issue
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun PoliceIssueCard(issue: Issue) {
    
    // Get accent color based on issue type
    val accentColor = when (issue.type.lowercase()) {
        "traffic" -> AccentTraffic
        "parking" -> AccentParking
        "garbage", "road damage" -> AccentIssues
        else -> AccentAlerts
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = CardShadow,
                spotColor = CardShadowMedium
            ),
        shape = RoundedCornerShape(14.dp),
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Type: ${issue.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    
                    StatusChip(status = issue.status)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Button(
                        onClick = {
                            FirebaseFirestore.getInstance()
                                .collection("issues")
                                .document(issue.id)
                                .update("status", "In Progress")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            "In Progress",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextOnPrimary
                        )
                    }

                    Button(
                        onClick = {
                            FirebaseFirestore.getInstance()
                                .collection("issues")
                                .document(issue.id)
                                .update("status", "Resolved")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Text(
                            "Resolved",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextOnPrimary
                        )
                    }
                }
            }
        }
    }
}
