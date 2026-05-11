# Changelog

All notable changes to the Grant library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.4.1] - 2026-05-12

### 🐛 Bug Fixes
- **LOCATION_ALWAYS Flow**: Fixed a regression introduced in 1.4.0 where a duplicate background location request caused a 60-second timeout if the app already possessed foreground location permissions (Issue #33).

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
