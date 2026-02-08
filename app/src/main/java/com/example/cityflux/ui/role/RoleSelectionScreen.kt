package com.example.cityflux.ui.role

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cityflux.R
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun RoleSelectionScreen(
    onCitizenClick: () -> Unit,
    onAdminClick: () -> Unit,
    onPoliceClick: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid
    val colors = MaterialTheme.cityFluxColors

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun saveRole(role: String, onSuccess: () -> Unit) {
        if (uid == null) return

        loading = true
        firestore.collection("users")
            .document(uid)
            .update("role", role)
            .addOnSuccessListener {
                loading = false
                onSuccess()
            }
            .addOnFailureListener {
                loading = false
                error = "Failed to save role"
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.XLarge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Logo
            Image(
                painter = painterResource(id = R.drawable.cityflux_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "CityFlux",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                text = "Select your role to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.XLarge)
            )

            // Role Cards
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(CornerRadius.XLarge),
                        ambientColor = colors.cardShadow,
                        spotColor = colors.cardShadowMedium
                    ),
                shape = RoundedCornerShape(CornerRadius.XLarge),
                colors = CardDefaults.cardColors(
                    containerColor = colors.cardBackground
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.XLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Choose Your Role",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = Spacing.XLarge)
                    )

                    RoleOptionCard(
                        title = "Citizen",
                        description = "Report issues and track progress",
                        icon = Icons.Outlined.Person,
                        accentColor = AccentTraffic,
                        onClick = { saveRole("citizen", onCitizenClick) }
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    RoleOptionCard(
                        title = "Admin",
                        description = "Manage and resolve city issues",
                        icon = Icons.Outlined.AccountBalance,
                        accentColor = AccentParking,
                        onClick = { saveRole("admin", onAdminClick) }
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    RoleOptionCard(
                        title = "Police",
                        description = "Respond to traffic and safety issues",
                        icon = Icons.Outlined.LocalPolice,
                        accentColor = AccentIssues,
                        onClick = { saveRole("police", onPoliceClick) }
                    )

                    if (loading) {
                        Spacer(modifier = Modifier.height(Spacing.Large))
                        LoadingSpinner()
                    }

                    error?.let {
                        Spacer(modifier = Modifier.height(Spacing.Medium))
                        Text(
                            it, 
                            color = AccentRed,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.cityFluxColors
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(CornerRadius.Large),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(colors.divider)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent Icon Container
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(Spacing.Large))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
