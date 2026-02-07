package com.example.cityflux.ui.report

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
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
                .padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                "Report Issue",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Issue Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { imageLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Image")
            }

            imageUri?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Image(
                    painter = rememberAsyncImagePainter(it),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Location: $latitude , $longitude",
                color = Color.White
            )

            message?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    color = if (it.contains("failed", true)) Color.Red else Color.Green
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {

                    if (title.isBlank() || description.isBlank()) {
                        message = "Please fill all fields"
                        return@Button
                    }

                    loading = true
                    message = null

                    val uid = auth.currentUser?.uid ?: return@Button

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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text("Submit Report")
                }
            }
        }
    }
}
