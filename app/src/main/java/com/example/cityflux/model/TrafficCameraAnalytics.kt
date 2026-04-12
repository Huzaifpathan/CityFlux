package com.example.cityflux.model

import kotlin.math.abs

data class CameraSample(
    val timestamp: Long,
    val vehicleCount: Int
)

enum class CameraHealthStatus {
    ONLINE,
    NO_FEED,
    OFFLINE
}

data class TrafficCameraCluster(
    val centerLat: Double,
    val centerLng: Double,
    val cameras: List<TrafficCamera>
) {
    val isCluster: Boolean get() = cameras.size > 1
    val totalVehicleCount: Int get() = cameras.sumOf { it.vehicleCount }
    val avgVehicleCount: Int get() = if (cameras.isEmpty()) 0 else totalVehicleCount / cameras.size
    val maxCongestionLevel: String
        get() = when {
            cameras.any { it.congestionLevel == "HIGH" } -> "HIGH"
            cameras.any { it.congestionLevel == "MEDIUM" } -> "MEDIUM"
            else -> "LOW"
        }
}

data class CameraHeatCell(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val intensity: Float,
    val incidentCorrelationCount: Int
)

fun getCameraHealthStatus(lastUpdated: Long, now: Long = System.currentTimeMillis()): CameraHealthStatus {
    val ageMs = (now - lastUpdated).coerceAtLeast(0L)
    return when {
        ageMs <= 2 * 60_000L -> CameraHealthStatus.ONLINE
        ageMs <= 10 * 60_000L -> CameraHealthStatus.NO_FEED
        else -> CameraHealthStatus.OFFLINE
    }
}

fun computeCameraTrend(history: List<CameraSample>, windowMinutes: Int): Int {
    if (history.isEmpty()) return 0
    val now = history.maxOf { it.timestamp }
    val anchorTs = now - (windowMinutes * 60_000L)
    val current = history.maxByOrNull { it.timestamp } ?: return 0
    val anchor = history.minByOrNull { abs(it.timestamp - anchorTs) } ?: return 0
    return current.vehicleCount - anchor.vehicleCount
}

fun computePeakVehicles(history: List<CameraSample>, windowMs: Long): Int {
    if (history.isEmpty()) return 0
    val now = history.maxOf { it.timestamp }
    return history
        .asSequence()
        .filter { now - it.timestamp <= windowMs }
        .maxOfOrNull { it.vehicleCount }
        ?: 0
}

fun isHighCongestionForFiveMinutes(history: List<CameraSample>): Boolean {
    if (history.isEmpty()) return false
    val sorted = history.sortedBy { it.timestamp }
    val latest = sorted.last()
    if (TrafficCamera.fromVehicleCount(latest.vehicleCount) != "HIGH") return false

    val fiveMinutesAgo = latest.timestamp - 5 * 60_000L
    val anchor = sorted.minByOrNull { abs(it.timestamp - fiveMinutesAgo) } ?: return false
    return TrafficCamera.fromVehicleCount(anchor.vehicleCount) == "HIGH"
}

fun buildTrafficCameraClusters(
    cameras: List<TrafficCamera>,
    zoom: Float
): List<TrafficCameraCluster> {
    if (cameras.isEmpty()) return emptyList()
    val cellSize = when {
        zoom >= 16f -> 0.0015
        zoom >= 14f -> 0.0035
        zoom >= 12f -> 0.0065
        else -> 0.012
    }

    val grouped = cameras.groupBy { cam ->
        val latBucket = (cam.latitude / cellSize).toInt()
        val lngBucket = (cam.longitude / cellSize).toInt()
        latBucket to lngBucket
    }

    return grouped.values.map { cams ->
        val lat = cams.sumOf { it.latitude } / cams.size
        val lng = cams.sumOf { it.longitude } / cams.size
        TrafficCameraCluster(
            centerLat = lat,
            centerLng = lng,
            cameras = cams
        )
    }
}

fun buildTrafficCameraHeatCells(
    cameras: List<TrafficCamera>,
    incidents: List<Report>
): List<CameraHeatCell> {
    if (cameras.isEmpty()) return emptyList()
    return cameras
        .filter { it.hasValidLocation() }
        .map { cam ->
            val nearbyIncidents = incidents.count { report ->
                distanceMeters(
                    cam.latitude, cam.longitude,
                    report.latitude, report.longitude
                ) <= 350.0
            }
            val baseIntensity = (cam.vehicleCount / 100f).coerceIn(0.08f, 0.55f)
            val incidentBoost = (nearbyIncidents * 0.06f).coerceAtMost(0.24f)
            val radius = 180.0 + (cam.vehicleCount * 4.0) + (nearbyIncidents * 35.0)
            CameraHeatCell(
                latitude = cam.latitude,
                longitude = cam.longitude,
                radiusMeters = radius,
                intensity = (baseIntensity + incidentBoost).coerceIn(0.08f, 0.72f),
                incidentCorrelationCount = nearbyIncidents
            )
        }
}

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    if (lat2 == 0.0 && lon2 == 0.0) return Double.MAX_VALUE
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}
