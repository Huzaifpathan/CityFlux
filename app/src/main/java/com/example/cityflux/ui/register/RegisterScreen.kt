package com.example.cityflux.ui.register

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val colors = MaterialTheme.cityFluxColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.XLarge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(Spacing.XXLarge))

            Text(
                text = "CityFlux",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )

            Text(
                text = "Smart Traffic and Parking Mobility System",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.XLarge)
            )

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
                        text = "Create Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = Spacing.Large)
                    )

                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Full Name"
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Address"
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(
                                    if (passwordVisible) "Hide" else "Show",
                                    color = PrimaryBlue,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    AppTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Confirm Password",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        Text(
                            it, 
                            color = AccentRed, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.XLarge))

                    PrimaryButton(
                        text = "REGISTER",
                        onClick = {
                            errorMessage = null

                            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                                errorMessage = "All fields are required"
                                return@PrimaryButton
                            }

                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                                return@PrimaryButton
                            }

                            loading = true

                            auth.createUserWithEmailAndPassword(email.trim(), password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val uid = auth.currentUser?.uid
                                        if (uid == null) {
                                            loading = false
                                            errorMessage = "User ID not found"
                                            return@addOnCompleteListener
                                        }

                                        val userMap = hashMapOf(
                                            "uid" to uid,
                                            "name" to name.trim(),
                                            "email" to email.trim(),
                                            "role" to "none",
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )

                                        firestore.collection("users")
                                            .document(uid)
                                            .set(userMap)
                                            .addOnSuccessListener {
                                                loading = false
                                                Log.d("REGISTER", "User saved in Firestore")
                                                onRegisterSuccess()
                                            }
                                            .addOnFailureListener { e ->
                                                loading = false
                                                errorMessage = e.message
                                            }

                                    } else {
                                        loading = false
                                        errorMessage = task.exception?.message
                                    }
                                }
                        },
                        enabled = !loading,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                        TextButton(onClick = onLoginClick) {
                            Text(
                                "Login",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.XXLarge))
        }
    }
}
