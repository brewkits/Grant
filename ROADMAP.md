# Grant Library — Roadmap

> Last updated: 2026-05-11 | Current stable: **v1.3.1** | Release candidate: **v1.4.0**

---

## 🛠️ In Progress / Upcoming

### v1.4.0 (RC — pending final QA) — Enterprise Hardening & Extensibility
*Focus: Resilience against Process Death, Performance at Scale, and Architectural Purity.*

**1. Resilience (Android)**
- [x] **Process Death Recovery**: `SavedStateHandle` integration in `GrantRequestActivity` (`GrantRequestActivity.kt`, `AndroidSavedStateDelegate.kt`). Active permission requests survive when the OS kills the app in the background.
- [x] **Activity Launch Guard**: `isActivityActive` volatile flag prevents overlapping `GrantRequestActivity` instances during rapid concurrent calls (`GrantRequestActivity.kt:164`).

**2. Extensibility (iOS)**
- [x] **Handler Registry**: `IosPermissionHandlerRegistry` allows developers to register custom handlers for `RawPermission` on iOS.
- [x] **Modern iOS API Support**: `LocationTemporaryFullAccuracyHandler` for `NSLocationTemporaryFullAccuracyUsageDescriptionKey`; `ContactsPermissionHandler` covers iOS 18 `CNAuthorizationStatusLimited`.

**3. Performance & UI**
- [x] **Parallel Status Checks**: `GrantGroupHandler.refreshAllStatuses` / `request` use `async/awaitAll` for concurrent status verification.
- [x] **Compose-Layer Throttling**: `derivedStateOf` in `GrantDialog` / `GrantGroupDialog` / `GrantAndServiceDialog` isolates dialog-kind changes from unrelated state updates. `StateFlow` provides distinct-emission semantics natively for `_state`, `_status`, `_statuses` so no `distinctUntilChanged` is needed at the flow layer.

**4. Core Architecture**
- [x] **Robust Locking**: `*Internal` / public separation in `PlatformGrantDelegate` (iOS) and `GrantHandler` (`tryLock` always nested inside `scope.launch` to eliminate double-unlock — see Issue #32 fix).
- [x] **Atomic Store Operations**: `InMemoryGrantStore` uses `PlatformLock` across platforms.

**5. Bug Fixes (post-1.3.1)**
- [x] **Issue #32**: `IllegalStateException: This mutex is not locked` — eliminated double-unlock pattern in `request`, `requestSuspend`, `onRationaleConfirmed`, `requestWithCustomUi` (and the matching methods in `GrantGroupHandler`). Regression tests in `regression/Issue32MutexDoubleUnlockTest.kt`.
- [x] **Issue #33**: `LOCATION_ALWAYS` `PARTIAL_GRANTED` now routes to settings dialog instead of completing as success. Driven by the new `GrantPermission.requiresBackgroundUpgrade` flag (true for `AppGrant.LOCATION_ALWAYS`). Single-permission, group, and custom-UI flows all consistent. Regression tests in `regression/Issue33PartialGrantedSettingsTest.kt`.

## ✅ Released

### v1.3.1 (2026-05-05)
- HOTFIX: iOS `request()` mutex deadlock resolution (Issue #29)
- Regression tests for non-reentrant mutex patterns
- `grantedEvents` SharedFlow audit (made `internal` in v1.4.0)
- Removed dead `isAppInForeground` from `GrantRequestActivity`
- Fixed CI kover task paths and `release.yml` phantom Gradle task
- Clarified non-Activity context log message

### v1.3.0 (2026-04-29)
- Koin decoupled into `grant-core-koin` module
- `GrantAndServiceHandler` for unified permission + service flows
- iOS delegate refactored into per-framework handler classes (no unused-framework linking)
- `InMemoryGrantStore` thread safety via `PlatformLock`
- Process-death recovery hardening in `GrantRequestActivity`
- `GALLERY_IMAGES_ONLY` and `GALLERY_VIDEO_ONLY` permissions
- LOCATION_ALWAYS 2-step (foreground → background) flow on Android 10+
- 430+ automated tests, 100% pass rate

### v1.2.x (2026-04)
- `GrantAndServiceChecker` for read-only status queries
- `GrantGroupHandler` batch permission requests
- Kotlin/Native concurrency fixes (mutex map, status cache)

### v1.0.x–v1.1.x (2025)
- Initial KMP release: Android + iOS
- `GrantHandler`, `GrantGroupHandler`, Compose UI layer
- Koin DI integration, `RawPermission` custom permissions

---

## 📋 Planned — v1.4.0 (continued)

### Android
- [ ] **Android 16 photo picker (`PICK_IMAGES` intent) — full library integration**. Recipe is shipped at `docs/recipes/photo-picker-fallback.md`; turning it into a built-in `AppGrant`-level surface is deferred to v1.4.1.
- [x] `NEARBY_WIFI_DEVICES` permission support (Android 13+)
- [x] `UsbManager` permission support via `RawPermission` recipe (`docs/recipes/usb-permission.md`)

### iOS
- [x] `NSLocationTemporaryFullAccuracyUsageDescriptionKey` (precision location upgrade)
- [x] iOS 18 `CNContactPickerViewController` contacts access permission
- [x] Swift Package Manager distribution alongside Maven Central (`Package.swift`)

### Core
- [x] `GrantFlow` builder DSL — fluent alternative to `GrantHandler` for complex multi-step flows
- [x] Coroutine Flow-based `requestFlow()` API returning `Flow<GrantStatus>` (no callback)
- [x] Suspend `requestSuspend()` as a simpler non-UI alternative to `request(onGranted)`

### Testing / Quality
- [x] Robolectric tests for `LOCATION_ALWAYS` `checkStatus` (`PlatformGrantDelegateLocationAlwaysTest.kt`). End-to-end Robolectric test for the 2-step `request()` flow (foreground → background prompt) is **deferred to v1.4.1** — Robolectric does not currently simulate the second prompt cleanly.
- [ ] iOS XCTest snapshot tests for `GrantDialog` Compose UI — **deferred to v1.5.0** for framework evaluation.
- [x] Kover minimum threshold raised from 82% → 85%

---

## 💡 Under Consideration — v1.5.0+

- **Wear OS support** — minimal permission surface, sensor-only grants
- **Android TV / large screen** — `requestWithCustomUi()` examples for non-phone form factors
- **Windows/Linux/macOS targets** — stubs only, for KMP Desktop apps that share ViewModel code
- **Analytics hooks** — optional `GrantEventListener` for tracking permission funnel drop-off
- [x] **Compose Multiplatform Material3 dialogs** — replaced `AlertDialog` with M3 `BasicAlertDialog` (shipped in v1.4.0)

---

## 🔒 Out of Scope

- Runtime permission bypass or suppression utilities
- Root / shell permission escalation
- Any API that requests permissions without explicit user interaction
