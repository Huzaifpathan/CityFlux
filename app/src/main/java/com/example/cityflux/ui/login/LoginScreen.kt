package com.example.cityflux.ui.login

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
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
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
            enter = fadeIn(animationSpec = tween(400)) + 
                    slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = tween(400)
                    )
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = CardShadow,
                        spotColor = CardShadowMedium
                    ),
                shape = RoundedCornerShape(20.dp),
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

                    // App Title
                    Text(
                        text = "CityFlux",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Smart City Management",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Email Field
                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Login Button
                    PrimaryButton(
                        text = "Login with Email",
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter email and password"
                                return@PrimaryButton
                            }

                            loading = true
                            errorMessage = ""

                            auth.signInWithEmailAndPassword(email, password)
                                .addOnSuccessListener {
                                    loading = false
                                    onLoginSuccess()
                                }
                                .addOnFailureListener {
                                    loading = false
                                    errorMessage = it.message ?: "Login failed"
                                }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = loading,
                        enabled = !loading
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Divider
                        )
                        Text(
                            text = "  OR  ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Divider
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Phone Number Field
                    AppTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "Phone Number (+91XXXXXXXXXX)"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // OTP Button (Disabled)
                    SecondaryButton(
                        text = "Login with OTP (Coming Soon)",
                        onClick = {
                            errorMessage = "Phone OTP login will be enabled after billing activation"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Register Link
                    TextButton(onClick = onRegisterClick) {
                        Text(
                            text = "New user? Register here",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}
