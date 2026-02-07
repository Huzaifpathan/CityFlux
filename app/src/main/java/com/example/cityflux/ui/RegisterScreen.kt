package com.example.cityflux.ui.register

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()

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
            enter = fadeIn(animationSpec = tween(400)) + 
                    slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = tween(400)
                    )
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = CardShadow,
                        spotColor = CardShadowMedium
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = CardBackground
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Join CityFlux",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AppTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = "Confirm Password",
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Error Message
                    AnimatedVisibility(
                        visible = errorMessage.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    PrimaryButton(
                        text = "Register",
                        onClick = {
                            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                                errorMessage = "All fields are required"
                            } else if (password != confirmPassword) {
                                errorMessage = "Passwords do not match"
                            } else {
                                loading = true
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnSuccessListener {
                                        loading = false
                                        errorMessage = ""
                                        onRegisterSuccess()
                                    }
                                    .addOnFailureListener {
                                        loading = false
                                        errorMessage = it.message ?: "Registration failed"
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = loading,
                        enabled = !loading
                    )
                }
            }
        }
    }
}
