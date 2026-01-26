# Grant Library - Best Practices

This document outlines best practices for the Grant library, learned from real-world Android/iOS permission handling.

---

## Table of Contents
1. [Permission Request Patterns](#permission-request-patterns)
2. [Android-Specific Best Practices](#android-specific-best-practices)
3. [iOS-Specific Best Practices](#ios-specific-best-practices)
4. [Location Permissions](#location-permissions)
5. [UI/UX Patterns](#uiux-patterns)
6. [Service Status Checking](#service-status-checking)
7. [Testing Permissions](#testing-permissions)

---

## Permission Request Patterns

### 1. Always Show Context Before Requesting

**❌ BAD - Request without context:**
```kotlin
fun onCameraClick() {
    cameraGrant.request { openCamera() }
}
```

**✅ GOOD - Show rationale first:**
```kotlin
fun onCameraClick() {
    cameraGrant.request(
        rationaleMessage = "Camera access is needed to scan QR codes for quick login",
        settingsMessage = "Camera was denied. Enable it in Settings > Permissions > Camera"
    ) {
        openCamera()
    }
}
```

**Why:** Users are more likely to grant permissions when they understand WHY you need them.

---

### 2. Request Permissions Just-In-Time

**❌ BAD - Request all permissions on app launch:**
```kotlin
override fun onCreate() {
    // Don't do this!
    requestAllPermissions()
}
```

**✅ GOOD - Request when needed:**
```kotlin
fun onScanQRClick() {
    // Request camera only when user wants to scan
    cameraGrant.request { openScanner() }
}

fun onSavePhotoClick() {
    // Request storage only when user wants to save
    storageGrant.request { savePhoto() }
}
```

**Why:**
- Better user experience
- Higher grant rates
- Complies with Android/iOS guidelines

**Source:** [Android Best Practices](https://developer.android.com/training/permissions/requesting#explain)

---

### 3. Handle All Permission States

**❌ BAD - Only handle granted:**
```kotlin
cameraGrant.request { openCamera() }
// What if denied? User is stuck!
```

**✅ GOOD - Handle all states:**
```kotlin
// GrantHandler automatically handles:
// - GRANTED: Runs callback
// - DENIED: Shows rationale dialog
// - DENIED_ALWAYS: Shows settings guide
// - NOT_DETERMINED: Requests immediately

cameraGrant.request(
    rationaleMessage = "We need camera to scan QR codes",
    settingsMessage = "Camera access denied. Enable in Settings."
) {
    openCamera() // Only runs when GRANTED
}
```

**Why:** Users should never hit a dead end. Always provide a path forward.

---

## Android-Specific Best Practices

### 1. Multiple Permissions Pattern

Some Android permissions require requesting multiple platform permissions together.

**Example: Location on Android 12+**
```kotlin
// Android 12+ (API 31+) requires BOTH for precise location:
// - ACCESS_FINE_LOCATION
// - ACCESS_COARSE_LOCATION

AppGrant.LOCATION -> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
```

**Why:** Android 12+ made this change to give users more control. Apps must support both fine and coarse to avoid permission denial.

---

### 2. Background Location Requires 2-Step Request (Android 11+)

**❌ BAD - Request all at once:**
```kotlin
// This will FAIL on Android 11+!
requestPermissions(arrayOf(
    ACCESS_FINE_LOCATION,
    ACCESS_COARSE_LOCATION,
    ACCESS_BACKGROUND_LOCATION  // This will be IGNORED!
))
```

**✅ GOOD - Request foreground first, then background:**
```kotlin
// Step 1: Request foreground location
AppGrant.LOCATION_ALWAYS -> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val hasForeground = checkForegroundLocationGranted()

        if (hasForeground) {
            // Step 2: Only request background after foreground granted
            listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            // Step 1: Request foreground only
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }
}
```

**Why:** Android 11+ blocks background location requests if foreground isn't granted first. This is a security measure.

**Source:** [Android Docs - Background Location](https://developer.android.com/training/location/permissions#request-background-location-separately)

---

### 3. Request Multiple Permissions Together When Related

**✅ GOOD - Group related permissions:**
```kotlin
// Camera + Microphone for video recording
fun requestVideoPermissions() {
    // Request camera first
    cameraGrant.request(
        rationaleMessage = "Camera needed for video recording"
    ) {
        // Then microphone
        microphoneGrant.request(
            rationaleMessage = "Microphone needed for audio"
        ) {
            startVideoRecording()
        }
    }
}
```

**Why:** Android can show multiple permission dialogs in sequence, providing better UX than scattered requests.

---

### 4. Handle Permission Rationale Correctly

Android provides `shouldShowRequestPermissionRationale()` to detect permission states:

| State | shouldShow | First Request? | Action |
|-------|------------|---------------|---------|
| Never asked | `false` | Yes | Request immediately |
| Denied once | `true` | No | Show rationale → Request |
| Denied permanently | `false` | No | Show settings guide |

**Grant handles this automatically:**
```kotlin
// GrantRequestActivity logic:
val rationaleStatus = deniedGrants.map { grant ->
    shouldShowRequestPermissionRationale(grant)
}

val anyCanShowRationale = rationaleStatus.any { it }

if (anyCanShowRationale) {
    GrantResult.DENIED  // Can request again
} else {
    GrantResult.DENIED_PERMANENTLY  // Must go to Settings
}
```

---

## iOS-Specific Best Practices

### 1. You Only Get ONE Chance to Request

**iOS Behavior:**
- First request: System dialog shown
- User denies: CAN NEVER request again from code
- Must guide to Settings immediately

**✅ Handle iOS constraint:**
```kotlin
// GrantHandler automatically:
// - Shows rationale BEFORE first request (good practice)
// - On denial, immediately shows settings guide (required on iOS)

cameraGrant.request(
    rationaleMessage = "Camera needed to scan QR codes",  // Shows BEFORE dialog
    settingsMessage = "Enable camera in Settings > [App Name] > Camera"
) {
    openCamera()
}
```

**Why:** iOS takes privacy seriously. Apps can't spam permission requests.

---

### 2. Location "Always" Requires Two-Step Dialog

On iOS, requesting "Always" location:
1. First shows "When In Use" dialog
2. If user grants, shows second dialog for "Always"
3. User can approve or deny the "Always" upgrade

**Grant handles this:**
```kotlin
// iOS implementation
private suspend fun requestLocationGrant(always: Boolean): GrantStatus {
    val authStatus = if (always) {
        locationDelegate.requestAlwaysAuthorization()  // Triggers 2-step flow
    } else {
        locationDelegate.requestWhenInUseAuthorization()
    }
    return mapLocationStatus(authStatus, always)
}
```

---

### 3. Info.plist Usage Descriptions Are Required

**iOS will CRASH if you request permission without usage description!**

Required keys:
```xml
<key>NSCameraUsageDescription</key>
<string>Camera is used to scan QR codes for quick login</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>Location is used to show nearby stores</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>Location is used to track delivery routes in background</string>

<key>NSPhotoLibraryUsageDescription</key>
<string>Photos are used to upload profile pictures</string>

<!-- Add more as needed -->
```

**Best Practice:** Always provide clear, user-friendly descriptions explaining the value to the user.

**Localization:** For multi-language apps, use `InfoPlist.strings` files to localize permission descriptions:

```
// en.lproj/InfoPlist.strings
"NSCameraUsageDescription" = "Camera is used to scan QR codes for quick login";

// vi.lproj/InfoPlist.strings
"NSCameraUsageDescription" = "Camera được sử dụng để quét mã QR đăng nhập nhanh";

// ja.lproj/InfoPlist.strings
"NSCameraUsageDescription" = "カメラはQRコードをスキャンしてクイックログインに使用されます";
```

📖 **Full guide:** [Info.plist Localization](ios/INFO_PLIST_LOCALIZATION.md)

---

## Location Permissions

### Choosing Between LOCATION and LOCATION_ALWAYS

| Use Case | Permission | Platform Behavior |
|----------|-----------|-------------------|
| Show user's current location on map | `LOCATION` | iOS: "When In Use"<br>Android: Foreground only |
| Navigate to destination | `LOCATION` | iOS: "When In Use"<br>Android: Foreground only |
| Track delivery routes | `LOCATION_ALWAYS` | iOS: "Always"<br>Android: Background access |
| Fitness tracking while app closed | `LOCATION_ALWAYS` | iOS: "Always"<br>Android: Background access |

### Request Flow for Background Location

**Step 1: Request foreground first (recommended)**
```kotlin
locationGrant.request(
    rationaleMessage = "We need your location to show nearby restaurants"
) {
    // User granted foreground - now app works!
    showNearbyRestaurants()
}
```

**Step 2: Later, request background if needed**
```kotlin
// Only request when user enables "Track my run" feature
fun onEnableRunTracking() {
    locationAlwaysGrant.request(
        rationaleMessage = "Background location lets us track your running route even when the app is closed.\n\n⚠️ This is a sensitive permission that affects your privacy.",
        settingsMessage = "Enable background location in Settings > Location > Allow all the time"
    ) {
        startRunTracking()
    }
}
```

**Why:**
- Better approval rates
- Less intrusive to users
- Follows Android/iOS guidelines

---

## UI/UX Patterns

### 1. Use GrantHandler for Automatic Dialog Management

**✅ RECOMMENDED:**
```kotlin
// ViewModel
class CameraViewModel(grantManager: GrantManager, scope: CoroutineScope) {
    val cameraGrant = GrantHandler(grantManager, AppGrant.CAMERA, scope)

    fun onScanClick() {
        cameraGrant.request(
            rationaleMessage = "Camera needed to scan QR codes",
            settingsMessage = "Enable in Settings > Camera"
        ) {
            openScanner()
        }
    }
}

// Compose UI
GrantDialogHandler(handler = viewModel.cameraGrant)
```

**Benefits:**
- Automatic rationale dialog (when DENIED)
- Automatic settings guide (when DENIED_ALWAYS)
- Consistent UX across app
- 3 lines of code instead of 30+

---

### 2. Provide Clear Rationale Messages

**❌ BAD - Generic message:**
```kotlin
rationaleMessage = "This permission is required"
```

**✅ GOOD - Specific, value-focused:**
```kotlin
rationaleMessage = "Camera access is needed to scan QR codes on product packages for instant price comparison"
```

**Best Practice:**
- Explain WHAT you'll do with the permission
- Explain WHY it benefits the user
- Keep it concise (1-2 sentences)

---

### 3. Settings Guide Should Be Actionable

**❌ BAD - Vague instruction:**
```kotlin
settingsMessage = "Permission denied. Please enable it."
```

**✅ GOOD - Step-by-step path:**
```kotlin
settingsMessage = "Camera access was denied. To enable:\n\n1. Tap 'Open Settings' below\n2. Enable Camera permission\n3. Return to app and try again"
```

Or use platform-specific paths:
```kotlin
settingsMessage = when (platform) {
    Platform.ANDROID -> "Enable in Settings > Apps > ${appName} > Permissions > Camera"
    Platform.IOS -> "Enable in Settings > ${appName} > Camera"
}
```

---

### 4. Show Permission Status in UI

**✅ Visual status indicators:**
```kotlin
@Composable
fun PermissionStatusBadge(status: GrantStatus) {
    val (text, color) = when (status) {
        GrantStatus.GRANTED -> "✓ Granted" to Color.Green
        GrantStatus.DENIED -> "✗ Denied" to Color.Yellow
        GrantStatus.DENIED_ALWAYS -> "⚠️ Denied Always" to Color.Red
        GrantStatus.NOT_DETERMINED -> "? Not Determined" to Color.Gray
    }

    Badge(text = text, backgroundColor = color)
}
```

**Why:** Users should understand their permission status at a glance.

---

## Service Status Checking

### Beyond Permissions: Check if Services Are Enabled

**Permissions ≠ Service Enabled**

| Permission | Service Status | Result |
|-----------|---------------|---------|
| Location GRANTED | Location OFF | ❌ Can't get location |
| Location GRANTED | Location ON | ✅ Can get location |
| Bluetooth GRANTED | Bluetooth OFF | ❌ Can't connect devices |
| Bluetooth GRANTED | Bluetooth ON | ✅ Can connect devices |

**Grant provides ServiceManager:**
```kotlin
// Check both permission AND service
suspend fun checkLocationReady(): Boolean {
    val grantStatus = grantManager.checkStatus(AppGrant.LOCATION)
    val serviceStatus = serviceManager.checkLocationService()

    return grantStatus == GrantStatus.GRANTED &&
           serviceStatus == ServiceStatus.ENABLED
}

// Guide user to enable service if needed
if (serviceStatus == ServiceStatus.DISABLED) {
    showEnableLocationDialog {
        serviceManager.openLocationSettings()  // Opens system location settings
    }
}
```

---

## Testing Permissions

### 1. Test All Permission Flows

**Required test scenarios:**

1. ✅ **First request (NOT_DETERMINED)**
   - Should show system dialog
   - Grant → Success
   - Deny → Show rationale

2. ✅ **After denial (DENIED)**
   - Should show rationale dialog
   - Accept rationale → Show system dialog again
   - Deny again → Settings guide

3. ✅ **Permanent denial (DENIED_ALWAYS)**
   - Should show settings guide
   - "Open Settings" → Opens app settings
   - Return from settings → Check status again

4. ✅ **Already granted (GRANTED)**
   - Should execute callback immediately
   - Should NOT show any dialog

---

### 2. Test Platform-Specific Behaviors

**Android:**
```kotlin
@Test
fun testBackgroundLocation_Android11Plus() {
    // First request should only ask for foreground
    val firstRequest = grant.toAndroidGrants()
    assertEquals(
        listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION),
        firstRequest
    )

    // Grant foreground
    grantForegroundLocation()

    // Second request should ask for background
    val secondRequest = grant.toAndroidGrants()
    assertEquals(
        listOf(ACCESS_BACKGROUND_LOCATION),
        secondRequest
    )
}
```

**iOS:**
```kotlin
@Test
fun testLocationAlways_iOSBehavior() {
    // When user grants WhenInUse but we need Always
    locationDelegate.requestAlwaysAuthorization()

    val status = mapLocationStatus(
        kCLAuthorizationStatusAuthorizedWhenInUse,
        always = true
    )

    // Should return DENIED (soft denial)
    assertEquals(GrantStatus.DENIED, status)
}
```

---

### 3. Use Real Device Testing

**Simulators/Emulators have limitations:**
- iOS Simulator: Can't test Bluetooth, some sensors
- Android Emulator: Limited GPS simulation
- Both: Don't reflect real-world denial rates

**Best Practice:**
- Test on real devices
- Test on different OS versions
- Test with accessibility features enabled
- Test in low-connectivity scenarios

---

## Summary Checklist

### Before Requesting Permission:
- [ ] Show clear context explaining WHY you need it
- [ ] Request just-in-time (not on app launch)
- [ ] Add usage descriptions to Info.plist (iOS)
- [ ] Declare permissions in AndroidManifest.xml (Android)

### When Requesting:
- [ ] Use GrantHandler for automatic UI management
- [ ] Provide custom rationale message
- [ ] Provide custom settings guide message
- [ ] Handle all states (GRANTED, DENIED, DENIED_ALWAYS, NOT_DETERMINED)

### After Request:
- [ ] Check service status (not just permission)
- [ ] Guide users to Settings if permanently denied
- [ ] Show visual status indicators in UI
- [ ] Refresh status when returning from Settings

### Testing:
- [ ] Test first request flow
- [ ] Test denial → rationale → re-request flow
- [ ] Test permanent denial → settings guide flow
- [ ] Test on real devices
- [ ] Test platform-specific behaviors (Android 11+, iOS)

---

## Additional Resources

- [Android Permission Best Practices](https://developer.android.com/training/permissions/requesting)
- [iOS Permission Best Practices](https://developer.apple.com/design/human-interface-guidelines/privacy)
- [Grant Library Documentation](../README.md)

---

*Last Updated: 2026-01-23*
