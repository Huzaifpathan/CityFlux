package com.example.cityflux.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.cityflux.MainActivity
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Foreground Service that broadcasts the user's live location to Firebase RTDB.
 * Other citizens see these markers on their map in real-time.
 *
 * RTDB path: live_locations/{userId}
 * Fields: lat, lng, speed, heading, name, timestamp
 */
class LocationTrackingService : Service() {

    companion object {
        const val TAG = "LocationTracking"
        const val CHANNEL_ID = "live_location_channel"
        const val NOTIFICATION_ID = 2001
        const val UPDATE_INTERVAL = 8000L       // 8 seconds
        const val FASTEST_INTERVAL = 5000L      // 5 seconds minimum
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var userName: String = "Citizen"

    private var isTracking = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        fetchUserName()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MUST call startForeground() immediately to satisfy the system requirement
        // from startForegroundService(), even if we're about to stop.
        showForegroundNotification()

        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (!isTracking) {
                    startLocationUpdates()
                    isTracking = true
                }
            }
        }
        return START_STICKY
    }

    private fun fetchUserName() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                userName = doc.getString("name") ?: "Citizen"
            }
    }

    private fun showForegroundNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CityFlux Live")
            .setContentText("Sharing your location with nearby citizens")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop Sharing", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, UPDATE_INTERVAL)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL)
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    pushLocationToRTDB(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "Location updates started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location updates", e)
        }
    }

    private fun pushLocationToRTDB(location: android.location.Location) {
        val uid = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("live_locations").child(uid)

        val data = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "speed" to (location.speed * 3.6).toInt(), // m/s → km/h
            "heading" to location.bearing.toDouble(),
            "name" to userName,
            "timestamp" to System.currentTimeMillis()
        )

        // Auto-remove entry if device goes offline unexpectedly
        ref.onDisconnect().removeValue()

        ref.setValue(data)
            .addOnSuccessListener {
                Log.d(TAG, "Location pushed: ${location.latitude}, ${location.longitude}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to push location", e)
            }
    }

    private fun stopTracking() {
        isTracking = false
        try {
            if (::locationCallback.isInitialized) {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (_: Exception) {}

        // Remove from RTDB so marker disappears
        try {
            val uid = auth.currentUser?.uid ?: return
            rtdb.getReference("live_locations").child(uid).removeValue()
            Log.d(TAG, "Location tracking stopped, RTDB entry removed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove RTDB entry", e)
        }
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Location",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when CityFlux is sharing your live location"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
