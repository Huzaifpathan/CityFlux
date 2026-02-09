package com.example.cityflux

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.cityflux.navigation.AppNavGraph
import com.example.cityflux.service.CityFluxMessagingService
import com.example.cityflux.ui.theme.CityFluxTheme
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private val analytics by lazy { Firebase.analytics }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission (Android 13+)
        requestNotificationPermission()

        // Fetch FCM token and save to Firestore
        fetchAndSaveFcmToken()

        setContent {
            CityFluxTheme {
                val navController = rememberNavController()
                AppNavGraph(navController = navController)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun fetchAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM", "Device token: $token")
                CityFluxMessagingService.saveTokenToFirestore(token)
            }
            .addOnFailureListener { e ->
                Log.e("FCM", "Failed to get token", e)
            }
    }
}
