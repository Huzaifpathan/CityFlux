package com.example.cityflux.ui.police

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.cityflux.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*

/**
 * Full-screen mandatory dialog shown to police officers on first login
 * to collect their working area name and location via Google Maps picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoliceLocationSetupDialog(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.cityFluxColors

    var areaName by remember { mutableStateOf("") }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var saving by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Default camera — India centre; will move to current location if available
    val defaultPosition = LatLng(20.5937, 78.9629)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultPosition, 5f)
    }

    // Location permission
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )

    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) moveToCurrentLocation(fusedClient, cameraPositionState) { selectedLatLng = it }
    }

    // Auto-move to current location on first composition if permission granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            moveToCurrentLocation(fusedClient, cameraPositionState) { selectedLatLng = it }
        }
    }

    Dialog(
        onDismissRequest = { /* non-dismissible */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(PrimaryBlue, GradientBright))
                        )
                        .padding(horizontal = Spacing.XLarge, vertical = Spacing.Large)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.Small))
                        Text(
                            "Set Your Working Area",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Select your patrol zone on the map",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // ── Area Name Field ──
                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    label = { Text("Working Area Name") },
                    placeholder = { Text("e.g. Andheri West, Sector 21…") },
                    leadingIcon = {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge, vertical = Spacing.Medium),
                    shape = RoundedCornerShape(CornerRadius.Large),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        cursorColor = PrimaryBlue
                    )
                )

                // ── Map ──
                Text(
                    "Tap on the map to select your working location",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge)
                )

                Spacer(modifier = Modifier.height(Spacing.Small))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = Spacing.XLarge)
                        .clip(RoundedCornerShape(CornerRadius.Large))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            selectedLatLng = latLng
                        },
                        uiSettings = MapUiSettings(
                            zoomControlsEnabled = true,
                            compassEnabled = true,
                            myLocationButtonEnabled = false
                        ),
                        properties = MapProperties(
                            isMyLocationEnabled = hasLocationPermission
                        )
                    ) {
                        selectedLatLng?.let { pos ->
                            Marker(
                                state = MarkerState(position = pos),
                                title = areaName.ifBlank { "Working Location" }
                            )
                            // 4 km radius circle
                            Circle(
                                center = pos,
                                radius = 4000.0,
                                fillColor = PrimaryBlue.copy(alpha = 0.12f),
                                strokeColor = PrimaryBlue.copy(alpha = 0.5f),
                                strokeWidth = 2f
                            )
                        }
                    }

                    // "Use My Location" FAB
                    FloatingActionButton(
                        onClick = {
                            if (hasLocationPermission) {
                                moveToCurrentLocation(fusedClient, cameraPositionState) {
                                    selectedLatLng = it
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.Medium),
                        containerColor = colors.cardBackground,
                        contentColor = PrimaryBlue
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = "My Location")
                    }
                }

                // ── Selected location info ──
                selectedLatLng?.let { pos ->
                    Text(
                        "📍 ${String.format("%.5f", pos.latitude)}, ${String.format("%.5f", pos.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.Small)
                    )
                }

                // ── Error ──
                errorMsg?.let {
                    Text(
                        it,
                        color = AccentRed,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.XLarge)
                    )
                }

                // ── Save Button ──
                Spacer(modifier = Modifier.height(Spacing.Medium))

                PrimaryButton(
                    text = if (saving) "SAVING…" else "CONFIRM WORKING AREA",
                    onClick = {
                        errorMsg = null
                        if (areaName.isBlank()) {
                            errorMsg = "Please enter your working area name"
                            return@PrimaryButton
                        }
                        if (selectedLatLng == null) {
                            errorMsg = "Please select a location on the map"
                            return@PrimaryButton
                        }

                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid == null) {
                            errorMsg = "Not signed in"
                            return@PrimaryButton
                        }

                        saving = true
                        val updates = hashMapOf<String, Any>(
                            "workingAreaName" to areaName.trim(),
                            "workingLatitude" to selectedLatLng!!.latitude,
                            "workingLongitude" to selectedLatLng!!.longitude
                        )

                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .update(updates)
                            .addOnSuccessListener {
                                saving = false
                                Toast.makeText(context, "Working area saved!", Toast.LENGTH_SHORT).show()
                                onSetupComplete()
                            }
                            .addOnFailureListener { e ->
                                saving = false
                                errorMsg = "Failed to save: ${e.message}"
                            }
                    },
                    enabled = !saving,
                    loading = saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.XLarge)
                )

                Spacer(modifier = Modifier.height(Spacing.Large))
            }
        }
    }
}

/** Fetch current GPS location and animate the camera to it. */
@SuppressLint("MissingPermission")
private fun moveToCurrentLocation(
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
    cameraState: CameraPositionState,
    onLocation: (LatLng) -> Unit
) {
    fusedClient.lastLocation.addOnSuccessListener { location ->
        location?.let {
            val latLng = LatLng(it.latitude, it.longitude)
            onLocation(latLng)
            cameraState.move(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
        }
    }
}
