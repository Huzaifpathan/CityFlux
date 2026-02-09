package com.example.cityflux.ui.profile

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.model.SavedPlace
import com.example.cityflux.model.User
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Production ViewModel for ProfileScreen.
 *
 * Real-time Firestore listeners for user profile + saved places,
 * profile image upload to Firebase Storage, secure logout with
 * FCM token removal, and report statistics.
 */
class ProfileViewModel : ViewModel() {

    companion object {
        private const val TAG = "ProfileVM"
    }

    // ═══════════════════════════════════════════════════════
    // UI State
    // ═══════════════════════════════════════════════════════

    data class ProfileUiState(
        val isLoading: Boolean = true,
        val user: User? = null,
        val savedPlaces: List<SavedPlace> = emptyList(),
        val totalReports: Int = 0,
        val resolvedReports: Int = 0,
        val pendingReports: Int = 0,
        val isUploadingImage: Boolean = false,
        val uploadProgress: Float = 0f,
        val error: String? = null,
        val isOffline: Boolean = false,
        val snackbarMessage: String? = null
    )

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private var profileListener: ListenerRegistration? = null
    private var savedPlacesListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null

    init {
        observeProfile()
        observeSavedPlaces()
        observeReportStats()
    }

    // ═══════════════════════════════════════════════════════
    // Real-time Firestore listeners
    // ═══════════════════════════════════════════════════════

    private fun observeProfile() {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
            return
        }

        profileListener?.remove()
        profileListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Profile listener error", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load profile",
                            isOffline = true
                        )
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = try {
                        snapshot.toObject(User::class.java)?.copy(uid = uid)
                    } catch (e: Exception) {
                        Log.e(TAG, "Parse user error", e)
                        null
                    }

                    // Fallback to Auth data if Firestore fields are empty
                    val finalUser = user?.copy(
                        email = user.email.ifEmpty {
                            auth.currentUser?.email ?: ""
                        },
                        name = user.name.ifEmpty {
                            auth.currentUser?.displayName ?: ""
                        }
                    )

                    _uiState.update {
                        it.copy(
                            user = finalUser,
                            isLoading = false,
                            error = null,
                            isOffline = false
                        )
                    }
                } else {
                    // No Firestore doc — use Auth data
                    val fallbackUser = User(
                        uid = uid,
                        name = auth.currentUser?.displayName ?: "",
                        email = auth.currentUser?.email ?: "",
                        role = "citizen"
                    )
                    _uiState.update {
                        it.copy(
                            user = fallbackUser,
                            isLoading = false,
                            error = null
                        )
                    }
                }
            }
    }

    private fun observeSavedPlaces() {
        val uid = auth.currentUser?.uid ?: return

        savedPlacesListener?.remove()
        savedPlacesListener = firestore.collection("users").document(uid)
            .collection("saved_places")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Saved places listener error", error)
                    return@addSnapshotListener
                }

                val places = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(SavedPlace::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Parse saved place error: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                _uiState.update { it.copy(savedPlaces = places) }
            }
    }

    private fun observeReportStats() {
        val uid = auth.currentUser?.uid ?: return

        reportsListener?.remove()
        reportsListener = firestore.collection("reports")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Reports listener error", error)
                    return@addSnapshotListener
                }

                val reports = snapshot?.documents ?: emptyList()
                _uiState.update {
                    it.copy(
                        totalReports = reports.size,
                        resolvedReports = reports.count { doc ->
                            doc.getString("status")?.lowercase() == "resolved"
                        },
                        pendingReports = reports.count { doc ->
                            val s = doc.getString("status")?.lowercase() ?: ""
                            s == "pending" || s == "in progress"
                        }
                    )
                }
            }
    }

    // ═══════════════════════════════════════════════════════
    // Profile Image Upload
    // ═══════════════════════════════════════════════════════

    fun uploadProfileImage(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return

        _uiState.update { it.copy(isUploadingImage = true, uploadProgress = 0f) }

        val ref = storage.reference.child("profiles/$uid.jpg")
        val uploadTask = ref.putFile(uri)

        uploadTask
            .addOnProgressListener { taskSnapshot ->
                val progress = taskSnapshot.bytesTransferred.toFloat() /
                        taskSnapshot.totalByteCount.toFloat()
                _uiState.update { it.copy(uploadProgress = progress) }
            }
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update profileImageUrl in Firestore
                    firestore.collection("users").document(uid)
                        .update("profileImageUrl", downloadUri.toString())
                        .addOnSuccessListener {
                            _uiState.update {
                                it.copy(
                                    isUploadingImage = false,
                                    uploadProgress = 0f,
                                    snackbarMessage = "Profile photo updated"
                                )
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update profileImageUrl", e)
                            _uiState.update {
                                it.copy(
                                    isUploadingImage = false,
                                    snackbarMessage = "Failed to update profile photo"
                                )
                            }
                        }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get download URL", e)
                    _uiState.update {
                        it.copy(
                            isUploadingImage = false,
                            snackbarMessage = "Upload failed"
                        )
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Image upload failed", e)
                _uiState.update {
                    it.copy(
                        isUploadingImage = false,
                        snackbarMessage = "Upload failed: ${e.localizedMessage}"
                    )
                }
            }
    }

    // ═══════════════════════════════════════════════════════
    // Edit Profile
    // ═══════════════════════════════════════════════════════

    fun updateProfile(name: String, phone: String) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update(
                        mapOf(
                            "name" to name.trim(),
                            "phone" to phone.trim()
                        )
                    ).await()
                _uiState.update {
                    it.copy(snackbarMessage = "Profile updated successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update profile failed", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to update profile")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Saved Places
    // ═══════════════════════════════════════════════════════

    fun deleteSavedPlace(placeId: String) {
        val uid = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("saved_places").document(placeId)
                    .delete().await()
                _uiState.update {
                    it.copy(snackbarMessage = "Place removed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Delete saved place failed", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to remove place")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Secure Logout
    // ═══════════════════════════════════════════════════════

    fun logout(onLogoutComplete: () -> Unit) {
        val uid = auth.currentUser?.uid

        viewModelScope.launch {
            try {
                // 1. Remove FCM token from Firestore
                if (uid != null) {
                    try {
                        firestore.collection("users").document(uid)
                            .update("fcmToken", "").await()
                        Log.d(TAG, "FCM token removed from Firestore")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to remove FCM token", e)
                    }

                    // Also delete local FCM instance token
                    try {
                        FirebaseMessaging.getInstance().deleteToken().await()
                        Log.d(TAG, "Local FCM token deleted")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete local token", e)
                    }
                }

                // 2. Sign out from Firebase Auth
                auth.signOut()

                // 3. Clear ViewModel state
                _uiState.update { ProfileUiState(isLoading = false) }

                // 4. Navigate back to login
                onLogoutComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Logout failed. Please try again.")
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════════════════

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null, isOffline = false) }
        observeProfile()
        observeSavedPlaces()
        observeReportStats()
    }

    override fun onCleared() {
        super.onCleared()
        profileListener?.remove()
        savedPlacesListener?.remove()
        reportsListener?.remove()
    }
}
