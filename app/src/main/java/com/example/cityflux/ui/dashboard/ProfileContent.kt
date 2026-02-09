package com.example.cityflux.ui.dashboard

import android.text.format.DateUtils
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cityflux.ui.theme.*
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileContent(onLogout: () -> Unit) {
    val colors = MaterialTheme.cityFluxColors
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()

    // ── User data from Firestore ──
    var userName by remember { mutableStateOf<String?>(null) }
    var userEmail by remember { mutableStateOf<String?>(null) }
    var userPhone by remember { mutableStateOf<String?>(null) }
    var userRole by remember { mutableStateOf<String?>(null) }
    var memberSince by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    // ── Stats from Firestore ──
    var totalReports by remember { mutableIntStateOf(0) }
    var resolvedReports by remember { mutableIntStateOf(0) }
    var pendingReports by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        try { Firebase.analytics.logEvent("profile_opened", null) } catch (_: Exception) {}
    }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: run { loading = false; return@LaunchedEffect }

        // Fetch user profile
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userName = doc.getString("name")
                userEmail = doc.getString("email") ?: auth.currentUser?.email
                userPhone = doc.getString("phone")
                userRole = doc.getString("role")
                val ts = doc.getTimestamp("createdAt")
                memberSince = ts?.let {
                    SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(it.toDate())
                }
                loading = false
            }
            .addOnFailureListener {
                userName = auth.currentUser?.displayName
                userEmail = auth.currentUser?.email
                loading = false
            }

        // Fetch report stats
        firestore.collection("reports")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snap, _ ->
                val reports = snap?.documents ?: return@addSnapshotListener
                totalReports = reports.size
                resolvedReports = reports.count {
                    it.getString("status")?.lowercase() == "resolved"
                }
                pendingReports = reports.count {
                    val s = it.getString("status")?.lowercase() ?: ""
                    s == "pending" || s == "in progress"
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        // ═══════════════════════ Profile Header ═══════════════════════
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
                .padding(vertical = Spacing.XXLarge, horizontal = Spacing.XLarge),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = PrimaryBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.1f))
                            .border(2.dp, PrimaryBlue.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (userName ?: "U").take(2).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }

                    Spacer(Modifier.height(Spacing.Medium))

                    Text(
                        text = userName ?: "User",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(Modifier.height(Spacing.XSmall))

                    // Role badge
                    userRole?.let { role ->
                        Surface(
                            shape = RoundedCornerShape(CornerRadius.Round),
                            color = PrimaryBlue.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = role.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }

                    memberSince?.let { since ->
                        Spacer(Modifier.height(Spacing.Small))
                        Text(
                            text = "Member since $since",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textTertiary
                        )
                    }
                }
            }
        }

        // ═══════════════════════ Stats Row ═══════════════════════
        if (!loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.XLarge),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                StatCard(
                    label = "Total",
                    value = totalReports.toString(),
                    color = PrimaryBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Resolved",
                    value = resolvedReports.toString(),
                    color = AccentParking,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Pending",
                    value = pendingReports.toString(),
                    color = AccentAlerts,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.XXLarge))

            // ═══════════════════════ Account Details ═══════════════════════
            Column(modifier = Modifier.padding(horizontal = Spacing.XLarge)) {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(Spacing.Medium))

                ProfileInfoRow(
                    icon = Icons.Outlined.Email,
                    label = "Email",
                    value = userEmail ?: "—"
                )
                ProfileInfoRow(
                    icon = Icons.Outlined.Phone,
                    label = "Phone",
                    value = userPhone ?: "Not set"
                )
                ProfileInfoRow(
                    icon = Icons.Outlined.Badge,
                    label = "Role",
                    value = userRole?.replaceFirstChar { it.uppercase() } ?: "—"
                )
                ProfileInfoRow(
                    icon = Icons.Outlined.CalendarMonth,
                    label = "Joined",
                    value = memberSince ?: "—"
                )
            }

            Spacer(Modifier.height(Spacing.XXLarge))

            // ═══════════════════════ App Info ═══════════════════════
            Column(modifier = Modifier.padding(horizontal = Spacing.XLarge)) {
                Text(
                    "App",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(Spacing.Medium))

                ProfileInfoRow(
                    icon = Icons.Outlined.Info,
                    label = "Version",
                    value = "1.0.0"
                )
                ProfileInfoRow(
                    icon = Icons.Outlined.Shield,
                    label = "Privacy",
                    value = "Protected"
                )
            }

            Spacer(Modifier.height(Spacing.Section))

            // ═══════════════════════ Logout Button ═══════════════════════
            Column(modifier = Modifier.padding(horizontal = Spacing.XLarge)) {
                OutlinedButton(
                    onClick = {
                        try {
                            Firebase.analytics.logEvent("logout", null)
                        } catch (_: Exception) {}
                        FirebaseAuth.getInstance().signOut()
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(CornerRadius.Round),
                    border = BorderStroke(1.dp, AccentIssues.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AccentIssues
                    )
                ) {
                    Icon(
                        Icons.Outlined.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Spacing.Small))
                    Text(
                        "Sign Out",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(Spacing.Section))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Private sub-components
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.cityFluxColors

    Card(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(CornerRadius.Large),
                ambientColor = colors.cardShadow
            ),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(containerColor = colors.cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.Medium, horizontal = Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val colors = MaterialTheme.cityFluxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(CornerRadius.Medium))
                .background(PrimaryBlue.copy(alpha = 0.07f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(Spacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }
    }

    HorizontalDivider(
        color = colors.divider,
        thickness = 0.5.dp
    )
}
