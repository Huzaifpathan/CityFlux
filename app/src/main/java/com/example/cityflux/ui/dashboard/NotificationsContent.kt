package com.example.cityflux.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cityflux.model.Report
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Notifications / My Reports tab.
 * Shows the citizen's own reports + any system notifications in real time from Firestore.
 */
@Composable
fun NotificationsContent() {
    val colors = MaterialTheme.cityFluxColors
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var myReports by remember { mutableStateOf<List<Report>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid == null) { loading = false; return@LaunchedEffect }
        FirebaseFirestore.getInstance()
            .collection("reports")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { loading = false; return@addSnapshotListener }
                myReports = snap?.documents?.mapNotNull { doc ->
                    doc.toObject(Report::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                loading = false
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Column(
            modifier = Modifier.padding(
                start = Spacing.XLarge, end = Spacing.XLarge,
                top = Spacing.Large, bottom = Spacing.Medium
            )
        ) {
            Text(
                "Notifications",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                "Your reports & system alerts",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                }
            }

            myReports.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.NotificationsNone, null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.textTertiary
                        )
                        Spacer(Modifier.height(Spacing.Medium))
                        Text("No notifications yet", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary)
                        Text("Your report updates will appear here", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = Spacing.XLarge, vertical = Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
                ) {
                    items(myReports, key = { it.id }) { report ->
                        NotificationReportCard(report)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationReportCard(report: Report) {
    val colors = MaterialTheme.cityFluxColors
    val typeColor = when (report.type) {
        "accident" -> AccentRed
        "illegal_parking" -> AccentParking
        "traffic_violation" -> AccentTraffic
        "road_damage" -> AccentOrange
        "hawker" -> AccentAlerts
        else -> PrimaryBlue
    }
    val statusIcon = when (report.status.lowercase()) {
        "resolved" -> Icons.Outlined.CheckCircle
        "in progress" -> Icons.Outlined.Autorenew
        "rejected" -> Icons.Outlined.Cancel
        else -> Icons.Outlined.Schedule
    }
    val timeAgo = report.timestamp?.let {
        DateUtils.getRelativeTimeSpanString(
            it.toDate().time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        ).toString()
    } ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(CornerRadius.Large), ambientColor = colors.cardShadow),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(typeColor)
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, null, tint = typeColor, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            report.title.ifBlank { report.type.replace("_", " ").replaceFirstChar { it.uppercase() } },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Text(timeAgo, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                }
                if (report.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        report.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                StatusChip(status = report.status)
            }
        }
    }
}
