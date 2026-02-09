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

// ── Configurable tuning parameters ───────────────────────────────
const RECENT_WINDOW_MINUTES = 10; // window to consider reports as 'clustered'
const CLUSTER_THRESHOLD_HIGH = 3; // >= this => HIGH
const CLUSTER_THRESHOLD_MEDIUM = 2; // == this => MEDIUM
const CONGESTION_DECAY_MINUTES = 30; // if no updates for this many minutes, lower congestion

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
  const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
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
