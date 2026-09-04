# Migration Guide to Grant

**Version:** 2.4.0
**Last Updated:** September 4, 2026

This guide helps you migrate from previous versions of Grant or other permission libraries.

---

## 📚 Table of Contents

1. [Upgrading from Grant 2.3.0 to 2.4.0](#upgrading-from-grant-230-to-240)
2. [Upgrading from Grant 2.2.x to 2.3.0](#upgrading-from-grant-22x-to-230)
3. [Upgrading from Grant 2.1.0 to 2.2.0](#upgrading-from-grant-210-to-220)
4. [Upgrading from Grant 2.0.0 to 2.1.0](#upgrading-from-grant-200-to-210)
5. [Upgrading from Grant 1.x to 2.0.0](#upgrading-from-grant-1x-to-200)
6. [Upgrading from Grant 1.3.x to 1.4.2](#upgrading-from-grant-13x-to-142)
7. [From moko-permissions](#from-moko-permissions)
8. [From Google Accompanist](#from-google-accompanist)
9. [From Custom Implementation](#from-custom-implementation)
10. [From Native Android APIs](#from-native-android-apis)
11. [Common Migration Patterns](#common-migration-patterns)
12. [Troubleshooting](#troubleshooting)

---

## 🚀 Upgrading from Grant 2.3.0 to 2.4.0

> **2.4.0 is not on Maven Central yet.** It is built and tested but unreleased; the coordinates
> below will resolve once it ships. Until then, 2.3.0 remains the latest published version.

### Overview

**Nothing you have to change.** 2.4.0 is additive: every existing `AppGrant`, method and
behaviour works exactly as before. The items below are opportunities, plus one compatibility
note that matters only if you mix binary versions.

New in 2.4.0: finer-grained Bluetooth, an App Tracking Transparency module, a multi-process
advisory, and fixes for `SCHEDULE_EXACT_ALARM` and the iOS 17+ write-only calendar key.

### 1. New: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` — ask for less

`AppGrant.BLUETOOTH` requests Android's `BLUETOOTH_SCAN` **and** `BLUETOOTH_CONNECT` together.
Android 12 separated them because they differ in sensitivity, and Grant now exposes that split.

**Why it is worth acting on**, not just cosmetic: a plain `BLUETOOTH_SCAN` is treated by
Android as capable of deriving physical location — scan results reveal which devices are
nearby. So a **connect-only** app (POS terminal, car key, scale, wearable) using
`AppGrant.BLUETOOTH` has been carrying a location implication for a capability it never uses —
visible to Play review, privacy audits, and in the permission list users see.

| Your app | Use | Requests |
|---|---|---|
| Connects to already-paired devices only | `AppGrant.BLUETOOTH_CONNECT` | `BLUETOOTH_CONNECT` — **nothing below API 31** |
| Scans for devices/beacons only | `AppGrant.BLUETOOTH_SCAN` | `BLUETOOTH_SCAN` (API 31+) / `ACCESS_FINE_LOCATION` below |
| Both scans and connects | `AppGrant.BLUETOOTH` — unchanged | both |

```kotlin
// Before — over-asks for a connect-only app
val status = grantManager.request(AppGrant.BLUETOOTH)

// After — asks for exactly what a POS/car-key/scale app uses
val status = grantManager.request(AppGrant.BLUETOOTH_CONNECT)
```

Narrow your manifest to match:

```xml
<!-- Connect-only: this is the whole declaration you need -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

**On iOS nothing changes.** iOS has a single Bluetooth authorization covering scan, connect and
advertise, so all four values behave identically there. The split is an Android-only
distinction; using the narrower value is safe in shared KMP code.

### 2. New advisory: `BLUETOOTH_SCAN` without `neverForLocation`

If you request `BLUETOOTH_SCAN` and your manifest does not declare the opt-out, Grant now logs
a warning once per process:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
```

Add the flag if your app does **not** derive physical location from scan results. It is a
declaration only your app can make, which is why Grant warns rather than changing any status.
Nothing breaks if you ignore it — but this is usually discovered during a Play review instead.

### 3. New advisory: multi-process apps

**Only relevant if your app runs in more than one process** (`android:process=":miniapp"`,
`:webview`, `:push` — the usual super-app shape). If it does not, skip this.

Grant's fallback dialog host, `GrantRequestActivity`, always launches in your app's **main**
process, and the state bridging a request to its result is `static` — meaning per-process. A
`request()` issued from a *secondary* process therefore waits on a result the main process
completes somewhere it cannot see: the dialog appears, the user answers, and the call times out
after five minutes reporting the unchanged status.

Grant cannot bridge processes on your behalf — that needs IPC your app owns — so as of 2.4.0 it
**detects and logs** this instead of failing silently.

> **Measured on a real Android 17 device.** The same request, tapping Allow the same way,
> differing only in which process it ran from:
>
> | Process | Permission actually granted | What the caller saw |
> |---|---|---|
> | main | yes | `GRANTED` after 2.8s |
> | secondary | yes | **nothing — still waiting after 120s** |
>
> The user says yes, the permission is granted, and your code in the secondary process never
> finds out.

**The fix is one line, per process:**

```kotlin
// In the Activity of each process that requests permissions
grantManager.setLauncher(object : GrantLauncher {
    override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
        // your ActivityResultLauncher, bound to THIS process's Activity
    }
})
```

`setLauncher()` never touches the static bridge, so it works correctly from any process.

### 4. New: `AppGrant.APP_TRACKING` (iOS App Tracking Transparency)

For ad attribution and cross-app measurement. Needs the opt-in module — linking
`AppTrackingTransparency` makes Apple require the usage-description key in *every* app that
links it, so it cannot live in `grant-core`:

```kotlin
implementation("dev.brewkits:grant-tracking:2.4.0")
```

```kotlin
GrantTracking.initialize()   // once, at startup
val status = grantManager.request(AppGrant.APP_TRACKING)
```

```xml
<!-- Info.plist -->
<key>NSUserTrackingUsageDescription</key>
<string>Explain what tracking gives the user.</string>
```

⚠️ **Timing is not optional here.** iOS shows the ATT prompt only while your app is
foreground-**active**. Called during launch or from the background, it returns the current
status with no prompt — and the **single ask each install gets is spent**. Request it from a
screen the user is looking at. Grant logs a warning if it detects a non-active state, but it
cannot recover the lost ask.

On Android this is a no-op reporting `GRANTED`: cross-app tracking is gated there by
`com.google.android.gms.permission.AD_ID`, an install-time permission with no runtime prompt.

### 5. Behaviour change: `SCHEDULE_EXACT_ALARM` now actually opens something

**Android.** Previously `request(AppGrant.SCHEDULE_EXACT_ALARM)` showed nothing at all and the
status then escalated to `DENIED_ALWAYS`, sending users to a Settings page that does not
contain the toggle. `SCHEDULE_EXACT_ALARM` is *special app access*, not a runtime permission —
`requestPermissions()` can never grant it.

It now opens the Alarms & reminders screen, which is the platform's real request flow. Two
consequences for your code:

- The status is now `DENIED` rather than `DENIED_ALWAYS` when not granted. Special app access
  has no permanent-denial state — the toggle stays available and re-requesting reopens the
  screen. If you branched on `DENIED_ALWAYS` for this permission specifically, switch to
  `DENIED`.
- `request()` returns as soon as Settings opens; it cannot observe the user's choice. Re-read
  on resume with `GrantHandler.onReturnFromSettings()` or `refreshStatus()` — the same pattern
  `openSettings()` already required.

**iOS.** This reported `GRANTED` unconditionally, which was correct through iOS 25 where
nothing gated scheduling. iOS 26 added AlarmKit, which *is* consent-gated. If your app declares
`NSAlarmKitUsageDescription`, Grant now reports `DENIED_ALWAYS` rather than a `GRANTED` it
cannot back — AlarmKit is Swift-only, so Kotlin/Native cannot query it. For a real answer,
bridge AlarmKit in Swift and register it:

```kotlin
IosPermissionHandlerRegistry.register(AppGrant.SCHEDULE_EXACT_ALARM.identifier, yourHandler)
```

Apps that do not declare that key see no change.

### 6. Fixed: iOS 17+ write-only calendar access

If your app declares only `NSCalendarsWriteOnlyAccessUsageDescription` — the minimal-scope
choice for an app that only *adds* events — Grant used to report `DENIED_ALWAYS` before EventKit
was ever consulted. That key is now accepted. No action needed; it just starts working.

### Compatibility note: enum ordinals shifted

`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` were inserted after `BLUETOOTH` rather than appended,
which shifts `AppGrant.ordinal` for the eight values below them by two.

**This affects nothing in normal use.** Grant identifies permissions by `name`, never ordinal —
`identifier` is `name`, and persisted request history is keyed on `name`, so stored state
survives the upgrade untouched.

It matters only if **you** persisted an ordinal, sent one across a process or network boundary,
or mix modules compiled against different Grant versions (ordinals are inlined at compile time).
If any of those apply, rebuild all modules against 2.4.0 and migrate any stored ordinals to
`name` — which is the more robust key regardless.

### Version bump

```kotlin
implementation("dev.brewkits:grant-core:2.4.0")
implementation("dev.brewkits:grant-compose:2.4.0")
implementation("dev.brewkits:grant-core-koin:2.4.0")
implementation("dev.brewkits:grant-tracking:2.4.0")          // new, optional (iOS ATT)
// ...and any other optional iOS modules you use, all at 2.4.0
```

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

v2.2.0 (Issue #45) continues the **iOS Framework Isolation** work started in v2.0.0. Two more sensitive paths were moved out of `grant-core` into opt-in modules so apps that don't use them are never flagged by Apple's static scanner:

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

## 🛡️ Upgrading from Grant 2.0.0 to 2.1.0

### Overview

v2.1.0 is the **Analytics & i18n** release. It carries two breaking changes, both narrow:

- **`grant-compose` only** — the individual `String` parameters on the dialog composables are replaced by a single `strings: GrantDialogStrings`.
- **iOS custom-handler authors only** — the `IosPermissionHandler` interface is renamed to `PermissionHandler`.

If you do not use `grant-compose` and have not written a custom `RawPermission` handler for iOS, **this upgrade is a version bump and nothing else**. Android app code is unaffected either way.

### What Changed?

- **`GrantDialogStrings`** — one immutable holder for every user-visible dialog string, supplied app-wide through `GrantDialogStringsProvider` (a `CompositionLocal`) instead of repeated per-callsite arguments.
- **`GrantEventListener`** — new, optional, non-breaking. Observes the permission funnel (`onRequested`, `onGranted`, `onDenied`, `onRationaleShown`, `onSettingsGuideShown`, `onSettingsOpened`).
- **`IosPermissionHandler` → `PermissionHandler`** — the `Ios` prefix was redundant in a source set that is already iOS-only.
- **Bug fixes** — Issue #41 (double-denial dead-end on Android: the flow now escalates to the settings guide when the OS returns `DENIED` after a rationale was already shown) and Issue #33 (a false-positive `onGranted` when background location was denied but foreground was granted).

### Step-by-Step Upgrade

#### 1. Bump every Grant artifact to `2.1.0`

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.1.0")
    implementation("dev.brewkits:grant-compose:2.1.0")
    // …and any optional modules you use, at the same version
}
```

#### 2. Replace the dialog string parameters — `grant-compose` only

The compiler catches every one of these; there is no silent behaviour change.

**Before (2.0.0):**

```kotlin
GrantDialog(
    handler = viewModel.cameraGrant,
    rationaleTitle = "Camera access",
    rationaleConfirm = "Continue",
    rationaleDismiss = "Not now",
    settingsTitle = "Camera denied",
    settingsConfirm = "Open Settings",
    settingsDismiss = "Not now",
)
```

**After (2.1.0):**

```kotlin
GrantDialog(
    handler = viewModel.cameraGrant,
    strings = GrantDialogStrings(
        rationaleTitle = "Camera access",
        rationaleConfirm = "Continue",
        rationaleDismiss = "Not now",
        settingsTitle = "Camera denied",
        settingsConfirm = "Open Settings",
        settingsDismiss = "Not now",
    ),
)
```

The parameter names are unchanged — they simply moved inside `GrantDialogStrings`, so this is a mechanical edit. The same applies to `GrantGroupDialog` and `GrantAndServiceDialog`.

`GrantAndServiceDialog` is the one case where names were also **consolidated**, because it previously had a separate set for the service-settings dialog:

| 2.0.0 parameter | 2.1.0 field on `GrantDialogStrings` |
|---|---|
| `permissionSettingsTitle` | `settingsTitle` |
| `permissionSettingsConfirm` | `settingsConfirm` |
| `serviceSettingsTitle` | `serviceSettingsTitle` |
| `serviceSettingsConfirm` | `serviceSettingsConfirm` |
| `dismissText` | `settingsDismiss` / `serviceSettingsDismiss` (now separate) |

#### 3. Prefer setting the strings once, app-wide

If you were passing the same strings at several call sites, the provider replaces all of them. This is the reason the API changed — it is what makes the dialogs translatable.

```kotlin
// Once, in your app theme or root composable:
GrantDialogStringsProvider(
    strings = GrantDialogStrings(
        rationaleTitle   = stringResource(Res.string.grant_rationale_title),
        rationaleConfirm = stringResource(Res.string.grant_ok),
        rationaleDismiss = stringResource(Res.string.grant_cancel),
        settingsTitle    = stringResource(Res.string.grant_settings_title),
        settingsConfirm  = stringResource(Res.string.grant_open_settings),
        settingsDismiss  = stringResource(Res.string.grant_cancel),
    )
) {
    MyAppContent()   // every GrantDialog() inside picks these up
}
```

Individual call sites can still override by passing `strings = …` directly; the parameter defaults to `LocalGrantDialogStrings.current`.

> The library ships English defaults purely as a last-resort fallback. Translation is the host app's responsibility — a `GrantDialog` with no provider and no explicit `strings` renders English.

`GrantDialogStrings` also carries three body-text fields that had no parameter equivalent in 2.0.0: `rationaleMessage`, `settingsMessage` and `serviceSettingsMessage`. They are used when the caller does not supply a message, so you can now translate that text too.

#### 4. Rename the handler interface — iOS custom handlers only

Applies only if you implemented a custom handler for a `RawPermission` on iOS. There is **no deprecated typealias**, so this is a hard rename and the compiler will flag it.

**Before (2.0.0):**

```kotlin
class MyHandler : IosPermissionHandler {
    override fun checkStatus(): GrantStatus = …
    override suspend fun request(): GrantStatus = …
}
```

**After (2.1.0):**

```kotlin
class MyHandler : PermissionHandler {
    override fun checkStatus(): GrantStatus = …
    override suspend fun request(): GrantStatus = …
}
```

The method signatures are identical, and the import stays `dev.brewkits.grant.handlers.*`.

> ⚠️ The **registry object keeps its original name** — it is still `IosPermissionHandlerRegistry`, and `IosPermissionHandlerRegistry.register(identifier, handler)` is unchanged. Only the interface was renamed.

#### 5. Optionally attach a `GrantEventListener`

Additive — skip this if you do not need funnel analytics. Every method has a default empty implementation, so override only what you use.

```kotlin
val cameraGrant = GrantHandler(
    grantManager = grantManager,
    grant = AppGrant.CAMERA,
    scope = viewModelScope,
    eventListener = object : GrantEventListener {
        override fun onDenied(grant: GrantPermission, status: GrantStatus) {
            analytics.track("permission_denied", grant.toString(), status.toString())
        }
    },
)
```

`GrantGroupHandler` and `GrantAndServiceHandler` accept the same `eventListener` parameter.

---

## 🛡️ Upgrading from Grant 1.x to 2.0.0

### Overview

v2.0.0 is the **iOS Framework Isolation** release. `Contacts.framework`, `EventKit.framework`, and `CoreMotion.framework` are now opt-in Gradle/Maven modules. **Android is completely unaffected** — no code changes required on Android.

### What Changed?

- **New optional modules**: `grant-contacts`, `grant-calendar`, `grant-motion`. Each module links its native iOS framework only when added.
- **No more forced `NSUsageDescription` keys**: Apps that don't add an optional module are never prompted by App Store to add the corresponding usage key.
- **`IosPermissionHandlerRegistry` fix**: `checkStatus()` for `RawPermission` now correctly dispatches to custom registered handlers (previously only `request()` did).

### Step-by-Step Upgrade (iOS only)

#### 1. Update the core version

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.0.0")
}
```

#### 2. Add optional modules for permissions you use

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.0.0")
    // Add only the ones your app actually uses:
    implementation("dev.brewkits:grant-contacts:2.0.0")  // Contacts
    implementation("dev.brewkits:grant-calendar:2.0.0")  // Calendar / EventKit
    implementation("dev.brewkits:grant-motion:2.0.0")    // CoreMotion / Step Counter
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
v1.4.2 includes refined re-entrant locks for iOS delegates.

> **Note:** Grant is not distributed via Swift Package Manager. The `Package.swift` that
> shipped between v1.4.0 and v2.3.0 never resolved and was removed in v2.4.0 — see
> [Why there is no SPM or CocoaPods support](getting-started/installation.md#why-there-is-no-spm-or-cocoapods-support).
> Add Grant to your KMP module with Gradle instead.

---

## From moko-permissions

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

## From Google Accompanist

Accompanist Permissions is deprecated. Grant provides a more robust, multiplatform alternative with built-in rationale support.

### Mapping
| Accompanist | Grant |
| :--- | :--- |
| `rememberPermissionState` | `GrantHandler` (in ViewModel) |
| `permissionState.launchPermissionRequest()` | `handler.request()` |
| `permissionState.status` | `handler.status` (StateFlow) |

---

## From Custom Implementation

If you were manually handling `ActivityResultLauncher` or `onRequestPermissionsResult`, Grant automates this via its "Transparent Activity" pattern.

**Tip**: Move your logic from Activities/Fragments into ViewModels using `GrantHandler`.

---

## From Native Android APIs

Replace `ActivityCompat.requestPermissions` with `GrantHandler.request`. Grant handles the complexity of checking `shouldShowRequestPermissionRationale` and directing users to settings automatically.

---

## Common Migration Patterns

### Handling "Always Denied"
Grant automatically detects when a user has permanently denied a permission and surfaces a `showSettingsGuide` event in the `GrantUiState`.

### Atomic Groups
Use `GrantGroupHandler` to request multiple permissions at once, ensuring the UI only shows one rationale dialog for the entire group.

---

## Troubleshooting

### iOS Framework Linking
If you see "Koin not found" during iOS build after upgrading to 1.3.0+, ensure you have added `:grant-core-koin` to your `commonMain` dependencies and exported it if necessary.

### Android Activity Results
Ensure `GrantRequestActivity` is registered in your `AndroidManifest.xml` (automatically handled by manifest merger in most cases).
