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

[**Explore Documentation**](docs/README.md) • [**Quick Start**](#-quick-start) • [**Why Grant?**](#-why-grant) • [**Demo App**](#-demo)

</div>

---

## 🚀 Killer Features

- **🎯 Pure Logic-First API** — Works anywhere: ViewModels, Repositories, or Composables. **No Activity or Fragment references required.**
- **🛡️ iOS Crash-Guard** — Automatically validates `Info.plist` keys before requesting, preventing the dreaded `SIGABRT` production crashes.
- **🔄 Android Process-Death Resilience** — The only library that handles system-initiated process death gracefully with zero timeouts.
- **⚡ iOS Deadlock Fix** — Built-in protection against the infamous Camera/Microphone first-request deadlock.
- **📦 17+ Native Permissions** — Deep, native integration for Camera, Gallery (Partial access!), Location, Bluetooth, Motion, and more.
- **🛠️ Service Intelligence** — Don't just check permissions; check if services (GPS, Bluetooth) are actually enabled.
- **🧩 Custom Extensibility** — Use `RawPermission` to support new OS versions (Android 15+, iOS 18+) instantly without library updates.

---

## 💎 The "Grant" Experience

### 1️⃣ Define your logic (Logic Layer)
```kotlin
class CameraViewModel(private val grantManager: GrantManager) : ViewModel() {
    // 💡 Works in pure common code!
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun capturePhoto() {
        cameraGrant.request {
            // ✅ Only runs when permission is FULLY granted
            cameraEngine.startCapture()
        }
    }
}
```

### 2️⃣ Drop in the UI (Presentation Layer)
```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // 💡 Handles Rationale, Denied, and Settings dialogs automatically
    GrantDialog(handler = viewModel.cameraGrant)

    IconButton(onClick = { viewModel.capturePhoto() }) {
        Icon(Icons.Default.Camera, contentDescription = "Capture")
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
| **Android Deadlock Fix** | ✅ | ❌ | ❌ |
| **Process Death Recovery** | **Native** | ❌ | Manual |
| **Service Checks (GPS/BT)** | ✅ | ❌ | ❌ |
| **Android 14 Partial Access** | ✅ | Partial | ✅ |
| **iOS Deadlock Fix** | ✅ | ❌ | N/A |
| **Custom Permissions** | ✅ | Limited | Limited |

---

## 🗺️ Platform Support & Coverage

| Permission | Android | iOS | Production Notes |
| :--- | :---: | :---: | :--- |
| **Camera** | ✅ | ✅ | iOS Main-thread safe + Deadlock fix |
| **Gallery** | ✅ | ✅ | Full Android 14+ Partial Access support |
| **Location** | ✅ | ✅ | Intelligent GPS service check included |
| **Notifications** | ✅ | ✅ | Handles Android 13+ and legacy flows |
| **Bluetooth** | ✅ | ✅ | Service status check + Scan/Adv support |
| **Contacts** | ✅ | ✅ | Support for read-only vs full access |
| **Calendar** | ✅ | ✅ | iOS 17+ Privacy Manifest compliant |
| **Motion** | ✅ | ✅ | Simulator-ready (returns mock on Sim) |

---

## 📦 Installation

Add the ultimate permission engine to your `commonMain`:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:1.2.1")
            implementation("dev.brewkits:grant-compose:1.2.1") // Optional UI pack
        }
    }
}
```

> [!IMPORTANT]
> For projects targeting **Web (JS)** or **Desktop (JVM)**, please use an intermediate `mobileMain` source set. [Read the Guide](docs/DEPENDENCY_MANAGEMENT.md).

---

## 📖 Deep Dives

*   [**Architecture Guide**](docs/grant-core/ARCHITECTURE.md) - How we handle concurrency and state.
*   [**iOS Setup**](docs/platform-specific/ios/info-plist.md) - Critical Info.plist configuration.
*   [**Android Reliability**](docs/FIX_DEAD_CLICK_ANDROID.md) - How we fix "Dead Clicks".
*   [**Service Checking**](docs/grant-core/SERVICES.md) - Beyond just permissions.

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
