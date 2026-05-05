# Grant Library — Roadmap

> Last updated: 2026-05-05 | Current stable: **v1.3.1**

---

## 🛠️ In Progress / Upcoming

### v1.4.0 (Target: Q3 2026) — Enterprise Hardening & Extensibility
*Focus: Resilience against Process Death, Performance at Scale, and Architectural Purity.*

**1. Resilience (Android)**
- [ ] **Process Death Recovery**: Implement `SavedStateHandle` support in `GrantRequestActivity`. Ensure active permission requests survive when the OS kills the app in the background.
- [ ] **Activity Launch Guard**: Prevent multiple `GrantRequestActivity` instances from overlapping during rapid concurrent calls.

**2. Extensibility (iOS)**
- [ ] **Handler Registry**: Allow developers to register custom `IosPermissionHandler` implementations for `RawPermission`. No more "Not Implemented" dead-ends on iOS.
- [ ] **Modern iOS API Support**: Guidelines and helpers for `PHPicker` (no-permission photo selection) and `NSLocationTemporaryFullAccuracyUsageDescriptionKey`.

**3. Performance & UI**
- [ ] **Parallel Status Checks**: Refactor `GrantGroupHandler` to use `async/awaitAll` for status verification, eliminating "UI Jank" when checking 10+ permissions.
- [ ] **Emission Throttling**: Use `distinctUntilChanged` on internal flows to prevent redundant UI re-compositions.

**4. Core Architecture**
- [ ] **Robust Locking**: Replace the brittle `checkStatusInternal` pattern with a cleaner internal/external separation or a re-entrant safe locking strategy.
- [ ] **Atomic Store Operations**: Ensure `GrantStore` updates are atomic across all platforms to prevent race conditions during rapid state changes.

## ✅ Released

### v1.3.1 (2026-05-05)
- HOTFIX: iOS `request()` mutex deadlock resolution (Issue #29)
- Regression tests for non-reentrant mutex patterns

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

## 🚧 In Progress — v1.3.x patch

| Item | Status |
|---|---|
| Fix double-unlock pattern in `GrantGroupHandler` | ✅ Done (this branch) |
| Remove dead `isAppInForeground` from `GrantRequestActivity` | ✅ Done |
| Fix CI kover task paths | ✅ Done |
| Fix release.yml phantom Gradle task | ✅ Done |
| Clarify non-Activity context log message | ✅ Done |
| `grantedEvents` SharedFlow made `internal` | ✅ Done |

---

## 📋 Planned — v1.4.0

**Target:** Q3 2026

### Android
- [ ] Android 16 photo picker integration (`PICK_IMAGES` intent as fallback to `READ_MEDIA_*`)
- [ ] `NEARBY_WIFI_DEVICES` permission support (Android 13+)
- [ ] `UsbManager` permission support via `RawPermission` recipe in docs

### iOS
- [ ] `NSLocationTemporaryFullAccuracyUsageDescriptionKey` (precision location upgrade)
- [ ] iOS 18 `PHPickerViewController` contacts access permission
- [ ] Swift Package Manager distribution alongside Maven Central

### Core
- [ ] `GrantFlow` builder DSL — fluent alternative to `GrantHandler` for complex multi-step flows
- [ ] Coroutine Flow-based `requestFlow()` API returning `Flow<GrantStatus>` (no callback)
- [ ] Suspend `requestSuspend()` as a simpler non-UI alternative to `request(onGranted)`

### Testing / Quality
- [ ] Robolectric tests for `LOCATION_ALWAYS` 2-step flow
- [ ] iOS XCTest snapshot tests for `GrantDialog` Compose UI
- [ ] Kover minimum threshold raised from 82% → 85%

---

## 💡 Under Consideration — v1.5.0+

- **Wear OS support** — minimal permission surface, sensor-only grants
- **Android TV / large screen** — `requestWithCustomUi()` examples for non-phone form factors
- **Windows/Linux/macOS targets** — stubs only, for KMP Desktop apps that share ViewModel code
- **Analytics hooks** — optional `GrantEventListener` for tracking permission funnel drop-off
- **Compose Multiplatform Material3 dialogs** — replace `AlertDialog` with M3 `BasicAlertDialog`

---

## 🔒 Out of Scope

- Runtime permission bypass or suppression utilities
- Root / shell permission escalation
- Any API that requests permissions without explicit user interaction
