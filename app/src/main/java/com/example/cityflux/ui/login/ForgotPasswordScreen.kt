package com.example.cityflux.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cityflux.R
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val colors = MaterialTheme.cityFluxColors

    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

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

            Image(
                painter = painterResource(id = R.drawable.cityflux_icon),
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
                text = "Smart Traffic & Parking Mobility System",
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
                        text = "Reset Password",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = Spacing.Small)
                    )
                    
                    Text(
                        text = "Enter your email address and we'll send you a link to reset your password",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = Spacing.Large)
                    )

                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Enter your email"
                    )

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    PrimaryButton(
                        text = "Send Reset Link",
                        onClick = {
                            message = null

                            if (email.isBlank()) {
                                message = "Please enter email"
                                return@PrimaryButton
                            }

                            loading = true
                            auth.sendPasswordResetEmail(email.trim())
                                .addOnSuccessListener {
                                    loading = false
                                    message = "Reset link sent to your email"
                                }
                                .addOnFailureListener {
                                    loading = false
                                    message = it.message ?: "Failed to send reset link"
                                }
                        },
                        enabled = !loading,
                        loading = loading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // MESSAGE LABEL
                    message?.let {
                        Spacer(modifier = Modifier.height(Spacing.Medium))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = if (it.contains("sent", true))
                                AccentGreen
                            else
                                AccentRed
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    TextButton(onClick = onBackToLogin) {
                        Text(
                            "Back to Login", 
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
