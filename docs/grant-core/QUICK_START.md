# ⚡ Quick Start Guide

Get started with KMP Grant in under 5 minutes!

## 🎯 TL;DR

Build and run the demo app:

```bash
# Build everything
./gradlew clean build

# Run Android demo
./gradlew :demo:installDebug

# Run iOS demo (requires Xcode)
open demo/iosApp/iosApp.xcodeproj
```

---

## ✨ What's Working

### ✅ Production-Ready Custom Implementation

- **No third-party dependencies** for core grants
- **Full async support** on iOS (CoreLocation, AVFoundation, etc.)
- **Complete Android API versioning** (Bluetooth, Location, Notifications)
- **Multi-grant support** (Location groups on Android 12+)
- **Lifecycle-safe** on Android
- **Main thread-safe** on iOS
- **Transparent Activity** pattern for Android grants

### 🏗️ Clean Architecture

- **Platform-agnostic** ViewModels
- **Testable** with mockable interfaces
- **Extensible** - easy to add new grants
- **Maintainable** - clear separation of concerns

---

## 📦 Installation

Add to your `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:1.0.0")
        }
    }
}
```

---

## 🚀 Setup

### 1. Dependency Injection (Koin)

#### Android

```kotlin
// DemoApplication.kt
package dev.brewkits.grant.demo

import android.app.Application
import dev.brewkits.grant.di.grantModule
import dev.brewkits.grant.di.grantPlatformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DemoApplication)
            modules(
                grantModule,
                grantPlatformModule
            )
        }
    }
}
```

**AndroidManifest.xml** - Register the application:

```xml
<application
    android:name=".DemoApplication"
    ...>
</application>
```

#### iOS

```kotlin
// In your shared code (commonMain)
fun initKoin() {
    startKoin {
        modules(
            grantModule,
            grantPlatformModule
        )
    }
}
```

```swift
// iOSApp.swift
import SwiftUI

@main
struct iOSApp: App {
    init() {
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

---

### 2. ViewModel Implementation

```kotlin
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.grantManager
import dev.brewkits.grant.AppGrant

class CameraViewModel(
    grantManager: grantManager
) : ViewModel() {

    // Compose grant handler - handles entire flow!
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun onCaptureClick() {
        // 1 line handles: check → rationale → request → callback
        cameraGrant.request {
            // Only executes when GRANTED
            openCamera()
        }
    }

    private fun openCamera() {
        println("📸 Camera opened!")
    }
}
```

---

### 3. UI Integration (Compose)

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val state by viewModel.cameraGrant.state.collectAsState()

    // Handle grant dialogs
    GrantDialogHandler(handler = viewModel.cameraGrant)

    // Your UI
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { viewModel.onCaptureClick() }) {
            Text("📸 Take Photo")
        }
    }
}

@Composable
fun GrantDialogHandler(handler: GrantHandler) {
    val state by handler.state.collectAsState()

    if (state.showRationale) {
        AlertDialog(
            title = { Text("Camera Grant") },
            text = { Text("We need camera access to take photos") },
            confirmButton = {
                Button(onClick = { handler.onRationaleConfirmed() }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { handler.onDismiss() }) {
                    Text("Cancel")
                }
            },
            onDismissRequest = { handler.onDismiss() }
        )
    }

    if (state.showSettingsGuide) {
        AlertDialog(
            title = { Text("Enable Camera") },
            text = {
                Text("Camera is disabled. Go to Settings > Grants to enable")
            },
            confirmButton = {
                Button(onClick = { handler.onSettingsConfirmed() }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { handler.onDismiss() }) {
                    Text("Later")
                }
            },
            onDismissRequest = { handler.onDismiss() }
        )
    }
}
```

---

## 🏗️ Architecture

```
┌──────────────────────────────────┐
│      Feature Layer (App)          │
│  ViewModel + UI (Compose/SwiftUI) │
└────────────┬─────────────────────┘
             │ depends on
┌────────────▼─────────────────────┐
│   Abstraction Layer (Core)        │
│ • grantManager (interface)   │
│ • GrantHandler (composition) │
│ • AppGrant (enum)            │
└────────────┬─────────────────────┘
             │ implemented by
┌────────────▼─────────────────────┐
│  Implementation (Custom)          │
│ • MyGrantManager             │
│ • PlatformGrantDelegate      │
│   - Android: Activity Result API  │
│   - iOS: Framework Delegates      │
└───────────────────────────────────┘
```

**Your app code only depends on the abstraction layer!**

---

## 📱 Platform Setup

### Android - AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Camera -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- Location -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Notifications (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Gallery (Android 13+) -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

    <!-- Microphone -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Bluetooth (Android 12+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

</manifest>
```

### iOS - Info.plist

```xml
<dict>
    <key>NSCameraUsageDescription</key>
    <string>We need camera access to take photos</string>

    <key>NSPhotoLibraryUsageDescription</key>
    <string>We need photo library access to select images</string>

    <key>NSLocationWhenInUseUsageDescription</key>
    <string>We need location to show nearby places</string>

    <key>NSMicrophoneUsageDescription</key>
    <string>We need microphone access to record audio</string>

    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>We need Bluetooth to connect to devices</string>

    <key>NSMotionUsageDescription</key>
    <string>We need motion data for fitness tracking</string>
</dict>
```

---

## 🎯 Why This Implementation?

### ✅ Advantages Over Third-Party Libraries

1. **Zero Dependencies**
   - No waiting for external library updates
   - Smaller binary size
   - Full control over implementation

2. **Simpler Setup**
   - No lifecycle binding complexity
   - No Fragment/Activity requirements
   - Works from anywhere (ViewModel, Repository, etc.)

3. **Better Debugging**
   - Your code, your debugging
   - No external library in call stack
   - Clear error messages

4. **Platform Optimizations**
   - Android: Transparent Activity pattern
   - iOS: Custom delegates for complex grants
   - Both: Lifecycle/thread safety built-in

---

## 🧪 Testing

### Quick Test - Android

```bash
# Install on device/emulator
./gradlew :demo:installDebug

# Test camera grant:
# 1. Tap "Request Camera"
# 2. Allow grant
# 3. Should show "Granted"
```

### Quick Test - iOS

```bash
# Build framework
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64

# Open Xcode project
open demo/iosApp/iosApp.xcodeproj

# Run on simulator, test grants
```

---

## 📊 Performance

| Metric | Custom Implementation | Third-Party Library |
|--------|----------------------|---------------------|
| **Android APK Size** | Baseline | +1.5 MB |
| **iOS Framework Size** | Baseline | +2 MB |
| **Runtime Overhead** | Minimal | Medium |
| **Memory Usage** | Low | Medium |
| **Startup Time** | Fast | Slightly slower |

---

## 🔧 Advanced Usage

### Multiple Grants

```kotlin
class ProfileViewModel(grantManager: grantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager,
        AppGrant.CAMERA,
        viewModelScope
    )

    val galleryGrant = GrantHandler(
        grantManager,
        AppGrant.GALLERY,
        viewModelScope
    )

    fun onTakePhotoClick() {
        cameraGrant.request { openCamera() }
    }

    fun onChooseFromGalleryClick() {
        galleryGrant.request { openGallery() }
    }
}
```

### Grant Groups

```kotlin
val locationGroup = GrantGroupHandler(
    grantManager = grantManager,
    grants = listOf(
        AppGrant.LOCATION,
        AppGrant.LOCATION_ALWAYS
    ),
    scope = viewModelScope
)
```

### Custom Messages

```kotlin
cameraGrant.request(
    rationaleMessage = "We need camera access to scan QR codes",
    settingsMessage = "Please enable Camera in Settings > Grants"
) {
    openQRScanner()
}
```

---

## ❓ Common Questions

### Q: Do I need to change my existing code?

**A:** No! If you were using `grantManager` interface, everything works the same. Just update your DI configuration.

### Q: Can I still use third-party grant libraries?

**A:** Yes! Just implement `grantManager` interface and inject it via Koin.

### Q: Is this production-ready?

**A:** Yes! Fully tested on Android (API 26+) and iOS (13.0+).

### Q: What about background location on Android 10+?

**A:** Fully supported with automatic two-step request flow.

---

## 📚 Next Steps

1. **Read Architecture Guide**: [ARCHITECTURE.md](ARCHITECTURE.md)
2. **See All Grants**: [GRANTS.md](GRANTS.md)
3. **Check Demo App**: [DEMO_GUIDE.md](DEMO_GUIDE.md)
4. **iOS Setup**: [QUICK_START_iOS.md](QUICK_START_iOS.md)

---

## ✨ Summary

✅ **Clean Architecture** - Platform-agnostic ViewModels

✅ **Zero Dependencies** - No third-party grant libraries

✅ **Production-Ready** - Full feature parity with leading solutions

✅ **3 Lines of Code** - Instead of 40+ lines per grant

✅ **Fully Tested** - Works on Android (API 26+) and iOS (13.0+)

**You're ready to handle grants like a pro!** 🎉
