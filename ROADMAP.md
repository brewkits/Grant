# Grant Library — Roadmap

> Last updated: 2026-09-03 · Current stable: **v2.3.0** (live on Maven Central) · Next: **v2.4.0**

---

## 🛠️ In Progress / Upcoming

### v2.4.0 — Apple Reach + API Stability
*Focus: lock the public API surface down, publish real API docs, and settle how (or whether) Grant reaches Swift consumers.*

**1. Decide the Swift Package Manager story** ⚠️ *blocked on a product decision*
- [x] Investigated. `Package.swift` points at a `1.4.2` asset with a literal `PLACEHOLDER_CHECKSUM_WILL_BE_REPLACED_BY_CI`, and no release has ever attached an xcframework — the SPM path is dead for every Swift consumer today.
- [ ] **Blocker found:** shipping one xcframework per module does not work. Verified on the release `iosArm64` frameworks: `IosPermissionHandlerRegistry` and `GrantLogger` — both `grant-core` classes — are statically duplicated into `GrantContacts.framework` (Kotlin/Native copies the dependency in; `implementation(project(":grant-core"))` only hides it from the framework's public headers). An app linking both would get **two registry singletons with separate state**: `GrantContacts.initialize()` would register its handler into one copy while `grant-core` reads the other, so the handler is silently never found. There are also 19 duplicate `T` symbols including the runtime's `_IsInstance`.
- [ ] A single umbrella xcframework fixes the duplication but re-links `Contacts`/`EventKit`/`CoreMotion`/`CoreBluetooth` into every consumer — a direct regression of the Issue #38 and #45 isolation work. Not acceptable.
- [ ] **Decision needed** — pick one:
  - **(a) SPM ships `grant-core` only.** One xcframework, honest scope; optional modules stay Gradle/KMP-only.
  - **(b) Drop SPM.** Delete `Package.swift`, document Grant as a KMP-consumed library.
  - **(c) Pre-composed variants.** Publish an xcframework per module combination — 2⁵ = 32 variants. Rejected as unmaintainable.

**2. Public API stability** ✅ *done*
- [x] Enabled KGP 2.4's built-in ABI validation on all eight published modules (the standalone `binary-compatibility-validator` plugin is superseded by it and handles klib worse).
- [x] Committed 16 dumps under each module's `api/` — a `.klib.api` covering the Apple targets plus an Android `.api`. `grant-compose`'s klib dump independently confirms its `[iosArm64, iosSimulatorArm64]` target list, with no `iosX64`.
- [x] Wired `checkKotlinAbi` into `ios.yml` (macOS runner — Kotlin/Native cannot cross-compile Apple klibs on the Linux `ci.yml` runner).
- [x] Verified the gate actually bites: adding a method to `GrantFactory` failed `checkKotlinAbi` on both the klib and Android surfaces. Regenerate with `./gradlew updateKotlinAbi`.

**3. `appleMain` source-set refactor** — ❌ *deliberately not done; moved to Backlog*
- Investigated and **not done on purpose**: `appleMain` already exists as an implicit intermediate source set in KMP's default hierarchy template, so creating the physical directory while only iOS targets exist changes nothing. Worse, the move is not mechanical — `PlatformGrantDelegate.ios.kt`, `PlatformServiceDelegate.ios.kt` and `SimulatorDetector.kt` depend on **UIKit** (`UIApplicationOpenSettingsURLString`, `UIApplication.sharedApplication`, `UIDevice.currentDevice`), which does not exist on macOS. The refactor only pays for itself alongside a real macOS/watchOS target, and at that point `openSettings()` needs an AppKit/`NSWorkspace` design, not a file move.

**4. Documentation** ✅ *done*
- [x] Dokka 2.0.0 wired at the root as an aggregated publication over all eight modules (`demo` excluded); `./gradlew dokkaGenerate` → `build/dokka/html`.
- [x] Opted into the Dokka V2 Gradle plugin in `gradle.properties` — 2.0.0 still defaults to V1, which is deprecated and removed in 2.1.0.
- [x] Added `.github/workflows/docs.yml`: builds on a macOS runner and deploys to GitHub Pages on every `v*` tag. **Needs Pages enabled** in repo settings (Settings → Pages → Source: GitHub Actions) before the first run.

## 📋 Backlog / Considering

*Not committed to a version yet — pulled into a milestone when a consumer actually asks.*

- **Opt-in Handler Registration DSL** (from PR #39 by @RoryKelly) — a `GrantFactory.create { }` block with per-permission `expect/actual` registration (`location()`, `camera()`, …) so K/N DCE can strip *any* unused handler. Five modules are isolated today (`grant-contacts`, `grant-calendar`, `grant-motion`, `grant-bluetooth`, `grant-location-always`); the frameworks still un-strippable from `grant-core` are **CoreLocation** (when-in-use), **Photos**, and **AVFoundation**. Would stay backward compatible — no-arg `create()` keeps registering everything.
- **`appleMain` refactor + macOS / watchOS / tvOS targets** — one piece of work, not two. macOS has a real permission surface (camera, mic, location, contacts, calendar) and watchOS wants motion + location, but the UIKit-dependent files (`PlatformGrantDelegate.ios.kt`, `PlatformServiceDelegate.ios.kt`, `SimulatorDetector.kt`) must stay iOS-only, and `openSettings()` needs an AppKit/`NSWorkspace` path before macOS can build at all. Each new target also multiplies the publish matrix: the `MODULES` array in `create-grant-maven-bundle-auto.sh`, the eight version bumps, and the bundle's signature-count check all scale with it.
- **Wear OS / Android TV** — minimal permission surface, sensor-only grants; `requestWithCustomUi()` examples for non-phone form factors.
- **Photo picker as a first-class grant** — the Android Photo Picker (`PICK_IMAGES`) needs no runtime permission at all, so the win is an `AppGrant`-level surface that transparently picks picker-vs-permission per API level. Recipe already shipped at `docs/recipes/photo-picker-fallback.md`.
- **iOS XCTest snapshot tests** for the `GrantDialog` Compose UI.

## ✅ Released

### v2.3.0 (2026-07-10)
- **Kotlin 2.4 toolchain** upgrade across all modules.
- **Android 17 support**: `LOCAL_NETWORK` grant wired end-to-end (`AppGrant` now covers 19 permissions).
- Location and gallery status-mapping fixes; Contact Picker guidance added to the docs.
- **`grant-compose` dropped `iosX64`** — Compose Multiplatform 1.11 stopped publishing `iosX64` artifacts. Every other module still ships all three iOS targets.
- Full documentation pass + legacy-OS regression pins.

### v2.2.3 (2026-06-30)
- **Issue #55 follow-up**: in-session `DENIED` → `DENIED_ALWAYS` escalation now resolves correctly without an app restart.

### v2.2.2 (2026-06-26)
- **Issue #55**: a permission request was swallowed after app restart on Android. `checkStatus()` now evaluates `shouldShowRequestPermissionRationale()` first, and request history is persisted through `SharedPreferencesGrantStore` so it survives process death.

### v2.2.1 (2026-06-23)
- **HOTFIX — Issue #53**: the Android system permission dialog never opened when no launcher had been set.

### v2.2.0 (2026-06-08)
- **Issue #45 — two more iOS framework isolations**:
  - **`grant-bluetooth`** — `CoreBluetooth.framework` and the Bluetooth handler/delegate moved out of `grant-core`, so `NSBluetoothAlwaysUsageDescription` is no longer demanded of apps that don't use Bluetooth.
  - **`grant-location-always`** — the `requestAlwaysAuthorization` call path moved out of `grant-core`. Core now invokes only `requestWhenInUseAuthorization`; status checks still map `kCLAuthorizationStatusAuthorizedAlways → GRANTED`. Foreground-only apps are no longer asked for `NSLocationAlwaysAndWhenInUseUsageDescription`.
- An obfuscation approach (`performSelector(NSSelectorFromString("request" + "AlwaysAuthorization"))`) was **rejected** as a review-circumvention technique risking App Store Guideline 2.3.1. Module isolation is the transparent fix.

### v2.1.0 (2026-06-03)
- **`GrantEventListener`** — optional permission-funnel analytics on any handler (`onRequested`, `onGranted`, `onDenied`, `onRationaleShown`, `onSettingsGuideShown`, `onSettingsOpened`).
- **`GrantDialogStrings`** — i18n via `CompositionLocal`; one app-level `GrantDialogStringsProvider` replaces per-callsite string overrides.
- **Breaking (Compose only)**: individual `String` params on `GrantDialog` / `GrantGroupDialog` / `GrantAndServiceDialog` replaced by a single `strings: GrantDialogStrings`.
- **Breaking (iOS custom handler authors only)**: the `IosPermissionHandler` interface renamed to `PermissionHandler`. The registry object keeps its original name, `IosPermissionHandlerRegistry`.
- **Issue #41** — double-denial dead-end on Android: escalates to the settings guide when the OS returns `DENIED` after a rationale was already shown.
- **Issue #33** — `LOCATION_ALWAYS` PARTIAL: fixed a false-positive `onGranted` when background was denied but foreground granted.
- iOS: `requestWithCustomUi()` now emits all listener events; group-handler rationale display made consistent.

### v2.0.0 (2026-05-15)
- **iOS Framework Isolation**: `Contacts.framework`, `EventKit.framework`, `CoreMotion.framework` moved to opt-in modules (`grant-contacts`, `grant-calendar`, `grant-motion`). Apps that don't add these modules never link these frameworks — Apple's static scanner no longer requires the corresponding `NSUsageDescription` keys.
- **New modules**: `grant-contacts`, `grant-calendar`, `grant-motion` as separate Gradle/Maven artifacts.
- **`IosPermissionHandlerRegistry`**: Registry fix — `checkStatus()` for `RawPermission` now correctly dispatches to registered custom handlers.
- **Test suite expanded**: 1131 tests across 6 modules (Android JVM + iOS Simulator), 100% pass rate.
- **Breaking change**: iOS apps using Contacts/Calendar/Motion permissions must add the new optional module and call `initialize()` once. Android is unaffected.

### v1.4.2 (2026-05-13)
- **FINAL FIX**: Resolved 60s timeout in `LOCATION_ALWAYS` flow (Issue #33).
- **Hardening**: Immediate state reset in `GrantRequestActivity` and 10s fail-safe guard.
- **Logic**: Corrected `PARTIAL_GRANTED` upgrade path to allow system dialogs.
- **Android 15**: Optimized transitions and lifecycle handling.

### v1.4.1 (2026-05-12)
- HOTFIX: Initial mitigation for duplicate background location requests.

### v1.4.0 (2026-05-09)
- **Process Death Recovery**: `SavedStateHandle` integration in `GrantRequestActivity`.
- **Activity Launch Guard**: Prevented overlapping Activity instances.
- **IosPermissionHandlerRegistry**: Custom handlers for `RawPermission` on iOS.
- **NEARBY_WIFI_DEVICES**: Full Android 13+ support.
- **Material 3**: Upgraded all Compose dialogs to `BasicAlertDialog`.
- **New APIs**: `requestSuspend()` and `requestFlow()`.

### v1.3.1 (2026-05-05)
- HOTFIX: iOS `request()` mutex deadlock resolution (Issue #29).
- Regression tests for non-reentrant mutex patterns.
