package com.example.cityflux.ui.police

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun PoliceIssueCard(issue: Report) {
    
    val colors = MaterialTheme.cityFluxColors
    
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
            // Colored accent bar on left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = CornerRadius.Large, bottomStart = CornerRadius.Large))
                    .background(accentColor)
            )
            
            Column(modifier = Modifier.padding(Spacing.Large)) {

                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )

                Spacer(modifier = Modifier.height(Spacing.Small))

                Text(
                    text = issue.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(Spacing.Medium))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Type: ${issue.type}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    
                    StatusChip(status = issue.status)
                }

                Spacer(modifier = Modifier.height(Spacing.Large))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Button(
                        onClick = {
                            FirebaseFirestore.getInstance()
                                .collection("reports")
                                .document(issue.id)
                                .update("status", "In Progress")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
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
                                .collection("reports")
                                .document(issue.id)
                                .update("status", "Resolved")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
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
