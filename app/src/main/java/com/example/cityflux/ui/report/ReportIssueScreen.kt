package com.example.cityflux.ui.report

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueScreen(
    onReportSubmitted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val storage = FirebaseStorage.getInstance()
    val colors = MaterialTheme.cityFluxColors

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }

    val locationClient = LocationServices.getFusedLocationProviderClient(context)

    /* ---------- Image Picker ---------- */
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        imageUri = uri
    }

    /* ---------- Location Permission ---------- */
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            locationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            CityFluxTopBar(
                title = "Report Issue",
                showBack = true,
                onBackClick = onReportSubmitted
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.XLarge),
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(Spacing.Large))

            ScreenHeader(
                title = "Report an Issue",
                subtitle = "Help improve your city by reporting problems"
            )

            Spacer(modifier = Modifier.height(Spacing.XLarge))

            // Issue Title Field
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = "Issue Title"
            )

            Spacer(modifier = Modifier.height(Spacing.Medium))

            // Description Field
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { 
                    Text(
                        "Describe the issue in detail",
                        color = colors.textTertiary
                    ) 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(CornerRadius.Medium),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = colors.divider,
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant,
                    cursorColor = PrimaryBlue
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            )

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Image Upload Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(CornerRadius.Large),
                        ambientColor = colors.cardShadow,
                        spotColor = colors.cardShadowMedium
                    )
                    .clickable { imageLauncher.launch("image/*") },
                shape = RoundedCornerShape(CornerRadius.Large),
                colors = CardDefaults.cardColors(
                    containerColor = colors.cardBackground
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    width = 2.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (imageUri != null) AccentParking else colors.divider
                    )
                )
            ) {
                if (imageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(imageUri),
                        contentDescription = "Selected image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "Add photo",
                            modifier = Modifier.size(48.dp),
                            tint = colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        Text(
                            "Tap to add a photo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            // Location Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(CornerRadius.Large),
                        ambientColor = colors.cardShadow,
                        spotColor = colors.cardShadowMedium
                    ),
                shape = RoundedCornerShape(CornerRadius.Large),
                colors = CardDefaults.cardColors(
                    containerColor = colors.cardBackground
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.Large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(CornerRadius.Medium),
                        color = AccentTraffic.copy(alpha = 0.1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = "Location",
                                tint = AccentTraffic,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(Spacing.Medium))
                    
                    Column {
                        Text(
                            "Current Location",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            if (latitude != 0.0) 
                                "${"%.4f".format(latitude)}, ${"%.4f".format(longitude)}"
                            else 
                                "Fetching location...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Error/Success Message
            message?.let {
                Spacer(modifier = Modifier.height(Spacing.Medium))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.contains("failed", true) || it.contains("please", true)) 
                        AccentRed 
                    else 
                        AccentGreen
                )
            }

            Spacer(modifier = Modifier.height(Spacing.XLarge))

            // Submit Button
            PrimaryButton(
                text = "Submit Report",
                onClick = {
                    if (title.isBlank() || description.isBlank()) {
                        message = "Please fill all fields"
                        return@PrimaryButton
                    }

                    loading = true
                    message = null

                    val uid = auth.currentUser?.uid ?: return@PrimaryButton

                    fun saveIssue(imageUrl: String?) {
                        val issue = hashMapOf(
                            "userId" to uid,
                            "title" to title,
                            "description" to description,
                            "imageUrl" to imageUrl,
                            "latitude" to latitude,
                            "longitude" to longitude,
                            "status" to "Pending",
                            "timestamp" to System.currentTimeMillis()
                        )

                        firestore.collection("issues")
                            .add(issue)
                            .addOnSuccessListener {
                                loading = false
                                message = if (imageUrl == null)
                                    "Issue submitted, but image upload failed"
                                else
                                    "Issue submitted successfully"

                                onReportSubmitted()
                            }
                            .addOnFailureListener {
                                loading = false
                                message = "Failed to submit issue"
                            }
                    }

                    // ---------- IMAGE UPLOAD ----------
                    if (imageUri != null) {
                        val imageRef = storage.reference
                            .child("issues/${UUID.randomUUID()}.jpg")

                        imageRef.putFile(imageUri!!)
                            .continueWithTask { task ->
                                if (!task.isSuccessful) {
                                    throw task.exception ?: Exception("Upload failed")
                                }
                                imageRef.downloadUrl
                            }
                            .addOnSuccessListener { uri ->
                                saveIssue(uri.toString())
                            }
                            .addOnFailureListener {
                                // 🔥 STORAGE FAILED → SAVE TEXT ONLY
                                saveIssue(null)
                            }
                    } else {
                        // No image selected → save text only
                        saveIssue(null)
                    }
                },
                enabled = !loading,
                loading = loading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(Spacing.XXLarge))
        }
    }
}
