# Migration Guide to Grant

**Version:** 2.3.0
**Last Updated:** July 10, 2026

This guide helps you migrate from previous versions of Grant or other permission libraries.

---

## 📚 Table of Contents

1. [Upgrading from Grant 2.2.x to 2.3.0](#upgrading-from-grant-22x-to-230)
2. [Upgrading from Grant 2.1.0 to 2.2.0](#upgrading-from-grant-210-to-220)
2. [Upgrading from Grant 1.x to 2.1.0](#upgrading-from-grant-1x-to-200)
3. [Upgrading from Grant 1.3.x to 1.4.2](#upgrading-from-grant-13x-to-142)
4. [From moko-permissions](#from-moko-permissions)
5. [From Google Accompanist](#from-google-accompanist)
6. [From Custom Implementation](#from-custom-implementation)
7. [From Native Android APIs](#from-native-android-apis)
8. [Common Migration Patterns](#common-migration-patterns)
9. [Troubleshooting](#troubleshooting)

---

## 🚀 Upgrading from Grant 2.2.x to 2.3.0

### Overview

2.3.0 is a **toolchain + Android 17 release**: Kotlin 2.4.0, Compose Multiplatform 1.11.1,
kotlinx-coroutines 1.11.0. The public API is source-compatible with 2.2.x — for most apps
the migration is bumping the version number. Three things deserve attention:

### 1. ⚠️ Breaking (grant-compose only): the `iosX64` target is gone

Compose Multiplatform 1.11 stopped publishing `iosX64` artifacts, so `grant-compose` can no
longer build that target. **Every other module keeps `iosX64`.**

- Apple-silicon Macs, real devices, CI on arm64 runners: **no action needed.**
- If you still run the iOS **simulator on an Intel Mac** *and* use `grant-compose`:
  stay on `grant-compose:2.2.3` (it is API-compatible with `grant-core:2.3.0` for the
  dialog surface) or drop the iosX64 target from your app.

### 2. Behavior change: "Approximate"-only location now reports `PARTIAL_GRANTED`

Previously, a user who chose **Approximate** in the OS location dialog (grants
`ACCESS_COARSE_LOCATION` but not `ACCESS_FINE_LOCATION`) was misreported as
`DENIED`/`DENIED_ALWAYS` — even though the app held usable coarse location. 2.3.0 reports
this state as `PARTIAL_GRANTED`, consistent with the Android 14 partial-photos model.

**What to check:** anywhere you branch on `AppGrant.LOCATION` status, treat
`PARTIAL_GRANTED` as usable (coarse) access:

```kotlin
when (locationGrant.status.value) {
    GrantStatus.GRANTED         -> startPreciseTracking()
    GrantStatus.PARTIAL_GRANTED -> startCoarseTracking()   // NEW in 2.3.0 for approximate-only
    else                        -> requestOrExplain()
}
```

If you already handled `PARTIAL_GRANTED` for the gallery grants, the same handling applies.

### 3. Behavior change: `requestSuspend()` no longer hangs without a dialog host

If no collector is attached to `GrantHandler.state` (i.e. no `GrantDialog` / custom renderer
is composed), a DENIED / DENIED_ALWAYS flow used to suspend **forever** waiting for a dialog
that could never appear. 2.3.0 completes immediately with the denied status instead and
clears the unrenderable dialog state. The callback-based `request()` is unchanged.

**What to check:** if you relied on `requestSuspend()` never returning in that situation
(unlikely), handle the returned `DENIED`/`DENIED_ALWAYS` status.

### 4. New: `AppGrant.LOCAL_NETWORK` (Android 17)

Android 17 (API 37) introduced the `ACCESS_LOCAL_NETWORK` runtime permission for talking to
LAN devices (smart home, casting, printers). To adopt:

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
```

```kotlin
val status = grantManager.request(AppGrant.LOCAL_NETWORK)
// Below Android 17 and on iOS this is a no-op GRANTED.
```

On iOS there is no query/request API — the OS prompts automatically on first LAN access;
declare `NSLocalNetworkUsageDescription` in `Info.plist`.

Also fixed in 2.3.0: an Android 14+ gallery with `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO`
granted but `READ_MEDIA_VISUAL_USER_SELECTED` not (ADB/MDM/auto-reset edge states) was
misreported as `DENIED_ALWAYS`; it now correctly reports `GRANTED`.

### Version bump

```kotlin
implementation("dev.brewkits:grant-core:2.3.0")
implementation("dev.brewkits:grant-compose:2.3.0")          // see iosX64 note above
implementation("dev.brewkits:grant-core-koin:2.3.0")
// ...and any optional iOS modules you use, all at 2.3.0
```

---

## 🛡️ Upgrading from Grant 2.1.0 to 2.2.0

### Overview

v2.2.0 (Issue #45) continues the **iOS Framework Isolation** work started in v2.1.0. Two more sensitive paths were moved out of `grant-core` into opt-in modules so apps that don't use them are never flagged by Apple's static scanner:

- **`grant-bluetooth`** — `CoreBluetooth.framework` is no longer linked by `grant-core`, so the `NSBluetoothAlwaysUsageDescription` requirement disappears for apps that don't use Bluetooth.
- **`grant-location-always`** — the `requestAlwaysAuthorization` (background location) selector moved out of core. `grant-core` now calls **only** `requestWhenInUseAuthorization`. Apps using only foreground location are no longer asked for `NSLocationAlwaysAndWhenInUseUsageDescription`.

**Android is completely unaffected** — no code changes required on Android.

### What Changed?

- **New optional modules**: `grant-bluetooth`, `grant-location-always`. Each links its native iOS framework / selector only when added.
- **`AppGrant` is unchanged**: `BLUETOOTH`, `BLUETOOTH_ADVERTISE`, and `LOCATION_ALWAYS` are still valid enum values. On iOS they now resolve through the opt-in module's registered handler instead of a built-in one.

### Step-by-Step Upgrade (iOS only)

#### 1. Bump every Grant artifact to `2.2.0`

#### 2. Add the new modules only if you use those permissions

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.2.0")
    implementation("dev.brewkits:grant-bluetooth:2.2.0")        // only if you use AppGrant.BLUETOOTH / BLUETOOTH_ADVERTISE
    implementation("dev.brewkits:grant-location-always:2.2.0")  // only if you use AppGrant.LOCATION_ALWAYS (background) on iOS
}
```

#### 3. Call `initialize()` once on iOS for each new module you added

```swift
// Swift
GrantBluetooth.shared.initialize()
GrantLocationAlways.shared.initialize()
```

```kotlin
// iosMain — call once at app start
GrantBluetooth.initialize()
GrantLocationAlways.initialize()
```

> ⚠️ If you request `BLUETOOTH` / `BLUETOOTH_ADVERTISE` / `LOCATION_ALWAYS` on iOS **without** adding the corresponding module and calling `initialize()`, the handler is not registered: `checkStatus()` and `request()` log a warning and return `NOT_DETERMINED` (no system dialog is shown — it does not hang or crash). On Android these permissions continue to work without any extra module.

#### 4. No changes required for Android

---

## 🛡️ Upgrading from Grant 1.x to 2.1.0

### Overview

v2.1.0 is the **iOS Framework Isolation** release. `Contacts.framework`, `EventKit.framework`, and `CoreMotion.framework` are now opt-in Gradle/Maven modules. **Android is completely unaffected** — no code changes required on Android.

### What Changed?

- **New optional modules**: `grant-contacts`, `grant-calendar`, `grant-motion`. Each module links its native iOS framework only when added.
- **No more forced `NSUsageDescription` keys**: Apps that don't add an optional module are never prompted by App Store to add the corresponding usage key.
- **`IosPermissionHandlerRegistry` fix**: `checkStatus()` for `RawPermission` now correctly dispatches to custom registered handlers (previously only `request()` did).

### Step-by-Step Upgrade (iOS only)

#### 1. Update the core version

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.1.0")
}
```

#### 2. Add optional modules for permissions you use

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.1.0")
    // Add only the ones your app actually uses:
    implementation("dev.brewkits:grant-contacts:2.1.0")  // Contacts
    implementation("dev.brewkits:grant-calendar:2.1.0")  // Calendar / EventKit
    implementation("dev.brewkits:grant-motion:2.1.0")    // CoreMotion / Step Counter
}
```

#### 3. Call `initialize()` once on iOS

In your iOS app entry point (e.g., `AppDelegate.application(_:didFinishLaunchingWithOptions:)`):

```swift
// Swift
GrantContacts.shared.initialize()
GrantCalendar.shared.initialize()
GrantMotion.shared.initialize()
```

Or from Kotlin shared code in `iosMain`:

```kotlin
// iosMain — call once at app start
GrantContacts.initialize()
GrantCalendar.initialize()
GrantMotion.initialize()
```

#### 4. No changes required for Android

Android code, manifest permissions, and build configurations remain unchanged.

---

## 🛡️ Upgrading from Grant 1.3.x to 1.4.2

### Overview

Version 1.4.2 is a critical stability release addressing high-impact bugs in Android permission flows and refining the architectural purity of the library.

### What Changed?

- **Issue #33 Fixed (Critical):** Resolved a 60-second timeout on Android 11+ when requesting `LOCATION_ALWAYS` by eliminating a race condition in `GrantRequestActivity`.
- **Koin Module Decoupling:** `grant-core` no longer contains Koin dependencies. Use `grant-core-koin` for DI support.
- **Partial Upgrade Logic:** Fixed `GrantHandler` to correctly trigger native OS dialogs when upgrading from `PARTIAL_GRANTED` to `GRANTED`.
- **Android 15 Compatibility:** Optimized Activity transitions and lifecycle state management for upcoming Android versions.

### Step-by-Step Upgrade

#### 1. Update Version
Update your `build.gradle.kts` to version `1.4.2`.

#### 2. Handle Koin (If you use it)
If you were using `grantModule` or `grantPlatformModule`, you must now add the `grant-core-koin` dependency:

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:1.4.2")
    implementation("dev.brewkits:grant-core-koin:1.4.2") // Separate module
}
```

#### 3. iOS Stability
Ensure you are using the latest `Package.swift` if integrating via SPM. v1.4.2 includes refined re-entrant locks for iOS delegates.

---

## 2️⃣ From moko-permissions

If you are migrating from `moko-permissions`, you'll find Grant's `GrantHandler` very similar but more focused on state flows.

### Key Mapping
| moko-permissions | Grant |
| :--- | :--- |
| `Permission` | `AppGrant` / `GrantPermission` |
| `PermissionsController` | `GrantHandler` |
| `providePermission` | `request()` |

### Example
```kotlin
// moko
controller.providePermission(Permission.CAMERA)

// Grant
handler.request()
```

---

## 3️⃣ From Google Accompanist

Accompanist Permissions is deprecated. Grant provides a more robust, multiplatform alternative with built-in rationale support.

### Mapping
| Accompanist | Grant |
| :--- | :--- |
| `rememberPermissionState` | `GrantHandler` (in ViewModel) |
| `permissionState.launchPermissionRequest()` | `handler.request()` |
| `permissionState.status` | `handler.status` (StateFlow) |

---

## 4️⃣ From Custom Implementation

If you were manually handling `ActivityResultLauncher` or `onRequestPermissionsResult`, Grant automates this via its "Transparent Activity" pattern.

**Tip**: Move your logic from Activities/Fragments into ViewModels using `GrantHandler`.

---

## 5️⃣ From Native Android APIs

Replace `ActivityCompat.requestPermissions` with `GrantHandler.request`. Grant handles the complexity of checking `shouldShowRequestPermissionRationale` and directing users to settings automatically.

---

## 6️⃣ Common Migration Patterns

### Handling "Always Denied"
Grant automatically detects when a user has permanently denied a permission and surfaces a `showSettingsGuide` event in the `GrantUiState`.

### Atomic Groups
Use `GrantGroupHandler` to request multiple permissions at once, ensuring the UI only shows one rationale dialog for the entire group.

---

## 7️⃣ Troubleshooting

### iOS Framework Linking
If you see "Koin not found" during iOS build after upgrading to 1.3.0+, ensure you have added `:grant-core-koin` to your `commonMain` dependencies and exported it if necessary.

### Android Activity Results
Ensure `GrantRequestActivity` is registered in your `AndroidManifest.xml` (automatically handled by manifest merger in most cases).
