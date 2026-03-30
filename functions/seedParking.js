/**
 * Seed script to add demo parking zones in Aurangabad (Chhatrapati Sambhajinagar)
 * Run: node seedParking.js
 * Delete later: node seedParking.js --delete
 * 
 * New format includes:
 * - parkingType: "free" or "paid"
 * - ratePerHour: price per hour in ₹
 * - minDuration: minimum parking time in minutes
 * - maxDuration: maximum parking time in minutes
 * - vehicleTypes: array of supported vehicle types
 */

const admin = require("firebase-admin");

// Initialize Firebase Admin (uses default credentials from gcloud/firebase login)
const serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
  databaseURL: "https://cityflux-1adab-default-rtdb.firebaseio.com"
});

const firestore = admin.firestore();
const database = admin.database();

// Demo parking zones in Aurangabad - NEW FORMAT
const demoParkingSpots = [
  {
    id: "demo_parking_01",
    address: "Railway Station Parking",
    location: new admin.firestore.GeoPoint(19.8606, 75.3105),
    totalSlots: 50,
    availableSlots: 35,
    isLegal: true,
    parkingType: "paid",
    ratePerHour: 15,
    minDuration: 30,
    maxDuration: 480,
    vehicleTypes: ["car", "bike", "ev"]
  },
  {
    id: "demo_parking_02", 
    address: "Bus Stand Parking Zone",
    location: new admin.firestore.GeoPoint(19.8720, 75.3200),
    totalSlots: 30,
    availableSlots: 12,
    isLegal: true,
    parkingType: "paid",
    ratePerHour: 11,
    minDuration: 15,
    maxDuration: 60,
    vehicleTypes: ["car", "bike"]
  },
  {
    id: "demo_parking_03",
    address: "Kranti Chowk Market Parking",
    location: new admin.firestore.GeoPoint(19.8765, 75.3280),
    totalSlots: 40,
    availableSlots: 8,
    isLegal: true,
    parkingType: "paid",
    ratePerHour: 20,
    minDuration: 30,
    maxDuration: 240,
    vehicleTypes: ["car", "bike", "ev", "suv"]
  },
  {
    id: "demo_parking_04",
    address: "Prozone Mall Parking",
    location: new admin.firestore.GeoPoint(19.8780, 75.3433),
    totalSlots: 100,
    availableSlots: 45,
    isLegal: true,
    parkingType: "free",
    ratePerHour: 0,
    minDuration: 30,
    maxDuration: 180,
    vehicleTypes: ["car", "bike", "ev", "suv"]
  },
  {
    id: "demo_parking_05",
    address: "CIDCO N-1 Free Parking",
    location: new admin.firestore.GeoPoint(19.8450, 75.3100),
    totalSlots: 60,
    availableSlots: 38,
    isLegal: true,
    parkingType: "free",
    ratePerHour: 0,
    minDuration: 15,
    maxDuration: 120,
    vehicleTypes: ["car", "bike"]
  },
  {
    id: "demo_parking_06",
    address: "Jalna Road Side Parking",
    location: new admin.firestore.GeoPoint(19.8810, 75.3650),
    totalSlots: 15,
    availableSlots: 5,
    isLegal: false,
    parkingType: "paid",
    ratePerHour: 10,
    minDuration: 15,
    maxDuration: 60,
    vehicleTypes: ["bike"]
  },
  {
    id: "demo_parking_07",
    address: "Cambridge Road Commercial",
    location: new admin.firestore.GeoPoint(19.8550, 75.3050),
    totalSlots: 80,
    availableSlots: 32,
    isLegal: true,
    parkingType: "paid",
    ratePerHour: 25,
    minDuration: 60,
    maxDuration: 480,
    vehicleTypes: ["car", "ev", "suv", "truck"]
  }
];

async function seedParkingData() {
  console.log("🅿️  Adding demo parking zones to Firebase (NEW FORMAT)...\n");

  const batch = firestore.batch();
  const liveUpdates = {};
  const now = admin.firestore.FieldValue.serverTimestamp();

  for (const spot of demoParkingSpots) {
    const docRef = firestore.collection("parking").doc(spot.id);
    
    // Firestore data (static parking info with new format)
    batch.set(docRef, {
      address: spot.address,
      location: spot.location,
      totalSlots: spot.totalSlots,
      availableSlots: spot.availableSlots,
      isLegal: spot.isLegal,
      // New fields
      parkingType: spot.parkingType,
      ratePerHour: spot.ratePerHour,
      minDuration: spot.minDuration,
      maxDuration: spot.maxDuration,
      vehicleTypes: spot.vehicleTypes,
      createdAt: now,
      updatedAt: now
    });

    // Realtime DB data (live availability)
    liveUpdates[spot.id] = {
      availableSlots: spot.availableSlots,
      totalSlots: spot.totalSlots,
      lastUpdated: Date.now()
    };

    const typeLabel = spot.parkingType === "free" ? "🆓 FREE" : `💰 ₹${spot.ratePerHour}/hr`;
    console.log(`  ✓ ${spot.address} (${spot.availableSlots}/${spot.totalSlots} slots) ${typeLabel}`);
  }

  // Commit Firestore batch
  await batch.commit();
  console.log("\n✅ Firestore: parking collection updated with new format");

  // Update Realtime Database
  await database.ref("parking_live").update(liveUpdates);
  console.log("✅ Realtime DB: parking_live updated");

  console.log("\n🎉 Done! Added " + demoParkingSpots.length + " demo parking zones.");
  console.log("📍 Location: Aurangabad (Chhatrapati Sambhajinagar), Maharashtra\n");
}

async function deleteParkingData() {
  console.log("🗑️  Deleting demo parking zones...\n");

  const batch = firestore.batch();

  for (const spot of demoParkingSpots) {
    const docRef = firestore.collection("parking").doc(spot.id);
    batch.delete(docRef);
    console.log(`  ✗ Deleting ${spot.id}`);
  }

  // Delete from Firestore
  await batch.commit();
  console.log("\n✅ Firestore: demo parking deleted");

  // Delete from Realtime Database
  for (const spot of demoParkingSpots) {
    await database.ref(`parking_live/${spot.id}`).remove();
  }
  console.log("✅ Realtime DB: demo parking_live deleted");

  console.log("\n🎉 Done! Removed all demo parking zones.\n");
}

// Main
const args = process.argv.slice(2);
if (args.includes("--delete")) {
  deleteParkingData()
    .then(() => process.exit(0))
    .catch(err => { console.error(err); process.exit(1); });
} else {
  seedParkingData()
    .then(() => process.exit(0))
    .catch(err => { console.error(err); process.exit(1); });
}
