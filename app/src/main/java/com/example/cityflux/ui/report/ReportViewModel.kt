package com.example.cityflux.ui.report

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.model.Report
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * ViewModel for ReportIssueScreen — manages form state, Firebase upload,
 * and real-time "My Reports" list for the current user.
 */
class ReportViewModel : ViewModel() {

    companion object {
        private const val TAG = "ReportViewModel"
    }

    // ── Issue Types ──
    enum class IssueType(val label: String, val value: String) {
        ILLEGAL_PARKING("Illegal Parking", "illegal_parking"),
        ACCIDENT("Accident", "accident"),
        HAWKERS("Hawkers", "hawker"),
        ROAD_DAMAGE("Road Damage", "road_damage"),
        TRAFFIC_VIOLATION("Traffic Violation", "traffic_violation"),
        OTHER("Other", "other")
    }

    // ── View Mode ──
    enum class ReportTab { NEW_REPORT, MY_REPORTS }

    // ── UI State ──
    data class ReportUiState(
        // Tab
        val activeTab: ReportTab = ReportTab.NEW_REPORT,
        // Form state
        val selectedType: IssueType? = null,
        val description: String = "",
        val photoUri: Uri? = null,
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val hasLocation: Boolean = false,
        // Upload state
        val isSubmitting: Boolean = false,
        val uploadProgress: Float = 0f,
        val submitSuccess: Boolean = false,
        val submitError: String? = null,
        // My Reports
        val myReports: List<Report> = emptyList(),
        val isLoadingReports: Boolean = true,
        val reportsError: String? = null,
        // General
        val isOffline: Boolean = false,
        val snackbarMessage: String? = null
    )

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    init {
        observeMyReports()
    }

    // ══════════════════════════════════════════════
    // My Reports — real-time Firestore listener
    // ══════════════════════════════════════════════

    private fun observeMyReports() {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoadingReports = false, reportsError = "Not signed in") }
            return
        }

        firestore.collection("reports")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "My reports error", error)
                    _uiState.update { it.copy(isLoadingReports = false, reportsError = "Failed to load reports") }
                    return@addSnapshotListener
                }
                val reports = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Report::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Parse report error: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                _uiState.update {
                    it.copy(myReports = reports, isLoadingReports = false, reportsError = null)
                }
            }
    }

    // ══════════════════════════════════════════════
    // Form Actions
    // ══════════════════════════════════════════════

    fun setActiveTab(tab: ReportTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setIssueType(type: IssueType) {
        _uiState.update { it.copy(selectedType = type) }
    }

    fun setDescription(desc: String) {
        _uiState.update { it.copy(description = desc) }
    }

    fun setPhoto(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri) }
    }

    fun setLocation(lat: Double, lng: Double) {
        _uiState.update { it.copy(latitude = lat, longitude = lng, hasLocation = true) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ══════════════════════════════════════════════
    // Validation
    // ══════════════════════════════════════════════

    fun validate(): String? {
        val state = _uiState.value
        return when {
            state.selectedType == null -> "Please select an issue type"
            state.photoUri == null -> "Please capture or select a photo"
            !state.hasLocation -> "Location is required"
            state.description.isBlank() -> "Please describe the issue"
            else -> null
        }
    }

    // ══════════════════════════════════════════════
    // Submit Report
    // ══════════════════════════════════════════════

    fun submitReport() {
        val state = _uiState.value
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(submitError = "Not signed in") }
            return
        }
        val validationError = validate()
        if (validationError != null) {
            _uiState.update { it.copy(snackbarMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isSubmitting = true, uploadProgress = 0f, submitError = null) }

        viewModelScope.launch {
            try {
                // Step 1: Upload image to Firebase Storage
                val reportId = UUID.randomUUID().toString()
                var imageUrl = ""

                state.photoUri?.let { uri ->
                    _uiState.update { it.copy(uploadProgress = 0.1f) }
                    val imageRef = storage.reference.child("reports/$reportId.jpg")
                    val uploadTask = imageRef.putFile(uri)

                    // Track upload progress
                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = taskSnapshot.bytesTransferred.toFloat() /
                                taskSnapshot.totalByteCount.toFloat()
                        _uiState.update { it.copy(uploadProgress = 0.1f + progress * 0.6f) }
                    }

                    uploadTask.await()
                    _uiState.update { it.copy(uploadProgress = 0.75f) }
                    imageUrl = imageRef.downloadUrl.await().toString()
                }

                _uiState.update { it.copy(uploadProgress = 0.85f) }

                // Step 2: Write report to Firestore
                val report = hashMapOf(
                    "userId" to uid,
                    "type" to (state.selectedType?.value ?: "other"),
                    "title" to (state.selectedType?.label ?: "Report"),
                    "description" to state.description,
                    "imageUrl" to imageUrl,
                    "latitude" to state.latitude,
                    "longitude" to state.longitude,
                    "status" to "Pending",
                    "timestamp" to Timestamp.now()
                )

                firestore.collection("reports").document(reportId).set(report).await()
                _uiState.update { it.copy(uploadProgress = 1f) }

                // Step 3: Success — clear form
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        uploadProgress = 0f,
                        submitSuccess = true,
                        selectedType = null,
                        description = "",
                        photoUri = null,
                        snackbarMessage = "Report submitted successfully!"
                    )
                }

                Log.i(TAG, "Report $reportId submitted successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Submit report failed", e)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        uploadProgress = 0f,
                        submitError = "Upload failed — please retry",
                        snackbarMessage = "Upload failed — please retry"
                    )
                }
            }
        }
    }

    fun clearForm() {
        _uiState.update {
            it.copy(
                selectedType = null,
                description = "",
                photoUri = null,
                submitSuccess = false,
                submitError = null,
                snackbarMessage = null
            )
        }
    }

    fun retryReports() {
        _uiState.update { it.copy(isLoadingReports = true, reportsError = null) }
        observeMyReports()
    }
}
