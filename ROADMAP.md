# Grant Library — Roadmap

> Last updated: 2026-09-04 · Current stable: **v2.3.0** (live on Maven Central) · Next: **v2.4.0**, then **v2.5.0**, then **v2.6.0** (multi-platform: JS/Wasm shipped, macOS camera+microphone verified on real hardware, Windows planned)

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

### v2.4.1 — API and runtime guarantees ✅

**4. Validated under R8** ✅
- [x] `demo` release now builds with `isMinifyEnabled = true` and `-allowaccessmodification`; `ci.yml` runs `:demo:assembleRelease` so it stays validated. R8 had **never** been run against Grant before this.
- [x] Confirmed **no consumer ProGuard rules are needed**, with evidence rather than assumption: `GrantRequestActivity` and `GrantInitializer` keep their names (manifest-declared, so R8 keeps them), while `AppGrant`/`GrantHandler`/`GrantStatus` are renamed but present.
- [x] The finding that mattered: **enum constant name strings survive in the DEX**. `SharedPreferencesGrantStore` persists request history keyed on `AppGrant.name`, so R8 rewriting those strings would have silently broken history across builds. Checked in the release DEX, not inferred from the mapping file.
- [x] `demo/proguard-rules.pro` is deliberately free of Grant-specific keeps — a speculative keep file would disable optimisation for every consumer and hide exactly the breakage this build exists to detect.

**5. `explicitApi()` on all eight modules** ✅
- [x] Roughly 340 declarations gained explicit visibility, plus nine explicit return types (`GrantEventListener`'s no-op defaults, a `when`-expression extension, an `openSettings()` override, a mutable property, and the Koin `Module` values).
- [x] **The ABI dumps did not change by a single line.** That is the proof the churn was mechanical and no public surface moved — the reason the ABI gate was worth landing first.

**6. Privacy position pinned** ✅
- [x] `GrantLogger` defaults to `isEnabled = false` with no handler installed, so a library sitting in front of contacts, calendar and location writes nothing the host app did not ask for. Now covered by `LoggerPrivacyDefaultTest`, including that installing a handler is itself an opt-in (independent of `isEnabled`, which gates only the console branch) and that clearing the handler restores silence.

### v2.5.0 — Group UX

**1. Opt-in pre-request rationale for groups**
- [ ] A single "priming" dialog that explains a whole `GrantGroupHandler` set **before** any system prompt fires, instead of the current per-permission rationale shown **after** a refusal.
- [ ] **Additive and opt-in — the default flow does not change.** Today `GrantGroupHandler` batches the system prompts first and then walks refusals individually (`rationaleMessages: Map<GrantPermission, String>`). That order deliberately follows the platform: Android's `shouldShowRequestPermissionRationale()` only returns `true` *after* a denial, so OS-native rationale is inherently post-denial. Priming is an app-level pattern layered on top, not a replacement.
- [ ] **Where it pays:** sensitive permissions where a cold prompt is expensive because the OS grants a limited number of asks — `LOCATION_ALWAYS`, `CONTACTS`, `CALENDAR`. **Where it costs:** users who would have accepted anyway now pay an extra tap, so it must never become the default.
- [ ] Needs a `GrantGroupUiState` flag for the priming stage and a `GrantEventListener` event so the funnel can measure whether priming actually reduces denials — otherwise the feature cannot be evaluated.

*Origin: an external evaluation proposed this alongside "permission funnel analytics" and "atomic batch requests". Those two already ship — `GrantEventListener` (v2.1.0) and `GrantGroupHandler` respectively — and the merged pre-request rationale was the one genuinely new idea in the proposal. That an expert reviewer missed both shipped features was itself the finding: `GrantGroupHandler` appeared **zero** times in the README and `GrantEventListener` only once, buried inside a code sample. Both now have their own Features bullet and Usage section.*

### v2.6.0 — Multi-platform expansion (JS/Wasm, macOS, Windows)

*Origin: a full-market survey found no general-purpose KMP permission library with working desktop or web support. The closest by reach, **Calf** (1642 ★), ships a `desktopMain` source set for permissions, but `launchMultiplePermissionRequest()` there is an empty function body — it compiles and does nothing. The rule this expansion follows throughout: **a platform ships only when its permission flow is real, verified on the actual OS, and covered by a test that would fail if the implementation regressed to a no-op** — never a silently-inert stub.*

**Scope decisions:**
- **JS/Wasm**: `js{browser()}` + `wasmJs{browser()}` on `grant-core`, real `navigator.permissions`/`getUserMedia`/`Notification`/`Geolocation`. Shipped (PR #71) — 358/358 tests pass on both targets in real headless Chrome. See Tier 1 below.
- **macOS**: via JVM/Compose Desktop (`jvm()` + a Kotlin/Native `.dylib` bridge to TCC via JNA), not a standalone Kotlin/Native target — serves real Compose Desktop apps, which the Kotlin/Native-klib path cannot (a klib can't be consumed by a JVM app).
- **Windows**: planned as service-check only, through `ServiceManager`/`ServiceType` — never through `GrantManager`/`AppGrant`. The only registry-readable signal (`CapabilityAccessManager\ConsentStore`) is a **global** Win32 toggle, not per-app consent; routing it through `AppGrant`/`request()` would imply a request flow that does not exist. Not started.
- **Linux**: out of scope — no cross-distro standard signal to read at all.

**Tier 1 — JS / Wasm (browser)** ✅ *shipped*
- [x] `grant-core` gains `js { browser() }` and `wasmJs { browser() }`, both consuming the real `navigator.permissions` / `getUserMedia` / `Notification.requestPermission` / `Geolocation.getCurrentPosition` APIs — never a stub. `wasmJs` exists specifically because Compose Multiplatform Web targets it, not the classic `js` backend; an earlier draft shipped `js`-only under a "JS/Wasm" label before this was caught — see caveat below.
- [x] All logic lives in **`webMain`**, the shared source set the KMP default hierarchy template provides for `js`+`wasmJs` since Kotlin 2.2.20 (project is on 2.4.0). `BrowserApis.kt`'s bindings are `dynamic`-free — every `external interface` extends `JsAny` (required on `wasmJs`, a harmless no-op marker on classic `js`) — which is what makes one binding file, and one delegate implementation, cover both targets instead of two copies drifting apart. Confirmed empirically (not assumed from docs): a probe `external interface : JsAny` with a `String` property compiled cleanly on both `compileKotlinJs` and `compileKotlinWasmJs` before the full rewrite.
- [x] Only **four** `AppGrant` values have a real browser equivalent: `CAMERA`, `MICROPHONE`, `LOCATION`, `NOTIFICATION`. Every other value resolves to `GrantStatus.DENIED_ALWAYS` with a logged reason — never a fabricated `GRANTED`, the same discipline `hasInfoPlistKey()` already holds iOS to.
- [x] `turbine` bumped `1.0.0 → 1.1.0` — the pinned version had no `wasmJs` published artifact; `1.1.0` was the first that did. `koin-test` and `kotest-assertions-core` already published `wasmJs` variants at the versions already in use, no bump needed there.
- [x] **358/358 tests pass on both targets**, in real headless Chrome (`jsBrowserTest` **and** `wasmJsBrowserTest`), including 9 tests in `WebGrantDelegateTest` that call through the real `PlatformGrantDelegate` — not a fake — mirroring the rule CLAUDE.md already holds `IosGrantDelegateTest` to.
- [x] ABI dump regenerated: `js` and `wasmJs` share one `// Targets: [iosArm64, iosSimulatorArm64, iosX64, js, wasmJs]` block (identical public surface across both, as expected from 100%-shared `webMain` code) — confirmed the iOS-only declarations did **not** disappear, just moved to their own `// Targets: [ios]` sub-blocks, KGP's standard multi-target dump layout.
- [x] Full multi-module build (`./gradlew build allTests checkKotlinAbi`, root — not just `:grant-core`) verified green both before and after adding `wasmJs`, confirming `grant-compose`/`grant-core-koin`/the five iOS opt-in modules are unaffected.
- **Caught in review before landing, not after**: a draft of this tier shipped `js`-only while the branch/plan were labeled "JS/Wasm" — the same overclaim-by-naming this expansion exists to avoid in competitors. Corrected by actually adding `wasmJs` rather than renaming the claim down.
- [ ] **Still open before calling this tier fully done per its own verification bar**: a small Compose-for-Web sample page, run in at least Chrome and Firefox, manually clicking through a real `getUserMedia`/notification prompt — the automated headless-Chrome suite exercises the real delegate code paths (including the Firefox-`TypeError` fallback logic) but cannot itself click an OS-level consent dialog or run in Firefox. Tracked here rather than silently dropped.

**Tier 2 — macOS via JVM/Compose Desktop: camera slice ✅ verified end-to-end on real hardware**
- [x] **Bridge mechanism, revised before any handler code was written**: an earlier draft called for hand-rolled JNA `objc_msgSend` calls for everything, including completion-handler-based APIs (`requestAccessForMediaType:completionHandler:`). That needs a hand-built Objective-C `Block_literal` struct to receive the callback — an undocumented, ABI-fragile technique this plan's own bar rules out. Fixed to: a Kotlin/Native `.dylib` (`grant-desktop`'s `macosArm64` target, `binaries.sharedLib()`) whose `macosMain` cinterops AVFoundation directly, the same typed cinterop `iosMain` already uses — an Objective-C completion handler is a plain Kotlin lambda there, so the block-ABI problem never has to be solved by hand. The JVM side loads six-or-fewer `@CName`-exported C functions over JNA; camera's slice needed two (`grant_camera_status`, `grant_camera_request_blocking`).
- [x] **A subprocess helper binary was considered and rejected**: macOS TCC attributes a permission *request* to the calling process's own code-signing identity and reads `NSUsageDescription` keys from that process's own `Info.plist`. A separate helper process has neither — consent it obtained would be attributed to the helper, not the host app. This is the one option that could look like it works in an ad-hoc test and be silently wrong in a shipped app.
- [x] **`grant-core` gained a minimal `jvm()` actual**: every permission resolves through `DesktopPermissionHandlerRegistry` (new, `jvmMain`-only, mirrors `IosPermissionHandlerRegistry`'s pattern exactly); a lookup miss reports `GrantStatus.DENIED_ALWAYS` + a logged reason, never a fabricated `GRANTED`. `grant-core` itself never links JNA or any macOS framework — that isolation lives entirely in the new opt-in `grant-desktop` module, the same shape `grant-contacts`/`grant-calendar`/`grant-motion` already use for iOS. A consumer that adds only `grant-core` and calls `jvm()` gets the honest-unsupported delegate.
- [x] **ABI probe run before trusting any handler**: a throwaway test read `AVCaptureDevice.authorizationStatusForMediaType:` through the full JNA→dylib→cinterop path and confirmed a real 0–3 value, not garbage from a miscompiled call signature — the specific arm64 risk this review flagged (variadic `objc_msgSend` ABI traps), side-stepped entirely by going through cinterop instead of hand-declaring the C signature to JNA directly.
- [x] **Verification harness built before any handler was trusted**: an unbundled `jvmTest` run has no `Info.plist`/code-signing identity of its own, so macOS TCC attributes it to the `java` binary — a correct bridge and a broken one look identical there. `desktop-harness` (new, **deliberately unpublished** — no `maven-publish`, no version, named without the `grant-` prefix so `create-grant-maven-bundle-auto.sh`'s `MODULES` array is never mistaken for needing it) is a real packaged `.app` via Compose Desktop's `nativeDistributions`, with `NSCameraUsageDescription` actually in its `Info.plist` — confirmed with `plutil -p` before any Kotlin was touched.
- [x] **The actual proof, run on this machine, not simulated**: launched the packaged `.app`, clicked Check Status (NOT_DETERMINED), clicked Request Camera — **the real macOS consent dialog appeared** — clicked Allow, clicked Check Status again — **status read GRANTED**. A status that flips because a human clicked Allow on a real system dialog is the only thing that proves the ObjC-completion-handler → `NSCondition` → JVM chain doesn't deadlock and doesn't fabricate a result.
- [x] **Microphone**, sharing the camera code path rather than duplicating it (`avStatus`/`avRequestBlocking` in `CameraBridge.kt`, `AVMediaPermissionHandler.camera()`/`.microphone()` on the JVM side — the same shape `AVPermissionHandler` already uses on iOS), so what was verified for camera is literally what microphone runs through.
- [x] **`openSettings()` via `NSWorkspace`**, verified by observation rather than assumption: with System Settings quit (`pgrep` count 0), `openSettings()` brought it to 1 process and the resulting window's title was **`Camera`** — i.e. the `x-apple.systempreferences:…?Privacy_Camera` deep link resolved to the right pane, which also proves the `String` argument survives the JVM → JNA → Kotlin/Native `@CName` boundary intact. `grant-core`'s `jvm()` actual reaches it through a new `DesktopPermissionHandlerRegistry.settingsOpener` hook, so `grant-core` still links no AppKit.

**Tier 2 audit (post-implementation, before merge) — four real defects found and fixed**
- [x] **A CI-breaking test.** `NativeBridgeAbiProbeTest` asserted the dylib always loads. `ci.yml` runs `./gradlew allTests` on **ubuntu-latest**, where no `darwin-aarch64` slice is bundled, so `NativeBridgeLoader.library` is `null` and the assertion fails — as would any Intel Mac. Confirmed by forcing `os.arch=x86_64` into the test JVM: the old assertion failed with `AssertionError`, the rewritten one passes. The fix is not a skip — the non-macOS branch now *asserts* the honest-degradation contract (`library == null`), so the fallback is pinned rather than untested.
- [x] **A false-positive trap in the verification method itself — the most important finding.** Running `GrantDesktopHarness.app/Contents/MacOS/GrantDesktopHarness` directly makes macOS attribute permissions to the **responsible process** (the launching terminal or IDE), not to the app bundle. Measured directly: after `tccutil reset Camera/Microphone dev.brewkits.grant.desktopharness` — i.e. the bundle has *no* TCC record — direct execution reported `CAMERA=GRANTED MICROPHONE=GRANTED` (the IDE's grants) while `open -a … --args --headless` on the same binary reported `CAMERA=NOT_DETERMINED MICROPHONE=NOT_DETERMINED`. A bridge that read nothing at all would look like it worked under the first launch mode. Documented at the top of `CameraHarness.kt` with the exact commands, because this is invisible unless the two modes are compared.
- [x] **Three thread-safety defects on the JVM side**, none of which exist on iOS, where the equivalent registry is populated once on the main thread before any request: `DesktopPermissionHandlerRegistry`'s plain `mutableMapOf` (a `register()` at startup can overlap a `get()` from a request already on `Dispatchers.IO`) → `ConcurrentHashMap`; `settingsOpener` written on one thread and read on another → `@Volatile`; `GrantDesktop.initialize()`'s unguarded flag → `@Synchronized`.
- [x] **A doc claim that no longer matched the code** (`Dispatchers.IO` described as "the default dispatcher"), corrected along with *why* IO is right here: a thread parked on a native wait for however long a human takes to answer a dialog must not come from `Default`'s core-count-sized pool.
- [x] **Deliberate plugin-set divergence, now documented rather than silently inconsistent**: `grant-desktop` gains `dokka` (it has real public API, and it joins the root aggregation), but not `cyclonedx` (every other module's SBOM task binds `releaseRuntimeClasspath`, an Android configuration this module has no target for — copying it would fail at configuration time) and not `kover` (a coverage floor on a thin bridge whose behaviour only a signed `.app` against real TCC can exercise would measure the wrong thing).

- [ ] **Still not verified, and not claimed**: the `DENIED_ALWAYS` mapping from an actual user denial. macOS deliberately refuses synthesised clicks on a TCC dialog, so "Don't Allow" needs a human; the mapping is currently backed only by code inspection (`AVAuthorizationStatus` 1/2 → `DENIED_ALWAYS`) and the JVM-side unit tests. The microphone *dialog* likewise has not been clicked, though its status path is shared with camera's and reads correctly.
- [ ] **Still not done, tracked rather than dropped**: location/contacts/calendar handlers; the `macosX64` slice (also newly KGP-deprecated — "target will be removed in a future release" — a second, independent reason not to add it speculatively); per-permission Settings deep links (currently always the Camera pane); publishing wiring (`grant-desktop` isn't yet in `create-grant-maven-bundle-auto.sh`'s `MODULES` array or the eight-module version-bump list, and gets its SBOM at that point).

**Tier 2.5 — Windows, `ServiceManager` only** — *not started*

**Explicitly out of scope, with reasons** (so it isn't re-litigated by assumption): Linux desktop (no reliable cross-environment signal), watchOS/tvOS (different problem shape, stays in Backlog below), standalone Kotlin/Native `macosArm64`/`macosX64` klib target (can't be consumed by a Compose Desktop app — this is what `grant-desktop`'s `.dylib`-over-JNA approach sidesteps).

## 📋 Backlog / Considering

*Not committed to a version yet — pulled into a milestone when a consumer actually asks.*

- **Opt-in Handler Registration DSL** (from PR #39 by @RoryKelly) — a `GrantFactory.create { }` block with per-permission `expect/actual` registration (`location()`, `camera()`, …) so K/N DCE can strip *any* unused handler. Five modules are isolated today (`grant-contacts`, `grant-calendar`, `grant-motion`, `grant-bluetooth`, `grant-location-always`); the frameworks still un-strippable from `grant-core` are **CoreLocation** (when-in-use), **Photos**, and **AVFoundation**. Would stay backward compatible — no-arg `create()` keeps registering everything.
- **macOS** moved out of this list into v2.6.0 above (camera slice shipped via `grant-desktop`, a JVM/Compose Desktop module — not an `appleMain`/Kotlin-Native-target refactor; that approach was evaluated and superseded, see v2.6.0's Tier 2 notes).
- **watchOS / tvOS targets** — watchOS wants motion + location, but the UIKit-dependent files (`PlatformGrantDelegate.ios.kt`, `PlatformServiceDelegate.ios.kt`, `SimulatorDetector.kt`) must stay iOS-only, so this would still need an `appleMain` split. Each new target also multiplies the publish matrix: the `MODULES` array in `create-grant-maven-bundle-auto.sh`, the eight version bumps, and the bundle's signature-count check all scale with it.
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
