# 📱 Grant Management Guide

Complete guide for handling grants in KMP Grant library.

## 📑 Table of Contents

1. [Overview](#overview)
2. [Grant Flow](#grant-flow)
3. [Platform Setup](#platform-setup)
4. [Best Practices](#best-practices)
5. [Troubleshooting](#troubleshooting)
6. [All Supported Grants](#all-supported-grants)

## 🎯 Overview

KMP Grant handles the complete grant lifecycle:

```
User Action → Check Status → Show Rationale (if needed) → Request System Dialog → Handle Result
```

### Grant States

| State | Description | Next Action |
|-------|-------------|-------------|
| `NOT_DETERMINED` | Never asked before | Request grant |
| `DENIED` | Soft denial (can ask again) | Show rationale, then request |
| `DENIED_ALWAYS` | Hard denial (can't ask) | Show settings guide |
| `GRANTED` | Grant granted | Execute feature |

## 🔄 Grant Flow

### Flow Diagram

```
┌─────────────────┐
│  User Clicks    │
│  Feature Button │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Check Status   │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──┐  ┌──▼──────┐
│GRANTED│ │NOT_DETERMINED│
└───┬──┘  └──┬──────┘
    │         │
    │    ┌────▼────┐
    │    │ Request │
    │    │ System  │
    │    └────┬────┘
    │         │
    │    ┌────┴────┐
    │    │         │
    │ ┌──▼───┐ ┌──▼────────┐
    │ │DENIED│ │DENIED_ALWAYS│
    │ └──┬───┘ └──┬────────┘
    │    │         │
    │ ┌──▼─────────▼────┐
    │ │Show Rationale or │
    │ │Settings Guide    │
    │ └──────────────────┘
    │
┌───▼─────────┐
│Run Feature  │
└─────────────┘
```

### Implementation with GrantHandler

```kotlin
// ViewModel
class FeatureViewModel(grantManager: GrantManager) : ViewModel() {
    val grantHandler = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun onButtonClick() {
        grantHandler.request {
            // Only executes when GRANTED
            runFeature()
        }
    }
}
```

The handler automatically:
1. ✅ Checks current status
2. ✅ Shows rationale if needed
3. ✅ Requests system grant
4. ✅ Shows settings guide if permanently denied
5. ✅ Calls callback only when granted

## 🧠 Intelligent Dialog Management

### Overview

One of KMP Grant's most powerful features is its **zero-configuration dialog management**. The library automatically determines which dialog to show based on the grant's current state, eliminating the need for manual state tracking.

### How It Works

#### State Detection

The library uses sophisticated logic to detect the exact grant state:

**Android:**
```kotlin
// PlatformGrantDelegate uses checkSelfPermission + shouldShowRequestPermissionRationale
val status = when {
    allGranted -> GrantStatus.GRANTED
    neverAsked -> GrantStatus.NOT_DETERMINED
    canShowRationale -> GrantStatus.DENIED
    else -> GrantStatus.DENIED_ALWAYS
}
```

**iOS:**
```kotlin
// Maps native iOS authorization statuses
val status = when (authorizationStatus) {
    AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
    AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
    AVAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
    // iOS doesn't have soft denial - always permanent
}
```

#### Dialog Decision Logic

Based on the detected state, `GrantHandler` shows the appropriate dialog:

| State | Dialog Shown | User Actions | Next Step |
|-------|--------------|--------------|-----------|
| `NOT_DETERMINED` | System Dialog | Grant/Deny | If granted → Execute callback<br>If denied → Show rationale |
| `DENIED` | Rationale Dialog | "Grant Permission" / "Cancel" | If confirmed → System dialog again<br>If cancelled → Clean up |
| `DENIED_ALWAYS` | Settings Guide Dialog | "Open Settings" / "Later" | If confirmed → Opens app settings<br>If cancelled → Clean up |
| `GRANTED` | None | N/A | Execute callback immediately |

### Platform-Specific Behavior

#### Android Multi-Permission Handling

For complex grants like `LOCATION_ALWAYS` (requires FINE + COARSE + BACKGROUND on Android 11+):

```kotlin
// Library automatically:
// 1. Requests all 3 permissions together
// 2. Android shows sequential dialogs (foreground first, then background)
// 3. Analyzes results:
grantsResult.forEach { (permission, granted) ->
    if (!granted && shouldShowRequestPermissionRationale(permission)) {
        // At least one can show rationale → DENIED (soft)
        return GrantStatus.DENIED
    }
}
// All denied permissions are permanent → DENIED_ALWAYS
```

#### Android API Level Intelligence

```kotlin
when (grant) {
    AppGrant.GALLERY -> {
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+: Use granular media permissions
            listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        } else {
            // Older: Use READ_EXTERNAL_STORAGE
            listOf(READ_EXTERNAL_STORAGE)
        }
    }
    AppGrant.LOCATION_ALWAYS -> {
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+: Need foreground before background
            listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION)
        } else if (Build.VERSION.SDK_INT >= 29) {
            // Android 10: Can request together
            listOf(ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION)
        } else {
            // Older: No separate background permission
            listOf(ACCESS_FINE_LOCATION)
        }
    }
}
```

#### iOS Location Intelligence

```kotlin
when (status) {
    kCLAuthorizationStatusAuthorizedAlways -> GrantStatus.GRANTED
    kCLAuthorizationStatusAuthorizedWhenInUse -> {
        if (requestedAlways) {
            // User granted "While Using" but we need "Always"
            // This is a soft denial for the Always feature
            GrantStatus.DENIED
        } else {
            GrantStatus.GRANTED
        }
    }
    kCLAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
}
```

### Complete User Journey Example

Let's trace what happens when a user interacts with a camera feature:

#### First Time User (Happy Path)

1. **User clicks "Take Photo"**
   ```kotlin
   cameraGrant.request { openCamera() }
   ```

2. **Library checks status** → `NOT_DETERMINED`

3. **Library calls** `grantManager.request(CAMERA)`

4. **System dialog appears** → User grants

5. **Callback executes** → Camera opens ✅

#### User Denies Once (Rationale Path)

1. **User clicks "Take Photo"** (first time)

2. **System dialog appears** → User denies

3. **Rationale dialog appears automatically:**
   ```
   Title: "Grant Required"
   Message: "Camera is needed to take photos for your profile"
   Buttons: "Grant Permission" | "Cancel"
   ```

4. **User clicks "Grant Permission"**

5. **System dialog appears again** → User grants

6. **Camera opens** ✅

#### User Denies Permanently (Settings Path)

1. **User denies twice** OR **ticks "Don't ask again"**

2. **Next time user clicks "Take Photo":**

3. **Settings guide dialog appears automatically:**
   ```
   Title: "Grant Denied"
   Message: "Camera is disabled. Please enable it in Settings > Permissions"
   Buttons: "Open Settings" | "Later"
   ```

4. **User clicks "Open Settings"** → App settings opens

5. **User enables camera** → Returns to app

6. **Clicks "Take Photo"** → Camera opens ✅

### Benefits

✅ **Zero Configuration** - No need to track state manually

✅ **Platform-Aware** - Handles Android/iOS differences automatically

✅ **Smart Multi-Permission** - Correctly analyzes complex permission groups

✅ **User-Friendly** - Always shows the right message at the right time

✅ **Memory Safe** - Automatically cleans up callbacks to prevent leaks

## 🔧 Platform Setup

### Android

#### 1. Koin Setup

**DemoApplication.kt**:

```kotlin
package dev.brewkits.grant.demo

import android.app.Application
import dev.brewkits.grant.di.grantModule
import dev.brewkits.grant.di.grantPlatformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DemoApplication)
            modules(
                grantModule,
                grantPlatformModule
            )
        }
    }
}
```

**AndroidManifest.xml** - Register application:

```xml
<application
    android:name=".DemoApplication"
    ...>
</application>
```

#### 2. Manifest Configuration

**AndroidManifest.xml** - Declare grants:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Camera -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Storage (Pre Android 13) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                     android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
                     android:maxSdkVersion="32" />

    <!-- Media (Android 13+) -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Bluetooth (Android 12+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

    <!-- Bluetooth (Pre Android 12) -->
    <uses-permission android:name="android.permission.BLUETOOTH"
                     android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN"
                     android:maxSdkVersion="30" />

    <!-- Audio -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Contacts -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <!-- Calendar -->
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />

    <!-- Motion/Activity -->
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

    <application ...>
        <!-- Your activities -->
    </application>
</manifest>
```

### iOS

#### Koin Setup

```kotlin
// In your shared code (commonMain)
fun initKoin() {
    startKoin {
        modules(
            grantModule,
            grantPlatformModule
        )
    }
}
```

```swift
// iOSApp.swift
import SwiftUI

@main
struct iOSApp: App {
    init() {
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

#### Info.plist Configuration

Add usage description for each grant:

```xml
<dict>
    <!-- Camera -->
    <key>NSCameraUsageDescription</key>
    <string>We need camera access to take photos and scan QR codes</string>

    <!-- Photo Library -->
    <key>NSPhotoLibraryUsageDescription</key>
    <string>We need gallery access to select and upload photos</string>

    <!-- Location When In Use -->
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>We need your location to show nearby places</string>

    <!-- Location Always (Background) -->
    <key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
    <string>We need background location to track your route</string>

    <!-- Notifications -->
    <key>NSUserNotificationsUsageDescription</key>
    <string>We'll send you important updates and reminders</string>

    <!-- Bluetooth -->
    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>We need Bluetooth to connect to nearby devices</string>

    <!-- Microphone -->
    <key>NSMicrophoneUsageDescription</key>
    <string>We need microphone access for voice notes and calls</string>

    <!-- Contacts -->
    <key>NSContactsUsageDescription</key>
    <string>We need contacts to help you find your friends</string>

    <!-- Calendar -->
    <key>NSCalendarsUsageDescription</key>
    <string>We need calendar access to schedule events</string>

    <!-- Motion/Activity -->
    <key>NSMotionUsageDescription</key>
    <string>We need motion data for fitness tracking</string>
</dict>
```

## 💡 Best Practices

### 1. Request at the Right Time

❌ **Bad** - Request on app launch:
```kotlin
class ViewModel(manager: GrantManager) {
    init {
        requestCameraGrant() // User hasn't interacted yet!
    }
}
```

✅ **Good** - Request on user action:
```kotlin
class ViewModel(manager: GrantManager) {
    fun onTakePhotoClick() {
        cameraGrant.request { openCamera() }
    }
}
```

### 2. Provide Clear Rationale

❌ **Bad** - Generic message:
```kotlin
rationaleMessage = "We need camera"
```

✅ **Good** - Specific reason with context:
```kotlin
rationaleMessage = "We need camera access to scan QR codes for faster checkout and verify product authenticity"
```

### 3. Handle All States

❌ **Bad** - Only handle GRANTED:
```kotlin
if (status == GrantStatus.GRANTED) {
    openCamera()
}
// What if DENIED or DENIED_ALWAYS?
```

✅ **Good** - Use GrantHandler (handles all states):
```kotlin
grantHandler.request { openCamera() }
// Handler shows rationale/settings guide automatically
```

### 4. Test on Both Platforms

Grants behave differently:
- **Android**: Can re-ask after soft denial
- **iOS**: Typically can't re-ask (goes straight to DENIED_ALWAYS)

Always test your flow on both platforms!

### 5. Never Block App Features

❌ **Bad** - Blocking users:
```kotlin
if (!hasCameraGrant) {
    showFullScreenBlocker() // Can't use app!
}
```

✅ **Good** - Graceful degradation:
```kotlin
if (!hasCameraGrant) {
    showManualInputOption() // Alternative flow
}
```

## 🐛 Troubleshooting

### Issue: Grant dialog never appears (Android)

**Possible Causes**:
1. Grant not declared in AndroidManifest.xml
2. Koin not initialized properly
3. Targeting wrong API level

**Solution**:
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
```

```kotlin
// DemoApplication.kt
startKoin {
    androidContext(this@DemoApplication)
    modules(grantModule, grantPlatformModule)
}
```

### Issue: App crashes when requesting grant (iOS)

**Cause**: Missing Info.plist usage description

**Solution**: Add corresponding `NS*UsageDescription` key to Info.plist:
```xml
<key>NSCameraUsageDescription</key>
<string>We need camera access to take photos</string>
```

### Issue: Settings screen doesn't open

**Cause**: Incorrect settings URL

**Solution**: Use `openSettings()` from grantManager (handles platform differences automatically)

```kotlin
grantHandler.onSettingsConfirmed() // Uses correct URL
```

### Issue: Grant always returns DENIED_ALWAYS on iOS

**Cause**: iOS limits re-asking. After first denial, next request may go directly to DENIED_ALWAYS.

**Solution**:
- Show strong rationale BEFORE first request
- Consider in-app explainer before triggering system dialog
- Design flow to work without grant if possible

### Issue: Background location not working (Android 10+)

**Cause**: Background location requires two-step request

**Solution**: Use `AppGrant.LOCATION_ALWAYS` - handled automatically:

```kotlin
val locationGrant = GrantHandler(
    grantManager,
    AppGrant.LOCATION_ALWAYS, // Handles two-step flow
    viewModelScope
)
```

## 📱 All Supported Grants

### Camera
- **Android**: `CAMERA`
- **iOS**: `NSCameraUsageDescription`
- **Use cases**: Photos, video, QR scanning

```kotlin
val cameraGrant = GrantHandler(
    grantManager,
    AppGrant.CAMERA,
    scope
)
```

### Gallery/Photos
- **Android**: `READ_EXTERNAL_STORAGE` (API < 33), `READ_MEDIA_IMAGES` (API 33+)
- **iOS**: `NSPhotoLibraryUsageDescription`
- **Use cases**: Photo selection, image upload

```kotlin
val galleryGrant = GrantHandler(
    grantManager,
    AppGrant.GALLERY,
    scope
)
```

### Location
- **Android**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- **iOS**: `NSLocationWhenInUseUsageDescription`
- **Use cases**: Maps, nearby search, location tagging

```kotlin
val locationGrant = GrantHandler(
    grantManager,
    AppGrant.LOCATION,
    scope
)
```

### Background Location
- **Android**: `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION`
- **iOS**: `NSLocationAlwaysAndWhenInUseUsageDescription`
- **Use cases**: Route tracking, geofencing

```kotlin
val backgroundLocationGrant = GrantHandler(
    grantManager,
    AppGrant.LOCATION_ALWAYS,
    scope
)
```

### Notifications
- **Android**: `POST_NOTIFICATIONS` (API 33+)
- **iOS**: `NSUserNotificationsUsageDescription`
- **Use cases**: Push notifications, alerts

```kotlin
val notificationGrant = GrantHandler(
    grantManager,
    AppGrant.NOTIFICATION,
    scope
)
```

### Microphone
- **Android**: `RECORD_AUDIO`
- **iOS**: `NSMicrophoneUsageDescription`
- **Use cases**: Voice notes, calls, audio recording

```kotlin
val microphoneGrant = GrantHandler(
    grantManager,
    AppGrant.MICROPHONE,
    scope
)
```

### Contacts
- **Android**: `READ_CONTACTS`
- **iOS**: `NSContactsUsageDescription`
- **Use cases**: Friend finder, contact sync

```kotlin
val contactsGrant = GrantHandler(
    grantManager,
    AppGrant.CONTACTS,
    scope
)
```

### Calendar
- **Android**: `READ_CALENDAR`, `WRITE_CALENDAR`
- **iOS**: `NSCalendarsUsageDescription`
- **Use cases**: Event scheduling, calendar sync

```kotlin
val calendarGrant = GrantHandler(
    grantManager,
    AppGrant.CALENDAR,
    scope
)
```

### Bluetooth
- **Android**: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+)
- **iOS**: `NSBluetoothAlwaysUsageDescription`
- **Use cases**: Device pairing, BLE communication

```kotlin
val bluetoothGrant = GrantHandler(
    grantManager,
    AppGrant.BLUETOOTH,
    scope
)
```

### Motion/Activity
- **Android**: `ACTIVITY_RECOGNITION`
- **iOS**: `NSMotionUsageDescription`
- **Use cases**: Step counting, fitness tracking

```kotlin
val motionGrant = GrantHandler(
    grantManager,
    AppGrant.MOTION,
    scope
)
```

### Storage
- **Android only**: `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`
- **iOS**: N/A (uses sandboxed storage)
- **Use cases**: File access, downloads

```kotlin
val storageGrant = GrantHandler(
    grantManager,
    AppGrant.STORAGE,
    scope
)
```

## 📚 References

- [Android Grants Best Practices](https://developer.android.com/training/grants/requesting)
- [iOS Authorization Guide](https://developer.apple.com/documentation/uikit/protecting_the_user_s_privacy)
- [Android Runtime Grants](https://developer.android.com/guide/topics/grants/overview)
- [iOS App Privacy](https://developer.apple.com/app-store/user-privacy-and-data-use/)

---

**Next**: Read [ARCHITECTURE.md](ARCHITECTURE.md) to understand design decisions
