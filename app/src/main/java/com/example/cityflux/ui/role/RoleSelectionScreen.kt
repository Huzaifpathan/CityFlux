package com.example.cityflux.ui.role

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun RoleSelectionScreen(
    onCitizenClick: () -> Unit,
    onAdminClick: () -> Unit,
    onPoliceClick: () -> Unit
) {

    val gradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0D47A1), Color(0xFF00C853))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Select Your Role",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 👤 Citizen
            RoleCard(
                title = "Citizen",
                description = "Report issues & city services",
                icon = Icons.Filled.Person
            ) {
                saveRoleToFirestore("citizen")
                onCitizenClick()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🧑‍💼 Admin
            RoleCard(
                title = "Admin",
                description = "Manage city operations",
                icon = Icons.Filled.Lock
            ) {
                saveRoleToFirestore("admin")
                onAdminClick()
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 🚓 Police (FIXED ICON)
            RoleCard(
                title = "Police",
                description = "Monitor & resolve city issues",
                icon = Icons.Filled.LocalPolice
            ) {
                saveRoleToFirestore("police")
                onPoliceClick()
            }
        }
    }
}

@Composable
fun RoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color.Gray
                )
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
