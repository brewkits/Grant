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

### 🛡️ Hotfix: iOS Mutex Deadlock (Issue #29)
- **CRITICAL**: Fixed a deadlock on iOS where calling `request()` would hang indefinitely without showing a system dialog. This was caused by a non-reentrant Mutex acquisition pattern in `PlatformGrantDelegate`.
- **Improved iOS Stability**: Unified internal status checking to bypass public locks, ensuring re-entrant calls within the platform delegates are safe.
- **Regression Tests**: Integrated a new test suite that validates sequential and concurrent permission requests on iOS to prevent future regressions.

## [1.3.0] - 2026-04-28

### 🛡️ Production Stabilization & Quality
- **Codebase Sanitization**: Conducted a final audit to remove all development markers (`FIX #X`), temporary technical notes, and inconsistent annotations, ensuring an enterprise-grade public API.
- **Documentation Parity**: Standardized all internal documentation and code comments to English, removing legacy Vietnamese annotations and examples for global readiness.
- **Improved Test Reliability**: Enhanced test logic to handle platform-specific behaviors (e.g., rationale-less iOS flows) and state desynchronization in unit tests, ensuring a stable 100% pass rate.
- **Apple App Store Resilience**: Verified and documented the combination of `Weak Linking` and the `Handler` pattern to prevent binary detection of sensitive frameworks (Camera/Microphone) by App Store scanners.

### 🧪 Full Spectrum Testing
- **Verified Pass Rate**: Successfully executed full test suites across common code, iOS Simulator, and Android Robolectric environments.
- **Performance & Stress Baseline**: Confirmed scaling performance for large permission groups and high-concurrency request patterns.
- **Security Audit**: Validated permission identity sanitization and strict store privacy isolation.

## [1.2.1] - 2026-04-10

### 🛡️ Rock-Solid Stability & Safety
- **CRITICAL: Android Re-entrant Deadlock**: Fixed a critical bug in `PlatformGrantDelegate` where `checkStatus` incorrectly used the same per-permission mutex as `request`, causing a deadlock on Android when requesting permissions.
- **Concurrency Guard**: Added `requestMutex` to `GrantHandler` and `GrantGroupHandler` to prevent race conditions and redundant system dialogs from rapid UI interactions (e.g., spam clicking).
- **Callback Visibility**: Applied `@Volatile` to internal callbacks to ensure thread-safe visibility across different coroutine contexts.

### ⚡ Performance & Parallelization
- **Parallel Group Checks**: `GrantGroupHandler` now checks status for all permissions in parallel using `async/awaitAll`, significantly reducing latency in multi-permission flows.
- **Atomic Multi-Request Result**: Optimized Android's multi-request logic to perform parallel status verification after the system dialog flow completes.
- **Cache Invalidation Parity**: Unified cache invalidation logic across Android and iOS, ensuring status reads are always fresh after a request operation.

### 🧪 Extreme Testing
- **Stress Testing**: Added `ThreadingStressTest` with 1000+ concurrent operations to verify stability under extreme load.
- **Deadlock Regression Tests**: Added automated tests to detect and prevent re-entrant locking issues.
- **Hardware Verified**: This release has been verified on physical hardware (**Pixel 6 Pro**) and simulators for both platforms.

### 🤝 Special Thanks
- Huge shout-out to [**rohan-paudel**](https://github.com/rohan-paudel) for their significant contributions to this release!

---

## [1.2.0] - 2026-04-08

### 🚀 Architectural Overhaul & Stability
- **`PARTIAL_GRANTED` Status**: Added support for partial access (e.g., Android 14+ "Select Photos" and iOS Limited Photo Library access).
- **Atomic Group Requests**: `GrantGroupHandler` now uses `GrantManager.request(List<GrantPermission>)` to request all denied permissions at once, allowing the OS to group dialogs and improving UX significantly.
- **Keyed Mutex for Concurrency**: Eliminated request bottlenecks in `PlatformGrantDelegate` by locking per-identifier instead of globally. Allows requesting unrelated permissions in parallel.
- **Monotonic Time Cache (iOS)**: `checkStatus` cache now uses `NSProcessInfo.processInfo.systemUptime` to prevent bugs when users change system time.
- **Reasonable Minimum Versions**: Downgraded to Kotlin 2.1.0 and Compose 1.9.3 to maximize compatibility across different consumer apps without forcing bleeding-edge requirements.
- **GrantRequestActivity Optimizations**: Replaced `UUID` with `AtomicInteger` for lighter request IDs, and `MutableStateFlow` with `CompletableDeferred` for efficient, single-shot result handling. Added standard `launchMode` for better concurrency.
- **Compose `GrantGroupDialog`**: Added a new dialog helper to auto-handle `GrantGroupHandler` UI flows in Compose.
- **New Test Suites**: Added `ThreadingStressTest`, `PlatformCacheTest`, `SecurityPlatformTest`, bringing coverage and stability guarantees to the next level.
- **Android Test Stability**: Fixed `TimeoutCancellationException` in `testRequestMultiplePermissions` by using more reliable permission targets in the instrumented test suite.

### 🍎 iOS Full Native Implementation
This version bridges the implementation gap completely, making iOS on **par with Android** for all 17 permission types.
- **Full Native Coverage**: Implemented native request/status checks for Camera, Microphone, Gallery (Limited access support), Location (Always/WhenInUse), Notifications, Contacts, Calendar (iOS 17+ support), Motion (Dummy Query pattern), and Bluetooth.
- **Info.plist Validation**: Every permission request validates the presence of its required `Info.plist` key before calling native APIs, preventing `SIGABRT` crashes.
- **Main Thread Safety**: All native callback completions are dispatched correctly to the main thread.
- **Simulator Continuity**: Motion and Bluetooth now return `GRANTED` on iOS Simulator to facilitate UI development without hardware.

---

