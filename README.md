<div align="center">

<img src="assets/logo.svg" height="120" alt="Grant logo" />

# Grant

**Type-safe permission management for Kotlin Multiplatform — Android, iOS & Browser**

[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/grant-core?style=flat-square&color=7F52FF&label=maven%20central)](https://central.sonatype.com/artifact/dev.brewkits/grant-core)
[![CI](https://img.shields.io/github/actions/workflow/status/brewkits/Grant/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/brewkits/Grant/actions/workflows/ci.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/brewkits/Grant/codeql.yml?branch=main&style=flat-square&label=CodeQL)](https://github.com/brewkits/Grant/security/code-scanning)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/platforms-android%20%7C%20ios%20%7C%20js%20%7C%20wasm-555?style=flat-square)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)

[Documentation](docs/README.md) · [API reference](https://brewkits.dev/Grant/) · [Quick start](docs/getting-started/quick-start.md) · [Why Grant?](#why-grant) · [Demo app](docs/demo/DEMO_GUIDE.md)

</div>

---

Grant handles the permission edge cases that simple wrappers miss: silent deadlocks, Android process death, iOS `Info.plist` crashes, partial media/location access, and hardware service state. The API is logic-first — request permissions from a ViewModel or repository with no Activity, Fragment, or lifecycle binding.

```kotlin
class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(grantManager, AppGrant.CAMERA, viewModelScope)

    fun onCaptureClick() = viewModelScope.launch {
        if (cameraGrant.requestSuspend() == GrantStatus.GRANTED) {
            cameraEngine.start()
        }
    }
}
```

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    GrantDialog(handler = viewModel.cameraGrant)   // renders rationale / settings-guide dialogs (Material 3)

    Button(onClick = viewModel::onCaptureClick) { Text("Start camera") }
}
```

**Requirements:** Android 8.0+ (API 26) · iOS 13+ · a browser with `navigator.permissions`
(`grant-core` only — Compose Multiplatform Web or Kotlin/JS) · Kotlin 2.x · JVM 17

## Features

- **Logic-first API** — works in ViewModels, repositories, or composables. No Activity or Fragment references, no lifecycle binding.
- **Adds no permissions to your app** — `grant-core` declares zero `<uses-permission>` entries, so nothing appears on your Play listing that you did not ask for. The Android counterpart to the iOS framework isolation below.
- **iOS framework isolation** — Contacts, Calendar, Motion, Bluetooth, and background location live in opt-in modules. Frameworks you don't use are never linked, so Apple's static scanner never demands phantom `NSUsageDescription` keys.
- **iOS crash guard** — validates `Info.plist` keys before requesting, turning the classic `SIGABRT` production crash into a clear error.
- **Android process-death recovery** — a request in flight survives system-initiated process death via `SavedStateHandle`, with no timeouts.
- **Deadlock-free by construction** — reentrant locking plus a `withTimeout` test policy that converts silent deadlocks into failing tests.
- **23 built-in permissions** — Camera, Gallery (incl. Android 14 partial access and a save-only mode that never prompts), Location (incl. "Approximate"-only), Bluetooth, Local Network (Android 17), App Tracking Transparency (iOS), and more — plus `RawPermission` for anything the library doesn't ship yet.
- **Browser target** (`grant-core` only) — real `navigator.permissions`/`getUserMedia`/`Notification`/`Geolocation` checks for Camera, Microphone, Location, and Notification on `js` and `wasmJs`, the latter specifically for Compose Multiplatform Web. Every other grant honestly reports unsupported rather than a fabricated `GRANTED`.
- **Permission groups as one unit** — `GrantGroupHandler` requests several permissions in a single batch, drives one `StateFlow` for the whole group, and fires `onAllGranted` only when every one is satisfied.
- **Funnel analytics** — attach an optional `GrantEventListener` to any handler and observe every stage: requested, granted, denied, rationale shown, settings guide shown, settings opened.
- **Service-state checks** — one call answers both "is the permission granted?" and "is GPS/Bluetooth actually on?".
- **Material 3 dialogs** — optional `grant-compose` module renders the rationale and settings-guide flow out of the box.
- **Heavily tested** — 2,100+ test executions across Android and iOS targets on every build, including concurrency-stress, regression, and process-death suites.

## Installation

Grant is published to Maven Central. Add the core module, plus only the optional modules you use:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:2.4.0")

            // Optional
            implementation("dev.brewkits:grant-compose:2.4.0")          // Compose dialogs (Material 3)
            implementation("dev.brewkits:grant-core-koin:2.4.0")        // Koin DI integration

            // Optional per-permission modules. Omitting a module means its iOS
            // framework is never linked — no phantom NSUsageDescription keys.
            implementation("dev.brewkits:grant-contacts:2.4.0")         // Contacts (iOS CNContactStore)
            implementation("dev.brewkits:grant-calendar:2.4.0")         // Calendar (iOS EventKit)
            implementation("dev.brewkits:grant-motion:2.4.0")           // Motion (iOS CoreMotion)
            implementation("dev.brewkits:grant-bluetooth:2.4.0")        // Bluetooth (iOS CoreBluetooth)
            implementation("dev.brewkits:grant-location-always:2.4.0")  // Background "always" location (iOS)
        }
    }
}
```

**Android** needs no setup: Grant ships a self-contained transparent Activity, so `request()` opens the system dialog from anywhere. Prefer your own `ActivityResultLauncher`? Register it once with `grantManager.setLauncher(...)` and Grant uses it instead.

**iOS** needs one call per optional module at app startup:

```swift
// AppDelegate / @main entry point
GrantContacts.shared.initialize()        // if you added grant-contacts
GrantCalendar.shared.initialize()        // if you added grant-calendar
GrantMotion.shared.initialize()          // if you added grant-motion
GrantBluetooth.shared.initialize()       // if you added grant-bluetooth
GrantLocationAlways.shared.initialize()  // if you added grant-location-always
```

> [!WARNING]
> **2.3.0 — `grant-compose` no longer ships an `iosX64` target.** Compose Multiplatform 1.11 stopped publishing iosX64 artifacts. All other modules keep iosX64; Intel-Mac-simulator consumers of `grant-compose` should stay on 2.2.3. Details in the [Migration Guide](docs/MIGRATION_GUIDE.md).

> [!NOTE]
> **Migrating from v1.x?** Contacts, Calendar, Motion, Bluetooth, and background location are now opt-in modules: add the artifact and call `initialize()` on iOS. Android needs no code changes. Projects that also target Web/Desktop should isolate Grant behind a `mobileMain` source set — see [Dependency Management](docs/DEPENDENCY_MANAGEMENT.md).

## Usage

### Request a permission

`GrantHandler` runs the full state machine for one permission — request, rationale, permanent denial, settings guide — and exposes it as a `StateFlow` your UI can render:

```kotlin
class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope,
    )

    // Suspend until the flow resolves…
    suspend fun startCapture() {
        if (cameraGrant.requestSuspend() == GrantStatus.GRANTED) cameraEngine.start()
    }

    // …or consume it reactively.
    val captureFlow = cameraGrant.requestFlow()
        .filter { it == GrantStatus.GRANTED }
        .onEach { cameraEngine.start() }
}
```

### Request several permissions as one unit

A feature that needs more than one permission — a video call needs Camera **and** Microphone — should not
ask twice and should not half-succeed. `GrantGroupHandler` batches the system prompts into one pass, then
walks any refusals individually to show the right rationale or settings guide. `onAllGranted` runs only when
every permission in the group is satisfied:

```kotlin
class CallViewModel(grantManager: GrantManager) : ViewModel() {
    val callGrants = GrantGroupHandler(
        grantManager = grantManager,
        grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
        scope = viewModelScope,
    )

    fun onJoinCall() {
        callGrants.request(
            rationaleMessages = mapOf(
                AppGrant.CAMERA     to "Your camera is needed so others can see you.",
                AppGrant.MICROPHONE to "Your microphone is needed so others can hear you.",
            )
        ) {
            // Runs only when BOTH are granted.
            callEngine.join()
        }
    }
}
```

`callGrants.state` is a single `StateFlow<GrantGroupUiState>` for the whole group, and `GrantGroupDialog(callGrants)`
renders it. Per-permission results stay available through `callGrants.statuses`.

### Track the permission funnel

Every handler takes an optional `GrantEventListener`. Each method has a default empty implementation, so
override only the stages you measure — useful for finding where users actually drop off:

```kotlin
val cameraGrant = GrantHandler(
    grantManager = grantManager,
    grant = AppGrant.CAMERA,
    scope = viewModelScope,
    eventListener = object : GrantEventListener {
        override fun onRationaleShown(grant: GrantPermission) {
            analytics.track("permission_rationale_shown", grant.toString())
        }

        override fun onDenied(grant: GrantPermission, status: GrantStatus) {
            // status distinguishes DENIED from DENIED_ALWAYS
            analytics.track("permission_denied", grant.toString(), status.toString())
        }

        override fun onSettingsOpened(grant: GrantPermission) {
            analytics.track("permission_settings_opened", grant.toString())
        }
    },
)
```

### Chain sequential permissions

```kotlin
val scanFlow = grantFlow {
    val btStatus = bluetoothHandler.requestSuspend()
    if (btStatus == GrantStatus.GRANTED) {
        locationHandler.requestSuspend()   // needed for BLE scanning on some Android versions
    }
}
```

### Check permission and hardware together

A granted location permission is useless with GPS turned off. `GrantAndServiceChecker` answers both questions in one call:

```kotlin
fun startTracking() {
    viewModelScope.launch {
        when (checker.checkLocationReady()) {
            is LocationReadyStatus.Ready           -> sensor.start()
            is LocationReadyStatus.ServiceDisabled -> uiState.showEnableGps()
            is LocationReadyStatus.GrantDenied     -> requestPermission()
            is LocationReadyStatus.BothRequired    -> uiState.showBothPrompts()
        }
    }
}
```

## Why Grant?

Most KMP permission libraries are thin wrappers around the native APIs. Grant is built around the failure modes those wrappers hit in production:

| | Grant | moko-permissions | accompanist-permissions |
| :--- | :---: | :---: | :---: |
| No lifecycle binding | ✅ | ❌ needs `BindEffect` | ❌ needs Activity |
| ViewModel support | full | partial | ❌ |
| iOS crash prevention (`Info.plist`) | ✅ | ❌ | — |
| iOS framework isolation | ✅ | ❌ | — |
| Process-death recovery | built-in | ❌ | manual |
| Service checks (GPS/BT/Health) | ✅ | ❌ | ❌ |
| Android 14 partial access | ✅ | partial | ✅ |
| Custom permissions | ✅ | limited | limited |

## Engineering rigor

Every claim below is enforced by CI or was checked by hand on real hardware — nothing here is
aspirational.

- **Every pull request is gated**, not just merges to `main` — build, full test suite, Android
  Lint, and the API-surface check all run before a PR can land.
- **[CodeQL](https://github.com/brewkits/Grant/security/code-scanning) runs on every push** and
  currently reports zero open alerts — every finding was triaged by hand, not just silenced.
- **The public API surface is locked.** All 9 published modules use Kotlin's explicit-API mode
  plus a committed ABI dump (`checkKotlinAbi`); a PR that changes the surface without
  regenerating the dump fails CI. Two breaking changes shipped unnoticed in v2.1.0 before this
  gate existed — it hasn't happened since.
- **Every published module ships a [CycloneDX](https://cyclonedx.org/) SBOM** — answer "what's
  inside this dependency" without unpacking it.
- **Verified on real hardware, not just a simulator.** The Android 17 `ACCESS_LOCAL_NETWORK`
  mapping and the multi-process advisory in 2.4.0 were both confirmed on a physical device —
  the multi-process case specifically because a secondary-process request measured a real
  120-second timeout with the permission silently granted underneath it, a class of bug a
  simulator-only test suite would never have caught.
- **The browser target runs against real Chrome**, not a fake: 358/358 tests pass in headless
  Chrome for both the `js` and `wasmJs` targets, including the Firefox `permissions.query()`
  fallback path.

## Size and supply chain

| Artifact | Release AAR |
|---|---|
| `grant-core` | 293 KB |
| `grant-compose` | 30 KB |
| `grant-core-koin` | 7 KB |
| `grant-contacts` · `grant-calendar` · `grant-motion` · `grant-bluetooth` · `grant-location-always` · `grant-tracking` | ~2 KB each |

Download size is not app size. In a real R8-minified build (the demo, with
`-allowaccessmodification`), Grant contributes **83 classes** out of 2,686 — the rest is
stripped. Adding an optional module such as `grant-bluetooth` costs single-digit kilobytes.

Every published module emits a **CycloneDX SBOM** (`./gradlew cyclonedxBom` →
`<module>/build/reports/bom.json`), so you can answer "what is inside this dependency?"
without unpacking it.

**No Baseline Profile is shipped, deliberately.** Grant's entire startup contribution is one
`ContentProvider.onCreate()` that registers an activity-lifecycle callback; there is no hot
path for AOT compilation to improve. The permission request path is user-triggered and gated
behind a system dialog, where JIT versus AOT is not measurable. A profile here would be
ceremony, not speed.

## Supported permissions

23 built-in permissions across Camera, Microphone, Gallery (read and save-only), Storage, Location, Notifications, Bluetooth (combined, or scan-only / connect-only separately), Contacts, Calendar, Motion, Exact Alarms, Nearby Wi-Fi, Local Network, and App Tracking Transparency — anything else via `RawPermission`.

<details>
<summary><strong>Full permission matrix</strong></summary>

| Permission | Android | iOS | Notes |
| :--- | :---: | :---: | :--- |
| Camera | ✅ | ✅ | iOS main-thread safe + deadlock fix |
| Microphone | ✅ | ✅ | Shares the AVFoundation handler with Camera |
| Gallery (full) | ✅ | ✅ | Android 14+ partial access → `PARTIAL_GRANTED` |
| Gallery (images only) | ✅ | ✅ | `AppGrant.GALLERY_IMAGES_ONLY` |
| Gallery (video only) | ✅ | ✅ | `AppGrant.GALLERY_VIDEO_ONLY` |
| Gallery (save only) | ✅ | ✅ | `AppGrant.GALLERY_ADD_ONLY` — no prompt at all on Android 10+; `PHAccessLevelAddOnly` on iOS |
| Storage (legacy) | ✅ | ✅ | Pre-API 33 fallback |
| Location (when in use) | ✅ | ✅ | GPS service check; "Approximate"-only → `PARTIAL_GRANTED` |
| Location (always) | ✅ | ✅ | Android two-step background flow handled |
| Notifications | ✅ | ✅ | Android 13+ and legacy flows |
| Bluetooth | ✅ | ✅ | Service status check + Scan/Connect |
| Bluetooth Advertise | ✅ | ✅ | `AppGrant.BLUETOOTH_ADVERTISE` |
| Contacts (full) | ✅ | ✅ | Read + write access |
| Contacts (read-only) | ✅ | ✅ | `AppGrant.READ_CONTACTS` |
| Calendar (full) | ✅ | ✅ | iOS 17+ `FullAccess` / `WriteOnly` mapped correctly |
| Calendar (read-only) | ✅ | ✅ | `AppGrant.READ_CALENDAR` |
| Motion / Activity | ✅ | ✅ | Simulator-aware (safe mock on Simulator) |
| Schedule Exact Alarm | ✅ | ✅ | Android 12+ `SCHEDULE_EXACT_ALARM` |
| Nearby Wi-Fi Devices | ✅ | ✅ | `NEARBY_WIFI_DEVICES` (API 33+); no-op on iOS |
| Local Network | ✅ | ✅ | Android 17+ `ACCESS_LOCAL_NETWORK`; no-op below API 37 and on iOS (OS auto-prompts) |
| App Tracking Transparency | ✅ | ✅ | `AppGrant.APP_TRACKING` — iOS `ATTrackingManager` (requires the optional `grant-tracking` module); Android has no runtime gate for cross-app tracking, so this honestly reports `GRANTED` rather than prompting |

**Service checks (`ServiceType`)**

| Service | Android | iOS |
| :--- | :---: | :---: |
| GPS / Location | ✅ | ✅ |
| Bluetooth | ✅ | ✅ |
| Wi-Fi | ✅ | ✅ |
| NFC | ✅ | — |
| Camera hardware | ✅ | ✅ |
| Health Connect / HealthKit | ✅ | ✅ |

</details>

## Documentation

| Guide | Description |
| :--- | :--- |
| [Quick start](docs/getting-started/quick-start.md) | Request your first permission in five minutes |
| [Architecture](docs/grant-core/ARCHITECTURE.md) | Concurrency, state machines, and the mutex flow |
| [iOS setup](docs/platform-specific/ios/info-plist.md) | `Info.plist` configuration — read before shipping |
| [Migration guide](docs/MIGRATION_GUIDE.md) | Upgrading to 2.4.0 (and from v1.x → 2.x) |
| [Service checking](docs/grant-core/SERVICES.md) | Combining permission and hardware service checks |
| [Support policy](SUPPORT.md) | Versioning, supported versions, platform support, and what Grant will not do |
| [Manual injection](docs/MANUAL_INJECTION.md) | Using Grant without a DI framework |
| [Android reliability](docs/FIX_DEAD_CLICK_ANDROID.md) | How Grant fixes "dead clicks" on Android |
| [Best practices](docs/BEST_PRACTICES.md) | Patterns for production apps |

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) and the
[Code of Conduct](CODE_OF_CONDUCT.md). New to the codebase? Start with an issue labeled
[`good first issue`](https://github.com/brewkits/Grant/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).
Run `./gradlew :grant-core:allTests` before submitting a PR.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

<div align="center">
<sub>Built by BrewKits</sub>
</div>
