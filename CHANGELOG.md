# Changelog

All notable changes to the Grant library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [2.3.0] - 2026-07-10

### ⚠️ Breaking (grant-compose only)

- **`grant-compose` no longer publishes the `iosX64` target.** Compose Multiplatform 1.11
  stopped publishing `iosX64` artifacts, so the target can no longer resolve its compose
  dependencies. This affects only Intel-Mac-simulator consumers of `grant-compose`; **every
  other module (`grant-core`, `grant-core-koin`, `grant-contacts`, `grant-calendar`,
  `grant-motion`, `grant-bluetooth`, `grant-location-always`) keeps `iosX64`.** Apps needing
  `grant-compose` on an Intel-Mac simulator must stay on 2.2.3.

### 🐛 Fixed

- **`requestSuspend()` suspended forever when no dialog host was attached**
  On a DENIED / DENIED_ALWAYS grant, the `StateBased` flow raises rationale / settings-guide
  dialog state and parks until the dialog host (`GrantDialog` or a custom renderer of
  `GrantHandler.state`) resumes it. When the app rendered no dialog for that handler — e.g. a
  headless `requestSuspend()` pre-check before a screen loads — nothing could ever resume the
  flow and the caller suspended **forever** (real-world case: it silently wedged a consuming
  app's gallery on its loading skeleton). `requestSuspend` now detects "parked on a dialog
  with no collector attached to `state`", clears the unrenderable dialog state (mirroring
  `onDismiss`, including the rationale memory), and completes with the current status. The
  callback-based `request()` flow is unchanged — raising dialog state without a live collector
  remains a supported pattern there. Regression: `RequestSuspendNoHostTest`.

- **Android 14+: fully-granted gallery misreported as `DENIED_ALWAYS`**
  `GALLERY.toAndroidGrants()` includes `READ_MEDIA_VISUAL_USER_SELECTED` on API 34+ so the
  system dialog offers "Select photos" — but `checkStatus()` also counted it when judging
  full access (`all { granted }`). The OS can grant `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO`
  while leaving `USER_SELECTED` denied (ADB `pm grant`, MDM policy, permission auto-reset
  edge states); that fully-usable state failed the all-granted check, and with the grant
  already in the request history it escalated to `DENIED_ALWAYS`. Full access is now judged
  on the required permissions only (`toRequiredAndroidGrants()`); `USER_SELECTED` alone still
  reports `PARTIAL_GRANTED`. Regression: `PlatformGrantDelegateGalleryFullAccessTest`.

### 🔧 Toolchain

| Component | From | To |
|---|---|---|
| Kotlin | 2.1.0 | 2.4.0 |
| Compose Multiplatform | 1.9.3 | 1.11.1 |
| kotlinx-coroutines | 1.10.2 | 1.11.0 |
| Kover | 0.7.5 | 0.9.8 |
| atomicfu | 0.27.0 | 0.33.0 |
| kotlinx-datetime (transitive force-pin) | 0.6.0 | 0.8.0 |

- Kover config migrated to the 0.9 DSL. Note for future edits: report filters must live in
  `reports.total.filtersAppend { }` — the top-level `reports.filters { }` block does not
  affect `koverVerify`/`koverXmlReport` in 0.9.8 (verified empirically). The 85% line floor
  is unchanged; `GrantRequestActivity` (an OS-driven transparent Activity only exercisable
  by on-device instrumented tests, which Kover does not measure) is now explicitly excluded
  to keep the floor comparable with what 0.7 measured.
- The `compose.*` dependency accessors are deprecated by the Compose 1.11 Gradle plugin;
  they still resolve to the correct per-target artifacts and are `@Suppress`ed at each use
  site — full migration to direct coordinates is tracked separately.
- `grant-compose`'s old 85% kover rule was **removed, not migrated**: under 0.7 it never
  actually enforced anything (the module's single logic test measures 0.0% under 0.9's
  merged report — a real gate would have failed every release). A real floor returns once
  the dialogs get Robolectric compose tests. `grant-core`'s enforced 85% floor is unchanged.

### 🐛 Demo fixes

- Scenario 3 "Test Grant Denial Flow" was mis-wired to `requestGalleryGrant()` — the
  denial → rationale → settings walkthrough never actually ran. Now requests
  `READ_CONTACTS` as the card describes (verified on a Pixel 6 Pro: deny → rationale
  dialog → deny again → settings guide).
- The iOS demo project links against whichever framework directory the search path finds
  first; building for a physical device from the CLI needs
  `FRAMEWORK_SEARCH_PATHS=.../iosArm64/debugFramework` (the project lists the simulator
  path first).

---

## [2.2.3] - 2026-06-30

### 🐛 Fixed

- **Android: settings guide shown as rationale again after a permanent denial, within the same session ([#55](https://github.com/brewkits/Grant/issues/55) follow-up)**
  After a permission was permanently denied, dismissing the settings guide and requesting again re-showed the **rationale** dialog instead of the **settings** guide — but only in-session; an app restart behaved correctly. `checkStatus()` short-circuited on the in-memory status cache before consulting the OS-persisted `shouldShowRequestPermissionRationale()` flag, so the second (permanent) denial was masked by the first denial's cached `DENIED` and never became `DENIED_ALWAYS` until the cache was cleared on process death. The OS rationale flag is now consulted first — matching the `RawPermission` and `LOCATION_ALWAYS` branches — so the `DENIED → DENIED_ALWAYS` transition is detected immediately. Verified on a physical Pixel 6 Pro (Android 16); iOS (iPhone XS Max, iOS 18.7.9) is unaffected by design, since it reads the live OS status and has no rationale step.

---

## [2.2.2] - 2026-06-27

### 🐛 Fixed

- **Android permission request swallowed after app restart ([#55](https://github.com/brewkits/Grant/issues/55))**
  After denying a permission and restarting the app, the first request tap could silently do nothing instead of showing the rationale (soft denial) or the settings guide (permanent denial). Two root causes were fixed:
  - `checkStatus()` now consults the OS-persisted `shouldShowRequestPermissionRationale()` flag **before** the in-memory request history, so a *soft* denial is recovered after a restart.
  - The request history itself is now persisted on Android via the new **`SharedPreferencesGrantStore`** (file `grant_request_history`), so a *permanent* denial is recovered after a restart and correctly reported as `DENIED_ALWAYS` (routing to Settings) instead of `NOT_DETERMINED`.

### ⚠️ Behavior Change (Android)

- **`SharedPreferencesGrantStore` is now the default `GrantStore` on Android** (iOS remains `InMemoryGrantStore`). It persists **only** the immutable "has been requested" fact — never the permission status, which is always re-read from the OS — so there is no state desync. The backing file is excluded from backup and device-to-device transfer, so fresh installs start clean. Apps can opt back into the old behavior with `GrantFactory.create(context, store = InMemoryGrantStore())`. Verified on a physical Pixel 6 Pro (Android 16). See [GrantStore Architecture](docs/architecture/grant-store.md).

---

## [2.2.1] - 2026-06-25

### 🐛 Fixed

- **Android: system permission dialog never opened without a registered `GrantLauncher` ([#53](https://github.com/brewkits/Grant/issues/53))**
  When no launcher was registered (e.g. requesting from a ViewModel without wiring `rememberLauncherForActivityResult`), the request fell through to the transparent `GrantRequestActivity` fallback, which now opens the system dialog correctly.

---

## [2.2.0] - 2026-06-21

### ✨ iOS Framework Isolation (continued — [#45](https://github.com/brewkits/Grant/issues/45))

Two more frameworks moved out of `grant-core` into opt-in modules, so apps that don't use them are not flagged by Apple's static scanner for the corresponding `NSUsageDescription` keys:

- **`grant-bluetooth`** — `CoreBluetooth.framework` and the Bluetooth handler/delegate moved out of core; removes the `NSBluetoothAlwaysUsageDescription` requirement for apps that don't use Bluetooth.
- **`grant-location-always`** — the `requestAlwaysAuthorization` (background location) call path moved out of core; `grant-core` now invokes only `requestWhenInUseAuthorization`. Apps using only foreground location are no longer asked for `NSLocationAlwaysAndWhenInUseUsageDescription`.

Both follow the same transparent module-isolation approach as the v2.0.0 `grant-contacts` / `grant-calendar` / `grant-motion` split (an obfuscation-based alternative was rejected as App Store review circumvention).

---

## [2.1.0] - 2026-06-03

### ⚠️ Breaking Changes (Compose UI layer only)

- **`GrantDialog` / `GrantGroupDialog` / `GrantAndServiceDialog` — string params replaced by `GrantDialogStrings`**
  The six individual `String` parameters on each dialog composable (`rationaleTitle`, `rationaleConfirm`, `rationaleDismiss`, `settingsTitle`, `settingsConfirm`, `settingsDismiss`) have been replaced by a single `strings: GrantDialogStrings` parameter. See the [migration guide](docs/MIGRATION_GUIDE.md#upgrading-from-2x-to-21x) for the one-time callsite update.

- **`IosPermissionHandler` renamed to `PermissionHandler`** *(iOS custom handler authors only)*
  The interface used to implement custom `RawPermission` handlers on iOS has been renamed from `IosPermissionHandler` to `PermissionHandler`. `IosPermissionHandlerRegistry` retains its name. Affects only apps that implemented their own handler classes — update the `implements` clause from `IosPermissionHandler` to `PermissionHandler`.

### ✨ New: `GrantEventListener` — Permission Funnel Analytics

Attach an optional listener to any handler to observe the full permission flow lifecycle. Zero overhead when not used (null by default).

```kotlin
val cameraGrant = GrantHandler(
    grantManager  = grantManager,
    grant         = AppGrant.CAMERA,
    scope         = viewModelScope,
    eventListener = object : GrantEventListener {
        override fun onRequested(grant: GrantPermission, status: GrantStatus) {
            analytics.track("perm_requested", mapOf("grant" to grant.identifier, "initial_status" to status.name))
        }
        override fun onGranted(grant: GrantPermission, status: GrantStatus) {
            analytics.track("perm_granted")
        }
        override fun onDenied(grant: GrantPermission, status: GrantStatus) {
            analytics.track("perm_denied", mapOf("permanent" to (status == GrantStatus.DENIED_ALWAYS)))
        }
        override fun onRationaleShown(grant: GrantPermission) { analytics.track("perm_rationale_shown") }
        override fun onSettingsGuideShown(grant: GrantPermission) { analytics.track("perm_settings_guide_shown") }
        override fun onSettingsOpened(grant: GrantPermission) { analytics.track("perm_settings_opened") }
    }
)
```

All six callbacks have default no-op bodies — implement only what you need. `GrantGroupHandler` and `GrantAndServiceHandler` also accept `eventListener`.

### ✨ New: `GrantDialogStrings` — i18n via CompositionLocal

Replace per-callsite string overrides with a single app-level provider. The library now ships English as a last-resort fallback only; translation is the host app's responsibility.

```kotlin
// Set once in your app theme / root composable
GrantDialogStringsProvider(
    GrantDialogStrings(
        rationaleTitle   = stringResource(R.string.grant_rationale_title),
        rationaleConfirm = stringResource(R.string.grant_ok),
        rationaleDismiss = stringResource(R.string.grant_cancel),
        settingsTitle    = stringResource(R.string.grant_settings_title),
        settingsConfirm  = stringResource(R.string.grant_open_settings),
        settingsDismiss  = stringResource(R.string.grant_cancel),
        // Body fallbacks — shown when caller omits rationaleMessage / settingsMessage
        rationaleMessage = stringResource(R.string.grant_rationale_body),
        settingsMessage  = stringResource(R.string.grant_settings_body),
    )
) {
    MyAppContent()
}

// Every GrantDialog() below this point picks up the strings above — zero boilerplate.
GrantDialog(handler = viewModel.cameraGrant)
```

### 🐛 Bug Fixes

- **Issue #41 — Double-denial dead-end (Android)**: After denying a permission twice, the rationale dialog's "Continue" button appeared to do nothing. The OS returns `DENIED` (not `DENIED_ALWAYS`) when re-requesting a blocked permission. Both `GrantHandler` and `GrantGroupHandler` now escalate to the settings guide when the OS returns `DENIED` after a rationale has already been shown. `GrantGroupHandler` no longer loops the rationale infinitely. ([regression test](grant-core/src/commonTest/kotlin/dev/brewkits/grant/regression/Issue41DoubleDenialSettingsTest.kt))

- **Issue #33 follow-up — `LOCATION_ALWAYS` PARTIAL stuck on `refreshStatus`**: `GrantDialog` calls `refreshStatus()` on every `ON_RESUME`. For `LOCATION_ALWAYS`, after the user granted foreground but denied background, returning to the app caused `refreshStatus()` to treat `PARTIAL_GRANTED` as success and fire `onGranted` — a false positive. `PARTIAL_GRANTED` is now correctly treated as unsatisfied for permissions that require a background upgrade. ([regression test](grant-core/src/commonTest/kotlin/dev/brewkits/grant/regression/Issue33RefreshStatusPartialTest.kt))

- **iOS — `requestWithCustomUi` missing events**: The custom-UI flow was not emitting `GrantEventListener` events (`onGranted`, `onDenied`, `onRationaleShown`, `onSettingsGuideShown`). Also fixed missing `resetState()` and `hasShownRationaleDialog = false` in the `GRANTED`/`PARTIAL_GRANTED` branches, and removed a spurious `refreshStatus()` call after re-requesting through the custom-UI rationale path.

- **iOS — rationale skipped for `GrantGroupHandler` on iOS**: `GrantGroupHandler` was showing its app-level rationale on all platforms, inconsistent with `GrantHandler` which already gates rationale on `PlatformConfig.isRationaleSupported`. Group handler now routes directly to the settings guide on iOS.

- **iOS — spurious `requestWhenInUseAuthorization()` before `requestAlwaysAuthorization()`**: On iOS 15+, calling `requestAlwaysAuthorization()` directly shows the correct dialog. The prior pre-call to `requestWhenInUseAuthorization()` was synchronous but resolved asynchronously, causing the Always dialog to fire before the WhenInUse dialog closed — resulting in flicker or instant-dismiss on older devices.

- **iOS Contacts — `CNAuthorizationStatusLimited` magic literal**: The iOS 18 Contacts partial access status was matched with the magic literal `4L`. Replaced with the proper named constant `CNAuthorizationStatusLimited` (available in Kotlin/Native interop since iOS 18.0).

### ♻️ Internal Refactoring

- **Unified state machine**: `handleStatus()` and `handleStatusWithCustomUi()` (~200 lines of duplicated logic) merged into a single `handleStatus(status, ui: UiStrategy)` via a `sealed class UiStrategy`. Future bug fixes apply once, benefit both Compose-state and custom-UI paths. This was the source of Issues #29, #33, and #41 — each required a manual mirror fix.

- **`IosPermissionHandler` → `PermissionHandler`**: The `Ios` prefix is redundant in `iosMain` source sets per KMP convention. Renamed across all 14 handler files. `IosPermissionHandlerRegistry` keeps its name (public API used by optional modules).

### ✅ Test Suite

- 610 Android / 578 iOS tests, 0 failures across all 6 modules.
- New: `GrantEventListenerTest` (15 tests covering all 6 events × 2 handlers × platform variants).
- New: `Issue41DoubleDenialSettingsTest`, `Issue33RefreshStatusPartialTest`.
- New: iOS `request()` withTimeout tests for `LOCATION_ALWAYS`, `GALLERY_IMAGES_ONLY`, `GALLERY_VIDEO_ONLY`, `BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`.

---

## [2.0.0] - 2026-05-14

### ⚠️ Breaking Changes (iOS only)

- **iOS framework isolation** — `Contacts.framework`, `EventKit.framework`, and `CoreMotion.framework` are now opt-in modules. Apps that request these permissions on iOS must add the corresponding Gradle artifact and call `initialize()` once at startup. Android is completely unaffected.

### New Modules

| Artifact | iOS Framework | Initialize |
|---|---|---|
| `dev.brewkits:grant-contacts:2.0.0` | `Contacts.framework` | `GrantContacts.initialize()` |
| `dev.brewkits:grant-calendar:2.0.0` | `EventKit.framework` | `GrantCalendar.initialize()` |
| `dev.brewkits:grant-motion:2.0.0` | `CoreMotion.framework` | `GrantMotion.initialize()` |

### Why This Change

Apple's App Store static scanner rejects apps that link frameworks without declaring the corresponding `NSUsageDescription` keys in `Info.plist`. When `grant-core` directly imported `CNContactStore`, `EKEventStore`, and `CMMotionActivityManager`, those symbols appeared in every app's binary — even apps that never requested those permissions. The only correct fix is to move each framework's import to a separate Gradle module.

### Migration from 1.x

See [Upgrading from Grant 1.x to 2.0.0](docs/MIGRATION_GUIDE.md#upgrading-from-1x-to-20x) in the migration guide.

---

## [1.4.2] - 2026-05-14

### 🐛 Critical Bug Fixes

- **`AndroidGrantLauncher` — Callback Never Invoked (Root Cause of Issue #33)**: The `launch()` method was silently discarding the `onResult` callback passed by `PlatformGrantDelegate`. The `ActivityResultLauncher` inside was registered with a fixed callback at construction time via `from()`, meaning the per-call `onResult` that drives `deferred.complete()` was never called. Every `request()` on Android would hang for the full 5-minute timeout. Fixed by replacing the broken constructor-capture pattern with a `@Volatile pendingCallback` that is set in `launch()` and consumed by the registered `ActivityResultLauncher` callback.

- **`BindGrantsController` No-Op on Android (Demo/Consumer Wiring Broken)**: The Android `actual` implementation of `BindGrantsController` was an empty no-op, meaning `PlatformGrantDelegate.setLauncher()` was never called. Every `request()` in the demo app returned `DENIED` immediately (the explicit early-return guard when `launcher == null`). Fixed by implementing `BindGrantsController` with `rememberLauncherForActivityResult` and `SideEffect` to register and wire the `ActivityResultLauncher` into `GrantManager` on every composition, including after Activity recreation.

- **`ReentrantMutex` Context Key Collision on iOS**: Fixed a bug where nested locks from different `ReentrantMutex` instances overwrote each other's coroutine context keys, causing silent deadlocks in concurrent batch `request()` calls on iOS.

### 🛠️ Architecture & API

- **`GrantLauncher` Interface**: Introduced as the abstraction between `PlatformGrantDelegate` and the Activity Result API. Allows consumers to wire their own launcher without coupling to a specific Activity lifecycle.

- **`AndroidGrantLauncher`**: New public class implementing `GrantLauncher`. Created via `AndroidGrantLauncher.from(activity)` or `from(fragment)`. Handles the mutable-callback bridge between the Kotlin coroutine `deferred` and the Android `ActivityResultLauncher`.

- **`LOCATION_ALWAYS` Two-Step Flow Integrity**: The step-1 (foreground) / step-2 (background) separation introduced in v1.4.2 is now end-to-end functional. Step 1 uses `GrantLauncher` (now correctly wired); step 2 continues to use `GrantRequestActivity` directly, which is correct since background location on Android 11+ requires a separate transparent-Activity launch to avoid overlapping system dialogs.

### ✅ Verified on Device

Full `LOCATION_ALWAYS` flow tested end-to-end on Pixel 6 Pro (Android 16):
1. Request `LOCATION_ALWAYS` from `NOT_DETERMINED` → system foreground dialog appears
2. Grant "While using the app" → library detects `PARTIAL_GRANTED`, auto-triggers step 2
3. `GrantRequestActivity` opens Android Location Settings
4. Select "Allow all the time" → Back → app receives `GRANTED`

No timeout, no crash, no `DENIED` short-circuit.

### 🧹 Code Quality

- Removed all `// FIX N:` labels and temporary work-in-progress comments from production source
- Removed Vietnamese-language inline comments
- Removed stale `// Simplified demo app while we fix the grant implementation` comment from `DemoApp.kt`
- Removed unused `requestIssue33LocationAlways()` / `issue33Result` dead code from demo `GrantDemoViewModel`
- Cleaned version-tagged `// v1.2.0 new` inline comment from `GrantDemoScreen`
- Updated `GrantsBinder.kt` KDoc to accurately describe Android behavior

### 🧪 Test Suite

- Updated `PlatformGrantDelegateStatusTest` to advance `ShadowSystemClock` by 1001ms to correctly account for the 1000ms status cache TTL
- Fixed `Issue33HotfixDuplicateRequestTest` to inject a `GrantLauncher` mock that correctly calls `onResult`, matching the refactored architecture
- Updated `IosGrantDelegateTest` to validate the full `GrantStatus` enum including `BUSY`
- Added `ReentrantMutexTest` covering reentrant locking, non-reentrant behaviour, and concurrent access

---

## [1.4.2] - 2026-05-13

### 🐛 Critical Bug Fixes
- **LOCATION_ALWAYS Timeout (Final Fix)**: Completely resolved the 60-second timeout on Android 11+ by addressing a race condition where sequential background requests launched before the previous Activity fully closed.
- **State Integrity**: Re-engineered `GrantRequestActivity` to reset internal state in `finishAndCleanup()` instead of `onDestroy()`, ensuring immediate availability for subsequent requests.
- **Fail-safe Recovery**: Implemented a 10-second automatic reset for internal permission locks to prevent permanent deadlocks if an Activity is killed unexpectedly.
- **Partial Upgrade Logic**: Fixed `GrantHandler` and `GrantAndServiceHandler` to correctly attempt an OS-level request when a permission is in `PARTIAL_GRANTED` status, instead of jumping prematurely to the Settings guide.

### 🛠️ Stability & Performance
- **Android 15 Optimizations**: Disabled redundant Activity animations during the 2-step location flow to improve speed and reliability.
- **Concurrent Request Guard**: Added strict mutex-based serialization for all native request calls to prevent UI spam and activity leakage.

## [1.4.1] - 2026-05-12

### 🐛 Bug Fixes
- **LOCATION_ALWAYS Flow**: Initial mitigation for a regression introduced in 1.4.0 where duplicate background location requests could cause timeouts. (Full resolution in v1.4.2).

---

## [1.4.0] - 2026-05-09

### 🚀 Enterprise Hardening & Extensibility
- **Process Death Recovery**: `GrantRequestActivity` now integrates `SavedStateHandle` to preserve active permission requests if the Android OS kills the app in the background.
- **Activity Launch Guard**: Prevents multiple overlapping `GrantRequestActivity` instances during rapid concurrent calls.
- **Robust Locking Strategy**: Introduced a custom `ReentrantMutex` across iOS and Android delegates to resolve edge-case deadlocks globally.
- **IosPermissionHandlerRegistry**: Developers can now register custom implementations for `RawPermission` on iOS, eliminating "Not Implemented" limitations.

### ✨ New APIs & Features
- **Core APIs**: Introduced `requestSuspend()` and `requestFlow()` to provide non-callback alternatives perfectly tailored for modern Kotlin Coroutines.
- **GrantFlow DSL**: A new builder DSL (`grantFlow { ... }`) allows you to fluently orchestrate complex, multi-step sequential permission flows.
- **NEARBY_WIFI_DEVICES**: Full support for Android 13+ nearby Wi-Fi hardware permissions.
- **iOS Location Precision**: Added support for `NSLocationTemporaryFullAccuracyUsageDescriptionKey` via `LocationTemporaryFullAccuracyHandler`.
- **Material 3 Dialogs**: Upgraded `GrantRationaleDialog` and `GrantSettingsDialog` in `grant-compose` to use the new Compose Multiplatform `BasicAlertDialog`, adhering strictly to Material 3 design guidelines.

### 🛠️ Performance & Quality
- **Parallel Status Checks**: `GrantAndServiceChecker` and handlers now check OS permissions and hardware services concurrently using `async`/`awaitAll`, minimizing UI latency.
- **SPM Support**: Added a `Package.swift` configuration to support distributing the library via Swift Package Manager.
- **Expanded Documentation**: New recipes added for Android 16 Photo Picker, `UsbManager`, and iOS 18 Limited Contacts (`CNContactPickerViewController`).
- **Testing**: Raised Kover minimum coverage threshold to 85% and added new Robolectric suites for Android 10 `LOCATION_ALWAYS` 2-step flows.

## [1.3.1] - 2026-05-05

### 🛡️ Critical Bug Fix: iOS Deadlock
- **iOS Mutex Re-entrancy Fix**: Resolved a critical deadlock in `PlatformGrantDelegate.ios.kt` where calling `request()` would hang indefinitely. This occurred because `requestInternal()` incorrectly called the public `checkStatus()` (which acquires a per-permission Mutex) while the same Mutex was already held by the parent `request()` call.
- **Regression Safety**: Added `Issue29IosMutexDeadlockTest` to ensure this class of deadlock is automatically detected in the future using `withTimeout` guards.

## [1.3.0] - 2026-04-29

### 🚀 Major Architectural Shift: Koin Decoupling
- **`grant-core-koin` Module**: To resolve critical Kotlin/Native linker issues, all Koin-related code has been extracted into a dedicated artifact.
- **Pure Core**: `grant-core` is now 100% free of Koin dependencies, ensuring a zero-overhead experience for developers using other DI frameworks (Hilt, Dagger) or manual injection.
- **iOS Linker Fix**: Resolved a `NullPointerException` during iOS framework linking caused by `compileOnly` Koin dependencies in previous versions.

### ✨ New Features: HealthCheck Support
- **Health Services Monitoring**: Added `ServiceType.HEALTH` for monitoring system-level health data availability.
- **Android Health Connect**: Intelligent detection of Health Connect availability via intent resolution.
- **Apple HealthKit**: Seamless integration with `HKHealthStore.isHealthDataAvailable()`.

### 🛡️ Production Hardening (QA & Senior Review)
- **iOS Status Cache Thread-Safety**: Fixed a potential TOCTOU (Time-Of-Check to Time-Of-Use) race condition in `PlatformGrantDelegate` by implementing per-permission mutex locking.
- **iOS App Extension Safety**: Refactored `PlatformServiceDelegate` to use KVC reflection when accessing `UIApplication.sharedApplication`, preventing crashes and build errors in App Extensions (Widgets, Share Extensions).
- **Test Suite Optimization**: Reached **430+ automated tests** with 100% pass rate across Android and iOS Simulator.
- **Sample App Overhaul**: Updated the Demo application to showcase the latest unified `GrantAndServiceHandler` pattern.

---

## [1.2.1] - 2026-04-10
... rest of changelog ...
