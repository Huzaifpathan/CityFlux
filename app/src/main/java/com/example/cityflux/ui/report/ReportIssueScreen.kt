package com.example.cityflux.ui.report

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cityflux.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onReportSubmitted: () -> Unit
) {

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var issueType by remember { mutableStateOf("Traffic") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val issueTypes = listOf("Traffic", "Parking", "Garbage", "Road Damage", "Other")

    LaunchedEffect(Unit) {
        visible = true
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Report Issue",
                showNotification = false,
                showProfile = true
            )
        },
        containerColor = SurfaceWhite
    ) { padding ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceWhite)
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400))
            ) {
                Column {
                    Text(
                        text = "Report an Issue",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Help us improve your city",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, delayMillis = 100)) +
                        slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = tween(400, delayMillis = 100)
                        )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 6.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = CardShadow,
                            spotColor = CardShadowMedium
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = CardBackground
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp)
                    ) {
                        AppTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = "Issue Title"
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AppTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = "Issue Description",
                            singleLine = false,
                            minLines = 3
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Issue Type Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = issueType,
                                onValueChange = {},
                                readOnly = true,
                                label = { 
                                    Text(
                                        "Issue Type",
                                        style = MaterialTheme.typography.bodyMedium
                                    ) 
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = InputBorderFocused,
                                    unfocusedBorderColor = InputBorder,
                                    focusedLabelColor = PrimaryBlue,
                                    unfocusedLabelColor = TextSecondary
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                issueTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                type,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            ) 
                                        },
                                        onClick = {
                                            issueType = type
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Error Message
                        AnimatedVisibility(
                            visible = error.isNotEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentRed,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        PrimaryButton(
                            text = "Submit Issue",
                            onClick = {
                                if (title.isBlank() || description.isBlank()) {
                                    error = "All fields are required"
                                    return@PrimaryButton
                                }

                                val userId = FirebaseAuth.getInstance().currentUser?.uid
                                if (userId == null) {
                                    error = "User not logged in"
                                    return@PrimaryButton
                                }

                                loading = true
                                error = ""

                                val issueData = hashMapOf(
                                    "title" to title,
                                    "description" to description,
                                    "type" to issueType,
                                    "userId" to userId,
                                    "timestamp" to System.currentTimeMillis()
                                )

                                FirebaseFirestore.getInstance()
                                    .collection("issues")
                                    .add(issueData)
                                    .addOnSuccessListener {
                                        loading = false
                                        onReportSubmitted()
                                    }
                                    .addOnFailureListener { e ->
                                        loading = false
                                        error = e.message ?: "Failed to submit issue"
                                    }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            loading = loading,
                            enabled = !loading
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
