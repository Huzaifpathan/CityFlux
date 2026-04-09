package com.example.cityflux.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.util.Locale

/**
 * UPI Payment Service
 * Handles UPI payment intents for parking bookings
 * TODO: Will be fully implemented later
 */
object UpiPaymentService {
    
    // ══════════════════════════════════════════════════════════════
    // 🔧 CONFIGURED UPI ID FOR PAYMENTS
    // Real UPI ID for receiving CityFlux parking payments
    // ══════════════════════════════════════════════════════════════
    private const val DEFAULT_UPI_ID = "shivamatram2002-1@okhdfcbank"
    private const val MERCHANT_NAME = "CityFlux Parking"
    
    // Test mode flag - set to true to skip actual payment
    var testMode: Boolean = false
    
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
     * Create chooser intent so caller can launch UPI and receive activity result
     */
    fun createPaymentChooserIntent(
        amount: Double,
        parkingName: String,
        bookingId: String,
        vehicleNumber: String,
        upiId: String = DEFAULT_UPI_ID
    ): Intent {
        val note = "Parking: $parkingName | Vehicle: $vehicleNumber | Booking: $bookingId"
        val paymentIntent = createPaymentIntent(
            amount = amount,
            note = note,
            upiId = upiId,
            transactionRef = bookingId
        )
        return Intent.createChooser(paymentIntent, "Pay ₹${amount.toInt()} via UPI")
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

    /**
     * Parse UPI app response string into structured result.
     * Typical format: "Status=SUCCESS&txnId=123&txnRef=ABC&ApprovalRefNo=XYZ"
     */
    fun parsePaymentResponse(response: String?): UpiPaymentResult {
        if (response.isNullOrBlank()) {
            return UpiPaymentResult(
                isSuccess = false,
                isCancelled = true,
                message = "Payment cancelled by user."
            )
        }

        val params = response
            .split("&")
            .mapNotNull { item ->
                val idx = item.indexOf("=")
                if (idx <= 0) null else {
                    val key = item.substring(0, idx).trim().lowercase(Locale.ROOT)
                    val value = item.substring(idx + 1).trim()
                    key to value
                }
            }
            .toMap()

        val status = params["status"]?.lowercase(Locale.ROOT).orEmpty()
        val transactionId = params["txnid"] ?: params["approvalrefno"] ?: params["approvalref"]
        val transactionRef = params["txnref"] ?: params["tr"]

        return when {
            status == "success" || status == "submitted" -> UpiPaymentResult(
                isSuccess = true,
                isCancelled = false,
                message = "Payment successful.",
                transactionId = transactionId,
                transactionRef = transactionRef
            )
            status == "failure" || status == "failed" -> UpiPaymentResult(
                isSuccess = false,
                isCancelled = false,
                message = params["responsecode"]?.let { "Payment failed (code: $it)." } ?: "Payment failed.",
                transactionId = transactionId,
                transactionRef = transactionRef
            )
            response.contains("success", ignoreCase = true) -> UpiPaymentResult(
                isSuccess = true,
                isCancelled = false,
                message = "Payment successful.",
                transactionId = transactionId,
                transactionRef = transactionRef
            )
            else -> UpiPaymentResult(
                isSuccess = false,
                isCancelled = true,
                message = "Payment cancelled or not completed.",
                transactionId = transactionId,
                transactionRef = transactionRef
            )
        }
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

data class UpiPaymentResult(
    val isSuccess: Boolean,
    val isCancelled: Boolean,
    val message: String,
    val transactionId: String? = null,
    val transactionRef: String? = null
)
