package com.example.cityflux.model

/**
 * Dummy live user locations on real Solapur city roads.
 * Used to demonstrate live citizen tracking and traffic congestion on map screens.
 * 60 users spread across major Solapur roads/areas with road-based congestion levels.
 */
object SolapurDummyData {

    // ═══════════════════════════════════════════════════════════════════
    // ROAD DEFINITIONS with center coordinates and congestion levels
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Road data class for congestion tracking
     */
    data class SolapurRoad(
        val id: String,
        val name: String,
        val centerLat: Double,
        val centerLng: Double,
        val congestionLevel: String, // HIGH, MEDIUM, LOW
        val avgSpeed: Int,
        val userCount: Int
    )

    /**
     * 12 major Solapur roads with predefined congestion levels based on traffic patterns.
     * HIGH congestion: Market areas, bus stands, railway station (avg speed < 15 km/h)
     * MEDIUM congestion: Hospital roads, shopping areas (avg speed 15-35 km/h)
     * LOW congestion: Highways, outer roads (avg speed > 35 km/h)
     */
    val solapurRoads: List<SolapurRoad> = listOf(
        // ── HIGH CONGESTION (Red zones) ──
        SolapurRoad(
            id = "road_17.6715_75.9164",
            name = "Railway Station Road",
            centerLat = 17.6715,
            centerLng = 75.9164,
            congestionLevel = "HIGH",
            avgSpeed = 12,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6650_75.9100",
            name = "Budhwar Peth Market",
            centerLat = 17.6650,
            centerLng = 75.9100,
            congestionLevel = "HIGH",
            avgSpeed = 10,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6700_75.9050",
            name = "Central Bus Stand",
            centerLat = 17.6700,
            centerLng = 75.9050,
            congestionLevel = "HIGH",
            avgSpeed = 12,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6702_75.9063",
            name = "Siddheshwar Temple Area",
            centerLat = 17.6702,
            centerLng = 75.9063,
            congestionLevel = "HIGH",
            avgSpeed = 5,
            userCount = 2
        ),

        // ── MEDIUM CONGESTION (Orange zones) ──
        SolapurRoad(
            id = "road_17.6530_75.9130",
            name = "Civil Hospital Road",
            centerLat = 17.6530,
            centerLng = 75.9130,
            congestionLevel = "MEDIUM",
            avgSpeed = 35,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6550_75.9050",
            name = "South Sadar Bazar",
            centerLat = 17.6550,
            centerLng = 75.9050,
            congestionLevel = "MEDIUM",
            avgSpeed = 28,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6580_75.9080",
            name = "Murarji Peth Shopping",
            centerLat = 17.6580,
            centerLng = 75.9080,
            congestionLevel = "MEDIUM",
            avgSpeed = 38,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6450_75.9200",
            name = "College Road",
            centerLat = 17.6450,
            centerLng = 75.9200,
            congestionLevel = "MEDIUM",
            avgSpeed = 21,
            userCount = 4
        ),
        SolapurRoad(
            id = "road_17.6599_75.9064",
            name = "City Center / Main Road",
            centerLat = 17.6599,
            centerLng = 75.9064,
            congestionLevel = "MEDIUM",
            avgSpeed = 28,
            userCount = 5
        ),

        // ── LOW CONGESTION (Green zones) ──
        SolapurRoad(
            id = "road_17.6580_75.9250",
            name = "Akkalkot Road (NH52)",
            centerLat = 17.6580,
            centerLng = 75.9250,
            congestionLevel = "LOW",
            avgSpeed = 45,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6750_75.9100",
            name = "Vijapur Road (Highway)",
            centerLat = 17.6750,
            centerLng = 75.9100,
            congestionLevel = "LOW",
            avgSpeed = 50,
            userCount = 5
        ),
        SolapurRoad(
            id = "road_17.6450_75.9200",
            name = "Hotgi Road (South)",
            centerLat = 17.6350,
            centerLng = 75.9190,
            congestionLevel = "LOW",
            avgSpeed = 65,
            userCount = 5
        )
    )

    /**
     * Get road congestion as TrafficStatus map (for use in ViewModels)
     * Returns road ID -> TrafficStatus mapping
     */
    val dummyTrafficMap: Map<String, TrafficStatus> = solapurRoads.associate { road ->
        road.id to TrafficStatus(
            congestionLevel = road.congestionLevel,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Dummy traffic cameras that mirror Firestore `traffic cameras` collection shape.
     * Used until real live vehicle counts are connected.
     */
    val dummyTrafficCameras: List<TrafficCamera> = listOf(
        TrafficCamera(
            id = "cam_railway_station",
            name = "Railway Station Camera",
            latitude = 17.6715,
            longitude = 75.9164,
            vehicleCount = 82,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_budhwar_peth",
            name = "Budhwar Peth Camera",
            latitude = 17.6650,
            longitude = 75.9100,
            vehicleCount = 76,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_bus_stand",
            name = "Central Bus Stand Camera",
            latitude = 17.6700,
            longitude = 75.9050,
            vehicleCount = 68,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_civil_hospital",
            name = "Civil Hospital Camera",
            latitude = 17.6530,
            longitude = 75.9130,
            vehicleCount = 49,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_city_center",
            name = "City Center Camera",
            latitude = 17.6599,
            longitude = 75.9064,
            vehicleCount = 38,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_akkalkot_road",
            name = "Akkalkot Road Camera",
            latitude = 17.6580,
            longitude = 75.9250,
            vehicleCount = 22,
            lastUpdated = System.currentTimeMillis()
        ),
        TrafficCamera(
            id = "cam_vijapur_road",
            name = "Vijapur Road Camera",
            latitude = 17.6750,
            longitude = 75.9100,
            vehicleCount = 16,
            lastUpdated = System.currentTimeMillis()
        )
    )

    /**
     * Get congestion level for a specific road ID
     */
    fun getRoadCongestionLevel(roadId: String): String {
        return solapurRoads.find { it.id == roadId }?.congestionLevel ?: "LOW"
    }

    /**
     * Get road name from road ID - returns human-readable name
     */
    fun getRoadName(roadId: String): String {
        return solapurRoads.find { it.id == roadId }?.name ?: roadId
    }

    /**
     * Get road coordinates from road ID
     */
    fun getRoadCoordinates(roadId: String): Pair<Double, Double>? {
        val road = solapurRoads.find { it.id == roadId }
        return road?.let { Pair(it.centerLat, it.centerLng) }
    }

    /**
     * Get full road data from road ID
     */
    fun getRoad(roadId: String): SolapurRoad? {
        return solapurRoads.find { it.id == roadId }
    }

    /**
     * Get roads by congestion level
     */
    fun getRoadsByLevel(level: String): List<SolapurRoad> {
        return solapurRoads.filter { it.congestionLevel.equals(level, ignoreCase = true) }
    }

    /**
     * Analytics: Count roads by congestion level
     */
    val highCongestionCount: Int get() = solapurRoads.count { it.congestionLevel == "HIGH" }
    val mediumCongestionCount: Int get() = solapurRoads.count { it.congestionLevel == "MEDIUM" }
    val lowCongestionCount: Int get() = solapurRoads.count { it.congestionLevel == "LOW" }

    // ═══════════════════════════════════════════════════════════════════
    // DUMMY USER DATA
    // ═══════════════════════════════════════════════════════════════════

    // Marathi names for dummy users
    private val dummyNames = listOf(
        "Rahul Jadhav", "Priya Kulkarni", "Amit Shinde", "Sunita Patil", "Ravi Deshpande",
        "Meena Gaikwad", "Vijay Chavan", "Kavita More", "Suresh Pawar", "Anjali Bhosle",
        "Deepak Salunkhe", "Pooja Kamble", "Mahesh Mane", "Rekha Thorat", "Ganesh Waghmare",
        "Nisha Lokhande", "Sanjay Birajdar", "Smita Kale", "Anil Biradar", "Lata Jadhav",
        "Sachin Patil", "Varsha Desai", "Nilesh Ghole", "Swati Shelke", "Pravin Muthe",
        "Rohini Suryavanshi", "Ajay Shivajkar", "Archana Nale", "Santosh Yevale", "Madhuri Borde",
        "Tushar Kachare", "Manisha Raut", "Kiran Aher", "Sunanda Inamdar", "Ashok Kakade",
        "Neha Magar", "Vikram Sathe", "Sarika Pol", "Prasad Nalawade", "Shobha Tupe",
        "Manoj Gade", "Poonam Dhamal", "Rakesh Dandge", "Alka Hankare", "Sunil Sanas",
        "Geeta Bhalerao", "Hemant Ghuge", "Rupali Bhusare", "Dilip Kshirsagar", "Usha Bagi",
        "Rajesh Thombre", "Sneha Wagh", "Vinod Chaudhari", "Anita Khandale", "Prakash Dhage",
        "Shilpa Pardeshi", "Mohan Shirsat", "Jyoti Navale", "Arun Pande", "Kaveri Joshi"
    )

    /**
     * 60 dummy live users positioned on major Solapur city roads.
     * Coordinates are on real Solapur roads: Railway Station Road, Budhwar Peth,
     * Akkalkot Road, South Sadar Bazar, Vijapur Road, Hotgi Road, etc.
     */
    val dummyUsers: Map<String, LiveUserLocation> = buildMap {

        // Data: (lat, lng, speed, heading)
        val roadUsers = listOf(
            // ── Railway Station Road / Main City Center ──
            Triple(17.6599, 75.9064, 25 to 90.0),   // user_001 - City Center Mall
            Triple(17.6610, 75.9080, 30 to 92.0),   // user_002
            Triple(17.6625, 75.9095, 22 to 88.0),   // user_003
            Triple(17.6640, 75.9110, 28 to 270.0),  // user_004 (returning)
            Triple(17.6655, 75.9125, 35 to 270.0),  // user_005

            // ── Railway Station Area ──
            Triple(17.6715, 75.9164, 18 to 180.0),  // user_006 - Railway Station
            Triple(17.6700, 75.9150, 12 to 0.0),    // user_007
            Triple(17.6685, 75.9135, 20 to 180.0),  // user_008
            Triple(17.6670, 75.9120, 15 to 180.0),  // user_009
            Triple(17.6655, 75.9105, 22 to 0.0),    // user_010

            // ── Budhwar Peth Market ──
            Triple(17.6650, 75.9100, 10 to 90.0),   // user_011 - Market Area
            Triple(17.6645, 75.9090, 8  to 180.0),  // user_012
            Triple(17.6640, 75.9080, 12 to 0.0),    // user_013
            Triple(17.6635, 75.9070, 15 to 270.0),  // user_014
            Triple(17.6630, 75.9060, 6  to 90.0),   // user_015

            // ── Civil Hospital / Medical Road ──
            Triple(17.6530, 75.9130, 35 to 315.0),  // user_016
            Triple(17.6520, 75.9120, 40 to 315.0),  // user_017
            Triple(17.6510, 75.9110, 30 to 135.0),  // user_018
            Triple(17.6500, 75.9100, 38 to 135.0),  // user_019
            Triple(17.6490, 75.9090, 42 to 315.0),  // user_020

            // ── Akkalkot Road (Eastern Highway) ──
            Triple(17.6580, 75.9250, 50 to 90.0),   // user_021
            Triple(17.6585, 75.9300, 45 to 90.0),   // user_022
            Triple(17.6590, 75.9350, 38 to 270.0),  // user_023
            Triple(17.6595, 75.9400, 42 to 270.0),  // user_024
            Triple(17.6600, 75.9450, 30 to 90.0),   // user_025

            // ── South Sadar Bazar ──
            Triple(17.6550, 75.9050, 28 to 225.0),  // user_026
            Triple(17.6540, 75.9040, 32 to 225.0),  // user_027
            Triple(17.6530, 75.9030, 25 to 45.0),   // user_028
            Triple(17.6520, 75.9020, 36 to 225.0),  // user_029
            Triple(17.6510, 75.9010, 20 to 45.0),   // user_030

            // ── Vijapur Road (North Highway) ──
            Triple(17.6750, 75.9100, 55 to 0.0),    // user_031
            Triple(17.6800, 75.9095, 50 to 0.0),    // user_032
            Triple(17.6850, 75.9090, 45 to 180.0),  // user_033
            Triple(17.6900, 75.9085, 52 to 180.0),  // user_034
            Triple(17.6950, 75.9080, 48 to 0.0),    // user_035

            // ── Hotgi Road (South) ──
            Triple(17.6450, 75.9200, 65 to 180.0),  // user_036
            Triple(17.6400, 75.9195, 70 to 180.0),  // user_037
            Triple(17.6350, 75.9190, 68 to 0.0),    // user_038
            Triple(17.6300, 75.9185, 60 to 0.0),    // user_039
            Triple(17.6250, 75.9180, 72 to 180.0),  // user_040

            // ── Bus Stand / Central Bus Station Area ──
            Triple(17.6700, 75.9050, 15 to 0.0),    // user_041
            Triple(17.6695, 75.9040, 12 to 180.0),  // user_042
            Triple(17.6690, 75.9030, 18 to 0.0),    // user_043
            Triple(17.6685, 75.9020, 10 to 180.0),  // user_044
            Triple(17.6680, 75.9010, 8  to 0.0),    // user_045

            // ── Murarji Peth / Shopping Complex ──
            Triple(17.6580, 75.9080, 40 to 315.0),  // user_046
            Triple(17.6575, 75.9070, 35 to 315.0),  // user_047
            Triple(17.6570, 75.9060, 45 to 135.0),  // user_048
            Triple(17.6565, 75.9050, 38 to 135.0),  // user_049
            Triple(17.6560, 75.9040, 30 to 315.0),  // user_050

            // ── Sports Stadium Area ──
            Triple(17.6620, 75.9150, 45 to 45.0),   // user_051
            Triple(17.6630, 75.9160, 50 to 45.0),   // user_052
            Triple(17.6640, 75.9170, 42 to 225.0),  // user_053
            Triple(17.6650, 75.9180, 48 to 225.0),  // user_054

            // ── College Road / University Area ──
            Triple(17.6450, 75.9200, 20 to 90.0),   // user_055
            Triple(17.6455, 75.9210, 18 to 90.0),   // user_056
            Triple(17.6460, 75.9220, 22 to 270.0),  // user_057
            Triple(17.6465, 75.9230, 25 to 270.0),  // user_058

            // ── Siddheshwar Temple Area ──
            Triple(17.6702, 75.9063, 5  to 0.0),    // user_059 (nearly stopped)
            Triple(17.6708, 75.9070, 0  to 90.0)    // user_060 (stopped)
        )

        val now = System.currentTimeMillis()
        roadUsers.forEachIndexed { index, (lat, lng, speedHeading) ->
            val uid = "dummy_user_${String.format("%03d", index + 1)}"
            put(
                uid,
                LiveUserLocation(
                    lat = lat,
                    lng = lng,
                    speed = speedHeading.first,
                    heading = speedHeading.second,
                    name = dummyNames[index],
                    timestamp = now
                )
            )
        }
    }
}

// Backward compatibility alias for old code referencing AurangabadDummyData
@Deprecated("Use SolapurDummyData instead", ReplaceWith("SolapurDummyData"))
typealias AurangabadDummyData = SolapurDummyData
