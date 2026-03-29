package com.example.cityflux.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * UPI Payment Service
 * Handles UPI payment intents for parking bookings
 */
object UpiPaymentService {
    
    // Default UPI ID for CityFlux parking payments
    // This should be configured per parking zone in production
    private const val DEFAULT_UPI_ID = "cityflux@ybl"
    private const val MERCHANT_NAME = "CityFlux Parking"
    
    /**
     * Create UPI payment intent
     * @param context Android context
     * @param amount Payment amount in INR
     * @param note Transaction note/description
     * @param upiId UPI ID of the parking zone (optional, uses default if not provided)
     * @param transactionRef Unique transaction reference
     */
    fun createPaymentIntent(
        amount: Double,
        note: String,
        upiId: String = DEFAULT_UPI_ID,
        merchantName: String = MERCHANT_NAME,
        transactionRef: String = generateTransactionRef()
    ): Intent {
        // Build UPI URI
        // Format: upi://pay?pa=<UPI_ID>&pn=<PAYEE_NAME>&am=<AMOUNT>&cu=INR&tn=<NOTE>&tr=<REF>
        val upiUri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId)           // Payee UPI ID
            .appendQueryParameter("pn", merchantName)     // Payee Name
            .appendQueryParameter("am", String.format("%.2f", amount))  // Amount
            .appendQueryParameter("cu", "INR")            // Currency
            .appendQueryParameter("tn", note)             // Transaction Note
            .appendQueryParameter("tr", transactionRef)   // Transaction Reference
            .build()
        
        return Intent(Intent.ACTION_VIEW).apply {
            data = upiUri
        }
    }
    
    /**
     * Launch UPI payment app chooser
     * Shows all available UPI apps on the device
     */
    fun launchPayment(
        context: Context,
        amount: Double,
        parkingName: String,
        bookingId: String,
        vehicleNumber: String,
        upiId: String = DEFAULT_UPI_ID
    ): Boolean {
        val note = "Parking: $parkingName | Vehicle: $vehicleNumber | Booking: $bookingId"
        
        val intent = createPaymentIntent(
            amount = amount,
            note = note,
            upiId = upiId,
            transactionRef = bookingId
        )
        
        return try {
            // Create chooser to show all available UPI apps
            val chooser = Intent.createChooser(intent, "Pay ₹${amount.toInt()} via UPI")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No UPI app found. Please install Google Pay, PhonePe, or Paytm.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }
    
    /**
     * Get list of available UPI apps on device
     */
    fun getAvailableUpiApps(context: Context): List<UpiAppInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("upi://pay")
        }
        
        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(intent, 0)
        
        return activities.map { resolveInfo ->
            UpiAppInfo(
                appName = resolveInfo.loadLabel(packageManager).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        }
    }
    
    /**
     * Generate unique transaction reference
     */
    private fun generateTransactionRef(): String {
        return "CF${System.currentTimeMillis()}${(1000..9999).random()}"
    }
    
    /**
     * Get UPI ID for specific parking zone
     * In production, this would fetch from database
     */
    fun getUpiIdForParking(parkingId: String): String {
        // TODO: Fetch from database based on parking zone
        // For now, return default
        return DEFAULT_UPI_ID
    }
}

/**
 * Data class for UPI app information
 */
data class UpiAppInfo(
    val appName: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable
)
