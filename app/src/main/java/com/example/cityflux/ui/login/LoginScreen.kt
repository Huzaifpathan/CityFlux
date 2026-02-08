package com.example.cityflux.ui.login

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cityflux.R
import com.example.cityflux.ui.theme.*
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

fun isPhoneNumber(input: String): Boolean {
    val trimmed = input.trim()
    return trimmed.all { it.isDigit() } && trimmed.length >= 10
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onCitizenLogin: () -> Unit,
    onAdminLogin: () -> Unit,
    onPoliceLogin: () -> Unit,
    onRoleSelection: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotClick: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isOtpSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var verificationId by remember { mutableStateOf<String?>(null) }
    val colors = MaterialTheme.cityFluxColors

    fun handlePostLogin() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                isLoading = false
                when (doc.getString("role")) {
                    "citizen" -> onCitizenLogin()
                    "admin" -> onAdminLogin()
                    "police" -> onPoliceLogin()
                    else -> onRoleSelection()
                }
            }
            .addOnFailureListener {
                isLoading = false
                errorMessage = "Failed to load user role"
            }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.XXLarge)
                .statusBarsPadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Spacer(modifier = Modifier.height(Spacing.Section))

            Image(
                painter = painterResource(id = R.drawable.cityflux_icon),
                contentDescription = "Logo",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                "CityFlux",
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Smart Traffic & Parking Mobility System",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.Section)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(CornerRadius.XLarge),
                        ambientColor = colors.cardShadow,
                        spotColor = colors.cardShadow
                    ),
                shape = RoundedCornerShape(CornerRadius.XLarge),
                colors = CardDefaults.cardColors(
                    containerColor = colors.cardBackground
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.XXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Welcome Back",
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = Spacing.XLarge)
                    )

                    AppTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "Email or Phone"
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    if (isPhoneNumber(input)) {
                        if (isOtpSent) {
                            AppTextField(
                                value = otp,
                                onValueChange = { otp = it },
                                label = "Enter OTP"
                            )
                        }
                    } else {
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            visualTransformation = if (passwordVisible) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
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
                    }

                    Spacer(modifier = Modifier.height(Spacing.Small))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onForgotClick) {
                            Text(
                                "Forgot password?", 
                                color = colors.textAccent,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    PrimaryButton(
                        text = when {
                            isPhoneNumber(input) && !isOtpSent -> "Send OTP"
                            isPhoneNumber(input) -> "Verify OTP"
                            else -> "Sign In"
                        },
                        onClick = {
                            errorMessage = null
                            isLoading = true
                            val trimmedInput = input.trim()

                            if (isPhoneNumber(trimmedInput)) {

                                val activity = context as? Activity ?: run {
                                    isLoading = false
                                    return@PrimaryButton
                                }
                                val phone = "+91$trimmedInput"

                                if (!isOtpSent) {
                                    PhoneAuthProvider.verifyPhoneNumber(
                                        PhoneAuthOptions.newBuilder(auth)
                                            .setPhoneNumber(phone)
                                            .setTimeout(60L, TimeUnit.SECONDS)
                                            .setActivity(activity)
                                            .setCallbacks(object :
                                                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                                                override fun onVerificationCompleted(
                                                    credential: PhoneAuthCredential
                                                ) {
                                                    auth.signInWithCredential(credential)
                                                        .addOnSuccessListener { handlePostLogin() }
                                                        .addOnFailureListener {
                                                            isLoading = false
                                                            errorMessage = "OTP verification failed"
                                                        }
                                                }

                                                override fun onVerificationFailed(e: FirebaseException) {
                                                    isLoading = false
                                                    errorMessage = e.message
                                                }

                                                override fun onCodeSent(
                                                    id: String,
                                                    token: PhoneAuthProvider.ForceResendingToken
                                                ) {
                                                    isLoading = false
                                                    verificationId = id
                                                    isOtpSent = true
                                                }
                                            }).build()
                                    )
                                } else {
                                    val cred = PhoneAuthProvider.getCredential(
                                        verificationId ?: run {
                                            isLoading = false
                                            return@PrimaryButton
                                        },
                                        otp
                                    )
                                    auth.signInWithCredential(cred)
                                        .addOnSuccessListener { handlePostLogin() }
                                        .addOnFailureListener {
                                            errorMessage = "Incorrect OTP"
                                        }
                                }

                            } else {
                                auth.signInWithEmailAndPassword(trimmedInput, password)
                                    .addOnSuccessListener { handlePostLogin() }
                                    .addOnFailureListener {
                                        errorMessage = "Incorrect email or password"
                                    }
                            }
                        },
                        loading = isLoading,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        Text(
                            it, 
                            color = AccentRed, 
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.Large))

                    Text(
                        "Don't have an account?", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    TextButton(onClick = onRegisterClick) {
                        Text(
                            "Create Account", 
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.Section))
        }
    }
}
