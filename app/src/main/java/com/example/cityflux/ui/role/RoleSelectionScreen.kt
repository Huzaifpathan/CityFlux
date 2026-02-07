package com.example.cityflux.ui.role

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun RoleSelectionScreen(
    onCitizenClick: () -> Unit,
    onAdminClick: () -> Unit,
    onPoliceClick: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceWhite)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(400))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Select Your Role",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Choose how you want to use CityFlux",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Citizen Role with animation
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 100)) + 
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400, delayMillis = 100)
                            )
                ) {
                    RoleCard(
                        title = "Citizen",
                        description = "Report issues & city services",
                        icon = Icons.Filled.Person,
                        onClick = {
                            saveRoleToFirestore("citizen")
                            onCitizenClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Admin Role with animation
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 200)) + 
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400, delayMillis = 200)
                            )
                ) {
                    RoleCard(
                        title = "Admin",
                        description = "Manage city operations",
                        icon = Icons.Filled.Lock,
                        onClick = {
                            saveRoleToFirestore("admin")
                            onAdminClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Police Role with animation
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, delayMillis = 300)) + 
                            slideInVertically(
                                initialOffsetY = { it / 4 },
                                animationSpec = tween(400, delayMillis = 300)
                            )
                ) {
                    RoleCard(
                        title = "Police",
                        description = "Monitor & resolve city issues",
                        icon = Icons.Filled.LocalPolice,
                        onClick = {
                            saveRoleToFirestore("police")
                            onPoliceClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Save role in Firestore (non-blocking)
 */
private fun saveRoleToFirestore(role: String) {
    val user = FirebaseAuth.getInstance().currentUser ?: return

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.uid)
        .set(
            mapOf(
                "role" to role,
                "email" to user.email
            )
        )
}
