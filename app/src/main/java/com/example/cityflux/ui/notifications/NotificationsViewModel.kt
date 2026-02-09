package com.example.cityflux.ui.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cityflux.model.Notification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for NotificationsScreen — manages real-time Firestore
 * notifications at users/{uid}/notifications, with read/pin/delete actions.
 */
class NotificationsViewModel : ViewModel() {

    companion object {
        private const val TAG = "NotificationsVM"
    }

    data class NotificationsUiState(
        val isLoading: Boolean = true,
        val notifications: List<Notification> = emptyList(),
        val error: String? = null,
        val isOffline: Boolean = false
    )

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    val unreadCount: StateFlow<Int> = _uiState.map { state ->
        state.notifications.count { !it.read }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        observeNotifications()
    }

    // ══════════════════════════════════════════════
    // Real-time Firestore listener
    // ══════════════════════════════════════════════

    private fun observeNotifications() {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, error = "Not signed in") }
            return
        }

        listenerRegistration?.remove()
        listenerRegistration = firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Notifications listener error", error)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load notifications")
                    }
                    return@addSnapshotListener
                }

                val notifications = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Notification::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Parse notification error: ${doc.id}", e)
                        null
                    }
                } ?: emptyList()

                // Pinned first, then by timestamp (already ordered by Firestore)
                val sorted = notifications.sortedWith(
                    compareByDescending<Notification> { it.pinned }
                        .thenByDescending { it.timestamp }
                )

                _uiState.update {
                    it.copy(
                        notifications = sorted,
                        isLoading = false,
                        error = null,
                        isOffline = false
                    )
                }
            }
    }

    // ══════════════════════════════════════════════
    // Actions
    // ══════════════════════════════════════════════

    fun markAsRead(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("notifications").document(notificationId)
                    .update("read", true).await()
            } catch (e: Exception) {
                Log.e(TAG, "Mark read failed", e)
            }
        }
    }

    fun markAllAsRead() {
        val uid = auth.currentUser?.uid ?: return
        val unread = _uiState.value.notifications.filter { !it.read }
        if (unread.isEmpty()) return

        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                unread.forEach { notification ->
                    val ref = firestore.collection("users").document(uid)
                        .collection("notifications").document(notification.id)
                    batch.update(ref, "read", true)
                }
                batch.commit().await()
            } catch (e: Exception) {
                Log.e(TAG, "Mark all read failed", e)
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("notifications").document(notificationId)
                    .delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Delete notification failed", e)
            }
        }
    }

    fun togglePin(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        val notification = _uiState.value.notifications.find { it.id == notificationId } ?: return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("notifications").document(notificationId)
                    .update("pinned", !notification.pinned).await()
            } catch (e: Exception) {
                Log.e(TAG, "Toggle pin failed", e)
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        observeNotifications()
    }

    override fun onCleared() {
        super.onCleared()
        listenerRegistration?.remove()
    }
}
