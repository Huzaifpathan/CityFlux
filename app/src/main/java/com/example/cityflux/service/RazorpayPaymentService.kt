package com.example.cityflux.service

import android.app.Activity
import com.example.cityflux.BuildConfig
import com.razorpay.Checkout
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

data class RazorpayPaymentResult(
    val isSuccess: Boolean,
    val paymentId: String? = null,
    val orderId: String? = null,
    val signature: String? = null,
    val errorCode: Int? = null,
    val errorMessage: String? = null
)

object RazorpayPaymentBridge {
    private var callback: ((RazorpayPaymentResult) -> Unit)? = null

    fun registerCallback(listener: (RazorpayPaymentResult) -> Unit) {
        callback = listener
    }

    fun clearCallback() {
        callback = null
    }

    fun notifyResult(result: RazorpayPaymentResult) {
        callback?.invoke(result)
    }
}

object RazorpayPaymentService {

    fun preload(activity: Activity) {
        Checkout.preload(activity.applicationContext)
    }

    fun startPayment(
        activity: Activity,
        amountInRupees: Double,
        parkingName: String,
        bookingId: String,
        vehicleNumber: String
    ): Result<Unit> {
        val keyId = BuildConfig.RAZORPAY_KEY_ID.trim()
        if (keyId.isBlank()) {
            return Result.failure(IllegalStateException("Razorpay key is missing. Add RAZORPAY_KEY_ID in local.properties"))
        }

        val checkout = Checkout().apply {
            setKeyID(keyId)
        }

        val amountInPaise = toPaise(amountInRupees)

        val options = JSONObject().apply {
            put("name", "CityFlux Parking")
            put("description", "Parking booking: $vehicleNumber")
            put("currency", "INR")
            put("amount", amountInPaise.toString())
            // Force UPI selection in checkout and keep intent flow enabled.
            put("method", "upi")
            put("upi", JSONObject().apply {
                put("flow", "intent")
            })
            put("config", JSONObject().apply {
                put("display", JSONObject().apply {
                    put("hide", JSONArray().apply {
                        put("cards")
                        put("netbanking")
                        put("wallet")
                        put("emi")
                        put("paylater")
                    })
                    put("sequence", JSONArray().apply {
                        put("block.upi")
                    })
                    put("blocks", JSONObject().apply {
                        put("upi", JSONObject().apply {
                            put("name", "Pay using UPI")
                            put("instruments", JSONArray().apply {
                                put(JSONObject().apply { put("method", "upi") })
                            })
                        })
                    })
                })
            })
            put("notes", JSONObject().apply {
                put("booking_id", bookingId)
                put("vehicle_number", vehicleNumber)
                put("parking_name", parkingName.take(80))
            })
            put("retry", JSONObject().apply {
                put("enabled", true)
                put("max_count", 2)
            })
            put("theme", JSONObject().apply {
                put("color", "#2563EB")
            })
        }

        return try {
            checkout.open(activity, options)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toPaise(amountInRupees: Double): Long {
        return BigDecimal.valueOf(amountInRupees)
            .takeIf { it.signum() > 0 }
            ?.multiply(BigDecimal(100))
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.toLong()
            ?: 100L
    }
}

