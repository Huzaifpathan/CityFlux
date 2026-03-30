package com.example.cityflux.service

import com.example.cityflux.model.ParkingSpot
import com.example.cityflux.model.VehicleType

/**
 * Pricing service for parking bookings
 * Phase 4: Calculates parking fees based on duration, vehicle type, and parking spot rates
 */
object PricingService {
    
    // Default base hourly rates per vehicle type (in ₹) - used if parking spot has no rate
    private val DEFAULT_RATES = mapOf(
        VehicleType.TWO_WHEELER to 10.0,
        VehicleType.CAR to 20.0,
        VehicleType.SUV to 30.0,
        VehicleType.TRUCK to 50.0,
        VehicleType.BUS to 70.0
    )
    
    // Vehicle type multipliers (relative to base rate)
    private val VEHICLE_MULTIPLIERS = mapOf(
        VehicleType.TWO_WHEELER to 0.5,   // 50% of car rate
        VehicleType.CAR to 1.0,            // Base rate
        VehicleType.SUV to 1.5,            // 150% of car rate
        VehicleType.TRUCK to 2.5,          // 250% of car rate
        VehicleType.BUS to 3.5             // 350% of car rate
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
     * Calculate parking fee using parking spot's ratePerHour
     * This is the NEW method that uses zone-based pricing
     */
    fun calculateParkingFee(
        parkingSpot: ParkingSpot,
        vehicleType: VehicleType,
        durationHours: Int,
        applyPeakHourCharges: Boolean = false
    ): PricingBreakdown {
        // If parking is FREE, return zero pricing
        if (parkingSpot.isFree) {
            return PricingBreakdown(
                baseRate = 0.0,
                durationHours = durationHours,
                baseAmount = 0.0,
                peakHourCharge = 0.0,
                discount = 0.0,
                discountPercentage = 0.0,
                subtotal = 0.0,
                gst = 0.0,
                totalAmount = 0.0,
                isFreeParking = true
            )
        }
        
        // Get parking spot's rate per hour and apply vehicle multiplier
        val spotRate = if (parkingSpot.ratePerHour > 0) parkingSpot.ratePerHour.toDouble() else DEFAULT_RATES[VehicleType.CAR]!!
        val vehicleMultiplier = VEHICLE_MULTIPLIERS[vehicleType] ?: 1.0
        val baseRate = spotRate * vehicleMultiplier
        
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
            totalAmount = totalAmount,
            isFreeParking = false
        )
    }
    
    /**
     * Calculate total parking fee (legacy method - uses default rates)
     * Kept for backward compatibility
     */
    fun calculateParkingFee(
        vehicleType: VehicleType,
        durationHours: Int,
        applyPeakHourCharges: Boolean = false
    ): PricingBreakdown {
        val baseRate = DEFAULT_RATES[vehicleType] ?: DEFAULT_RATES[VehicleType.CAR]!!
        
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
            totalAmount = totalAmount,
            isFreeParking = false
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
     * Get quick price estimates for specific parking spot
     */
    fun getQuickEstimates(parkingSpot: ParkingSpot, vehicleType: VehicleType): List<QuickEstimate> {
        // Convert min/max duration to hours
        val minHours = (parkingSpot.minDuration / 60f).coerceAtLeast(1f).toInt()
        val maxHours = (parkingSpot.maxDuration / 60f).coerceAtLeast(1f).toInt()
        
        // Generate estimates within allowed duration range
        val durations = listOf(1, 2, 4, 8, 24).filter { it in minHours..maxHours }
        
        return durations.map { hours ->
            QuickEstimate(hours, calculateParkingFee(parkingSpot, vehicleType, hours).totalAmount)
        }
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
    val totalAmount: Double,
    val isFreeParking: Boolean = false
) {
    fun getDiscountLabel(): String {
        return if (discountPercentage > 0) {
            "${(discountPercentage * 100).toInt()}% OFF"
        } else {
            ""
        }
    }
    
    fun getTotalDisplayString(): String {
        return if (isFreeParking) "FREE" else "₹${totalAmount.toInt()}"
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
