package com.example.cityflux.ui.role

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.cityflux.R
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF020C2B),
                        Color(0xFF031A3D),
                        Color(0xFF020C2B)
                    )
                )
            )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Logo
            Image(
                painter = painterResource(id = R.drawable.cityflux_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CityFlux",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Select your role",
                color = Color(0xFF9FB3FF),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0F1C3F).copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Choose Role",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 20.dp)
                    )

                    RoleButton("Citizen") {
                        saveRole("citizen", onCitizenClick)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RoleButton("Admin") {
                        saveRole("admin", onAdminClick)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    RoleButton("Police") {
                        saveRole("police", onPoliceClick)
                    }

                    if (loading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(color = Color(0xFF4AA3FF))
                    }

                    error?.let {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(it, color = Color.Red)
                    }
                }
            }
        }
    }
}

@Composable
fun RoleButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4AA3FF)
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}
