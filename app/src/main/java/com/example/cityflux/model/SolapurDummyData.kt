package com.example.cityflux.model

/**
 * Dummy live user locations on real Aurangabad (Chhatrapati Sambhajinagar) city roads.
 * Used to demonstrate live citizen tracking on map screens.
 * 60 users spread across major Aurangabad roads/areas.
 */
object AurangabadDummyData {

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
     * 60 dummy live users positioned on major Aurangabad (Chhatrapati Sambhajinagar) city roads.
     * Coordinates are on real Aurangabad roads: Jalna Road, Station Road, 
     * Kranti Chowk, CIDCO, Waluj, Cambridge Road, etc.
     */
    val dummyUsers: Map<String, LiveUserLocation> = buildMap {

        // Data: (lat, lng, speed, heading)
        val roadUsers = listOf(
            // ── Jalna Road (Main Highway) ──
            Triple(19.8762, 75.3433, 55 to 90.0),   // user_001
            Triple(19.8780, 75.3510, 48 to 92.0),   // user_002
            Triple(19.8795, 75.3580, 60 to 88.0),   // user_003
            Triple(19.8810, 75.3650, 52 to 270.0),  // user_004 (returning)
            Triple(19.8825, 75.3720, 45 to 270.0),  // user_005

            // ── Station Road / Railway Station Area ──
            Triple(19.8685, 75.3205, 18 to 180.0),  // user_006
            Triple(19.8670, 75.3220, 12 to 0.0),    // user_007
            Triple(19.8655, 75.3235, 20 to 180.0),  // user_008
            Triple(19.8640, 75.3250, 15 to 180.0),  // user_009
            Triple(19.8625, 75.3265, 22 to 0.0),    // user_010

            // ── Kranti Chowk / City Center ──
            Triple(19.8765, 75.3280, 10 to 90.0),   // user_011
            Triple(19.8758, 75.3295, 8  to 180.0),  // user_012
            Triple(19.8750, 75.3310, 12 to 0.0),    // user_013
            Triple(19.8742, 75.3325, 15 to 270.0),  // user_014
            Triple(19.8735, 75.3340, 6  to 90.0),   // user_015

            // ── CIDCO N1 to N10 ──
            Triple(19.8450, 75.3100, 35 to 315.0),  // user_016
            Triple(19.8420, 75.3150, 40 to 315.0),  // user_017
            Triple(19.8390, 75.3200, 30 to 135.0),  // user_018
            Triple(19.8360, 75.3250, 38 to 135.0),  // user_019
            Triple(19.8330, 75.3300, 42 to 315.0),  // user_020

            // ── Waluj MIDC Industrial Area ──
            Triple(19.8200, 75.2800, 50 to 270.0),  // user_021
            Triple(19.8180, 75.2750, 45 to 270.0),  // user_022
            Triple(19.8160, 75.2700, 38 to 90.0),   // user_023
            Triple(19.8140, 75.2650, 42 to 90.0),   // user_024
            Triple(19.8120, 75.2600, 30 to 270.0),  // user_025

            // ── Cambridge Road / Cantonment ──
            Triple(19.8550, 75.3050, 28 to 225.0),  // user_026
            Triple(19.8520, 75.3000, 32 to 225.0),  // user_027
            Triple(19.8490, 75.2950, 25 to 45.0),   // user_028
            Triple(19.8460, 75.2900, 36 to 225.0),  // user_029
            Triple(19.8430, 75.2850, 20 to 45.0),   // user_030

            // ── Paithan Road ──
            Triple(19.8900, 75.3100, 55 to 180.0),  // user_031
            Triple(19.8850, 75.3080, 50 to 180.0),  // user_032
            Triple(19.8800, 75.3060, 45 to 0.0),    // user_033
            Triple(19.8750, 75.3040, 52 to 0.0),    // user_034
            Triple(19.8700, 75.3020, 48 to 180.0),  // user_035

            // ── Beed Bypass Road ──
            Triple(19.8600, 75.3600, 65 to 90.0),   // user_036
            Triple(19.8610, 75.3700, 70 to 90.0),   // user_037
            Triple(19.8620, 75.3800, 68 to 270.0),  // user_038
            Triple(19.8630, 75.3900, 60 to 270.0),  // user_039
            Triple(19.8640, 75.4000, 72 to 90.0),   // user_040

            // ── Adalat Road / Court Area ──
            Triple(19.8720, 75.3180, 15 to 0.0),    // user_041
            Triple(19.8730, 75.3195, 12 to 180.0),  // user_042
            Triple(19.8740, 75.3210, 18 to 0.0),    // user_043
            Triple(19.8750, 75.3225, 10 to 180.0),  // user_044
            Triple(19.8760, 75.3240, 8  to 0.0),    // user_045

            // ── Satara Parisar / 7 Hills ──
            Triple(19.9000, 75.3200, 40 to 315.0),  // user_046
            Triple(19.9050, 75.3150, 35 to 315.0),  // user_047
            Triple(19.9100, 75.3100, 45 to 135.0),  // user_048
            Triple(19.9150, 75.3050, 38 to 135.0),  // user_049
            Triple(19.9200, 75.3000, 30 to 315.0),  // user_050

            // ── Harsul Road ──
            Triple(19.8950, 75.3400, 45 to 45.0),   // user_051
            Triple(19.9000, 75.3450, 50 to 45.0),   // user_052
            Triple(19.9050, 75.3500, 42 to 225.0),  // user_053
            Triple(19.9100, 75.3550, 48 to 225.0),  // user_054

            // ── Aurangpura / Samarth Nagar ──
            Triple(19.8850, 75.3250, 20 to 90.0),   // user_055
            Triple(19.8860, 75.3300, 18 to 90.0),   // user_056
            Triple(19.8870, 75.3350, 22 to 270.0),  // user_057
            Triple(19.8880, 75.3400, 25 to 270.0),  // user_058

            // ── Connaught Place / TV Center ──
            Triple(19.8680, 75.3380, 5  to 0.0),    // user_059 (nearly stopped)
            Triple(19.8690, 75.3390, 0  to 90.0)    // user_060 (stopped)
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

// Backward compatibility alias
@Deprecated("Use AurangabadDummyData instead", ReplaceWith("AurangabadDummyData"))
typealias SolapurDummyData = AurangabadDummyData

// Extension to access dummy users with old name
@Suppress("DEPRECATION")
val SolapurDummyData.dummy50Users: Map<String, LiveUserLocation>
    get() = AurangabadDummyData.dummyUsers
