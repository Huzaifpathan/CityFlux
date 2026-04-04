# Camera-Based Parking Validation System - CityFlux

## Overview
This document outlines the automated camera-based parking validation system that replaces QR code-based entry/exit. The system uses computer vision and license plate recognition (LPR/ANPR) to automatically validate parking, enforce rules, and issue fines.

## System Architecture

### Components
1. **Parking Zone Cameras** - High-resolution cameras at parking zone entrances
2. **License Plate Recognition (LPR) Engine** - AI/ML model for reading vehicle numbers
3. **Booking Validation Service** - Backend service to check active bookings
4. **Notification System** - Sends SMS/push notifications to users
5. **Fine Management System** - Auto-generates and sends fines

## Three Core Validation Scenarios

### Scenario 1: Valid Booking with Parking in Designated Spot ✅

**Trigger**: Vehicle enters parking zone with active booking

**Camera Detection Flow**:
```
1. Camera detects vehicle entering parking zone
   ↓
2. LPR system captures and reads license plate number
   ↓
3. System queries Firestore: "bookings" collection
   ↓
4. Check: Does this vehicle number have an active booking?
   ├─ YES: Booking exists and is active for current time
   │  ↓
   │  Allow entry - No action required
   │  ↓
   │  Continue monitoring for time expiry
   └─ NO: Proceed to Scenario 2 or 3
```

**Time Expiry Warnings**:
- **10 minutes before expiry**: Send notification
  ```
  "⏰ Parking Expiring Soon
  Your parking expires in 10 minutes.
  Tap here to renew or remove your vehicle."
  ```

- **5 minutes before expiry**: Send urgent notification
  ```
  "⚠️ Parking Expiring in 5 Minutes
  Please renew your booking or move your vehicle to avoid fines."
  ```

**On Expiry**:
```
If booking expires and vehicle still detected:
  ↓
Wait 2-minute grace period
  ↓
If vehicle not removed:
  ↓
Issue overtime fine
  ↓
Send fine notification to registered mobile number
```

---

### Scenario 2: No Booking + Parking in Designated Spot ❌

**Trigger**: Vehicle enters parking zone WITHOUT active booking

**Camera Detection Flow**:
```
1. Camera detects vehicle in parking spot
   ↓
2. LPR reads license plate number
   ↓
3. System queries: "bookings" collection
   ↓
4. Check: Active booking for this vehicle number?
   ↓
NO BOOKING FOUND
   ↓
5. Query: "users" collection for vehicle registration
   ↓
6. Find user with this vehicle number
   ↓
7. Issue IMMEDIATE FINE
   ↓
8. Send notification to registered mobile number
```

**Fine Notification**:
```
"🚨 Parking Fine Issued
Amount: ₹500
Reason: Parking without booking
Vehicle: MH12AB1234
Location: Zone A, Spot 45
Time: 14:30, 25 Dec 2024
Pay Now: [Link]"
```

**Fine Amount**: As per municipal parking violation rates (e.g., ₹500)

---

### Scenario 3: Parking Outside Designated Spot ⚠️

**Trigger**: Vehicle parked outside marked parking spots (detected by perimeter cameras)

**Camera Detection Flow**:
```
1. Perimeter camera detects vehicle outside designated spots
   ↓
2. LPR reads license plate number
   ↓
3. System queries: "bookings" collection
   ↓
4. Check: Does this vehicle have an active booking?
```

**Sub-Scenario 3A: HAS BOOKING but parked outside spot**
```
Vehicle has valid booking BUT is outside designated spot
  ↓
Send WARNING notification (NOT a fine):
  
"⚠️ Incorrect Parking Location
You have a valid booking but your vehicle is parked outside the designated spot.
Please move your vehicle to the assigned parking spot to avoid fines.
Assigned Spot: Zone B, Spot 12"
```

**Sub-Scenario 3B: NO BOOKING and parked outside spot**
```
Vehicle has NO booking AND is outside designated spot
  ↓
Issue IMMEDIATE FINE (higher amount for double violation)
  ↓
Send fine notification:

"🚨 Parking Fine Issued
Amount: ₹750
Reason: Unauthorized parking outside designated spot
Vehicle: MH12AB1234
Location: Zone A (No spot assigned)
Time: 16:45, 25 Dec 2024
Pay Now: [Link]"
```

---

## Technical Implementation

### Camera System Specifications

**Hardware Requirements**:
- **Resolution**: Minimum 1080p (1920x1080) for clear license plate capture
- **Frame Rate**: 15-30 FPS
- **Field of View**: Wide-angle lens covering entire zone entrance/perimeter
- **Night Vision**: IR/Low-light capability for 24/7 operation
- **Weatherproof**: IP66 or higher rating

**Placement**:
- **Entry/Exit Cameras**: Mounted at 3-4 meters height at zone entrances
- **Perimeter Cameras**: Spaced to cover all non-designated parking areas
- **Spot Cameras**: Optional overhead cameras for individual spot monitoring

### License Plate Recognition (LPR)

**Technology**:
- **OCR Engine**: Tesseract, EasyOCR, or commercial LPR SDK
- **Preprocessing**: Image enhancement, skew correction, noise reduction
- **Recognition Accuracy**: Target >95% for Indian number plates

**Supported Formats**:
```
- Standard: XX00XX0000 (e.g., MH12AB1234)
- Old Format: XX00X0000 (e.g., MH12A1234)
- BH Series: 00BHXXXX (e.g., 22BH1234AB)
```

**Processing Flow**:
```python
def process_vehicle_detection(camera_id, frame, timestamp):
    # 1. Detect license plate region in frame
    plate_region = detect_plate(frame)
    
    # 2. Extract and preprocess plate image
    plate_image = extract_plate(plate_region)
    enhanced = preprocess(plate_image)
    
    # 3. OCR to read vehicle number
    vehicle_number = ocr_engine.read_text(enhanced)
    vehicle_number = sanitize(vehicle_number)  # Clean up OCR errors
    
    # 4. Validate format
    if not is_valid_plate_format(vehicle_number):
        return None  # Skip invalid plates
    
    # 5. Determine parking status
    location_type = determine_location(camera_id)  # 'spot', 'entrance', 'perimeter'
    
    # 6. Query booking database
    booking = check_active_booking(vehicle_number, timestamp)
    
    # 7. Apply validation logic
    if location_type == 'spot':
        if booking and booking.spot_id == camera_id:
            # Scenario 1: Valid booking in correct spot
            check_expiry_warnings(booking)
        elif booking and booking.spot_id != camera_id:
            # Scenario 3A: Has booking but wrong spot
            send_relocation_warning(vehicle_number, booking.spot_id)
        else:
            # Scenario 2: No booking
            issue_fine(vehicle_number, "PARKING_WITHOUT_BOOKING", camera_id)
    
    elif location_type == 'perimeter':
        # Scenario 3: Outside designated spots
        if booking:
            # Scenario 3A: Has booking but parked outside
            send_relocation_warning(vehicle_number, booking.spot_id)
        else:
            # Scenario 3B: No booking and outside spot
            issue_fine(vehicle_number, "UNAUTHORIZED_PARKING", camera_id)
```

### Database Schema

**Collections**:

**1. `bookings`** (Firestore)
```kotlin
{
  id: String,
  userId: String,
  vehicleNumber: String,      // "MH12AB1234"
  parkingSpotId: String,       // "zone_a_spot_12"
  startTime: Timestamp,
  endTime: Timestamp,
  durationHours: Int,
  status: String,              // "active", "expired", "cancelled"
  notificationsSent: {
    tenMinWarning: Boolean,
    fiveMinWarning: Boolean,
    expiryNotification: Boolean
  }
}
```

**2. `camera_detections`** (Firestore)
```kotlin
{
  id: String,
  cameraId: String,            // "zone_a_entrance_cam_1"
  vehicleNumber: String,       // "MH12AB1234"
  timestamp: Timestamp,
  imageUrl: String,            // Cloud storage URL of captured frame
  locationType: String,        // "spot", "entrance", "perimeter"
  spotId: String?,             // If detected in a specific spot
  action: String              // "allowed", "warned", "fined"
}
```

**3. `fines`** (Firestore)
```kotlin
{
  id: String,
  vehicleNumber: String,
  userId: String,
  amount: Double,              // 500.0, 750.0
  reason: String,              // "PARKING_WITHOUT_BOOKING", "UNAUTHORIZED_PARKING"
  location: String,            // "Zone A, Spot 45"
  timestamp: Timestamp,
  evidenceImageUrl: String,    // Photo proof from camera
  status: String,              // "pending", "paid", "disputed"
  paymentLink: String
}
```

### Notification System

**SMS Format** (via SmsManager):
```kotlin
fun sendParkingNotification(phoneNumber: String, type: String, data: Map<String, Any>) {
    val message = when(type) {
        "EXPIRY_WARNING_10" -> """
            ⏰ Parking Expiring Soon
            Your parking at ${data["location"]} expires in 10 minutes.
            Tap to renew: ${data["renewLink"]}
        """.trimIndent()
        
        "EXPIRY_WARNING_5" -> """
            ⚠️ Parking Expiring in 5 Minutes
            Location: ${data["location"]}
            Please renew or remove vehicle to avoid fines.
            Renew: ${data["renewLink"]}
        """.trimIndent()
        
        "FINE_NO_BOOKING" -> """
            🚨 Parking Fine Issued
            Amount: ₹${data["amount"]}
            Reason: Parking without booking
            Vehicle: ${data["vehicleNumber"]}
            Location: ${data["location"]}
            Pay Now: ${data["paymentLink"]}
        """.trimIndent()
        
        "RELOCATION_WARNING" -> """
            ⚠️ Incorrect Parking Location
            You have a valid booking but your vehicle is parked outside the designated spot.
            Assigned Spot: ${data["assignedSpot"]}
            Current Location: ${data["currentLocation"]}
            Please relocate immediately.
        """.trimIndent()
        
        else -> return
    }
    
    SmsManager.getDefault().sendTextMessage(phoneNumber, null, message, null, null)
}
```

**Push Notifications** (Firebase Cloud Messaging):
```kotlin
fun sendPushNotification(userId: String, title: String, body: String, data: Map<String, String>) {
    val message = Message.builder()
        .setToken(getUserFCMToken(userId))
        .setNotification(
            Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build()
        )
        .putAllData(data)
        .build()
    
    FirebaseMessaging.getInstance().send(message)
}
```

### Backend Service (Cloud Functions)

**Camera Event Listener** (Firebase Cloud Functions - Node.js):
```javascript
exports.processCameraDetection = functions.firestore
    .document('camera_detections/{detectionId}')
    .onCreate(async (snap, context) => {
        const detection = snap.data();
        const { vehicleNumber, cameraId, timestamp, locationType, spotId } = detection;
        
        // Query active bookings
        const bookingsRef = admin.firestore().collection('bookings');
        const bookingQuery = await bookingsRef
            .where('vehicleNumber', '==', vehicleNumber)
            .where('status', '==', 'active')
            .where('startTime', '<=', timestamp)
            .where('endTime', '>=', timestamp)
            .get();
        
        const hasBooking = !bookingQuery.empty;
        const booking = hasBooking ? bookingQuery.docs[0].data() : null;
        
        // Apply validation logic
        if (locationType === 'spot') {
            if (hasBooking && booking.parkingSpotId === spotId) {
                // Scenario 1: Valid
                await checkExpiryWarnings(booking, vehicleNumber);
            } else if (!hasBooking) {
                // Scenario 2: Fine for no booking
                await issueFine(vehicleNumber, 'PARKING_WITHOUT_BOOKING', 500, cameraId);
            }
        } else if (locationType === 'perimeter') {
            if (hasBooking) {
                // Scenario 3A: Warning
                await sendRelocationWarning(vehicleNumber, booking.parkingSpotId);
            } else {
                // Scenario 3B: Fine for unauthorized parking
                await issueFine(vehicleNumber, 'UNAUTHORIZED_PARKING', 750, cameraId);
            }
        }
    });

async function checkExpiryWarnings(booking, vehicleNumber) {
    const now = admin.firestore.Timestamp.now().toMillis();
    const endTime = booking.endTime.toMillis();
    const timeRemaining = (endTime - now) / (60 * 1000); // minutes
    
    if (timeRemaining <= 10 && !booking.notificationsSent.tenMinWarning) {
        await sendNotification(vehicleNumber, 'EXPIRY_WARNING_10', { location: booking.parkingSpotName });
        await admin.firestore().doc(`bookings/${booking.id}`)
            .update({ 'notificationsSent.tenMinWarning': true });
    }
    
    if (timeRemaining <= 5 && !booking.notificationsSent.fiveMinWarning) {
        await sendNotification(vehicleNumber, 'EXPIRY_WARNING_5', { location: booking.parkingSpotName });
        await admin.firestore().doc(`bookings/${booking.id}`)
            .update({ 'notificationsSent.fiveMinWarning': true });
    }
}

async function issueFine(vehicleNumber, reason, amount, cameraId) {
    // Find user by vehicle number
    const usersRef = admin.firestore().collection('users');
    const userQuery = await usersRef.where('vehicleNumber', '==', vehicleNumber).get();
    
    if (userQuery.empty) {
        console.log(`No user found for vehicle ${vehicleNumber}`);
        return;
    }
    
    const user = userQuery.docs[0];
    const userId = user.id;
    const phoneNumber = user.data().phoneNumber;
    
    // Create fine record
    const fine = {
        vehicleNumber,
        userId,
        amount,
        reason,
        location: getCameraLocation(cameraId),
        timestamp: admin.firestore.Timestamp.now(),
        status: 'pending',
        paymentLink: generatePaymentLink(amount)
    };
    
    await admin.firestore().collection('fines').add(fine);
    
    // Send notification
    await sendNotification(phoneNumber, 'FINE_' + reason, fine);
}
```

## Security & Privacy

### Data Protection
- Camera footage encrypted at rest and in transit
- License plate data anonymized after 30 days (GDPR/privacy compliance)
- Access logs for all camera queries
- Role-based access control for camera feeds

### False Positive Handling
- Manual review queue for disputed fines
- Admin override capability
- Appeal process via app
- Refund mechanism for incorrect fines

## Operational Considerations

### Maintenance
- **Camera Health Monitoring**: Auto-alerts for offline/malfunctioning cameras
- **Calibration**: Quarterly calibration for optimal LPR accuracy
- **Cleaning**: Weekly lens cleaning schedule
- **Firmware Updates**: Quarterly security patches

### Performance Metrics
- **Detection Accuracy**: >95% license plate recognition rate
- **Response Time**: <3 seconds from detection to notification
- **False Positive Rate**: <2%
- **Uptime**: >99.5% camera availability

### Backup Systems
- **Manual Entry**: Staff can manually log vehicles if camera fails
- **Offline Mode**: Local processing continues, syncs when connection restored
- **Redundant Cameras**: Dual cameras at critical entry points

## Implementation Roadmap

### Phase 1: Infrastructure Setup (Weeks 1-4)
- Install cameras at all parking zones
- Deploy LPR software on edge devices/cloud
- Setup Firestore collections and Cloud Functions
- Configure notification systems (SMS, Push)

### Phase 2: Testing & Calibration (Weeks 5-6)
- Test LPR accuracy with various plates
- Calibrate detection thresholds
- Simulate all three scenarios
- Load testing for peak hours

### Phase 3: Pilot Rollout (Weeks 7-8)
- Deploy to 2-3 pilot parking zones
- Monitor and collect metrics
- Gather user feedback
- Fix issues and optimize

### Phase 4: Full Deployment (Weeks 9-12)
- Gradual rollout to all zones
- Staff training for manual overrides
- Public awareness campaign
- 24/7 monitoring and support

## Cost Estimates

### Hardware (Per Parking Zone)
- Cameras (3 units): ₹45,000
- Edge Processing Unit: ₹25,000
- Installation & Cabling: ₹15,000
- **Total per zone**: ₹85,000

### Software & Cloud (Monthly)
- LPR API/Service: ₹10,000
- Firebase/Cloud hosting: ₹8,000
- SMS Gateway: ₹5,000 (based on volume)
- Maintenance: ₹12,000
- **Total monthly**: ₹35,000

## Success Metrics

- **Revenue Impact**: 40% reduction in unpaid parking violations
- **Efficiency**: 90% reduction in manual checks
- **User Satisfaction**: >80% positive feedback on automated system
- **Fine Collection**: 70% payment rate within 7 days
- **Dispute Rate**: <5% of fines disputed

---

**Document Version**: 1.0  
**Last Updated**: April 2026  
**Owner**: CityFlux Engineering Team  
**Status**: Implementation Planned
