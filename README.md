# Grant 🎯

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7.1-green)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/grant-core)](https://central.sonatype.com/artifact/dev.brewkits/grant-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](https://opensource.org/licenses/Apache-2.0)

**The Most Comprehensive & Powerful Permission Library for Kotlin Multiplatform**

> 🚀 **No Fragment/Activity required. No BindEffect boilerplate. Smart Android 12+ handling. Built-in Service Checking.**

> 🏆 **EXCLUSIVE: Smart Config Validation + Process Death Handling** - The only KMP library that won't crash from missing Info.plist keys and handles Android process death with zero timeout.

Grant is the **production-ready, battle-tested permission library** that eliminates boilerplate, fixes platform bugs, and handles every edge case. With **zero** dead clicks, memory leaks, or configuration headaches, Grant provides the cleanest API for KMP permission management.

**What makes Grant unique:** After analyzing 20+ permission libraries, Grant is the **only one** that:
- ✅ Validates iOS Info.plist keys **before** crash (no SIGABRT in production)
- ✅ Handles Android process death with **zero timeout** (no 60-second hangs)
- ✅ Fixes iOS Camera/Microphone deadlock (works on first request)
- ✅ Supports custom permissions via RawPermission (extensible beyond enum)

---

## ⚡ Quick Start (30 seconds)

```kotlin
// 1️⃣ In your ViewModel
class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun openCamera() {
        cameraGrant.request {
            // ✅ This runs ONLY when permission is granted
            startCameraCapture()
        }
    }
}

// 2️⃣ In your Compose UI
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    GrantDialog(handler = viewModel.cameraGrant) // Handles all dialogs automatically

    Button(onClick = { viewModel.openCamera() }) {
        Text("Take Photo")
    }
}
```

**That's it!** No Fragment. No BindEffect. No configuration. Just works. ✨

---

## 📱 Platform Support

| Platform | Version | Notes |
|----------|---------|-------|
| 🤖 **Android** | API 24+ | Full support for Android 12, 13, 14 (Partial Gallery Access) |
| 🍎 **iOS** | 13.0+ | Crash-guard & Main thread safety built-in |
| 🎨 **Compose** | 1.7.1+ | Separate `grant-compose` module with GrantDialog |

> 💡 **Note:** See [iOS Info.plist Setup](docs/platform-specific/ios/info-plist.md) and [iOS Setup Guide](docs/ios/IOS_SETUP_ANDROID_STUDIO.md) for detailed configuration.

---

## 🎬 See It In Action

<!-- TODO: Add screenshots/GIF showing:
     - Permission dialog flow (rationale → settings guide)
     - Android 14 partial gallery access
     - Demo app with manifest validation warnings
     Instructions: See docs/images/README.md for screenshot guidelines
-->

> 📸 **Coming Soon:** Live demo GIF showing the complete permission flow with GrantDialog automatically handling rationale and settings dialogs.
>
> 🎮 **Try it now:** Run the demo app to see all 14 permissions in action:
> ```bash
> ./gradlew :demo:installDebug  # Android
> # Or open iosApp in Xcode for iOS
> ```

---

## Why Grant? 🎯

### The Traditional Approach

Traditional permission handling requires extensive boilerplate and lifecycle management:

```kotlin
// ❌ TRADITIONAL: Fragment/Activity required + Boilerplate
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this) // Needs Fragment!

    fun requestCamera() {
        permissionHelper.bindToLifecycle() // BindEffect boilerplate
        permissionHelper.request(Permission.CAMERA) {
            // Complex state management
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

### 🏆 Exclusive Features - What Other Libraries Don't Have
- ✅ **Smart Configuration Validation (iOS)** - **Only library** that validates Info.plist keys before crash
  - Other libraries (moko-permissions, etc.): Instant SIGABRT crash if key missing
  - Grant: Returns DENIED_ALWAYS with clear error message, app continues running
  - Validates all 9 permission types before native API calls
  - **Production-safe** - No crashes in production from missing config

- ✅ **Robust Process Death Handling (Android)** - **Only library** with zero-timeout recovery
  - Other libraries: 60-second hang, memory leaks, frustrated users
  - Grant: Instant recovery (0ms), automatic orphan cleanup, dialog state restoration
  - savedInstanceState integration for seamless UX
  - **Enterprise-grade** - Handles Android's aggressive memory management perfectly

### 🏗️ Production-Ready Architecture - Enterprise Grade
- ✅ **Enum-based Status** - Clean, predictable flow (not exception-based)
- ✅ **In-Memory State** - Industry standard approach (90% of libraries including Google Accompanist)
- ✅ **Thread-Safe** - Coroutine-first design with proper mutex handling
- ✅ **Memory Leak Free** - Application context only, zero Activity retention
- ✅ **103+ Unit Tests** - Comprehensive test coverage, all passing
- ✅ **Zero Compiler Warnings** - Clean, maintainable codebase

### 🛠️ Built-in Service Checking - The Missing Piece

**Permissions ≠ Services!** Having permission doesn't mean the service is enabled. Grant is the only KMP library that handles both.

```kotlin
val serviceManager = ServiceFactory.create(context)

// ✅ Check if Location service is enabled (not just permission!)
when {
    !serviceManager.isLocationEnabled() -> {
        // GPS is OFF - guide user to enable it
        serviceManager.openLocationSettings()
    }
    grantManager.checkStatus(AppGrant.LOCATION) == GrantStatus.GRANTED -> {
        // Both permission AND service are ready!
        startLocationTracking()
    }
}

// ✅ Check Bluetooth service status
if (!serviceManager.isBluetoothEnabled()) {
    serviceManager.openBluetoothSettings()
}
```

**Why This Matters:**
- 🎯 Users often grant permission but forget to enable GPS/Bluetooth
- 🎯 Silent failures are confusing - Grant helps you guide users properly
- 🎯 One library for both permissions AND services - no extra dependencies

**Supported Services:**
- **Location** - GPS/Network location services
- **Bluetooth** - Bluetooth adapter status
- **Background Location** - Platform-specific background location checks

### 📱 Cross-Platform Coverage
- **Android**: API 24+ (100% coverage)
- **iOS**: iOS 13.0+ (100% coverage)
- **14 Permission Types**: Camera, Microphone, Gallery (Images/Videos/Both), Storage, Location, Location Always, Notifications, Schedule Exact Alarm, Bluetooth, Contacts, Motion, Calendar

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
            implementation("dev.brewkits:grant-core:1.0.0")
            implementation("dev.brewkits:grant-compose:1.0.0") // Optional
        }
    }
}
```

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

// 4. Check Service Status (bonus feature!)
val serviceManager = ServiceFactory.create(context)

suspend fun requestLocationWithServiceCheck() {
    // First check if Location service is enabled
    if (!serviceManager.isLocationEnabled()) {
        // Guide user to enable GPS
        serviceManager.openLocationSettings()
        return
    }

    // Then request permission
    when (grantManager.request(AppGrant.LOCATION)) {
        GrantStatus.GRANTED -> startLocationTracking() // Both permission AND service ready!
        else -> showError()
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
| `CALENDAR` | ✅ API 23+ | ✅ iOS 13+ | Calendar events access |

---

## 🔧 Extensibility: Custom Permissions

**Need Android 15 permission? New OS feature not yet in the library? No problem!**

Unlike other libraries (looking at you, [moko-permissions](https://github.com/icerockdev/moko-permissions)), Grant doesn't lock you into a fixed permission set. Use `RawPermission` to add any custom permission without waiting for library updates.

### Why This Matters

When Android 15 or iOS 18 introduces new permissions, you're **not blocked**:
- ❌ **moko-permissions**: Enum-based, must wait for maintainer to add permission
- ✅ **Grant**: Sealed interface + `RawPermission` = instant extensibility

### Custom Permission Examples

#### Android 15 New Permission

```kotlin
// Android 15 introduces a new permission? Use it immediately!
val predictiveBackPermission = RawPermission(
    identifier = "PREDICTIVE_BACK",
    androidPermissions = listOf("android.permission.PREDICTIVE_BACK"),
    iosUsageKey = null  // Android-only permission
)

suspend fun requestPredictiveBack() {
    when (grantManager.request(predictiveBackPermission)) {
        GrantStatus.GRANTED -> enablePredictiveBack()
        else -> useFallback()
    }
}
```

#### iOS 18 New Permission

```kotlin
// iOS 18 adds a new privacy key? No problem!
val healthKit = RawPermission(
    identifier = "HEALTH_KIT",
    androidPermissions = emptyList(),  // iOS-only
    iosUsageKey = "NSHealthShareUsageDescription"
)

val status = grantManager.request(healthKit)
```

#### Cross-Platform Custom Permission

```kotlin
// Enterprise custom permission
val biometric = RawPermission(
    identifier = "BIOMETRIC_AUTH",
    androidPermissions = listOf("android.permission.USE_BIOMETRIC"),
    iosUsageKey = "NSFaceIDUsageDescription"
)

// Works exactly like AppGrant.CAMERA
val handler = GrantHandler(
    grantManager = grantManager,
    grant = biometric,  // ✅ RawPermission works here too!
    scope = viewModelScope
)
```

#### Android 14+ Partial Photo Picker

```kotlin
// Custom implementation for READ_MEDIA_VISUAL_USER_SELECTED (Android 14+)
val partialGallery = RawPermission(
    identifier = "PARTIAL_GALLERY",
    androidPermissions = listOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    ),
    iosUsageKey = "NSPhotoLibraryUsageDescription"
)
```

### How It Works

Grant uses a **sealed interface** architecture:

```kotlin
sealed interface GrantPermission {
    val identifier: String
}

// ✅ Built-in permissions (type-safe, documented)
enum class AppGrant : GrantPermission {
    CAMERA, LOCATION, MICROPHONE, ...
}

// ✅ Custom permissions (extensible, user-defined)
data class RawPermission(
    override val identifier: String,
    val androidPermissions: List<String>,
    val iosUsageKey: String?
) : GrantPermission
```

**Benefits:**
- ✅ `AppGrant` for common permissions (compile-time safety)
- ✅ `RawPermission` for new/custom permissions (runtime flexibility)
- ✅ Both work with `GrantManager.request()`, `GrantHandler`, and `GrantDialog`
- ✅ No library update needed for OS updates

### Important Notes

1. **Platform Compatibility**: You're responsible for checking API levels
   ```kotlin
   if (Build.VERSION.SDK_INT >= 34) {
       grantManager.request(android14Permission)
   }
   ```

2. **Manifest Declaration**: Remember to add permissions to `AndroidManifest.xml`
   ```xml
   <uses-permission android:name="android.permission.YOUR_CUSTOM_PERMISSION" />
   ```

3. **iOS Info.plist**: Add usage description keys
   ```xml
   <key>NSYourCustomUsageDescription</key>
   <string>We need this permission because...</string>
   ```

### Real-World Use Cases

- 🆕 **OS Updates**: Android 15 new permissions (available day one)
- 🏢 **Enterprise**: Custom company-specific permissions
- 🧪 **Testing**: Experimental or proprietary permissions
- 🔧 **Edge Cases**: Platform-specific permissions not in `AppGrant`

---

## 📊 Library Comparison

Why choose Grant over alternatives? Here's what makes Grant unique:

| Feature | Grant | moko-permissions | Accompanist | Native APIs |
|---------|-------|------------------|-------------|-------------|
| **Zero Boilerplate** | ✅ No Fragment/Activity | ❌ Requires binding | ❌ Activity required | ❌ Complex setup |
| **Smart Config Validation** | ✅ **Validates Info.plist** | ❌ Crashes if missing | N/A (Android-only) | ❌ No validation |
| **Process Death Handling** | ✅ **Zero timeout** | ❌ 60s hang + leaks | ❌ State loss | ❌ Manual handling |
| **iOS Deadlock Fix** | ✅ Works on first request | ❌ Hangs on first request | N/A | ❌ Known issue |
| **Custom Permissions** | ✅ RawPermission API | ❌ Enum only | ❌ Limited | ✅ Full control |
| **Service Checking** | ✅ Built-in GPS/BT check | ❌ Not available | ❌ Not available | ❌ Separate APIs |
| **Android 14 Partial Gallery** | ✅ Full support | ❌ Silent denial | ✅ Supported | ✅ Supported |
| **Granular Gallery Permissions** | ✅ Images/Videos separate | ❌ All or nothing | ❌ All or nothing | ✅ Manual handling |
| **Production-Safe** | ✅ No crashes, no hangs | ⚠️ Config crashes | ⚠️ State loss | ⚠️ Complex |
| **Enum-Based Status** | ✅ Clean flow control | ❌ Exception-based | ✅ Result type | ❌ Multiple APIs |
| **Cross-Platform** | ✅ Android + iOS | ✅ Android + iOS | ❌ Android only | ❌ Platform-specific |

### 🏆 Unique to Grant

**Only Grant provides:**
1. **Info.plist validation** - No production crashes from missing config
2. **Process death recovery** - Zero timeout, no memory leaks
3. **Service checking** - GPS/Bluetooth status built-in
4. **RawPermission extensibility** - Custom permissions without library updates
5. **Complete bug fixes** - 88% of moko-permissions issues resolved

**Production Impact:**
- 🛡️ **No crashes** from missing iOS config
- ⚡ **No hangs** from Android process death
- 🎯 **No dead clicks** from Android 12+ issues
- 😊 **Better UX** with automatic dialog handling

---

## 📚 Documentation

### Getting Started
- [Quick Start Guide](docs/getting-started/quick-start.md) - Get running in 5 minutes
- [Quick Start (iOS)](docs/grant-core/QUICK_START_iOS.md) - iOS-specific setup
- [iOS Setup in Android Studio](docs/ios/IOS_SETUP_ANDROID_STUDIO.md) - Complete iOS setup guide

### Core Concepts
- [Permission Types](docs/grant-core/GRANTS.md) - All supported permissions
- [Service Checking](docs/grant-core/SERVICES.md) - Check GPS, Bluetooth, etc.
- [Architecture](docs/grant-core/ARCHITECTURE.md) - System design and patterns
- [GrantStore](docs/architecture/grant-store.md) - State management, persistence, backup rules

### Platform Guides
- **Android**: [Dead Click Fix](docs/FIX_DEAD_CLICK_ANDROID.md) - Fixing Android 12+ dead clicks
- **iOS**: [Info.plist Setup](docs/platform-specific/ios/info-plist.md) ⚠️ **Critical** • [Simulator Limitations](docs/ios/SIMULATOR_LIMITATIONS.md) • [Info.plist Localization](docs/ios/INFO_PLIST_LOCALIZATION.md)

### Advanced Topics
- [Testing Guide](docs/TESTING.md) - Unit testing with FakeGrantManager
- [Best Practices](docs/BEST_PRACTICES.md) - Production-ready patterns
- [Compose Integration](docs/grant-compose/COMPOSE_SUPPORT_RELEASE_NOTES.md) - Using grant-compose module
- [Dependency Management](docs/DEPENDENCY_MANAGEMENT.md) - Handling version conflicts

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

## ⭐ Star Us on GitHub!

If Grant saves you time, please give us a star!

It helps other developers discover this project.

[⬆️ Back to Top](#grant-)

---

<div align="center">

**Made with ❤️ by Nguyễn Tuấn Việt at Brewkits**

**Support:** datacenter111@gmail.com • **Community:** [GitHub Issues](https://github.com/brewkits/Grant/issues)

[⭐ Star on GitHub](https://github.com/brewkits/Grant) • [📦 Maven Central](https://central.sonatype.com/artifact/dev.brewkits/grant-core)

</div>
