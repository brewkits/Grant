<div align="center">

<img src="assets/logo.svg" height="128" alt="Grant Logo" />

# Grant: Robust Permission Management for KMP

**Production-ready, type-safe permission handling for Kotlin Multiplatform — handling the complex edge cases of Android and iOS flows.**

[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/grant-core?color=7F52FF&label=Maven%20Central&style=for-the-badge)](https://central.sonatype.com/artifact/dev.brewkits/grant-core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-lightgrey?style=for-the-badge)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)

---

### ⚡ Zero Boilerplate. Zero Lifecycle Binding. Zero Headache.

Grant is not just another permission library. It is a **production-hardened engine** designed to handle complex edge cases that lead to crashes and hangs in other solutions. Built for professionals who demand absolute reliability.

[**Explore Documentation**](docs/README.md) • [**Quick Start**](docs/getting-started/quick-start.md) • [**Why Grant?**](#-why-grant) • [**Demo App**](docs/demo/DEMO_GUIDE.md)

</div>

---

## 🚀 Killer Features

- **🎯 Pure Logic-First API** — Works anywhere: ViewModels, Repositories, or Composables. **No Activity or Fragment references required.**
- **🍎 iOS Framework Isolation** — Each permission type is isolated to its own handler, preventing unused Apple frameworks (Location, Bluetooth, Motion, etc.) from being linked into your binary. No more phantom `NSUsageDescription` requirements.
- **🛡️ iOS Crash-Guard** — Automatically validates `Info.plist` keys before requesting, preventing the dreaded `SIGABRT` production crashes.
- **🔄 Android Process-Death Resilience** — The only library that handles system-initiated process death gracefully with zero timeouts (via `SavedStateHandle`).
- **⚡ Reentrant Locking** — Custom `ReentrantMutex` prevents deadlocks in complex nested permission flows.
- **📦 18 Native Permissions** — Deep, native integration for Camera, Gallery, Location, Bluetooth, NEARBY_WIFI_DEVICES, and more.
- **🎨 Modern M3 UI** — Out-of-the-box support for Material 3 `BasicAlertDialog` in the Compose module.
- **🧩 Custom Extensibility** — Register your own iOS handlers via `IosPermissionHandlerRegistry` or use `RawPermission`.
- **🧪 Ultra-Robust Testing** — **800+ automated tests** covering every platform edge case, state invariant, and UI interaction. 100% pass rate.

---

## 💎 The "Grant" Experience

### 1️⃣ Define your logic (Logic Layer)
```kotlin
class CameraViewModel(private val grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope,
        eventListener = object : GrantEventListener {
            override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                // Analytics: Track funnel conversion
                analytics.track("camera_permission_granted")
            }
        }
    )

    // Option A: Suspend function (Modern Coroutines)
    suspend fun startCapture() {
        val status = cameraGrant.requestSuspend()
        if (status == GrantStatus.GRANTED) {
            cameraEngine.start()
        }
    }

    // Option B: Flow-based (Reactive)
    val captureFlow = cameraGrant.requestFlow()
        .filter { it == GrantStatus.GRANTED }
        .onEach { cameraEngine.start() }
}
```

### 2️⃣ Orchestrate Sequential Flows (GrantFlow DSL)
```kotlin
val scanFlow = grantFlow {
    // 1. First, we need Bluetooth
    val btStatus = bluetoothHandler.requestSuspend()
    
    // 2. If granted, we need Location (for scanning on some Android versions)
    if (btStatus == GrantStatus.GRANTED) {
        locationHandler.requestSuspend()
    }
}
```

### 3️⃣ Drop in the UI (Presentation Layer)
```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // Automatically uses Material 3 BasicAlertDialog for a modern look
    GrantDialog(handler = viewModel.cameraGrant)

    Button(onClick = { viewModel.viewModelScope.launch { viewModel.startCapture() } }) {
        Text("Start Camera")
    }
}
```

### 🏆 Best Practice: The Full Readiness Check (Logic + Hardware)
Permission is only half the battle. In production, you also need to check if the hardware service (GPS, Bluetooth) is actually enabled.

```kotlin
// Use GrantAndServiceChecker to combine both worlds
class LocationViewModel(
    private val checker: GrantAndServiceChecker,
    private val grantManager: GrantManager
) : ViewModel() {

    fun startTracking() {
        viewModelScope.launch {
            when (val status = checker.checkLocationReady()) {
                LocationReadyStatus.Ready          -> sensor.start()
                LocationReadyStatus.ServiceDisabled -> _uiState.showEnableGPS()
                LocationReadyStatus.GrantDenied    -> requestPermission()
                LocationReadyStatus.BothRequired   -> _uiState.showTotalFailure()
            }
        }
    }
}
```

---

## ⚔️ Why Grant?

Most KMP permission libraries are simple wrappers around native APIs. Grant is an **Architectural Solution**.

| Feature | **Grant** | moko-permissions | accompanist-permissions |
| :--- | :---: | :---: | :---: |
| **No Lifecycle Binding** | ✅ | ❌ (needs BindEffect) | ❌ (needs Activity) |
| **ViewModel Support** | **Full** | Partial | ❌ |
| **iOS Crash Prevention** | ✅ | ❌ | ❌ |
| **iOS Framework Isolation** | ✅ | ❌ | N/A |
| **Android Deadlock Fix** | ✅ | ❌ | ❌ |
| **Process Death Recovery** | **Native** | ❌ | Manual |
| **Service Checks (GPS/BT/Health)** | ✅ | ❌ | ❌ |
| **Android 14 Partial Access** | ✅ | Partial | ✅ |
| **Custom Permissions** | ✅ | Limited | Limited |

---

## 🗺️ Platform Support & Coverage

| Permission | Android | iOS | Notes |
| :--- | :---: | :---: | :--- |
| **Camera** | ✅ | ✅ | iOS main-thread safe + deadlock fix |
| **Microphone** | ✅ | ✅ | Shares AVFoundation handler with Camera |
| **Gallery (full)** | ✅ | ✅ | Android 14+ partial access (`PARTIAL_GRANTED`) |
| **Gallery (images only)** | ✅ | ✅ | `AppGrant.GALLERY_IMAGES_ONLY` |
| **Gallery (video only)** | ✅ | ✅ | `AppGrant.GALLERY_VIDEO_ONLY` |
| **Storage (legacy)** | ✅ | ✅ | Pre-API 33 fallback |
| **Location (when in use)** | ✅ | ✅ | Intelligent GPS service check included |
| **Location (always)** | ✅ | ✅ | Android 2-step background flow handled |
| **Notifications** | ✅ | ✅ | Android 13+ and legacy flows |
| **Bluetooth** | ✅ | ✅ | Service status check + Scan/Connect |
| **Bluetooth Advertise** | ✅ | ✅ | `AppGrant.BLUETOOTH_ADVERTISE` |
| **Contacts (full)** | ✅ | ✅ | Read + Write access |
| **Contacts (read-only)** | ✅ | ✅ | `AppGrant.READ_CONTACTS` |
| **Calendar (full)** | ✅ | ✅ | iOS 17+ `FullAccess` / `WriteOnly` mapped correctly |
| **Calendar (read-only)** | ✅ | ✅ | `AppGrant.READ_CALENDAR` |
| **Motion / Activity** | ✅ | ✅ | Simulator-aware (safe mock on Simulator) |
| **Schedule Exact Alarm** | ✅ | ✅ | Android 12+ `SCHEDULE_EXACT_ALARM` |

### Service Checks (`ServiceType`)

| Service | Android | iOS |
| :--- | :---: | :---: |
| **GPS / Location** | ✅ | ✅ |
| **Bluetooth** | ✅ | ✅ |
| **Wi-Fi** | ✅ | ✅ |
| **NFC** | ✅ | — |
| **Camera hardware** | ✅ | ✅ |
| **Health Connect / HealthKit** | ✅ | ✅ |

---

## 📦 Installation

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:2.2.0")
            implementation("dev.brewkits:grant-compose:2.2.0")         // Optional: Compose dialogs
            implementation("dev.brewkits:grant-core-koin:2.2.0")       // Optional: Koin DI support

            // Optional: add only the permission modules you actually use on iOS.
            // Omitting a module means its iOS framework is never linked — no phantom
            // NSUsageDescription keys, no App Store rejections.
            implementation("dev.brewkits:grant-contacts:2.2.0")        // Optional: Contacts (iOS CNContactStore)
            implementation("dev.brewkits:grant-calendar:2.2.0")        // Optional: Calendar (iOS EventKit)
            implementation("dev.brewkits:grant-motion:2.2.0")          // Optional: Motion (iOS CoreMotion)
            implementation("dev.brewkits:grant-bluetooth:2.2.0")       // Optional: Bluetooth (iOS CoreBluetooth)
            implementation("dev.brewkits:grant-location-always:2.2.0") // Optional: background "always" location (iOS requestAlwaysAuthorization)
        }
    }
}
```

**iOS-only step**: call `initialize()` once at app startup for each optional module you added:

```swift
// iOS — AppDelegate / @main entry point
GrantContacts.shared.initialize()        // if you added grant-contacts
GrantCalendar.shared.initialize()        // if you added grant-calendar
GrantMotion.shared.initialize()          // if you added grant-motion
GrantBluetooth.shared.initialize()       // if you added grant-bluetooth
GrantLocationAlways.shared.initialize()  // if you added grant-location-always
```

**Android — no setup required**: Grant ships a self-contained transparent `Activity`, so `request()` opens the system dialog from anywhere (ViewModel, Repository, etc.) with **zero lifecycle binding**. You do not need to register anything.

> [!TIP]
> **Optional optimization.** If you prefer to drive the system dialog through your own Compose/Activity `ActivityResultLauncher` (no extra transparent Activity launch), register one once via `grantManager.setLauncher(...)`. When a launcher is registered Grant uses it; otherwise it automatically falls back to the built-in transparent Activity. Both paths show the same system dialog.

> [!IMPORTANT]
> For projects targeting **Web (JS)** or **Desktop (JVM)**, use an intermediate `mobileMain` source set to avoid linking iOS/Android dependencies on unsupported platforms. [Read the Guide](docs/DEPENDENCY_MANAGEMENT.md).

> [!NOTE]
> **Migrating from v1.x?** Contacts, Calendar, Motion, Bluetooth, and background ("always") Location permissions are now opt-in modules. Add the corresponding artifact and call `initialize()` on iOS. Android behavior is unchanged — no code changes required on Android. See the [Migration Guide](docs/MIGRATION_GUIDE.md).

---

## 📖 Deep Dives

| Guide | Description |
| :--- | :--- |
| [Architecture](docs/grant-core/ARCHITECTURE.md) | How concurrency, state machines, and the mutex flow work |
| [iOS Setup](docs/platform-specific/ios/info-plist.md) | Critical `Info.plist` configuration — read before shipping |
| [Migration Guide](docs/MIGRATION_GUIDE.md) | Upgrading from v1.x to v2.2.0 |
| [Service Checking](docs/grant-core/SERVICES.md) | Combining permission + hardware service checks |
| [Manual Injection](docs/MANUAL_INJECTION.md) | Using Grant without any DI framework |
| [Android Reliability](docs/FIX_DEAD_CLICK_ANDROID.md) | How we fix "Dead Clicks" on Android |
| [Best Practices](docs/BEST_PRACTICES.md) | Patterns for production apps |

---

## 🤝 Contributing

We are on a mission to make permissions a "solved problem" for KMP. Join us!

1. Check out [CONTRIBUTING.md](CONTRIBUTING.md).
2. Run `./gradlew :grant-core:allTests` to ensure stability.
3. Submit your PR.

---

## ⚖️ License

Grant is licensed under the **Apache License 2.0**. See [LICENSE](LICENSE) for details.

<div align="center">
  <sub>Built with ❤️ by BrewKits</sub>
</div>
