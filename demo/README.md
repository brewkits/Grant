# Grant Demo 🎯

**Comprehensive Examples Showcasing Grant's Power and Flexibility**

This demo provides production-ready examples of permission handling patterns using the Grant library. Learn how to handle **sequential requests**, **parallel requests**, and **complex permission flows** with zero boilerplate.

## Overview

The demo illustrates **3 main scenarios** with real-world use cases:

1. **Sequential Grants** - Request grants one after another
2. **Parallel Grants** - Request multiple grants simultaneously
3. **Grant Types** - Runtime vs Dangerous grant categories

## Scenarios

### 1. Sequential Grant Requests 🎥

**Use Case**: Video recording app

**Flow**:
```
Camera → (granted) → Microphone → (both granted) → Start recording
```

**Why Sequential?**
- Second grant (microphone) only makes sense after first (camera) is granted
- Clearer user experience - one thing at a time
- Avoids confusing users with multiple grant requests when the first fails

**Code Example**:
```kotlin
fun requestSequentialGrants() {
    // Step 1: Request Camera
    cameraGrant.request(
        rationaleMessage = "Camera is required to capture video"
    ) {
        // Camera granted! Now request microphone
        microphoneGrant.request(
            rationaleMessage = "Microphone is required to record audio"
        ) {
            // Both granted! Ready to record
            startVideoRecording()
        }
    }
}
```

**Grants Used**:
- `CAMERA` - **Dangerous** grant
- `MICROPHONE` - **Dangerous** grant

---

### 2. Parallel Grant Requests 📸

**Use Case**: Photo location tagging app

**Flow**:
```
Request Location + Storage → Wait for both → Save geotagged photos
```

**Why Parallel?**
- Both grants are independent
- Both are needed before the feature can work
- More efficient than waiting for one before requesting the other

**Code Example**:
```kotlin
fun requestParallelGrants() {
    var locationGranted = false
    var storageGranted = false

    // Request both at once
    locationGrant.request(
        rationaleMessage = "Location is needed to geotag your photos"
    ) {
        locationGranted = true
        checkIfBothGranted()
    }

    storageGrant.request(
        rationaleMessage = "Storage is needed to save photos"
    ) {
        storageGranted = true
        checkIfBothGranted()
    }
}

fun checkIfBothGranted() {
    if (locationGranted && storageGranted) {
        saveGeotaggedPhoto()
    }
}
```

**Grants Used**:
- `LOCATION` - **Dangerous** grant (when-in-use)
- `STORAGE` - **Dangerous** grant

**Note**: While we request them in parallel in code, the system shows grant dialogs sequentially (one at a time). The "parallel" nature is in how we handle the results.

---

### 3. Grant Types: Runtime vs Dangerous ⚠️

This scenario demonstrates the difference between grant categories and their risk levels.

#### 3a. Runtime Grant (Lower Risk)

**Example**: `NOTIFICATION`

**Characteristics**:
- Less invasive to user privacy
- May be granted by default on some platforms
- Can be revoked but less critical

**Use Case**: News app notifications

```kotlin
notificationGrant.request(
    rationaleMessage = "Enable notifications to receive updates"
) {
    enablePushNotifications()
}
```

---

#### 3b. Dangerous Grant (High Risk)

**Example**: `CONTACTS`

**Characteristics**:
- Accesses sensitive user data
- Always requires explicit user consent
- Can be revoked at any time
- Examples: Camera, Location, Contacts, Microphone, Storage

**Use Case**: Social app finding friends

```kotlin
contactsGrant.request(
    rationaleMessage = "Access contacts to find and invite friends"
) {
    loadContactsList()
}
```

---

#### 3c. Most Dangerous Grant (Highest Risk)

**Example**: `LOCATION_ALWAYS` (Background Location)

**Characteristics**:
- Can track user continuously in background
- Major privacy implications
- Often requires additional rationale
- May require two-step approval flow on some platforms

**Use Case**: Fitness app tracking runs

```kotlin
locationAlwaysGrant.request(
    rationaleMessage = "Background location tracks your routes when app is closed.\n\n" +
                      "⚠️ This is a sensitive grant."
) {
    startBackgroundTracking()
}
```

**Warning**: Background location is the highest risk grant. Only request it when absolutely necessary and provide very clear justification.

---

## Grant Categories Summary

| Grant Type | Risk Level | Examples | Auto-Granted? | Revocable? |
|----------------|------------|----------|---------------|------------|
| **Runtime** | Low | Notifications | Sometimes | Yes |
| **Dangerous** | High | Camera, Location, Contacts | No | Yes |
| **Most Dangerous** | Critical | Background Location | No | Yes |

### Android Dangerous Grants

According to [Android documentation](https://developer.android.com/guide/topics/grants/overview#dangerous_grants), dangerous grants include:

- **Calendar**: READ_CALENDAR, WRITE_CALENDAR
- **Camera**: CAMERA
- **Contacts**: READ_CONTACTS, WRITE_CONTACTS
- **Location**: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION
- **Microphone**: RECORD_AUDIO
- **Phone**: READ_PHONE_STATE, CALL_PHONE, etc.
- **Sensors**: BODY_SENSORS
- **SMS**: SEND_SMS, RECEIVE_SMS, etc.
- **Storage**: READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE (pre-Android 13)
- **Media**: READ_MEDIA_IMAGES, READ_MEDIA_VIDEO (Android 13+)

### iOS Sensitive Grants

iOS treats these as sensitive (require Info.plist description):

- Camera (NSCameraUsageDescription)
- Photo Library (NSPhotoLibraryUsageDescription)
- Location (NSLocationWhenInUseUsageDescription, NSLocationAlwaysUsageDescription)
- Microphone (NSMicrophoneUsageDescription)
- Contacts (NSContactsUsageDescription)
- Calendar (NSCalendarsUsageDescription)
- Bluetooth (NSBluetoothAlwaysUsageDescription)

---

## Running the Demo

### Setup

1. **Add dependencies** (already included if using the library)

2. **Configure platform-specific requirements**:

**Android** - Add to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
```

**iOS** - Add to `Info.plist`:
```xml
<key>NSCameraUsageDescription</key>
<string>Camera is needed to capture video</string>

<key>NSMicrophoneUsageDescription</key>
<string>Microphone is needed to record audio</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>Location is needed to geotag photos</string>

<key>NSLocationAlwaysUsageDescription</key>
<string>Background location tracks your running routes</string>

<key>NSContactsUsageDescription</key>
<string>Contacts help you find friends</string>
```

### Integration

```kotlin
// In your app setup (MainActivity or AppModule)
val grantManager = get<grantManager>()
val viewModel = GrantDemoViewModel(
    grantManager = grantManager,
    scope = viewModelScope // or rememberCoroutineScope()
)

// In your Compose UI
GrantDemoScreen(viewModel = viewModel)
```

---

## Key Learnings

### ✅ Best Practices Demonstrated

1. **Request at the right time** - Only when user takes action (button click)
2. **Provide clear rationale** - Explain *why* each grant is needed
3. **Handle all states** - NOT_DETERMINED, DENIED, DENIED_ALWAYS, GRANTED
4. **Show settings guide** - Direct users to settings when permanently denied
5. **Use GrantHandler** - Consistent, reusable logic across features

### ❌ Common Mistakes to Avoid

1. **Don't request on app launch** - Wait for user interaction
2. **Don't request all grants upfront** - Only request what's needed now
3. **Don't use generic messages** - Be specific about why you need each grant
4. **Don't ignore denial states** - Handle DENIED and DENIED_ALWAYS properly
5. **Don't chain too many grants** - Keep sequential chains to 2-3 max

---

## Testing Checklist

Test each scenario on **both Android and iOS** with these states:

- [ ] **First request** (NOT_DETERMINED) → Grant
- [ ] **First request** (NOT_DETERMINED) → Deny
- [ ] **Second request** after DENIED → Show rationale → Grant
- [ ] **Multiple denials** → DENIED_ALWAYS → Show settings guide
- [ ] **Settings guide** → Open settings → Enable → Return to app
- [ ] **Grant already granted** → Skip dialog, run feature immediately

### Platform-Specific Behaviors

**Android**:
- Can re-request after soft denial (DENIED)
- After 2-3 denials, becomes DENIED_ALWAYS
- Settings guide works via app info screen

**iOS**:
- First denial often goes straight to DENIED_ALWAYS
- Can't re-trigger system dialog after denial
- Settings guide works via app settings deep link

---

## Code Structure

```
demo/
├── GrantDemoViewModel.kt    # Business logic for all scenarios
├── GrantDemoScreen.kt       # UI with all demo scenarios
└── README.md                     # This file
```

---

## Questions?

See main documentation:
- [README.md](../README.md) - Quick start and overview
- [GRANTS.md](../docs/GRANTS.md) - Complete grant guide
- [ARCHITECTURE.md](../docs/ARCHITECTURE.md) - Technical design decisions

---

---

## License 📄

```
Copyright 2026 BrewKits

Licensed under the Apache License, Version 2.0
See LICENSE file for full text.
```

---

**Happy Grant Handling!** 🎉

<div align="center">

[⭐ Star on GitHub](https://github.com/brewkits/grant) • [📚 Documentation](../docs/README.md)

</div>
