package com.example.cityflux.model

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.Timestamp
import java.util.Date

data class TrafficCamera(
    val id: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val vehicleCount: Int = 0,
    val lastUpdated: Long = 0L
) {
    val congestionLevel: String
        get() = fromVehicleCount(vehicleCount)

    fun hasValidLocation(): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    companion object {
        const val MEDIUM_CONGESTION_MIN = 35
        const val HIGH_CONGESTION_MIN = 70

        fun fromVehicleCount(vehicleCount: Int): String {
            return when {
                vehicleCount >= HIGH_CONGESTION_MIN -> "HIGH"
                vehicleCount >= MEDIUM_CONGESTION_MIN -> "MEDIUM"
                else -> "LOW"
            }
        }
    }
}

fun DocumentSnapshot.toTrafficCamera(): TrafficCamera? {
    val coordinatesRaw = get("coordinates")
    val coordinatesPair = extractLatLngFromAny(coordinatesRaw)

    val lat = parseCoordinateValue(get("latitude"))
        ?: parseCoordinateValue(get("lat"))
        ?: (get("location") as? GeoPoint)?.latitude
        ?: (get("coordinates") as? GeoPoint)?.latitude
        ?: coordinatesPair?.first
    val lng = parseCoordinateValue(get("longitude"))
        ?: parseCoordinateValue(get("lng"))
        ?: (get("location") as? GeoPoint)?.longitude
        ?: (get("coordinates") as? GeoPoint)?.longitude
        ?: coordinatesPair?.second
    if (lat == null || lng == null) return null

    return TrafficCamera(
        id = id,
        name = (
            getString("name")
                ?: getString("cameraName")
                ?: getString("title")
                ?: getString("camera_name")
                ?: getString("location")
            ).orEmpty().ifBlank { "Camera $id" },
        latitude = lat,
        longitude = lng,
        vehicleCount = parseVehicleCount(
            get("vehicleCount")
                ?: get("liveVehicleCount")
                ?: get("currentVehicleCount")
                ?: get("vehicle_count")
                ?: get("count")
                ?: get("countedVehicles")
                ?: get("counted")
                ?: get("detected")
        ),
        lastUpdated = parseEpochMillis(
            get("lastUpdated")
                ?: get("updatedAt")
                ?: get("createdAt")
        ) ?: System.currentTimeMillis()
    )
}

private fun extractLatLngFromText(raw: String): Pair<Double, Double>? {
    if (raw.isBlank()) return null
    // Supports strings like: "[19.8762° N, 75.3433° E]"
    val pattern = Regex("""(-?\d+(?:\.\d+)?)\s*°?\s*([NSEW])?""", RegexOption.IGNORE_CASE)
    val matches = pattern.findAll(raw).toList()
    if (matches.size < 2) return null

    fun parse(match: MatchResult): Double {
        val base = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val dir = match.groupValues.getOrNull(2)?.uppercase().orEmpty()
        return when (dir) {
            "S", "W" -> -kotlin.math.abs(base)
            "N", "E" -> kotlin.math.abs(base)
            else -> base
        }
    }

    val lat = parse(matches[0])
    val lng = parse(matches[1])
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

private fun extractLatLngFromAny(raw: Any?): Pair<Double, Double>? {
    when (raw) {
        null -> return null
        is GeoPoint -> return raw.latitude to raw.longitude
        is List<*> -> {
            if (raw.size < 2) return null
            val first = parseCoordinateValue(raw[0])
            val second = parseCoordinateValue(raw[1])
            if (first != null && second != null) return first to second
            return extractLatLngFromText(raw.toString())
        }
        is Map<*, *> -> {
            val lat = parseCoordinateValue(raw["lat"] ?: raw["latitude"] ?: raw["y"])
            val lng = parseCoordinateValue(raw["lng"] ?: raw["longitude"] ?: raw["lon"] ?: raw["x"])
            if (lat != null && lng != null) return lat to lng
            return extractLatLngFromText(raw.toString())
        }
        else -> return extractLatLngFromText(raw.toString())
    }
}

private fun parseCoordinateValue(value: Any?): Double? {
    return when (value) {
        null -> null
        is Number -> value.toDouble()
        is String -> extractSingleCoordinateFromText(value)
        else -> extractSingleCoordinateFromText(value.toString())
    }
}

private fun extractSingleCoordinateFromText(text: String): Double? {
    val match = Regex("""(-?\d+(?:\.\d+)?)\s*°?\s*([NSEW])?""", RegexOption.IGNORE_CASE)
        .find(text)
        ?: return null
    val base = match.groupValues[1].toDoubleOrNull() ?: return null
    return when (match.groupValues.getOrNull(2)?.uppercase().orEmpty()) {
        "S", "W" -> -kotlin.math.abs(base)
        "N", "E" -> kotlin.math.abs(base)
        else -> base
    }
}

private fun parseVehicleCount(value: Any?): Int {
    val parsed = when (value) {
        null -> null
        is Number -> value.toInt()
        is String -> Regex("""-?\d+""").find(value)?.value?.toIntOrNull()
        else -> Regex("""-?\d+""").find(value.toString())?.value?.toIntOrNull()
    } ?: 0
    return parsed.coerceAtLeast(0)
}

private fun parseEpochMillis(value: Any?): Long? {
    return when (value) {
        null -> null
        is Number -> normalizeEpoch(value.toLong())
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is String -> value.toLongOrNull()?.let(::normalizeEpoch)
        else -> null
    }
}

private fun normalizeEpoch(raw: Long): Long {
    // Convert second-based timestamps to milliseconds.
    return if (kotlin.math.abs(raw) < 10_000_000_000L) raw * 1000L else raw
}

@Suppress("unused")
fun DocumentSnapshot.debugTrafficCameraParse(): String {
    return "id=$id name=${getString("name")} cameraName=${getString("cameraName")} " +
            "lat=${getDouble("latitude") ?: getDouble("lat")} " +
            "lng=${getDouble("longitude") ?: getDouble("lng")} " +
            "coordinates=${get("coordinates")} location=${get("location")} " +
            "vehicleCount=${get("vehicleCount")} counted=${get("counted")} detected=${get("detected")}"
}
