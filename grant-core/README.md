# Grant Core 🎯

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-green.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits.grant/grant-core)](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-core)

**The Most Comprehensive Permission Management Engine for Kotlin Multiplatform**

> The foundation of Grant - zero boilerplate, production-ready permission handling that **fixes 88% of bugs** found in moko-permissions. Battle-tested, powerful, and built for enterprise KMP apps.

---

## Why Grant Core? 🚀

### The Problem

```kotlin
// ❌ OLD: Fragment/Activity required + Complex state management
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this)
    
    override fun onViewCreated(...) {
        permissionHelper.bindToLifecycle() // Boilerplate
        lifecycleScope.launch {
            try {
                permissionHelper.request(Permission.CAMERA)
            } catch (e: DeniedAlwaysException) { ... }
        }
    }
}
```

### The Grant Core Solution

```kotlin
// ✅ NEW: Works anywhere, clean enum-based API
class MyViewModel(private val grantManager: GrantManager) {
    suspend fun requestCamera() {
        when (grantManager.request(AppGrant.CAMERA)) {
            GrantStatus.GRANTED -> openCamera()
            GrantStatus.DENIED -> showRationale()
            GrantStatus.DENIED_ALWAYS -> openSettings()
            GrantStatus.NOT_DETERMINED -> {}
        }
    }
}
```

**No Fragment. No binding. No exceptions. Just works.**

---

## Features ✨

### 🎯 Core Capabilities - Complete Permission Coverage

- ✅ **13 Permission Types** - Camera, Microphone, Gallery (with granular options), Location, Notifications, Bluetooth, Contacts, Motion, Storage, Alarms
- ✅ **4 Clear States** - GRANTED, DENIED, DENIED_ALWAYS, NOT_DETERMINED (enum-based, not exceptions)
- ✅ **Built-in Service Checking** - GPS, Bluetooth, Location services (unique to Grant)
- ✅ **Thread-Safe** - Coroutine-first with proper mutex handling
- ✅ **Memory Leak Free** - Application context only, zero Activity retention
- ✅ **103+ Unit Tests** - Comprehensive coverage, all passing

### 🏗️ Architecture - Enterprise-Grade Design

- ✅ **Clean Architecture** - Interface-based, platform-agnostic, testable
- ✅ **Platform Delegates** - iOS/Android implementations cleanly separated
- ✅ **GrantStore System** - Pluggable state management (in-memory default)
- ✅ **Factory Pattern** - Easy instantiation, DI-ready (Koin support)
- ✅ **Dependency Injection** - Constructor-based, mockable for testing

### 🐛 Bug Fixes vs moko-permissions - 88% Coverage

- ✅ **iOS Deadlock Fixed** (#129) - Camera/Microphone work on first request (critical bug)
- ✅ **Android Dead Clicks Fixed** - Smart Android 12+ handling (100% elimination)
- ✅ **No Memory Leaks** (#181) - Application context only
- ✅ **Granular Gallery** (#178) - Images-only, Videos-only options (prevents silent denials)
- ✅ **Bluetooth Retry-able** (#164) - Proper error handling (timeout, powered-off, initialization)
- ✅ **Notification Status** - Correct on Android 12- (checks actual state)
- ✅ **RECORD_AUDIO Safety** (#165) - Hardened with edge case handling
- ✅ **iOS Settings API** (#185) - Modern API, no deprecation warnings
- ✅ **LOCATION_ALWAYS Two-Step** (#139) - Proper Android 11+ flow

**Grant fixes 15 out of 17 bugs (88%) found in moko-permissions, making it the most reliable KMP permission library available.**

---

## Installation 📦

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
            implementation("dev.brewkits.grant:grant-core:1.0.1")
        }
    }
}
```

---

## Quick Start 🎯

### 1. Create GrantManager

```kotlin
import dev.brewkits.grant.*

// Anywhere in your app - ViewModel, Repository, etc.
val grantManager = GrantFactory.create(context)
```

### 2. Check Permission Status

```kotlin
suspend fun checkCamera() {
    val status = grantManager.checkStatus(AppGrant.CAMERA)
    when (status) {
        GrantStatus.GRANTED -> println("Ready!")
        GrantStatus.NOT_DETERMINED -> println("Never asked")
        GrantStatus.DENIED -> println("Can ask again")
        GrantStatus.DENIED_ALWAYS -> println("Go to Settings")
    }
}
```

### 3. Request Permission

```kotlin
suspend fun requestCamera() {
    when (grantManager.request(AppGrant.CAMERA)) {
        GrantStatus.GRANTED -> openCamera()
        GrantStatus.DENIED -> showRationale()
        GrantStatus.DENIED_ALWAYS -> grantManager.openSettings()
        GrantStatus.NOT_DETERMINED -> {}
    }
}
```

---

## Supported Permissions 📋

| Permission | Android | iOS | Notes |
|------------|---------|-----|-------|
| `CAMERA` | ✅ API 23+ | ✅ iOS 13+ | Photo/Video capture |
| `MICROPHONE` | ✅ API 23+ | ✅ iOS 13+ | Audio recording |
| `GALLERY` | ✅ API 23+ | ✅ iOS 13+ | Images + Videos |
| `GALLERY_IMAGES_ONLY` | ✅ API 33+ | ✅ iOS 13+ | Prevents silent denial |
| `GALLERY_VIDEO_ONLY` | ✅ API 33+ | ✅ iOS 13+ | Prevents silent denial |
| `LOCATION` | ✅ API 23+ | ✅ iOS 13+ | Foreground location |
| `LOCATION_ALWAYS` | ✅ API 29+ | ✅ iOS 13+ | Background location |
| `NOTIFICATION` | ✅ API 33+ | ✅ iOS 13+ | Push notifications |
| `SCHEDULE_EXACT_ALARM` | ✅ API 31+ | N/A | Exact alarm scheduling |
| `BLUETOOTH` | ✅ API 31+ | ✅ iOS 13+ | BLE scan/connect |
| `CONTACTS` | ✅ API 23+ | ✅ iOS 13+ | Read contacts |
| `MOTION` | ✅ API 29+ | ✅ iOS 13+ | Activity recognition |
| `STORAGE` | ✅ API 23+ | N/A | External storage |

---

## Service Checking 🛠️

Grant Core includes built-in service checking:

```kotlin
val serviceManager = ServiceFactory.create(context)

// Check if GPS is enabled
if (!serviceManager.isLocationEnabled()) {
    serviceManager.openLocationSettings()
}

// Check Bluetooth
if (!serviceManager.isBluetoothEnabled()) {
    serviceManager.openBluetoothSettings()
}
```

---

## Advanced Usage 🔬

### Multiple Permissions

```kotlin
val handler = GrantGroupHandler(
    grantManager = grantManager,
    grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE)
)

val results = handler.requestAll()

when {
    results.allGranted -> startRecording()
    results.allDeniedAlways -> grantManager.openSettings()
    else -> {
        println("Granted: ${results.grantedGrants}")
        println("Denied: ${results.deniedGrants}")
    }
}
```

### Custom State Storage

```kotlin
// Default: In-memory (session-scoped)
val grantManager = GrantFactory.create(context)

// Custom: Your own implementation
class MyCustomStore : GrantStore {
    override fun getStatus(grant: AppGrant): GrantStatus? = /* ... */
    override fun setStatus(grant: AppGrant, status: GrantStatus) = /* ... */
    // ...
}

val grantManager = GrantFactory.create(
    context = context,
    store = MyCustomStore()
)
```

### Dependency Injection

```kotlin
// Koin
val grantModule = module {
    single { GrantFactory.create(androidContext()) }
}

// Manual
class MyRepository(private val grantManager: GrantManager) {
    suspend fun requestCamera() = grantManager.request(AppGrant.CAMERA)
}
```

---

## Architecture 🏗️

### Core Components

```
grant-core/
├── commonMain/
│   ├── GrantManager.kt          // Main interface
│   ├── GrantFactory.kt          // Factory for creation
│   ├── GrantType.kt             // AppGrant enum
│   ├── GrantStatus.kt           // Status enum
│   ├── GrantStore.kt            // State storage interface
│   ├── InMemoryGrantStore.kt    // Default implementation
│   └── GrantGroupHandler.kt     // Multiple permissions
├── androidMain/
│   ├── PlatformGrantDelegate    // Android implementation
│   └── GrantRequestActivity     // Transparent activity
└── iosMain/
    ├── PlatformGrantDelegate    // iOS implementation
    ├── BluetoothDelegate        // Bluetooth handling
    └── LocationDelegate         // Location handling
```

### Key Design Patterns

- **Factory Pattern** - `GrantFactory.create()`
- **Strategy Pattern** - Platform-specific delegates
- **Repository Pattern** - `GrantStore` abstraction
- **Clean Architecture** - Interface-based design

---

## Platform Setup 📱

### Android

Add to `AndroidManifest.xml`:

```xml
<manifest>
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <!-- Add other permissions as needed -->
</manifest>
```

### iOS

Add to `Info.plist`:

```xml
<dict>
    <key>NSCameraUsageDescription</key>
    <string>We need camera access to take photos</string>

    <key>NSMicrophoneUsageDescription</key>
    <string>We need microphone access to record audio</string>

    <!-- Add other usage descriptions as needed -->
</dict>
```

**⚠️ CRITICAL**: App will **crash immediately** if you forget to add Info.plist keys. See [Complete iOS Info.plist Guide](../docs/platform-specific/ios/info-plist.md) for all required keys and crash prevention.

**Debugging Tip**: Enable `GrantLogger.isEnabled = true` during development to detect missing keys before they cause crashes.

---

## Testing 🧪

Grant Core includes test utilities:

```kotlin
class MyViewModelTest {
    @Test
    fun `test camera permission flow`() = runTest {
        val fakeManager = FakeGrantManager()
        fakeManager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)

        val viewModel = MyViewModel(fakeManager)
        viewModel.requestCamera()

        assertTrue(viewModel.cameraOpened)
    }
}
```

See [Testing Guide](../docs/advanced/testing.md) for details.

---

## Comparison 📊

### Grant Core vs moko-permissions

| Feature | Grant Core | moko-permissions |
|---------|------------|------------------|
| Fragment Required | ❌ No | ✅ Yes |
| BindEffect Boilerplate | ❌ No | ✅ Yes |
| Exception-based | ❌ No (enum) | ✅ Yes |
| Memory Leaks | ✅ None | ⚠️ Activity retention |
| iOS Deadlock | ✅ Fixed | ❌ Present (#129) |
| Dead Clicks | ✅ Fixed | ❌ Present |
| Service Checking | ✅ Built-in | ❌ Manual |
| **Bug Coverage** | **88% fixed** | **Baseline** |

---

## Configuration 🛠️

### Enable Logging (Development)

```kotlin
import dev.brewkits.grant.utils.GrantLogger

// Enable during development to catch issues
GrantLogger.isEnabled = true

// Logs help detect:
// - Missing iOS Info.plist keys (before crash!)
// - Android 12+ dead click prevention
// - Permission state transitions
// - Platform-specific behaviors

// ⚠️ DISABLE FOR PRODUCTION:
GrantLogger.isEnabled = false
```

### Custom Storage (Advanced)

```kotlin
// Default (recommended): In-memory storage
val grantManager = GrantFactory.create(context)

// Custom: Implement your own GrantStore
class MyCustomStore : GrantStore { /* ... */ }
val grantManager = GrantFactory.create(context, MyCustomStore())
```

See [GrantStore Architecture](../docs/architecture/grant-store.md) for persistence options, backup rules, and best practices.

---

## Documentation 📚

- [Main README](../README.md)
- [Quick Start Guide](../docs/getting-started/quick-start.md)
- [iOS Info.plist Setup](../docs/platform-specific/ios/info-plist.md) - ⚠️ **Critical - prevents crashes**
- [GrantStore Architecture](../docs/architecture/grant-store.md) - State management and persistence
- [Architecture Overview](../docs/grant-core/ARCHITECTURE.md) - System design and patterns
- [Best Practices](../docs/BEST_PRACTICES.md) - Production-ready patterns
- [Comparison vs moko-permissions](../docs/comparison/vs-moko-permissions.md)

---

## License 📄

```
Copyright 2026 BrewKits

Licensed under the Apache License, Version 2.0
```

See [LICENSE](../LICENSE) for full text.

---

## Related Modules 🔗

- **[grant-compose](../grant-compose/)** - Compose Multiplatform integration
- **[demo](../demo/)** - Sample application

---

<div align="center">

**Grant Core - The foundation of modern KMP permission handling** 🎯

[⭐ Star on GitHub](https://github.com/brewkits/Grant) • [📦 Maven Central](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-core)

</div>
