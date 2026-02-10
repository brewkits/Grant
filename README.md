# Grant 🎯

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7.1-green)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits/grant-core)](https://central.sonatype.com/artifact/dev.brewkits/grant-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](https://opensource.org/licenses/Apache-2.0)

**A Modern Permission Library for Kotlin Multiplatform**

Grant simplifies permission handling across Android and iOS with a clean, type-safe API. No Fragment/Activity required, no binding boilerplate, and built-in support for service checking (GPS, Bluetooth, etc.).

**Key Features:**
- Clean, enum-based API that works anywhere (ViewModels, repositories, Composables)
- iOS Info.plist validation to prevent crashes
- Android process death recovery without timeout
- Extensible design supporting custom permissions via RawPermission
- Built-in service status checking (Location, Bluetooth)

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

Simple and straightforward - no Fragment, no BindEffect, no manual configuration.

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

Simple, clean, and works anywhere.

---

## 🚀 Key Features

### Clean API Design
- **No Fragment/Activity required** - Works in ViewModels, repositories, or Composables
- **No lifecycle binding** - No BindEffect or manual lifecycle management
- **Enum-based status** - Type-safe, predictable flow control
- **Coroutine-first** - Async by default with suspend functions

### Platform-Specific Handling
- **Android 12+ Dead Click Fix** - Handles Android 12+ dead clicks automatically
- **Android 14 Partial Gallery Access** - Supports "Select Photos" mode
- **iOS Deadlock Prevention** - Fixes Camera/Microphone deadlock on first request
- **Granular Gallery Permissions** - Separate handling for images vs videos

### Production Safety
- **iOS Info.plist Validation** - Validates keys before calling native APIs
  - Prevents SIGABRT crashes from missing configuration
  - Returns DENIED_ALWAYS with error message instead of crashing

- **Android Process Death Recovery** - Handles process death gracefully
  - Instant recovery without timeout
  - Automatic orphan cleanup
  - Dialog state restoration via savedInstanceState

### Architecture
- **Thread-safe** - Proper mutex handling for concurrent requests
- **Memory efficient** - Application context only, no Activity retention
- **Well-tested** - 125+ unit tests with comprehensive coverage
- **Extensible** - Sealed interface design supports custom permissions

### Built-in Service Checking

Permissions don't guarantee services are enabled. Grant includes service status checking:

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

**Supported Services:**
- Location - GPS/Network location services
- Bluetooth - Bluetooth adapter status
- Background Location - Platform-specific checks

This helps you detect when users grant permission but forget to enable the required service.

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
            implementation("dev.brewkits:grant-core:1.0.1")
            implementation("dev.brewkits:grant-compose:1.0.1") // Optional
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

## 🔧 Custom Permissions

Grant supports custom permissions through the `RawPermission` API. This allows you to use new OS permissions or platform-specific features without waiting for library updates.

### When to Use Custom Permissions

- New OS versions (Android 15, iOS 18) introduce permissions not yet in `AppGrant`
- Platform-specific or experimental permissions
- Enterprise or company-specific permission requirements

### Examples

#### Android 15 Permission

```kotlin
// Use new Android permissions immediately
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

#### iOS 18 Permission

```kotlin
// Use new iOS permissions
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
    grant = biometric,  // RawPermission works seamlessly
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

// Built-in permissions (type-safe, documented)
enum class AppGrant : GrantPermission {
    CAMERA, LOCATION, MICROPHONE, ...
}

// Custom permissions (extensible, user-defined)
data class RawPermission(
    override val identifier: String,
    val androidPermissions: List<String>,
    val iosUsageKey: String?
) : GrantPermission
```

**Design:**
- `AppGrant` enum for common permissions (type-safe)
- `RawPermission` for custom permissions (flexible)
- Both work with all Grant APIs

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

### Use Cases

- New OS permissions (Android 15, iOS 18)
- Enterprise-specific permissions
- Experimental or proprietary features
- Platform-specific permissions

---

## 📊 Comparison

| Feature | Grant | Other KMP Libraries | Native APIs |
|---------|-------|---------------------|-------------|
| **No Fragment/Activity** | ✅ | Varies | ❌ |
| **Info.plist Validation** | ✅ | ❌ | ❌ |
| **Process Death Recovery** | ✅ | Limited | Manual |
| **Custom Permissions** | RawPermission | Limited | Full control |
| **Service Checking** | Built-in | Separate | Separate APIs |
| **Android 14 Partial Gallery** | ✅ | Varies | ✅ |
| **Enum-Based Status** | ✅ | Varies | Multiple APIs |
| **Cross-Platform** | Android + iOS | Android + iOS | Platform-specific |

**Notable features:**
- Info.plist validation prevents iOS crashes
- Process death recovery without timeout
- Service checking included (GPS, Bluetooth)
- Extensible via RawPermission for custom permissions

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
- **iOS Info.plist**: [Add required keys](docs/platform-specific/ios/info-plist.md) (required for iOS)
- **Logging**: Disable in production: `GrantLogger.isEnabled = false`
- **Backup**: [Exclude GrantStore from backup](docs/architecture/grant-store.md#backup-rules-android) if using persistent storage
- **Testing**: Test permission flows on real devices

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

**Logging helps with**:
- Detecting missing iOS Info.plist keys
- Debugging permission flows
- Understanding platform-specific behaviors
- Troubleshooting denied states

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

## 🤝 Support

- **Website:** [brewkits.dev](https://brewkits.dev)
- **Issues:** [GitHub Issues](https://github.com/brewkits/Grant/issues)
- **Email:** datacenter111@gmail.com

---

**License:** Apache 2.0 • **Author:** Nguyễn Tuấn Việt • [BrewKits](https://brewkits.dev)

[⬆️ Back to Top](#grant-)
