package com.example.cityflux.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.cityflux.MainActivity
import com.example.cityflux.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles incoming FCM messages and token refreshes.
 *
 * - Foreground: builds and shows a system notification immediately.
 * - Background: the system tray handles it automatically via the `notification` payload.
 * - Token refresh: persists the new token to Firestore `users/{uid}/fcmToken`.
 */
class CityFluxMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "cityflux_alerts"
        private const val CHANNEL_NAME = "CityFlux Alerts"

        /**
         * Save the current FCM token to Firestore under the authenticated user's document.
         * Call this on app launch and whenever the token refreshes.
         */
        fun saveTokenToFirestore(token: String) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d(TAG, "FCM token saved for $uid") }
                .addOnFailureListener { Log.e(TAG, "Failed to save token", it) }
        }
    }

    // ── Called when token is generated or refreshed ──────────────
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        saveTokenToFirestore(token)
    }

    // ── Called when a message is received while app is in foreground ──
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message from: ${message.from}")

        // Extract title and body from notification payload or data payload
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "CityFlux Alert"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new notification"

        val type = message.data["type"] ?: "general"

        showNotification(title, body, type)
    }

    // ── Build and display a system notification ─────────────────
    private fun showNotification(title: String, body: String, type: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Traffic, parking, and safety alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tapping the notification opens the app's main screen
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.cityflux_icon_mono)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()

        notificationManager.notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
