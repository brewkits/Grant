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

