package com.example.cityflux.model

/**
 * Dummy live user locations on real Solapur city roads.
 * Used to demonstrate live citizen tracking on map screens.
 * 50 users spread across major Solapur roads/areas.
 */
object SolapurDummyData {

    // Solapur Marathi names for dummy users
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
        "Geeta Bhalerao", "Hemant Ghuge", "Rupali Bhusare", "Dilip Kshirsagar", "Usha Bagi"
    )

    /**
     * 50 dummy live users positioned on major Solapur city roads.
     * Coordinates are on real Solapur roads: Pune-Hyderabad Highway,
     * Station Road, Hotgi Road, Akkalkot Road, Vijaypur Road, etc.
     */
    val dummy50Users: Map<String, LiveUserLocation> = buildMap {

        // Data: (lat, lng, speed, heading, nameIndex)
        val roadUsers = listOf(
            // ── Pune-Hyderabad Highway (NH-65) ──
            Triple(17.7023, 75.9101, 55 to 90.0),   // user_001
            Triple(17.6980, 75.9150, 48 to 92.0),   // user_002
            Triple(17.6955, 75.9180, 60 to 88.0),   // user_003
            Triple(17.6912, 75.9220, 52 to 270.0),  // user_004 (returning)
            Triple(17.6876, 75.9260, 45 to 270.0),  // user_005

            // ── Station Road / MG Road ──
            Triple(17.6745, 75.9008, 18 to 180.0),  // user_006
            Triple(17.6710, 75.9012, 12 to 0.0),    // user_007
            Triple(17.6680, 75.9018, 20 to 180.0),  // user_008
            Triple(17.6655, 75.9025, 15 to 180.0),  // user_009
            Triple(17.6625, 75.9030, 22 to 0.0),    // user_010

            // ── Hotgi Road ──
            Triple(17.6560, 75.9145, 35 to 315.0),  // user_011
            Triple(17.6520, 75.9200, 40 to 315.0),  // user_012
            Triple(17.6490, 75.9250, 30 to 135.0),  // user_013
            Triple(17.6460, 75.9300, 38 to 135.0),  // user_014
            Triple(17.6435, 75.9345, 42 to 315.0),  // user_015

            // ── Akkalkot Road ──
            Triple(17.6620, 75.8950, 28 to 225.0),  // user_016
            Triple(17.6580, 75.8890, 32 to 225.0),  // user_017
            Triple(17.6545, 75.8840, 25 to 45.0),   // user_018
            Triple(17.6510, 75.8790, 36 to 225.0),  // user_019
            Triple(17.6475, 75.8745, 20 to 45.0),   // user_020

            // ── Vijaypur Road ──
            Triple(17.6820, 75.8780, 50 to 315.0),  // user_021
            Triple(17.6780, 75.8730, 45 to 315.0),  // user_022
            Triple(17.6750, 75.8685, 38 to 135.0),  // user_023
            Triple(17.6715, 75.8645, 42 to 135.0),  // user_024
            Triple(17.6685, 75.8610, 30 to 315.0),  // user_025

            // ── Solapur Ring Road / Bypass ──
            Triple(17.7100, 75.8950, 65 to 90.0),   // user_026
            Triple(17.7095, 75.9050, 70 to 90.0),   // user_027
            Triple(17.7090, 75.9150, 68 to 270.0),  // user_028
            Triple(17.7085, 75.9250, 60 to 270.0),  // user_029
            Triple(17.7080, 75.9350, 72 to 90.0),   // user_030

            // ── Bijapur Road ──
            Triple(17.6890, 75.8650, 40 to 270.0),  // user_031
            Triple(17.6900, 75.8580, 35 to 270.0),  // user_032
            Triple(17.6910, 75.8510, 45 to 90.0),   // user_033
            Triple(17.6920, 75.8440, 38 to 90.0),   // user_034
            Triple(17.6930, 75.8370, 30 to 270.0),  // user_035

            // ── Siddheshwar Temple Road / City Center ──
            Triple(17.6712, 75.9080, 10 to 90.0),   // user_036
            Triple(17.6718, 75.9110, 8  to 180.0),  // user_037
            Triple(17.6725, 75.9140, 12 to 0.0),    // user_038
            Triple(17.6730, 75.9170, 15 to 270.0),  // user_039
            Triple(17.6738, 75.9200, 6  to 90.0),   // user_040

            // ── Mangalwedha Road ──
            Triple(17.6350, 75.9150, 55 to 180.0),  // user_041
            Triple(17.6300, 75.9130, 50 to 180.0),  // user_042
            Triple(17.6250, 75.9110, 45 to 0.0),    // user_043
            Triple(17.6200, 75.9090, 52 to 0.0),    // user_044

            // ── Barshi Road ──
            Triple(17.6800, 75.9600, 40 to 90.0),   // user_045
            Triple(17.6810, 75.9680, 45 to 90.0),   // user_046
            Triple(17.6820, 75.9760, 38 to 270.0),  // user_047

            // ── Hutatma Chowk / Dayanand College area ──
            Triple(17.6755, 75.9052, 5  to 0.0),    // user_048 (nearly stopped)
            Triple(17.6770, 75.9060, 0  to 90.0),   // user_049 (stopped)
            Triple(17.6740, 75.9045, 8  to 180.0)   // user_050
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
