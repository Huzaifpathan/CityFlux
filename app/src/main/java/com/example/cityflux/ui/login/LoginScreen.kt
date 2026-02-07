package com.example.cityflux.ui.login

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.cityflux.R
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

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    var verificationId by remember { mutableStateOf<String?>(null) }

    fun handlePostLogin() {
        val uid = auth.currentUser?.uid ?: return

        firestore.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                when (doc.getString("role")) {
                    "citizen" -> onCitizenLogin()
                    "admin" -> onAdminLogin()
                    "police" -> onPoliceLogin()
                    else -> onRoleSelection()
                }
            }
            .addOnFailureListener {
                errorMessage = "Failed to load user role"
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF020C2B), Color(0xFF031A3D), Color(0xFF020C2B))
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

            Image(
                painter = painterResource(id = R.drawable.cityflux_logo),
                contentDescription = "Logo",
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "CityFlux",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "Smart Traffic & Parking Mobility System",
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
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        "Welcome Back !!",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 20.dp)
                    )

                    GlassInputField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = "Email or Phone"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isPhoneNumber(input)) {
                        if (isOtpSent) {
                            GlassInputField(
                                value = otp,
                                onValueChange = { otp = it },
                                placeholder = "Enter OTP"
                            )
                        }
                    } else {
                        GlassInputField(
                            value = password,
                            onValueChange = { password = it },
                            placeholder = "Password",
                            isPassword = true,
                            passwordVisible = passwordVisible,
                            onTogglePassword = { passwordVisible = !passwordVisible }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onForgotClick) {
                            Text("Forgot password?", color = Color(0xFF4AA3FF))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            val trimmedInput = input.trim()

                            if (isPhoneNumber(trimmedInput)) {

                                val activity = context as? Activity ?: return@Button
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
                                                            errorMessage = "OTP verification failed"
                                                        }
                                                }

                                                override fun onVerificationFailed(e: FirebaseException) {
                                                    errorMessage = e.message
                                                }

                                                override fun onCodeSent(
                                                    id: String,
                                                    token: PhoneAuthProvider.ForceResendingToken
                                                ) {
                                                    verificationId = id
                                                    isOtpSent = true
                                                }
                                            }).build()
                                    )
                                } else {
                                    val cred = PhoneAuthProvider.getCredential(
                                        verificationId ?: return@Button,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4AA3FF)
                        )
                    ) {
                        Text(
                            if (isPhoneNumber(input) && !isOtpSent) "Send OTP"
                            else if (isPhoneNumber(input)) "Verify OTP"
                            else "Sign In",
                            color = Color.White
                        )
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(it, color = Color(0xFFFF5252), fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Don’t have an account?", color = Color(0xFFB8C6FF))
                    TextButton(onClick = onRegisterClick) {
                        Text("Create Account", color = Color(0xFF4AA3FF))
                    }
                }
            }
        }
    }
}

@Composable
fun GlassInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = Color(0xFF9FB3FF)) },
        visualTransformation =
        if (isPassword && !passwordVisible)
            PasswordVisualTransformation()
        else
            VisualTransformation.None,
        trailingIcon = {
            if (isPassword && onTogglePassword != null) {
                TextButton(onClick = onTogglePassword) {
                    Text(
                        if (passwordVisible) "Hide" else "Show",
                        color = Color(0xFF4AA3FF)
                    )
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF4AA3FF),
            unfocusedBorderColor = Color(0xFF3B4C7A),
            cursorColor = Color.White
        )
    )
}
