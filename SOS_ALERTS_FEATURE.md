# SOS Alerts Feature - CityFlux Citizen App

## Overview
The SOS (Save Our Souls) Alert feature enables citizens to send emergency alerts with their live location to authorities and emergency contacts when they are in distress or facing a dangerous situation.

## What SOS Alerts Do

### Current Functionality

**1. User Trigger**
- Citizens tap the "🆘 SOS Alert" button on the Home screen
- A confirmation dialog appears asking for confirmation
- User confirms by clicking "Send SOS" button

**2. Data Collection**
When SOS is triggered, the system collects:
- **User ID**: Firebase UID of the citizen
- **User Name**: Display name of the citizen
- **Live Location**: Current GPS coordinates (latitude/longitude)
- **Timestamp**: Exact time of alert creation
- **Status**: Set to "active"
- **Type**: Marked as "sos"

**3. Alert Storage**
- Alert is saved to Firestore collection: `sos_alerts`
- Real-time database ensures immediate availability to authorities
- Alert remains active until acknowledged by police/authorities

**4. Location Services**
- Uses Google Play Services FusedLocationProviderClient
- High-accuracy location priority for precise coordinates
- Requires location permission from user
- Handles cases where GPS is disabled or unavailable

**5. User Feedback**
- Success: "🆘 SOS Alert Sent! Help is on the way." toast message
- Failure cases:
  - Location access denied: "Location permission required."
  - GPS unavailable: "Could not get location. Check GPS."
  - Network/Firestore error: "Failed to send SOS. Try again."

### Enhanced Functionality (To Be Implemented)

**SMS Notification to Emergency Contact**
- When SOS is triggered, send SMS to: **9657288928**
- Message format:
  ```
  🆘 CITYFLUX SOS ALERT
  From: {userName}
  Location: {latitude}, {longitude}
  Time: {timestamp}
  Google Maps: https://maps.google.com/?q={latitude},{longitude}
  ```

## Use Cases

### When Citizens Should Use SOS

1. **Personal Safety Emergencies**
   - Being followed or stalked
   - Witnessing a crime
   - Feeling threatened or unsafe
   - Medical emergencies in public spaces

2. **Vehicle-Related Emergencies**
   - Vehicle breakdown in unsafe area
   - Accident without mobile phone access
   - Being blocked or harassed by other drivers

3. **Parking Zone Issues**
   - Harassment at parking spot
   - Unsafe conditions at municipal parking
   - Witnessing illegal activities

### How Authorities Respond

1. **Alert Reception**
   - Police officers with admin access receive real-time notifications
   - SOS alerts appear in their dashboard with priority marking
   - Location is displayed on map for quick navigation

2. **Response Process**
   - Nearest patrol is dispatched to the coordinates
   - Alert status is updated to "responding" 
   - Once resolved, status changes to "resolved"

## Technical Implementation

### Location**: `CitizenHomeContent.kt` (Lines 636-720)

### Dependencies
```kotlin
// Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp

// Google Play Services Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
```

### Required Permissions
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.SEND_SMS" /> <!-- For SMS feature -->
```

### Data Structure (Firestore)

**Collection**: `sos_alerts`

**Document Fields**:
```kotlin
{
  "userId": String,           // Firebase UID
  "userName": String,          // Display name
  "latitude": Double,          // GPS latitude
  "longitude": Double,         // GPS longitude
  "timestamp": Timestamp,      // Firebase Timestamp
  "status": String,            // "active", "responding", "resolved"
  "type": String              // "sos"
}
```

## Security & Privacy

### Location Privacy
- Location is only captured when SOS is explicitly triggered
- No background location tracking
- Coordinates are only shared with authorities
- Alert data respects Firestore security rules

### Authentication
- Requires user to be logged in (Firebase Auth)
- User ID verified before sending alert
- Only authenticated users can create SOS alerts

### Data Retention
- Active alerts remain until resolved
- Resolved alerts are archived (not deleted)
- Historical data helps identify problem areas

## Future Enhancements

1. **SMS Integration** ✓ (In Progress)
   - Send SMS to emergency contact: 9657288928
   - Include location link and user details

2. **Multiple Emergency Contacts**
   - Allow users to add personal emergency contacts
   - Send SMS to family members simultaneously

3. **Audio/Video Recording**
   - Auto-record 30 seconds of audio when SOS triggered
   - Upload to secure cloud storage
   - Share evidence with authorities

4. **Shake-to-Alert**
   - Enable SOS trigger by shaking phone vigorously
   - Useful when unable to access screen

5. **Alert Categories**
   - Medical emergency
   - Safety threat
   - Vehicle issue
   - Other

6. **Follow-up Notifications**
   - Auto-check if user is safe after 5 minutes
   - Send reminder if alert not acknowledged
   - Escalate if no response

## User Education

### Best Practices
- ✅ Keep GPS enabled for accurate location
- ✅ Grant location permission to app
- ✅ Only use for genuine emergencies
- ✅ Stay in safe area if possible while waiting
- ✅ Keep phone battery charged

### What NOT to Use SOS For
- ❌ Non-emergency parking disputes
- ❌ General complaints
- ❌ Testing the feature
- ❌ Non-urgent issues (use Report feature instead)

## Support & Contact

For questions about SOS Alerts:
- **Emergency**: Always call 100 (Police) for life-threatening situations
- **App Support**: Contact CityFlux support through in-app chat
- **Feature Feedback**: Submit via Settings → Feedback

---

**Last Updated**: April 2026
**Version**: 1.0
**Feature Status**: Active with SMS Enhancement Planned
