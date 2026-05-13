# Changelog

All notable changes to the Grant library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

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
