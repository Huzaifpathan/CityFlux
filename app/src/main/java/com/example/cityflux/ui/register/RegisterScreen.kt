package com.example.cityflux.ui.register

import android.util.Log
import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
    onCitizenRegistered: () -> Unit,
    onPoliceRegistered: () -> Unit,
    onLoginClick: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf("") }
    var roleDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Field-level validation states
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var roleError by remember { mutableStateOf<String?>(null) }

    // Touched states — show errors only after first interaction
    var nameTouched by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    var mobileTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmPasswordTouched by remember { mutableStateOf(false) }

    val roleOptions = listOf("Citizen" to "citizen", "Traffic Police" to "police")

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val colors = MaterialTheme.cityFluxColors

    // Real-time validation
    fun validateName() { nameError = if (name.isBlank()) "Full name is required" else null }
    fun validateEmail() {
        emailError = when {
            email.isBlank() -> "Email is required"
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email"
            else -> null
        }
    }
    fun validateMobile() {
        mobileError = when {
            mobile.isBlank() -> "Mobile number is required"
            mobile.trim().length != 10 || !mobile.trim().all { it.isDigit() } -> "Enter a valid 10-digit number"
            else -> null
        }
    }
    fun validatePassword() {
        passwordError = when {
            password.isBlank() -> "Password is required"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
    }
    fun validateConfirmPassword() {
        confirmPasswordError = when {
            confirmPassword.isBlank() -> "Please confirm your password"
            confirmPassword != password -> "Passwords do not match"
            else -> null
        }
    }
    fun validateRole() { roleError = if (selectedRole.isBlank()) "Please select a role" else null }

    fun validateAll(): Boolean {
        nameTouched = true; emailTouched = true; mobileTouched = true
        passwordTouched = true; confirmPasswordTouched = true
        validateName(); validateEmail(); validateMobile()
        validatePassword(); validateConfirmPassword(); validateRole()
        return listOf(nameError, emailError, mobileError, passwordError, confirmPasswordError, roleError).all { it == null }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.XXLarge),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = Spacing.XLarge)
                    )

                    // ── Full Name ──
                    AppTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (nameTouched) validateName()
                        },
                        label = "Full Name",
                        isError = nameTouched && nameError != null,
                        errorMessage = if (nameTouched) nameError else null
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    // ── Email ──
                    AppTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            if (emailTouched) validateEmail()
                        },
                        label = "Email Address",
                        isError = emailTouched && emailError != null,
                        errorMessage = if (emailTouched) emailError else null
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    // ── Mobile Number ──
                    AppTextField(
                        value = mobile,
                        onValueChange = {
                            if (it.length <= 10 && it.all { ch -> ch.isDigit() }) {
                                mobile = it
                                if (mobileTouched) validateMobile()
                            }
                        },
                        label = "Mobile Number",
                        isError = mobileTouched && mobileError != null,
                        errorMessage = if (mobileTouched) mobileError else null
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    // ── Password ──
                    AppTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (passwordTouched) validatePassword()
                            if (confirmPasswordTouched && confirmPassword.isNotEmpty()) validateConfirmPassword()
                        },
                        label = "Password",
                        isError = passwordTouched && passwordError != null,
                        errorMessage = if (passwordTouched) passwordError else null,
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

                    // ── Confirm Password ──
                    AppTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            if (confirmPasswordTouched) validateConfirmPassword()
                        },
                        label = "Confirm Password",
                        isError = confirmPasswordTouched && confirmPasswordError != null,
                        errorMessage = if (confirmPasswordTouched) confirmPasswordError else null,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Text(
                                    if (confirmPasswordVisible) "Hide" else "Show",
                                    color = PrimaryBlue,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(Spacing.Medium))

                    // ── Role Selection Dropdown ──
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { roleDropdownExpanded = !roleDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = roleOptions.find { it.second == selectedRole }?.first ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Your Role", style = MaterialTheme.typography.bodyMedium) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = roleError != null,
                            trailingIcon = {
                                IconButton(onClick = { roleDropdownExpanded = !roleDropdownExpanded }) {
                                    Icon(
                                        Icons.Filled.ArrowDropDown,
                                        contentDescription = "Select role",
                                        tint = if (roleError != null) AccentRed else colors.textSecondary
                                    )
                                }
                            },
                            shape = RoundedCornerShape(CornerRadius.Medium),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (roleError != null) AccentRed else colors.inputBorderFocused,
                                unfocusedBorderColor = if (roleError != null) AccentRed else colors.inputBorder,
                                focusedLabelColor = if (roleError != null) AccentRed else PrimaryBlue,
                                unfocusedLabelColor = if (roleError != null) AccentRed else colors.textSecondary,
                                cursorColor = PrimaryBlue,
                                focusedContainerColor = colors.inputBackground,
                                unfocusedContainerColor = colors.inputBackground,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                errorBorderColor = AccentRed,
                                errorLabelColor = AccentRed
                            )
                        )

                        DropdownMenu(
                            expanded = roleDropdownExpanded,
                            onDismissRequest = { roleDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            roleOptions.forEach { (displayName, roleValue) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (selectedRole == roleValue) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (selectedRole == roleValue) PrimaryBlue else colors.textPrimary
                                        )
                                    },
                                    onClick = {
                                        selectedRole = roleValue
                                        roleError = null
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (roleError != null) {
                        Text(
                            text = roleError!!,
                            color = AccentRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.Start)
                                .padding(start = 8.dp, top = 4.dp)
                        )
                    }

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
                        text = "CREATE ACCOUNT",
                        onClick = {
                            errorMessage = null

                            if (!validateAll()) return@PrimaryButton

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
                                            "phone" to mobile.trim(),
                                            "role" to selectedRole,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )

                                        firestore.collection("users")
                                            .document(uid)
                                            .set(userMap)
                                            .addOnSuccessListener {
                                                loading = false
                                                Log.d("REGISTER", "User saved with role: $selectedRole")
                                                when (selectedRole) {
                                                    "citizen" -> onCitizenRegistered()
                                                    "police" -> onPoliceRegistered()
                                                }
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

            Spacer(modifier = Modifier.height(Spacing.Section))
        }
    }
}
