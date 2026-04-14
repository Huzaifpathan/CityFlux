/**
 * CityFlux – Firebase Cloud Functions
 *
 * Triggers:
 *  1. onNewReport  – fires when a document is created in `reports/{reportId}`.
 *                    Sends an FCM push notification to all Traffic Police users.
 *
 *  2. onReportStatusChange – fires when a report's status is updated.
 *                            Notifies the citizen who filed the report.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const rtdb = admin.database();
const messaging = admin.messaging();
const storageBucket = admin.storage().bucket();

// ── Configurable tuning parameters ───────────────────────────────
const RECENT_WINDOW_MINUTES = 10; // window to consider reports as 'clustered'
const CLUSTER_THRESHOLD_HIGH = 3; // >= this => HIGH
const CLUSTER_THRESHOLD_MEDIUM = 2; // == this => MEDIUM
const CONGESTION_DECAY_MINUTES = 30; // if no updates for this many minutes, lower congestion

// Canonical Firestore mirror collections for admin map dashboard
const MAP_COLLECTIONS = {
  TRAFFIC: "map_traffic",
  PARKING_LIVE: "map_parking_live",
  LIVE_USERS: "map_live_users",
  INCIDENTS: "map_incidents",
  PARKING_SPOTS: "map_parking_spots",
  TRAFFIC_CAMERAS: "map_traffic_cameras",
};

// ── Mapping report type → human-friendly alert message ───────────
const REPORT_ALERTS = {
  illegal_parking: "🅿️ Illegal parking reported in your area",
  accident: "🚨 Accident reported in your area",
  hawker: "🛒 Hawker/street vendor issue reported",
  traffic_violation: "🚦 Traffic violation reported nearby",
  road_damage: "🚧 Road damage reported in your area",
  other: "📢 New civic issue reported",
};

const VALID_REPORT_TYPES = new Set([
  "illegal_parking",
  "accident",
  "hawker",
  "traffic_violation",
  "road_damage",
  "other",
]);

/** Helpers */
function nowMs() {
  return Date.now();
}

function toMillis(timestamp) {
  if (!timestamp) return 0;
  if (typeof timestamp.toMillis === "function") return timestamp.toMillis();
  if (timestamp.seconds) return timestamp.seconds * 1000 + (timestamp.nanoseconds || 0) / 1e6;
  return new Date(timestamp).getTime();
}

function roundBucket(lat, lng, precision = 3) {
  // Round coordinates to a grid to approximate 'road areas'
  return `${lat.toFixed(precision)}_${lng.toFixed(precision)}`;
}

function haversineMeters(lat1, lon1, lat2, lon2) {
  function toRad(v) { return (v * Math.PI) / 180; }
  const R = 6371000; // meters
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function toNumber(value, fallback = 0) {
  if (typeof value === "number" && !Number.isNaN(value)) return value;
  if (typeof value === "string" && value.trim().length) {
    const parsed = Number(value);
    if (!Number.isNaN(parsed)) return parsed;
  }
  return fallback;
}

function parseCameraCoordinates(data = {}) {
  const directLat = data.latitude ?? data.lat;
  const directLng = data.longitude ?? data.lng;
  if (directLat !== undefined && directLng !== undefined) {
    return { latitude: toNumber(directLat, 0), longitude: toNumber(directLng, 0) };
  }

  const location = data.location || data.coordinates;
  if (Array.isArray(location) && location.length >= 2) {
    return { latitude: toNumber(location[0], 0), longitude: toNumber(location[1], 0) };
  }

  if (location && typeof location === "object") {
    if (typeof location.latitude === "number" && typeof location.longitude === "number") {
      return { latitude: location.latitude, longitude: location.longitude };
    }
    if (typeof location.lat === "number" && typeof location.lng === "number") {
      return { latitude: location.lat, longitude: location.lng };
    }
  }

  return { latitude: 0, longitude: 0 };
}

async function mirrorTrafficCameraDoc(change, cameraId, sourceCollection) {
  const targetRef = db.collection(MAP_COLLECTIONS.TRAFFIC_CAMERAS).doc(cameraId);
  if (!change.after.exists) {
    await targetRef.delete().catch(() => null);
    return;
  }

  const data = change.after.data() || {};
  const { latitude, longitude } = parseCameraCoordinates(data);
  const vehicleCount = toNumber(
    data.vehicleCount ??
    data.liveVehicleCount ??
    data.currentVehicleCount ??
    data.vehicle_count ??
    data.count ??
    data.countedVehicles ??
    data.counted ??
    data.detected,
    0
  );

  await targetRef.set({
    cameraId,
    sourceCollection,
    name: data.name || data.cameraName || data.title || data.camera_name || `Camera ${cameraId}`,
    latitude,
    longitude,
    vehicleCount: Math.max(0, vehicleCount),
    raw: data,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  }, { merge: true });
}

async function notifyRoles(roles, notification, data = {}) {
  const tokens = [];
  const snapshot = await db.collection("users").where("role", "in", roles).get();
  snapshot.forEach(doc => {
    const d = doc.data();
    if (d.fcmToken) tokens.push(d.fcmToken);
  });

  if (tokens.length === 0) {
    functions.logger.info("No tokens for roles:", roles);
    return { success: 0, failure: 0 };
  }

  const message = {
    notification: notification,
    data: data,
    tokens: tokens
  };

  try {
    const resp = await messaging.sendEachForMulticast(message);
    functions.logger.info(`Sent to ${resp.successCount}/${tokens.length} devices`);

    // clean stale tokens
    if (resp.failureCount > 0) {
      const stale = [];
      resp.responses.forEach((r, i) => {
        if (!r.success) {
          const code = r.error?.code;
          if (code === "messaging/invalid-registration-token" || code === "messaging/registration-token-not-registered") {
            stale.push(tokens[i]);
          }
        }
      });
      if (stale.length) {
        const batch = db.batch();
        for (const t of stale) {
          const snap = await db.collection("users").where("fcmToken", "==", t).get();
          snap.forEach(d => batch.update(d.ref, { fcmToken: admin.firestore.FieldValue.delete() }));
        }
        await batch.commit();
        functions.logger.info(`Cleaned ${stale.length} stale tokens`);
      }
    }

    return { success: resp.successCount, failure: resp.failureCount };
  } catch (err) {
    functions.logger.error("FCM send error", err);
    throw err; // Let platform retry if transient
  }
}

/**
 * Auto-validate report fields on creation.
 * If invalid, mark as 'Rejected' with a failureReason.
 * Returns boolean `isValid`.
 */
async function validateReport(reportRef, reportData) {
  const errors = [];
  // user exists?
  if (!reportData.userId) {
    errors.push("missing userId");
  } else {
    const u = await db.collection("users").doc(reportData.userId).get();
    if (!u.exists) errors.push("userId not found");
  }

  // type
  if (!reportData.type || !VALID_REPORT_TYPES.has(reportData.type)) {
    errors.push("invalid type");
  }

  // image
  if (!reportData.imageUrl) errors.push("missing imageUrl");

  // coords
  const lat = Number(reportData.latitude);
  const lng = Number(reportData.longitude);
  if (Number.isNaN(lat) || Number.isNaN(lng)) {
    errors.push("invalid latitude/longitude");
  } else if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
    errors.push("coords out of range");
  }

  if (errors.length) {
    await reportRef.update({ status: "Rejected", validationErrors: errors, validatedAt: admin.firestore.Timestamp.now() });
    functions.logger.warn(`Report ${reportRef.id} rejected: ${errors.join(", ")}`);
    return false;
  }

  // mark as pending validated
  await reportRef.update({ status: "Pending", validatedAt: admin.firestore.Timestamp.now() });
  functions.logger.info(`Report ${reportRef.id} validated`);
  return true;
}

/** Count recent reports near a given lat/lng within WINDOW and radiusMeters */
async function countRecentReportsNearby(lat, lng, windowMinutes = RECENT_WINDOW_MINUTES, radiusMeters = 400) {
  const cutoff = Date.now() - windowMinutes * 60 * 1000;
  const snap = await db.collection("reports").where("timestamp", ">=", admin.firestore.Timestamp.fromMillis(cutoff)).get();
  let count = 0;
  snap.forEach(doc => {
    const r = doc.data();
    const rLat = Number(r.latitude);
    const rLng = Number(r.longitude);
    if (!isNaN(rLat) && !isNaN(rLng)) {
      const d = haversineMeters(lat, lng, rLat, rLng);
      if (d <= radiusMeters) count++;
    }
  });
  return count;
}

/** Update traffic congestion for a bucket */
async function updateTrafficBucket(lat, lng, level) {
  const bucket = roundBucket(lat, lng);
  const ref = rtdb.ref(`traffic/${bucket}`);
  const payload = {
    congestionLevel: level,
    lastUpdated: admin.database.ServerValue.TIMESTAMP,
    center: { lat: Number(lat), lng: Number(lng) }
  };
  await ref.update(payload);
  functions.logger.info(`Traffic bucket ${bucket} set to ${level}`);

  // If HIGH, send alerts to Police and Citizens nearby
  if (level === "HIGH") {
    const notification = { title: "Heavy traffic ahead — choose alternate route.", body: "Heavy traffic detected nearby." };
    await notifyRoles(["Traffic Police", "Citizen"], notification, { type: "congestion", bucket });
  }
}

/** === Function: onNewReport (validation, congestion calc, alerts) === */
exports.onNewReport = functions.firestore.document('reports/{reportId}').onCreate(async (snap, context) => {
  const report = snap.data();
  const id = context.params.reportId;
  functions.logger.info(`onNewReport start: ${id}`, { report });

  try {
    const valid = await validateReport(snap.ref, report);
    if (!valid) return null;

    const lat = Number(report.latitude);
    const lng = Number(report.longitude);

    // Count nearby recent reports
    let count = 0;
    if (!isNaN(lat) && !isNaN(lng)) {
      count = await countRecentReportsNearby(lat, lng);
    }

    let level = "LOW";
    if (count >= CLUSTER_THRESHOLD_HIGH) level = "HIGH";
    else if (count >= CLUSTER_THRESHOLD_MEDIUM) level = "MEDIUM";

    if (!isNaN(lat) && !isNaN(lng)) {
      await updateTrafficBucket(lat, lng, level);
    }

    // If serious (accident), alert immediately
    if (report.type === 'accident') {
      const notification = { title: "Accident reported nearby — slow down.", body: report.title || report.description || "Accident reported in your area." };
      await notifyRoles(["Traffic Police", "Citizen"], notification, { type: 'accident', reportId: id });
    }

    // Notify Traffic Police about the new report (summary)
    const summary = { title: REPORT_ALERTS[report.type] || REPORT_ALERTS.other, body: report.title || report.description || "New report filed." };
    await notifyRoles(["Traffic Police"], summary, { type: report.type || 'other', reportId: id });

  } catch (err) {
    functions.logger.error("Error in onNewReport", err);
    throw err; // allow functions platform to retry if transient
  }
  return null;
});

/** === Function: onParkingLiveChange (Realtime DB trigger) === */
exports.onParkingLiveChange = functions.database.ref('/parking_live/{parkingId}').onWrite(async (change, context) => {
  const parkingId = context.params.parkingId;
  const before = change.before.val() || {};
  const after = change.after.val() || {};
  functions.logger.info(`parking_live ${parkingId} changed`, { before, after });

  try {
    const mirrorRef = db.collection(MAP_COLLECTIONS.PARKING_LIVE).doc(parkingId);
    if (!change.after.exists()) {
      await mirrorRef.delete().catch(() => null);
    } else {
      await mirrorRef.set({
        parkingId,
        ...after,
        source: "rtdb",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      }, { merge: true });
    }

    const available = typeof after.availableSlots === 'number' ? after.availableSlots : null;
    if (available === 0) {
      // Find parking doc to get location
      const pDoc = await db.collection('parking').doc(parkingId).get();
      if (pDoc.exists) {
        const p = pDoc.data();
        const loc = p.location;
        if (loc && typeof loc.latitude === 'number' && typeof loc.longitude === 'number') {
          // increase congestion nearby
          await updateTrafficBucket(loc.latitude, loc.longitude, 'HIGH');
          // notify users
          const notification = { title: `Parking full near ${p.address || 'this location'}.`, body: 'Parking slots are now 0. Please plan alternate parking.' };
          await notifyRoles(['Citizen', 'Traffic Police'], notification, { type: 'parking_full', parkingId });
        }
      }
    }
  } catch (err) {
    functions.logger.error('Error in onParkingLiveChange', err);
    throw err;
  }
  return null;
});

/** === Function: onTrafficNodeChange (Realtime DB -> Firestore mirror) === */
exports.onTrafficNodeChange = functions.database.ref('/traffic/{roadId}').onWrite(async (change, context) => {
  const roadId = context.params.roadId;
  const targetRef = db.collection(MAP_COLLECTIONS.TRAFFIC).doc(roadId);

  try {
    if (!change.after.exists()) {
      await targetRef.delete().catch(() => null);
      return null;
    }

    const after = change.after.val() || {};
    const center = after.center || {};
    await targetRef.set({
      roadId,
      congestionLevel: (after.congestionLevel || "LOW").toString().toUpperCase(),
      lastUpdated: toNumber(after.lastUpdated, nowMs()),
      centerLat: toNumber(center.lat, 0),
      centerLng: toNumber(center.lng, 0),
      source: "rtdb",
      raw: after,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  } catch (err) {
    functions.logger.error("Error in onTrafficNodeChange", err);
    throw err;
  }

  return null;
});

/** === Function: onLiveLocationChange (Realtime DB -> Firestore mirror) === */
exports.onLiveLocationChange = functions.database.ref('/live_locations/{userId}').onWrite(async (change, context) => {
  const userId = context.params.userId;
  const targetRef = db.collection(MAP_COLLECTIONS.LIVE_USERS).doc(userId);

  try {
    if (!change.after.exists()) {
      await targetRef.delete().catch(() => null);
      return null;
    }

    const after = change.after.val() || {};
    await targetRef.set({
      userId,
      lat: toNumber(after.lat, 0),
      lng: toNumber(after.lng, 0),
      speed: Math.max(0, Math.round(toNumber(after.speed, 0))),
      heading: toNumber(after.heading, 0),
      name: (after.name || "Citizen").toString(),
      timestamp: toNumber(after.timestamp, nowMs()),
      source: "rtdb",
      raw: after,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  } catch (err) {
    functions.logger.error("Error in onLiveLocationChange", err);
    throw err;
  }

  return null;
});

/** === Function: syncParkingToRealtime (Firestore -> Realtime DB) === */
exports.syncParkingToRealtime = functions.firestore.document('parking/{parkingId}').onWrite(async (change, context) => {
  const parkingId = context.params.parkingId;
  const after = change.after.exists ? change.after.data() : null;
  functions.logger.info(`syncParkingToRealtime ${parkingId}`, { after });
  try {
    if (!after) {
      // removed — delete in Realtime DB
      await rtdb.ref(`parking_live/${parkingId}`).remove();
      return null;
    }

    const payload = {
      availableSlots: typeof after.availableSlots === 'number' ? after.availableSlots : (after.totalSlots || 0),
      totalSlots: typeof after.totalSlots === 'number' ? after.totalSlots : 0,
      lastUpdated: admin.database.ServerValue.TIMESTAMP
    };
    await rtdb.ref(`parking_live/${parkingId}`).update(payload);
    functions.logger.info(`Synced parking ${parkingId} to Realtime DB`);
  } catch (err) {
    functions.logger.error('Error syncing parking to RTDB', err);
    throw err;
  }
  return null;
});

/** === Function: onParkingSpotChange (parking -> Firestore map mirror) === */
exports.onParkingSpotChange = functions.firestore.document('parking/{parkingId}').onWrite(async (change, context) => {
  const parkingId = context.params.parkingId;
  const targetRef = db.collection(MAP_COLLECTIONS.PARKING_SPOTS).doc(parkingId);

  try {
    if (!change.after.exists) {
      await targetRef.delete().catch(() => null);
      return null;
    }

    const after = change.after.data() || {};
    await targetRef.set({
      parkingId,
      ...after,
      sourceCollection: "parking",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  } catch (err) {
    functions.logger.error("Error in onParkingSpotChange", err);
    throw err;
  }

  return null;
});

/** === Function: onReportMirrorToMapIncidents (reports -> Firestore map mirror) === */
exports.onReportMirrorToMapIncidents = functions.firestore.document('reports/{reportId}').onWrite(async (change, context) => {
  const reportId = context.params.reportId;
  const targetRef = db.collection(MAP_COLLECTIONS.INCIDENTS).doc(reportId);

  try {
    if (!change.after.exists) {
      await targetRef.delete().catch(() => null);
      return null;
    }

    const report = change.after.data() || {};
    await targetRef.set({
      reportId,
      userId: report.userId || "",
      type: report.type || "other",
      title: report.title || "",
      description: report.description || "",
      imageUrl: report.imageUrl || "",
      imageUrls: Array.isArray(report.imageUrls) ? report.imageUrls : [],
      latitude: toNumber(report.latitude, 0),
      longitude: toNumber(report.longitude, 0),
      status: report.status || "Pending",
      priority: report.priority || "medium",
      assignedTo: report.assignedTo || "",
      timestamp: report.timestamp || null,
      isAnonymous: Boolean(report.isAnonymous),
      upvoteCount: toNumber(report.upvoteCount, 0),
      sourceCollection: "reports",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  } catch (err) {
    functions.logger.error("Error in onReportMirrorToMapIncidents", err);
    throw err;
  }

  return null;
});

/** === Functions: mirror traffic camera collections into one canonical collection === */
exports.onTrafficCameraSingularMirror = functions.firestore.document('traffic camera/{cameraId}').onWrite(async (change, context) => {
  try {
    await mirrorTrafficCameraDoc(change, context.params.cameraId, "traffic camera");
  } catch (err) {
    functions.logger.error("Error in onTrafficCameraSingularMirror", err);
    throw err;
  }
  return null;
});

exports.onTrafficCamerasPluralMirror = functions.firestore.document('traffic cameras/{cameraId}').onWrite(async (change, context) => {
  try {
    await mirrorTrafficCameraDoc(change, context.params.cameraId, "traffic cameras");
  } catch (err) {
    functions.logger.error("Error in onTrafficCamerasPluralMirror", err);
    throw err;
  }
  return null;
});

exports.onTrafficCamerasHyphenMirror = functions.firestore.document('traffic-cameras/{cameraId}').onWrite(async (change, context) => {
  try {
    await mirrorTrafficCameraDoc(change, context.params.cameraId, "traffic-cameras");
  } catch (err) {
    functions.logger.error("Error in onTrafficCamerasHyphenMirror", err);
    throw err;
  }
  return null;
});

/** === Scheduled: decay congestion levels over time === */
exports.decayCongestion = functions.pubsub.schedule('every 5 minutes').onRun(async (context) => {
  functions.logger.info('decayCongestion start');
  try {
    const snapshot = await rtdb.ref('traffic').once('value');
    const data = snapshot.val() || {};
    const updates = {};
    const now = Date.now();
    for (const key of Object.keys(data)) {
      const node = data[key];
      const last = node.lastUpdated || 0;
      const ageMinutes = (now - last) / 1000 / 60;
      let level = (node.congestionLevel || 'LOW');

      if (ageMinutes > CONGESTION_DECAY_MINUTES) {
        if (level === 'HIGH') level = 'MEDIUM';
        else if (level === 'MEDIUM') level = 'LOW';
        else level = 'LOW';

        updates[key] = { congestionLevel: level };
        functions.logger.info(`Decayed ${key} to ${level} (age ${ageMinutes.toFixed(1)} min)`);
      }
    }

    // Apply updates
    const promises = [];
    for (const k of Object.keys(updates)) {
      promises.push(rtdb.ref(`traffic/${k}`).update(updates[k]));
    }
    await Promise.all(promises);
    functions.logger.info('decayCongestion finished');
  } catch (err) {
    functions.logger.error('Error in decayCongestion', err);
    throw err;
  }
  return null;
});

/** Keep the status-change function for notifying citizens */
exports.onReportStatusChange = functions.firestore
  .document("reports/{reportId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    // Only proceed if the status actually changed
    if (before.status === after.status) {
      return null;
    }

    const citizenUid = after.userId;
    if (!citizenUid) {
      functions.logger.warn("Report has no userId – cannot notify");
      return null;
    }

    // Look up the citizen's FCM token
    const userDoc = await db.collection("users").doc(citizenUid).get();
    if (!userDoc.exists) {
      functions.logger.warn(`User ${citizenUid} not found`);
      return null;
    }

    const fcmToken = userDoc.data().fcmToken;
    if (!fcmToken) {
      functions.logger.info(`User ${citizenUid} has no FCM token`);
      return null;
    }

    const statusEmoji =
      after.status === "Resolved"
        ? "✅"
        : after.status === "In Progress"
          ? "🔄"
          : "📋";

    const message = {
      notification: {
        title: `${statusEmoji} Report Status Updated`,
        body: `Your report "${after.title || "Untitled"}" is now: ${after.status}`,
      },
      data: {
        type: "status_update",
        reportId: context.params.reportId,
        newStatus: after.status,
        click_action: "OPEN_REPORT",
      },
      token: fcmToken,
    };

    // 1. Save in-app notification
    const notifTitle = `${statusEmoji} Report Status Updated`;
    const notifBody = `Your report "${after.title || "Untitled"}" is now: ${after.status}`;
    await db.collection("users").doc(citizenUid)
      .collection("notifications")
      .doc(`status_${context.params.reportId}_${after.status}`)
      .set({
        title: notifTitle,
        message: notifBody,
        type: "report",
        priority: after.status === "Resolved" ? "high" : "medium",
        read: false,
        pinned: false,
        timestamp: admin.firestore.Timestamp.now(),
      });

    // 2. Send FCM push
    try {
      await messaging.send(message);
      functions.logger.info(
        `Status notification sent to ${citizenUid} – ${after.status}`
      );
    } catch (error) {
      functions.logger.error("Error sending status notification", error);
      // Clean up stale token
      if (
        error.code === "messaging/invalid-registration-token" ||
        error.code === "messaging/registration-token-not-registered"
      ) {
        await db
          .collection("users")
          .doc(citizenUid)
          .update({
            fcmToken: admin.firestore.FieldValue.delete(),
          });
      }
    }

    return null;
  });

/**
 * seedDemoParkingZones – Callable Cloud Function
 *
 * Writes a fixed set of demo parking-zone documents into the `parking`
 * Firestore collection so the app has data to display without requiring
 * manual Firestore edits.  Safe to call multiple times – deterministic
 * document IDs mean existing documents are overwritten rather than
 * duplicated.
 *
 * Requires the caller to be authenticated.
 *
 * Input:  {} (no parameters required)
 * Output: { seeded: number }  – number of zones written
 */
exports.seedDemoParkingZones = functions.https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "You must be signed in to seed demo data."
    );
  }

  const DEMO_ZONES = [
    {
      id: "demo_parking_001",
      address: "Central Plaza Parking – Level 1, Main St",
      latitude: 3.1478,
      longitude: 101.6953,
      totalSlots: 120,
      availableSlots: 45,
      isLegal: true,
    },
    {
      id: "demo_parking_002",
      address: "Riverside Mall Multi-Storey Car Park",
      latitude: 3.1512,
      longitude: 101.7021,
      totalSlots: 200,
      availableSlots: 0,
      isLegal: true,
    },
    {
      id: "demo_parking_003",
      address: "City Park Open-Air Lot, Park Ave",
      latitude: 3.1445,
      longitude: 101.6897,
      totalSlots: 60,
      availableSlots: 12,
      isLegal: true,
    },
    {
      id: "demo_parking_004",
      address: "North Market Street Roadside Bays",
      latitude: 3.1560,
      longitude: 101.6870,
      totalSlots: 30,
      availableSlots: 4,
      isLegal: true,
    },
    {
      id: "demo_parking_005",
      address: "Tech Hub Tower Basement P2",
      latitude: 3.1490,
      longitude: 101.7085,
      totalSlots: 80,
      availableSlots: 80,
      isLegal: true,
    },
    {
      id: "demo_parking_006",
      address: "Heritage Square – Unmarked Lot (Illegal)",
      latitude: 3.1433,
      longitude: 101.6945,
      totalSlots: 20,
      availableSlots: 20,
      isLegal: false,
    },
    {
      id: "demo_parking_007",
      address: "Sunrise Hotel Visitors Bay, Jalan Ampang",
      latitude: 3.1538,
      longitude: 101.7112,
      totalSlots: 50,
      availableSlots: 18,
      isLegal: true,
    },
    {
      id: "demo_parking_008",
      address: "Stadium Road Public Car Park",
      latitude: 3.1405,
      longitude: 101.6830,
      totalSlots: 150,
      availableSlots: 67,
      isLegal: true,
    },
  ];

  const batch = db.batch();
  for (const { id, address, latitude, longitude, totalSlots, availableSlots, isLegal } of DEMO_ZONES) {
    const ref = db.collection("parking").doc(id);
    batch.set(
      ref,
      {
        address,
        location: new admin.firestore.GeoPoint(latitude, longitude),
        totalSlots,
        availableSlots,
        isLegal,
      },
      { merge: false }
    );
  }
  await batch.commit();

  functions.logger.info(`seedDemoParkingZones: wrote ${DEMO_ZONES.length} demo zones`);
  return { seeded: DEMO_ZONES.length };
});

/**
 * uploadReportImage – Callable Cloud Function
 *
 * Accepts a base64-encoded image from the client, uploads it to Cloud Storage
 * using the Admin SDK (which has full permissions), and returns the public
 * download URL. This bypasses client-side Storage SDK permission issues.
 *
 * Input:  { reportId: string, imageBase64: string }
 * Output: { imageUrl: string }
 */
exports.uploadReportImage = functions
  .runWith({ memory: "256MB", timeoutSeconds: 60 })
  .https.onCall(async (data, context) => {
    // 1. Auth check
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "You must be signed in to upload images."
      );
    }

    const { reportId, imageBase64 } = data;
    if (!reportId || !imageBase64) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "reportId and imageBase64 are required."
      );
    }

    // 2. Decode base64
    const buffer = Buffer.from(imageBase64, "base64");

    // Limit to 10 MB
    if (buffer.length > 10 * 1024 * 1024) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Image must be less than 10 MB."
      );
    }

    // 3. Upload to Storage via Admin SDK
    const filePath = `reports/${reportId}.jpg`;
    const file = storageBucket.file(filePath);

    try {
      await file.save(buffer, {
        metadata: {
          contentType: "image/jpeg",
          metadata: {
            uploadedBy: context.auth.uid,
          },
        },
      });

      // Make the file publicly readable and get the URL
      await file.makePublic();
      const imageUrl = `https://storage.googleapis.com/${storageBucket.name}/${filePath}`;

      functions.logger.info(
        `Image uploaded by ${context.auth.uid} for report ${reportId}`
      );

      return { imageUrl };
    } catch (err) {
      functions.logger.error("Image upload failed", err);
      throw new functions.https.HttpsError(
        "internal",
        "Failed to upload image. Please try again."
      );
    }
  });

// ═══════════════════════════════════════════════════════════════════
// onNewViolation – Fires when a parking violation is created.
// Matches vehicleInfo against citizens' vehicleNumbers array.
// Sends FCM push + saves in-app notification.
// ═══════════════════════════════════════════════════════════════════

exports.onNewViolation = functions.firestore
  .document("parking_violations/{violationId}")
  .onCreate(async (snap, context) => {
    const violation = snap.data();
    const violationId = context.params.violationId;
    const vehicleInfo = (violation.vehicleInfo || "").trim().toUpperCase();

    if (!vehicleInfo) {
      functions.logger.warn(`Violation ${violationId} has no vehicleInfo`);
      return null;
    }

    functions.logger.info(`onNewViolation: ${violationId}, vehicle=${vehicleInfo}`);

    try {
      // Find citizens whose vehicleNumbers array contains this vehicle
      const usersSnap = await db.collection("users")
        .where("vehicleNumbers", "array-contains", vehicleInfo)
        .get();

      if (usersSnap.empty) {
        functions.logger.info(`No user found for vehicle ${vehicleInfo}`);
        return null;
      }

      const actionType = (violation.actionType || "fine").charAt(0).toUpperCase() +
        (violation.actionType || "fine").slice(1);
      const fineAmount = violation.fineAmount || "";
      const address = violation.address || "";
      const lat = violation.latitude || 0;
      const lng = violation.longitude || 0;

      // Build notification content
      const actionEmoji = actionType.toLowerCase() === "tow" ? "🚨"
        : actionType.toLowerCase() === "fine" ? "💰" : "⚠️";

      const notifTitle = `${actionEmoji} ${actionType} — ${vehicleInfo}`;
      const notifBody = fineAmount
        ? `₹${fineAmount} fine issued${address ? ` at ${address}` : ""}`
        : `${actionType} issued on your vehicle${address ? ` at ${address}` : ""}`;

      const priority = actionType.toLowerCase() === "tow" ? "critical"
        : actionType.toLowerCase() === "fine" ? "high" : "medium";

      // Process each matched user
      const promises = usersSnap.docs.map(async (userDoc) => {
        const userData = userDoc.data();
        const uid = userDoc.id;

        // 1. Save in-app notification
        await db.collection("users").doc(uid)
          .collection("notifications")
          .doc(`violation_${violationId}`)
          .set({
            title: notifTitle,
            message: notifBody,
            type: "parking",
            priority: priority,
            latitude: lat,
            longitude: lng,
            read: false,
            pinned: false,
            timestamp: admin.firestore.Timestamp.now(),
          });

        functions.logger.info(`In-app notification saved for user ${uid}`);

        // 2. Send FCM push notification
        const fcmToken = userData.fcmToken;
        if (!fcmToken) {
          functions.logger.info(`User ${uid} has no FCM token, skipping push`);
          return;
        }

        const message = {
          notification: {
            title: notifTitle,
            body: notifBody,
          },
          data: {
            type: "parking_violation",
            violationId: violationId,
            vehicleInfo: vehicleInfo,
            actionType: actionType.toLowerCase(),
            fineAmount: fineAmount,
            latitude: String(lat),
            longitude: String(lng),
            click_action: "OPEN_VIOLATIONS",
          },
          token: fcmToken,
        };

        try {
          await messaging.send(message);
          functions.logger.info(`Push notification sent to user ${uid}`);
        } catch (error) {
          functions.logger.error(`Failed to send push to ${uid}`, error);
          if (
            error.code === "messaging/invalid-registration-token" ||
            error.code === "messaging/registration-token-not-registered"
          ) {
            await db.collection("users").doc(uid)
              .update({ fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      });

      await Promise.all(promises);
      functions.logger.info(`onNewViolation complete: notified ${usersSnap.size} user(s)`);
    } catch (err) {
      functions.logger.error("Error in onNewViolation", err);
      throw err;
    }

    return null;
  });

// ═══════════════════════════════════════════════════════════════════
// onNewChatMessage – Fires when a chat message is added to a report.
// Notifies the OTHER party (police msg → citizen, citizen msg → police).
// ═══════════════════════════════════════════════════════════════════

exports.onNewChatMessage = functions.firestore
  .document("reports/{reportId}/chat/{messageId}")
  .onCreate(async (snap, context) => {
    const msg = snap.data();
    const reportId = context.params.reportId;
    const senderRole = msg.senderRole || "";
    const senderId = msg.senderId || "";
    const senderName = msg.senderName || msg.sender || "Someone";
    const messageText = msg.message || "";

    if (!senderId) return null;

    functions.logger.info(`onNewChatMessage: report=${reportId}, from=${senderRole}`);

    try {
      const reportSnap = await db.collection("reports").doc(reportId).get();
      if (!reportSnap.exists) return null;
      const report = reportSnap.data();
      const reportTitle = report.title || report.type || "Report";

      let targetUids = [];

      if (senderRole === "citizen") {
        // Citizen sent → notify assigned officer + officers who chatted
        if (report.assignedTo) targetUids.push(report.assignedTo);
        const chatSnap = await db.collection("reports").doc(reportId)
          .collection("chat")
          .where("senderRole", "==", "police")
          .get();
        chatSnap.docs.forEach((doc) => {
          const sid = doc.data().senderId;
          if (sid && !targetUids.includes(sid)) targetUids.push(sid);
        });
      } else {
        // Police sent → notify the citizen who filed the report
        if (report.userId) targetUids.push(report.userId);
      }

      targetUids = targetUids.filter((uid) => uid !== senderId);
      if (targetUids.length === 0) return null;

      const notifTitle = `💬 New message — ${reportTitle}`;
      const notifBody = messageText.length > 80
        ? `${senderName}: ${messageText.substring(0, 80)}...`
        : `${senderName}: ${messageText}`;

      const promises = targetUids.map(async (uid) => {
        await db.collection("users").doc(uid)
          .collection("notifications")
          .doc(`chat_${context.params.messageId}`)
          .set({
            title: notifTitle,
            message: notifBody,
            type: "report",
            priority: "medium",
            read: false,
            pinned: false,
            timestamp: admin.firestore.Timestamp.now(),
          });

        const userSnap = await db.collection("users").doc(uid).get();
        const fcmToken = userSnap.exists ? userSnap.data().fcmToken : null;
        if (!fcmToken) return;

        try {
          await messaging.send({
            notification: { title: notifTitle, body: notifBody },
            data: {
              type: "chat_message",
              reportId: reportId,
              senderRole: senderRole,
              click_action: "OPEN_REPORT_CHAT",
            },
            token: fcmToken,
          });
        } catch (error) {
          if (
            error.code === "messaging/invalid-registration-token" ||
            error.code === "messaging/registration-token-not-registered"
          ) {
            await db.collection("users").doc(uid)
              .update({ fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      });

      await Promise.all(promises);
      functions.logger.info(`onNewChatMessage: notified ${targetUids.length} user(s)`);
    } catch (err) {
      functions.logger.error("Error in onNewChatMessage", err);
    }
    return null;
  });

// ═══════════════════════════════════════════════════════════════════
// onNewProof – Fires when proof is uploaded to a report.
// Notifies the citizen who filed the report.
// ═══════════════════════════════════════════════════════════════════

exports.onNewProof = functions.firestore
  .document("reports/{reportId}/proofs/{proofId}")
  .onCreate(async (snap, context) => {
    const proof = snap.data();
    const reportId = context.params.reportId;
    const proofId = context.params.proofId;
    const actionTaken = proof.actionTaken || "Action taken";
    const description = proof.description || "";

    functions.logger.info(`onNewProof: report=${reportId}, action=${actionTaken}`);

    try {
      const reportSnap = await db.collection("reports").doc(reportId).get();
      if (!reportSnap.exists) return null;
      const report = reportSnap.data();
      const citizenUid = report.userId;
      if (!citizenUid) return null;

      const reportTitle = report.title || report.type || "Report";
      const notifTitle = `✅ Proof uploaded — ${reportTitle}`;
      const notifBody = description
        ? `${actionTaken}: ${description.substring(0, 80)}`
        : `Officer took action: ${actionTaken}`;

      // In-app notification
      await db.collection("users").doc(citizenUid)
        .collection("notifications")
        .doc(`proof_${proofId}`)
        .set({
          title: notifTitle,
          message: notifBody,
          type: "report",
          priority: "high",
          read: false,
          pinned: false,
          timestamp: admin.firestore.Timestamp.now(),
        });

      // FCM push
      const userSnap = await db.collection("users").doc(citizenUid).get();
      const fcmToken = userSnap.exists ? userSnap.data().fcmToken : null;
      if (fcmToken) {
        try {
          await messaging.send({
            notification: { title: notifTitle, body: notifBody },
            data: {
              type: "proof_uploaded",
              reportId: reportId,
              actionTaken: actionTaken,
              click_action: "OPEN_REPORT_PROOFS",
            },
            token: fcmToken,
          });
        } catch (error) {
          if (
            error.code === "messaging/invalid-registration-token" ||
            error.code === "messaging/registration-token-not-registered"
          ) {
            await db.collection("users").doc(citizenUid)
              .update({ fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      }

      functions.logger.info(`onNewProof: notified citizen ${citizenUid}`);
    } catch (err) {
      functions.logger.error("Error in onNewProof", err);
    }
    return null;
  });
