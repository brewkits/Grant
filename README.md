<div align="center">

<img src="assets/logo.svg" height="120" alt="Grant logo" />

# Grant

**Type-safe permission management for Kotlin Multiplatform — Android & iOS**

[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/grant-core?style=flat-square&color=7F52FF&label=maven%20central)](https://central.sonatype.com/artifact/dev.brewkits/grant-core)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/platforms-android%20%7C%20ios-555?style=flat-square)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue?style=flat-square)](LICENSE)

[Documentation](docs/README.md) · [Quick start](docs/getting-started/quick-start.md) · [Why Grant?](#why-grant) · [Demo app](docs/demo/DEMO_GUIDE.md)

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

**Requirements:** Android 8.0+ (API 26) · iOS 13+ · Kotlin 2.x · JVM 17

## Features

- **Logic-first API** — works in ViewModels, repositories, or composables. No Activity or Fragment references, no lifecycle binding.
- **iOS framework isolation** — Contacts, Calendar, Motion, Bluetooth, and background location live in opt-in modules. Frameworks you don't use are never linked, so Apple's static scanner never demands phantom `NSUsageDescription` keys.
- **iOS crash guard** — validates `Info.plist` keys before requesting, turning the classic `SIGABRT` production crash into a clear error.
- **Android process-death recovery** — a request in flight survives system-initiated process death via `SavedStateHandle`, with no timeouts.
- **Deadlock-free by construction** — reentrant locking plus a `withTimeout` test policy that converts silent deadlocks into failing tests.
- **19 built-in permissions** — Camera, Gallery (incl. Android 14 partial access), Location (incl. "Approximate"-only), Bluetooth, Local Network (Android 17), and more — plus `RawPermission` for anything the library doesn't ship yet.
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
            implementation("dev.brewkits:grant-core:2.3.0")

            // Optional
            implementation("dev.brewkits:grant-compose:2.3.0")          // Compose dialogs (Material 3)
            implementation("dev.brewkits:grant-core-koin:2.3.0")        // Koin DI integration

            // Optional per-permission modules. Omitting a module means its iOS
            // framework is never linked — no phantom NSUsageDescription keys.
            implementation("dev.brewkits:grant-contacts:2.3.0")         // Contacts (iOS CNContactStore)
            implementation("dev.brewkits:grant-calendar:2.3.0")         // Calendar (iOS EventKit)
            implementation("dev.brewkits:grant-motion:2.3.0")           // Motion (iOS CoreMotion)
            implementation("dev.brewkits:grant-bluetooth:2.3.0")        // Bluetooth (iOS CoreBluetooth)
            implementation("dev.brewkits:grant-location-always:2.3.0")  // Background "always" location (iOS)
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
        eventListener = object : GrantEventListener {
            override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                analytics.track("camera_permission_granted")
            }
        }
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

## Supported permissions

19 built-in permissions across Camera, Microphone, Gallery, Storage, Location, Notifications, Bluetooth, Contacts, Calendar, Motion, Exact Alarms, Nearby Wi-Fi, and Local Network — anything else via `RawPermission`.

<details>
<summary><strong>Full permission matrix</strong></summary>

| Permission | Android | iOS | Notes |
| :--- | :---: | :---: | :--- |
| Camera | ✅ | ✅ | iOS main-thread safe + deadlock fix |
| Microphone | ✅ | ✅ | Shares the AVFoundation handler with Camera |
| Gallery (full) | ✅ | ✅ | Android 14+ partial access → `PARTIAL_GRANTED` |
| Gallery (images only) | ✅ | ✅ | `AppGrant.GALLERY_IMAGES_ONLY` |
| Gallery (video only) | ✅ | ✅ | `AppGrant.GALLERY_VIDEO_ONLY` |
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
| [Migration guide](docs/MIGRATION_GUIDE.md) | Upgrading to 2.3.0 (and from v1.x → 2.x) |
| [Service checking](docs/grant-core/SERVICES.md) | Combining permission and hardware service checks |
| [Manual injection](docs/MANUAL_INJECTION.md) | Using Grant without a DI framework |
| [Android reliability](docs/FIX_DEAD_CLICK_ANDROID.md) | How Grant fixes "dead clicks" on Android |
| [Best practices](docs/BEST_PRACTICES.md) | Patterns for production apps |

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Run `./gradlew :grant-core:allTests` before submitting a PR.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

<div align="center">
<sub>Built by BrewKits</sub>
</div>
