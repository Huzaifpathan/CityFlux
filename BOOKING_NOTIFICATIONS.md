# Booking Notifications Implementation Plan

## Database Structure

### 1. Enhanced Bookings Collection
```
bookings/{bookingId}
├── Core Info
│   ├── id: string
│   ├── userId: string
│   ├── parkingSpotId: string
│   ├── parkingSpotName: string
│   ├── parkingAddress: string
│   └── parkingLocation: GeoPoint
│
├── Vehicle Info
│   ├── vehicleNumber: string
│   ├── vehicleType: enum
│   └── vehicleModel: string (optional)
│
├── Booking Details
│   ├── durationHours: int
│   ├── bookingStatus: enum
│   ├── bookingCreatedAt: timestamp
│   ├── bookingStartTime: timestamp
│   ├── bookingEndTime: timestamp
│   ├── checkInTime: timestamp (nullable)
│   ├── checkOutTime: timestamp (nullable)
│   └── actualDuration: int (nullable)
│
├── Payment Info
│   ├── paymentStatus: enum
│   ├── paymentAmount: double
│   ├── paymentMethod: string
│   ├── transactionId: string
│   ├── paymentTimestamp: timestamp
│   └── refundAmount: double (nullable)
│
├── Pricing Breakdown
│   ├── baseAmount: double
│   ├── discountAmount: double
│   ├── gstAmount: double
│   ├── peakHourCharge: double
│   └── totalAmount: double
│
├── Additional
│   ├── qrCodeData: string
│   ├── notes: string
│   ├── rating: int (nullable)
│   ├── review: string (nullable)
│   └── deviceInfo: string
```

### 2. Booking Notifications Collection
```
booking_notifications/{notificationId}
├── id: string
├── userId: string
├── bookingId: string
├── type: enum
├── title: string
├── message: string
├── timestamp: timestamp
├── isRead: boolean
├── priority: enum (HIGH, MEDIUM, LOW)
└── data: map (booking snapshot)
```

### 3. Notification Types
- `BOOKING_CONFIRMED`: Booking created successfully
- `PAYMENT_SUCCESS`: Payment completed
- `BOOKING_STARTED`: Auto check-in when reached
- `BOOKING_REMINDER`: 30 min before end
- `BOOKING_ENDING_SOON`: 10 min before end
- `BOOKING_COMPLETED`: After checkout
- `BOOKING_CANCELLED`: Cancellation confirmed
- `BOOKING_EXTENDED`: Additional time added
- `REFUND_PROCESSED`: Money refunded

## Features Implementation

### Phase 1: Core Notifications ✅
1. Success dialog after booking
2. Create booking notification
3. Show in notification tab
4. Real-time updates

### Phase 2: Smart Features 🔄
1. Auto check-in (GPS-based)
2. Reminder notifications
3. Booking timer
4. QR code display

### Phase 3: Advanced Features ⏳
1. Extend booking
2. Cancel with refund
3. Share booking
4. Download receipt
5. Rate & review

## UI Components

### 1. Success Dialog
- Payment success confirmation
- Booking details summary
- Quick actions (View, Navigate, Share)

### 2. Notification Card
- Booking thumbnail with parking name
- Status badge (Active, Completed, etc.)
- Time remaining / Duration
- Quick actions
- Swipe to delete

### 3. Booking Details Bottom Sheet
- Full booking information
- QR code
- Extend/Cancel buttons
- Get directions
- Share button

## Database Rules

### Firestore Security Rules
```javascript
match /booking_notifications/{notificationId} {
  allow read: if request.auth.uid == resource.data.userId;
  allow create: if request.auth.uid == request.resource.data.userId;
  allow update: if request.auth.uid == resource.data.userId 
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['isRead']);
  allow delete: if request.auth.uid == resource.data.userId;
}
```

## Real-time Updates

### Firebase Realtime Database
```
user_bookings/{userId}/{bookingId}
├── status: string
├── timeRemaining: int (seconds)
├── lastUpdated: timestamp
└── isActive: boolean
```

This enables:
- Live status tracking
- Real-time countdown
- Instant notifications
- Offline support
