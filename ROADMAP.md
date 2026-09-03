# Grant Library — Roadmap

> Last updated: 2026-09-03 · Current stable: **v2.3.0** (live on Maven Central) · Next: **v2.4.0**

---

## 🛠️ In Progress / Upcoming

### v2.4.0 — API Stability + Docs ✅ *merged to main in #66*
*Focus: lock the public API surface down, publish real API docs, and stop advertising a Swift distribution that never worked.*

**1. Swift Package Manager** ✅ *decided: dropped*
- [x] Investigated. `Package.swift` referenced a `1.4.2` asset with a literal `PLACEHOLDER_CHECKSUM_WILL_BE_REPLACED_BY_CI`, and **no release has ever attached an xcframework** (verified: `assets=0` on v1.4.2, v2.0.0, v2.1.0, v2.2.0, v2.2.3, v2.3.0). The SPM path never resolved once, so it had no consumers to break.
- [x] **Not repairable without giving up more.** Kotlin/Native statically copies dependency code into every framework: `IosPermissionHandlerRegistry` and `GrantLogger` — both `grant-core` classes — are present inside `GrantContacts.framework` as well. An app linking both would hold **two registry singletons with separate state**, so `GrantContacts.initialize()` would register a handler `grant-core` never sees. (Also 19 duplicate `T` symbols, including the runtime's `_IsInstance`.) One umbrella xcframework avoids that but re-links `Contacts`/`EventKit`/`CoreMotion`/`CoreBluetooth` into every consumer — a direct regression of the Issue #38 and #45 isolation work.
- [x] **Decision: drop SPM.** `Package.swift` deleted. Rationale documented in `docs/getting-started/installation.md` ("Why there is no SPM or CocoaPods support") and the stale pointer in `docs/MIGRATION_GUIDE.md` corrected.
- Supporting evidence: 0 SPM/xcframework requests across all 17 issues ever filed; a `grant-core`-only package would still leave **8 of 16 iOS-relevant permissions unreachable** (`LOCATION_ALWAYS`, `BLUETOOTH`, `BLUETOOTH_ADVERTISE`, `CONTACTS`, `READ_CONTACTS`, `MOTION`, `CALENDAR`, `READ_CALENDAR`); and KMP consumers never needed it — their iOS app already gets `grant-core` through its own shared framework.

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

### v2.4.1 — Platform Accuracy

**1. `LOCAL_NETWORK` gated on `targetSdkVersion`, not device API level** ✅ *fixed*
- [x] Android gates `ACCESS_LOCAL_NETWORK` enforcement on the app's **target**: it appears only under "Behavior changes: apps targeting Android 17 or higher" and is **absent** from the "all apps" list. Grant checked `Build.VERSION.SDK_INT >= 37` only.
- [x] Consequence: an app targeting API 36 on an Android 17 device keeps working on the local network without the permission — and has no reason to declare it — yet Grant mapped it anyway, `checkSelfPermission` failed for an undeclared permission, and the request-history fallback escalated that to `DENIED_ALWAYS`, sending users to Settings to find a toggle that is not listed, for a feature that was never broken.
- [x] Not an edge case: Play requires a recent target but not the newest, so "targets 36, runs on 17" is the norm for roughly a year after each Android release.
- [x] Fixed by requiring both conditions. Regression test `LocalNetworkTargetSdkGatingTest` covers the 2×2 matrix and was **confirmed to fail against the old gate** before the fix landed.

**2. `AppGrant.GALLERY_ADD_ONLY`** ✅ *shipped*
- [x] `PhotoPermissionHandler` hardcoded `PHAccessLevelReadWrite`, so save-only access was unreachable — while the docs already referenced `NSPhotoLibraryAddUsageDescription`, the key for a mode the code could not request.
- [x] The handler now takes the access level and plist key as parameters; `GALLERY_ADD_ONLY` uses `PHAccessLevelAddOnly` + `NSPhotoLibraryAddUsageDescription`. Android needs no permission at all on API 29+ under scoped storage, so it reports `GRANTED` without prompting; `WRITE_EXTERNAL_STORAGE` on API 26-28.
- [x] Why it matters: a camera app that only saves its own captures should not have to ask to read the user's library — a larger ask, denied more often, and a likely App Store review question.
- [x] `AppGrant` now covers **20** permissions. The ABI gate from #66 caught the enum addition on both the klib and Android surfaces, exactly as intended; dumps regenerated.

**3. iOS 27 review** — *no action needed*
- [x] The widely reported "unified Privacy Management declaration" in OS 27 is an **MDM/enterprise feature** configured by IT administrators for managed apps, not a developer API. It does not change Grant's design, and `GrantGroupHandler` is not superseded by it.
- [x] Consumer coverage of "granular photo editing access" in iOS 27 could **not** be corroborated at the PhotoKit/`PHAccessLevel` level. Left unverified rather than planned around; revisit when Apple's own documentation lands.

## 📋 Backlog / Considering

*Not committed to a version yet — pulled into a milestone when a consumer actually asks.*

- **Opt-in Handler Registration DSL** (from PR #39 by @RoryKelly) — a `GrantFactory.create { }` block with per-permission `expect/actual` registration (`location()`, `camera()`, …) so K/N DCE can strip *any* unused handler. Five modules are isolated today (`grant-contacts`, `grant-calendar`, `grant-motion`, `grant-bluetooth`, `grant-location-always`); the frameworks still un-strippable from `grant-core` are **CoreLocation** (when-in-use), **Photos**, and **AVFoundation**. Would stay backward compatible — no-arg `create()` keeps registering everything.
- **`appleMain` refactor + macOS / watchOS / tvOS targets** — one piece of work, not two. macOS has a real permission surface (camera, mic, location, contacts, calendar) and watchOS wants motion + location, but the UIKit-dependent files (`PlatformGrantDelegate.ios.kt`, `PlatformServiceDelegate.ios.kt`, `SimulatorDetector.kt`) must stay iOS-only, and `openSettings()` needs an AppKit/`NSWorkspace` path before macOS can build at all. Each new target also multiplies the publish matrix: the `MODULES` array in `create-grant-maven-bundle-auto.sh`, the eight version bumps, and the bundle's signature-count check all scale with it.
- **Wear OS / Android TV** — minimal permission surface, sensor-only grants; `requestWithCustomUi()` examples for non-phone form factors.
- **System pickers as a first-class API** — the Android Photo Picker (`PICK_IMAGES`) needs no runtime permission at all, and Android 17 extends the same idea to the local network: adopting a system-mediated device picker skips the `ACCESS_LOCAL_NETWORK` prompt entirely. Contact Picker follows the same shape. The platform direction is clear — **pickers are replacing permissions** — so the win is an `AppGrant`-level surface that transparently chooses picker-vs-permission per API level, rather than three separate recipes. Recipes already shipped at `docs/recipes/photo-picker-fallback.md` and in the Contact Picker guidance.
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
