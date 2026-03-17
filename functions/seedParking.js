/**
 * Seed script to add demo parking zones in Solapur
 * Run: node seedParking.js
 * Delete later: node seedParking.js --delete
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

// Demo parking zones in Solapur
const demoParkingSpots = [
  {
    id: "demo_parking_01",
    address: "Solapur Railway Station Parking",
    location: new admin.firestore.GeoPoint(17.6599, 75.9064),
    totalSlots: 50,
    availableSlots: 35,
    isLegal: true
  },
  {
    id: "demo_parking_02", 
    address: "Siddheshwar Temple Parking",
    location: new admin.firestore.GeoPoint(17.6720, 75.9100),
    totalSlots: 30,
    availableSlots: 12,
    isLegal: true
  },
  {
    id: "demo_parking_03",
    address: "Hutatma Chowk Market Parking",
    location: new admin.firestore.GeoPoint(17.6550, 75.9050),
    totalSlots: 40,
    availableSlots: 8,
    isLegal: true
  },
  {
    id: "demo_parking_04",
    address: "City Mall Solapur Parking",
    location: new admin.firestore.GeoPoint(17.6780, 75.9200),
    totalSlots: 100,
    availableSlots: 45,
    isLegal: true
  },
  {
    id: "demo_parking_05",
    address: "Bus Stand Parking Zone",
    location: new admin.firestore.GeoPoint(17.6620, 75.9120),
    totalSlots: 25,
    availableSlots: 0,
    isLegal: true
  },
  {
    id: "demo_parking_06",
    address: "Akkalkot Road Side Parking",
    location: new admin.firestore.GeoPoint(17.6480, 75.9180),
    totalSlots: 15,
    availableSlots: 5,
    isLegal: false  // Illegal parking zone
  },
  {
    id: "demo_parking_07",
    address: "Vijapur Road Commercial Area",
    location: new admin.firestore.GeoPoint(17.6700, 75.8950),
    totalSlots: 60,
    availableSlots: 22,
    isLegal: true
  }
];

async function seedParkingData() {
  console.log("🅿️  Adding demo parking zones to Firebase...\n");

  const batch = firestore.batch();
  const liveUpdates = {};

  for (const spot of demoParkingSpots) {
    const docRef = firestore.collection("parking").doc(spot.id);
    
    // Firestore data (static parking info)
    batch.set(docRef, {
      address: spot.address,
      location: spot.location,
      totalSlots: spot.totalSlots,
      availableSlots: spot.availableSlots,
      isLegal: spot.isLegal
    });

    // Realtime DB data (live availability)
    liveUpdates[spot.id] = {
      availableSlots: spot.availableSlots
    };

    console.log(`  ✓ ${spot.address} (${spot.availableSlots}/${spot.totalSlots} slots)`);
  }

  // Commit Firestore batch
  await batch.commit();
  console.log("\n✅ Firestore: parking collection updated");

  // Update Realtime Database
  await database.ref("parking_live").update(liveUpdates);
  console.log("✅ Realtime DB: parking_live updated");

  console.log("\n🎉 Done! Added " + demoParkingSpots.length + " demo parking zones.");
  console.log("📍 Location: Solapur, Maharashtra\n");
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
