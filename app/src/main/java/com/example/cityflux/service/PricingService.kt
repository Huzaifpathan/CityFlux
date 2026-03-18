package com.example.cityflux.service

import com.example.cityflux.model.VehicleType

/**
 * Pricing service for parking bookings
 * Phase 4: Calculates parking fees based on duration and vehicle type
 */
object PricingService {
    
    // Base hourly rates per vehicle type (in ₹)
    private val BASE_RATES = mapOf(
        VehicleType.TWO_WHEELER to 10.0,
        VehicleType.CAR to 20.0,
        VehicleType.SUV to 30.0,
        VehicleType.TRUCK to 50.0,
        VehicleType.BUS to 70.0
    )
    
    // Discount tiers
    private const val DISCOUNT_4_HOURS = 0.10    // 10% off for 4+ hours
    private const val DISCOUNT_8_HOURS = 0.15    // 15% off for 8+ hours
    private const val DISCOUNT_24_HOURS = 0.20   // 20% off for 24+ hours
    
    // Peak hour multiplier (8 AM - 10 PM)
    private const val PEAK_HOUR_MULTIPLIER = 1.5
    
    // GST percentage
    private const val GST_RATE = 0.18  // 18% GST
    
    /**
     * Calculate total parking fee
     */
    fun calculateParkingFee(
        vehicleType: VehicleType,
        durationHours: Int,
        applyPeakHourCharges: Boolean = false
    ): PricingBreakdown {
        val baseRate = BASE_RATES[vehicleType] ?: BASE_RATES[VehicleType.CAR]!!
        
        // Calculate base amount
        var baseAmount = baseRate * durationHours
        
        // Apply peak hour charges
        if (applyPeakHourCharges) {
            baseAmount *= PEAK_HOUR_MULTIPLIER
        }
        
        // Apply duration-based discount
        val discount = when {
            durationHours >= 24 -> baseAmount * DISCOUNT_24_HOURS
            durationHours >= 8 -> baseAmount * DISCOUNT_8_HOURS
            durationHours >= 4 -> baseAmount * DISCOUNT_4_HOURS
            else -> 0.0
        }
        
        val discountedAmount = baseAmount - discount
        
        // Calculate GST
        val gst = discountedAmount * GST_RATE
        
        // Total amount
        val totalAmount = discountedAmount + gst
        
        return PricingBreakdown(
            baseRate = baseRate,
            durationHours = durationHours,
            baseAmount = baseAmount,
            peakHourCharge = if (applyPeakHourCharges) baseAmount * (PEAK_HOUR_MULTIPLIER - 1) else 0.0,
            discount = discount,
            discountPercentage = when {
                durationHours >= 24 -> DISCOUNT_24_HOURS
                durationHours >= 8 -> DISCOUNT_8_HOURS
                durationHours >= 4 -> DISCOUNT_4_HOURS
                else -> 0.0
            },
            subtotal = discountedAmount,
            gst = gst,
            totalAmount = totalAmount
        )
    }
    
    /**
     * Get quick price estimates for different durations
     */
    fun getQuickEstimates(vehicleType: VehicleType): List<QuickEstimate> {
        return listOf(
            QuickEstimate(1, calculateParkingFee(vehicleType, 1).totalAmount),
            QuickEstimate(2, calculateParkingFee(vehicleType, 2).totalAmount),
            QuickEstimate(4, calculateParkingFee(vehicleType, 4).totalAmount),
            QuickEstimate(8, calculateParkingFee(vehicleType, 8).totalAmount),
            QuickEstimate(24, calculateParkingFee(vehicleType, 24).totalAmount)
        )
    }
    
    /**
     * Format amount in Indian currency
     */
    fun formatAmount(amount: Double): String {
        return "₹%.2f".format(amount)
    }
}

/**
 * Pricing breakdown data class
 */
data class PricingBreakdown(
    val baseRate: Double,
    val durationHours: Int,
    val baseAmount: Double,
    val peakHourCharge: Double,
    val discount: Double,
    val discountPercentage: Double,
    val subtotal: Double,
    val gst: Double,
    val totalAmount: Double
) {
    fun getDiscountLabel(): String {
        return if (discountPercentage > 0) {
            "${(discountPercentage * 100).toInt()}% OFF"
        } else {
            ""
        }
    }
}

/**
 * Quick estimate for UI display
 */
data class QuickEstimate(
    val hours: Int,
    val amount: Double
) {
    fun getLabel(): String {
        return when {
            hours < 24 -> "$hours ${if (hours == 1) "hour" else "hours"}"
            else -> "${hours / 24} ${if (hours == 24) "day" else "days"}"
        }
    }
}
