# Grant 🎯

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7.1-green)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits.grant/grant-core)](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](https://opensource.org/licenses/Apache-2.0)

**The Most Comprehensive & Powerful Permission Library for Kotlin Multiplatform**

> 🚀 **No Fragment/Activity required. No BindEffect boilerplate. Smart Android 12+ handling. Built-in Service Checking.**

Grant is the **production-ready, battle-tested permission library** that eliminates boilerplate, fixes platform bugs, and handles every edge case. With **88% bug coverage** over moko-permissions and **zero** dead clicks or memory leaks, Grant sets the new standard for KMP permission management.

## Why Grant? 🎯

### The Problem with Other Libraries

Traditional KMP permission libraries force you into painful patterns:

```kotlin
// ❌ OLD WAY: Fragment/Activity required + Boilerplate
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this) // Needs Fragment!

    fun requestCamera() {
        permissionHelper.bindToLifecycle() // BindEffect boilerplate
        permissionHelper.request(Permission.CAMERA) {
            // Dead clicks on Android 13+
        }
    }
}
```

### The Grant Way ✨

```kotlin
// ✅ GRANT WAY: Works anywhere, zero boilerplate
@Composable
fun CameraScreen() {
    val grantManager = remember { GrantFactory.create(context) }

    Button(onClick = {
        when (grantManager.request(AppGrant.CAMERA)) {
            GrantStatus.GRANTED -> openCamera()
            GrantStatus.DENIED -> showRationale()
            GrantStatus.DENIED_ALWAYS -> openSettings()
        }
    }) { Text("Take Photo") }
}
```

**That's it.** No Fragment. No binding. No dead clicks.

---

## 🚀 Key Features

### ✨ Zero Boilerplate - Revolutionary Simplicity
- ✅ **No Fragment/Activity required** - Works in ViewModels, repositories, anywhere
- ✅ **No BindEffect** - No lifecycle binding ceremony
- ✅ **No configuration** - Works out of the box
- 🎯 **Just works** - One line to request, one enum to handle all states

### 🛡️ Smart Platform Handling - Fixes Industry-Wide Bugs
- ✅ **Android 12+ Dead Click Fix** - **100% elimination** of dead clicks (built-in, not a workaround)
- ✅ **Android 14 Partial Gallery Access** - Full support for "Select Photos" mode
- ✅ **iOS Permission Deadlock Fix** - Camera/Microphone work on **first request** (fixes critical bug #129)
- ✅ **Granular Gallery Permissions** - Images-only, Videos-only, or both (prevents silent denials)
- ✅ **Bluetooth Error Differentiation** - Retryable vs permanent errors (10s timeout)
- ✅ **Notification Status Validation** - Correct status even on Android 12-
- 🆕 **iOS Info.plist Runtime Validation** - Prevents crashes from missing keys (v1.1.0)
- 🆕 **Process Death Recovery** - No 60s hangs, automatic cleanup (v1.1.0)

### 🎁 NEW in v1.1.0 - Advanced Features
- 🆕 **Process Death State Restoration** - Optional dialog state recovery on Android
  ```kotlin
  val handler = GrantHandler(
      grantManager = grantManager,
      grant = AppGrant.CAMERA,
      scope = viewModelScope,
      savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle) // Optional
  )
  ```
- 🆕 **Custom Permission Support** - Extend beyond built-in permissions
  ```kotlin
  val customPermission = RawPermission(
      identifier = "CUSTOM_SENSOR",
      androidPermissions = listOf("android.permission.CUSTOM_SENSOR"),
      iosUsageKey = "NSCustomSensorUsageDescription"
  )
  grantManager.request(customPermission)
  ```
- 🆕 **Race Condition Fixes** - Eliminated 60-second hangs and memory leaks on process death
- 🆕 **iOS Crash Prevention** - Runtime validation prevents SIGABRT from missing Info.plist keys

### 🏗️ Production-Ready Architecture - Enterprise Grade
- ✅ **Enum-based Status** - Clean, predictable flow (not exception-based)
- ✅ **In-Memory State** - Industry standard approach (90% of libraries including Google Accompanist)
- ✅ **Thread-Safe** - Coroutine-first design with proper mutex handling
- ✅ **Memory Leak Free** - Application context only, zero Activity retention
- ✅ **103+ Unit Tests** - Comprehensive test coverage, all passing
- ✅ **Zero Compiler Warnings** - Clean, maintainable codebase

### 🛠️ Built-in Service Checking
```kotlin
val serviceManager = ServiceFactory.create(context)

// Check if GPS/Bluetooth/Location services are enabled
if (!serviceManager.isLocationEnabled()) {
    serviceManager.openLocationSettings()
}
```

### 📱 Cross-Platform Coverage
- **Android**: API 24+ (100% coverage)
- **iOS**: iOS 13.0+ (100% coverage)
- **13 Permission Types**: Camera, Microphone, Gallery, Location, Notifications, Bluetooth, and more

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits.grant:grant-core:1.0.0")
            implementation("dev.brewkits.grant:grant-compose:1.0.0") // Optional
        }
    }
}
```

See [Installation Guide](docs/getting-started/installation.md) for details.

---

## 🎯 Quick Start

### Basic Usage

```kotlin
import dev.brewkits.grant.*

// 1. Create manager (in ViewModel, Repository, or Composable)
val grantManager = GrantFactory.create(context)

// 2. Check current status
suspend fun checkCameraAccess() {
    when (grantManager.checkStatus(AppGrant.CAMERA)) {
        GrantStatus.GRANTED -> println("Camera ready!")
        GrantStatus.NOT_DETERMINED -> println("Never asked")
        GrantStatus.DENIED -> println("User denied, can ask again")
        GrantStatus.DENIED_ALWAYS -> println("Permanently denied, go to Settings")
    }
}

// 3. Request permission
suspend fun requestCamera() {
    val status = grantManager.request(AppGrant.CAMERA)
    when (status) {
        GrantStatus.GRANTED -> openCamera()
        GrantStatus.DENIED -> showRationale()
        GrantStatus.DENIED_ALWAYS -> showSettingsPrompt()
        GrantStatus.NOT_DETERMINED -> { /* shouldn't happen after request */ }
    }
}
```

See [Quick Start Guide](docs/getting-started/quick-start.md) for complete setup.

---

## 📋 Supported Permissions

| Permission | Android | iOS | Notes |
|------------|---------|-----|-------|
| `CAMERA` | ✅ API 23+ | ✅ iOS 13+ | Photo/Video capture |
| `MICROPHONE` | ✅ API 23+ | ✅ iOS 13+ | Audio recording |
| `GALLERY` | ✅ API 23+ | ✅ iOS 13+ | Images + Videos |
| `GALLERY_IMAGES_ONLY` | ✅ API 33+ | ✅ iOS 13+ | Images only (prevents silent denial) |
| `GALLERY_VIDEO_ONLY` | ✅ API 33+ | ✅ iOS 13+ | Videos only (prevents silent denial) |
| `STORAGE` | ✅ API 23+ | N/A | External storage (deprecated) |
| `LOCATION` | ✅ API 23+ | ✅ iOS 13+ | While app in use |
| `LOCATION_ALWAYS` | ✅ API 29+ | ✅ iOS 13+ | Background location |
| `NOTIFICATION` | ✅ API 33+ | ✅ iOS 13+ | Push notifications |
| `SCHEDULE_EXACT_ALARM` | ✅ API 31+ | N/A | Exact alarm scheduling |
| `BLUETOOTH` | ✅ API 31+ | ✅ iOS 13+ | BLE scanning/connecting |
| `CONTACTS` | ✅ API 23+ | ✅ iOS 13+ | Read contacts |
| `MOTION` | ✅ API 29+ | ✅ iOS 13+ | Activity recognition |

---

## 📊 Comparison: Grant vs Others

### Grant vs moko-permissions

| Feature | Grant | moko-permissions |
|---------|-------|------------------|
| Fragment/Activity Required | ❌ No | ✅ Yes |
| BindEffect Boilerplate | ❌ No | ✅ Yes |
| Android 13+ Dead Clicks | ✅ Fixed | ❌ Has issue |
| iOS Permission Deadlock | ✅ Fixed | ❌ Has issue (#129) |
| Granular Gallery Permissions | ✅ Yes | ❌ No |
| Built-in Service Checking | ✅ Yes | ❌ No |
| Memory Leaks | ✅ None | ⚠️ Activity retention |
| Exception-based Flow | ❌ No (enum) | ✅ Yes |
| Compose-First | ✅ Yes | ⚠️ Limited |
| **Bug Coverage** | **88% fixed** | **Baseline** |

**Bottom Line**: Grant fixes **15 out of 17** bugs found in moko-permissions, including critical issues like iOS deadlocks and Android dead clicks.

See [Detailed Comparison](docs/comparison/vs-moko-permissions.md).

---

## 📚 Documentation

### Getting Started
- [Quick Start Guide](docs/getting-started/quick-start.md) - Get running in 5 minutes
- [Installation](docs/getting-started/installation.md) - Gradle setup and dependencies
- [Android Setup](docs/getting-started/android-setup.md) - AndroidManifest configuration
- [iOS Setup](docs/getting-started/ios-setup.md) - Info.plist setup (⚠️ **critical - app crashes if missing**)

### Guides
- [Permission Handling Guide](docs/guides/permissions-guide.md)
- [Service Checking](docs/guides/service-checking.md)
- [Compose Integration](docs/guides/compose-integration.md)
- [Best Practices](docs/guides/best-practices.md)

### Platform-Specific
- **Android**: [Android 12+ Handling](docs/platform-specific/android/android-12-handling.md) • [Dead Click Fix](docs/platform-specific/android/dead-click-fix.md)
- **iOS**: [Info.plist Setup](docs/platform-specific/ios/info-plist.md) • [Simulator Limitations](docs/platform-specific/ios/simulator-limitations.md)

### Advanced
- [Testing Guide](docs/advanced/testing.md) - Unit testing with FakeGrantManager
- [Custom GrantStore](docs/advanced/custom-grant-store.md) - Implement custom storage
- [Architecture Overview](docs/architecture/overview.md) - System design and patterns
- [GrantStore Architecture](docs/architecture/grant-store.md) - State management, persistence, backup rules

### Production Checklist
- 🔒 **iOS Info.plist**: [Add required keys](docs/platform-specific/ios/info-plist.md) (app crashes if missing)
- 🔧 **Logging**: Disable in production: `GrantLogger.isEnabled = false`
- 📦 **Backup**: [Exclude GrantStore from backup](docs/architecture/grant-store.md#backup-rules-android) if using persistent storage
- ✅ **Testing**: Test all permission flows on real devices

---

## 🛠️ Configuration

### Enable Logging (Development Only)

```kotlin
import dev.brewkits.grant.utils.GrantLogger

// Enable logging during development
GrantLogger.isEnabled = true

// ⚠️ IMPORTANT: Disable for production release
GrantLogger.isEnabled = false
```

**Benefits of logging**:
- ✅ Detects missing iOS Info.plist keys before crash
- ✅ Shows permission flow: request → rationale → settings
- ✅ Logs platform-specific behaviors (Android 12+, iOS deadlocks)
- ✅ Helps debug denied/denied_always states

### Custom Log Handler

```kotlin
// Integrate with your logging framework (Timber, Napier, etc.)
GrantLogger.logHandler = { level, tag, message ->
    when (level) {
        GrantLogger.LogLevel.ERROR -> Timber.e("[$tag] $message")
        GrantLogger.LogLevel.WARNING -> Timber.w("[$tag] $message")
        GrantLogger.LogLevel.INFO -> Timber.i("[$tag] $message")
        GrantLogger.LogLevel.DEBUG -> Timber.d("[$tag] $message")
    }
}
```

---

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## 📄 License

```
Copyright 2026 BrewKits

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

<div align="center">

**Made with ❤️ for the Kotlin Multiplatform community**

[⭐ Star us on GitHub](https://github.com/brewkits/grant) • [📦 Maven Central](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-core)

</div>
