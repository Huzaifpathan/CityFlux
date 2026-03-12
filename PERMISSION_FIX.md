# Permission Denied Fix - Parking Violations

## Issue
Getting "permission denied" or "insufficient permissions" when trying to create parking violations.

## Root Cause
The Firebase security rules require the user to have `role: 'police'` in their Firestore user document.

---

## ✅ Solution 1: Set User Role in Firestore (Recommended)

1. **Open Firebase Console**: https://console.firebase.google.com
2. **Go to Firestore Database**
3. **Navigate to `users` collection**
4. **Find your user document** (document ID = your auth UID)
5. **Add or update the `role` field**:
   ```
   role: "police"
   ```
6. **Save**

### If user document doesn't exist:
1. Create a new document with your auth UID
2. Add these fields:
   ```javascript
   {
     "email": "your-email@example.com",
     "name": "Your Name",
     "role": "police",
     "createdAt": <current timestamp>
   }
   ```

---

## ✅ Solution 2: Deploy Updated Security Rules

The rules have been updated to handle missing user documents gracefully.

**Deploy rules:**
```powershell
# Navigate to project directory
cd C:\Users\vikas\AndroidStudioProjects\CityFlux

# Deploy Firestore rules
firebase deploy --only firestore:rules

# Deploy Storage rules
firebase deploy --only storage
```

---

## ✅ Solution 3: Temporary - Allow All Authenticated Users (Development Only)

**⚠️ WARNING: Only use this for testing! Remove before production!**

Replace the parking_violations rules in `firestore.rules` with:

```javascript
match /parking_violations/{violationId} {
  // TEMPORARY: Allow any authenticated user for testing
  allow read, create, update: if isAuthenticated();
  allow delete: if false;
}
```

Then deploy:
```powershell
firebase deploy --only firestore:rules
```

**Remember to revert to the secure version before going to production!**

---

## ✅ Solution 4: Quick Test - Check Your Auth UID

Run this in your app to see your current auth UID:

```kotlin
val currentUser = FirebaseAuth.getInstance().currentUser
Log.d("AUTH", "UID: ${currentUser?.uid}")
Log.d("AUTH", "Email: ${currentUser?.email}")
```

Then manually verify in Firestore that `/users/{thisUID}/` has `role: "police"`.

---

## Expected User Document Structure

```
/users/{userId}/
  ├─ email: "officer@police.gov"
  ├─ name: "Officer Name"
  ├─ role: "police"  ← THIS IS CRITICAL
  ├─ phone: "1234567890"
  └─ createdAt: Timestamp
```

---

## Verify Rules Are Deployed

1. Go to Firebase Console
2. Navigate to **Firestore Database** → **Rules** tab
3. Check if the rules show the latest version with `parking_violations` section
4. Check **Last Published** timestamp

---

## Still Having Issues?

Check Firebase Console → Firestore → Rules → **Rules Playground**:
- Test operation: `get`, `create`, `update`
- Location: `/parking_violations/test123`
- Auth type: `Authenticated`
- Provider UID: `<your-uid>`

This will show exactly why the permission is being denied.
