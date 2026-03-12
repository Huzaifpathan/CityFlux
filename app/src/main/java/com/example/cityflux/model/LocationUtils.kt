package com.example.cityflux.model

import kotlin.math.*

/**
 * Haversine-based distance calculation between two geographic coordinates.
 */
object LocationUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    /** Police working radius in kilometres. */
    const val POLICE_RADIUS_KM = 4.0

    /**
     * Returns the great-circle distance between two points in **kilometres**.
     */
    fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return EARTH_RADIUS_KM * 2 * asin(sqrt(a))
    }

    /**
     * Returns `true` when [reportLat]/[reportLon] is within [radiusKm] of the
     * police officer's working location.
     */
    fun isWithinRadius(
        policeLat: Double, policeLon: Double,
        reportLat: Double, reportLon: Double,
        radiusKm: Double = POLICE_RADIUS_KM
    ): Boolean = haversineDistance(policeLat, policeLon, reportLat, reportLon) <= radiusKm
}
