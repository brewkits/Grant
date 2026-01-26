# 🎯 KMP Grant

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-blue.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-green.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A **production-ready**, **UI-agnostic** grant management library for Kotlin Multiplatform that follows clean architecture principles.

## ✨ Why KMP Grant?

### The Problem
Managing grants across iOS and Android requires:
- Different APIs (UIKit authorization vs Android grants)
- Complex state management (rationale dialogs, settings navigation)
- Coupling between business logic and UI frameworks
- 30+ lines of boilerplate per grant in ViewModels

### The Solution
KMP Grant provides:
- ✅ **Single API** for both platforms
- ✅ **UI-agnostic** - works with Compose Multiplatform, SwiftUI, XML, or any UI framework
- ✅ **Clean ViewModels** - 3 lines instead of 30+ lines of grant logic
- ✅ **No third-party dependencies** - fully custom implementation
- ✅ **Full Flow Handling** - rationale, denial, settings redirection
- ✨ **NEW: First-Class Compose Support** - Beautiful Material 3 dialogs out of the box with `grant-compose`

## 🎯 Features

### 🏗️ Clean Architecture
- Core abstraction layer independent of third-party libraries
- Platform-specific delegates for Android and iOS
- No coupling between business logic and UI
- Easy to test and maintain

### 🔄 Intelligent Grant Flow
- **Automatic Status Detection** - Distinguishes between NOT_DETERMINED, DENIED, and DENIED_ALWAYS
- **Smart Dialog Management** - Shows the right dialog at the right time:
  - System dialog for first-time requests
  - Rationale dialog when user denies (explains why permission is needed)
  - Settings guide dialog when permanently denied (directs user to Settings)
- **Zero Configuration** - Works out of the box, no manual dialog management needed
- **Platform-Aware** - Handles Android's `shouldShowRequestPermissionRationale` and iOS authorization states correctly
- Lifecycle-safe on Android, Main thread-safe on iOS

### 🎨 UI Framework Agnostic
- Works with Compose Multiplatform
- Works with native UI (SwiftUI, XML)
- No @Composable constraints
- Bring your own UI

### 📦 GrantHandler Pattern
- Reusable component for ViewModels
- Eliminates boilerplate code
- Consistent UX across features
- Built-in state management

### ⚡ Advanced Features

#### Multiple Permissions Support
- **Smart permission grouping** - Automatically handles platform-specific permission requirements
- **Android 12+ Location** - Requests both FINE and COARSE permissions together (required by Android 12+)
- **Android 11+ Background Location** - Intelligent 2-step request (foreground first, then background)
- **Gallery Permissions** - Auto-handles Android 13+ media permissions vs older READ_EXTERNAL_STORAGE

#### Open Settings Integration
- **Android**: Direct navigation to app's permission settings page
- **iOS**: Opens app settings via `UIApplicationOpenSettingsURLString`
- **Automatic guidance** - GrantHandler shows "Open Settings" button when DENIED_ALWAYS

#### Service Status Checking (Exclusive Feature)
- **LocationService** - Check if GPS/Location services are enabled (not just permission)
- **BluetoothService** - Check if Bluetooth is turned on
- **InternetService** - Check network connectivity
- **Why it matters**: Permission granted ≠ Service enabled. Users need both!

Example:
```kotlin
// Check both permission AND service status
val locationReady = grantManager.checkStatus(AppGrant.LOCATION) == GrantStatus.GRANTED &&
                   serviceManager.checkLocationService() == ServiceStatus.ENABLED

if (!locationReady) {
    // Guide user to enable location service
    serviceManager.openLocationSettings()
}
```

## 📦 Installation

### Core Module (Required)

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

### Compose Module (Optional - For Jetpack Compose / Compose Multiplatform)

**NEW!** If you're using Compose, add `grant-compose` for beautiful Material 3 dialogs out of the box:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:1.0.0")
            implementation("dev.brewkits:grant-compose:1.0.0") // ✨ First-class Compose support
        }
    }
}
```

**Benefits of grant-compose**:
- ✅ One-line dialog handling with `GrantDialog(handler)`
- ✅ Material 3 design system
- ✅ Fully customizable (titles, messages, button labels)
- ✅ Zero boilerplate
- ✅ Works on both Android and iOS

📖 **See [grant-compose/README.md](grant-compose/README.md) for detailed Compose documentation**

## 🚀 Quick Start

### 1. Setup Koin (Dependency Injection)

#### Android

```kotlin
// DemoApplication.kt
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
// iosApp/iOSApp.swift
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

// In your shared code
fun initKoin() {
    startKoin {
        modules(
            grantModule,
            grantPlatformModule
        )
    }
}
```

### 2. Declare Grants

#### Android - AndroidManifest.xml

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
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />

    <!-- Microphone -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <!-- Contacts -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />

    <!-- Bluetooth (Android 12+) -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

</manifest>
```

#### iOS - Info.plist

```xml
<key>NSCameraUsageDescription</key>
<string>We need camera access to take photos</string>

<key>NSPhotoLibraryUsageDescription</key>
<string>We need photo library access to select images</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>We need location to show nearby places</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>We need background location for tracking</string>

<key>NSMicrophoneUsageDescription</key>
<string>We need microphone access to record audio</string>

<key>NSContactsUsageDescription</key>
<string>We need contacts access to find your friends</string>

<key>NSBluetoothAlwaysUsageDescription</key>
<string>We need Bluetooth to connect to devices</string>

<key>NSMotionUsageDescription</key>
<string>We need motion data for fitness tracking</string>
```

### 3. ViewModel Usage (Super Clean!)

```kotlin
class CameraViewModel(
    GrantManager: GrantManager
) : ViewModel() {

    // Compose this handler (NO boilerplate code!)
    val cameraGrant = GrantHandler(
        GrantManager = GrantManager,
        grant = GrantType.CAMERA,
        scope = viewModelScope
    )

    fun onCaptureClick() {
        // 1 line to handle entire grant flow!
        cameraGrant.request {
            // Only runs when grant is GRANTED
            openCamera()
        }
    }

    private fun openCamera() {
        println("📸 Camera opened!")
    }
}
```

### 4. UI Integration

#### Option A: With grant-compose (Recommended for Compose) ✨

**Zero boilerplate - just one line!**

```kotlin
import dev.brewkits.grant.compose.GrantDialog // Import from grant-compose

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // ✅ ONE LINE - handles ALL dialogs automatically
    GrantDialog(handler = viewModel.cameraGrant)

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
```

**That's it!** `GrantDialog` automatically:
- Shows rationale dialog when user denies permission
- Shows settings guide when permission is permanently denied
- Handles all user interactions (confirm/dismiss)
- Uses Material 3 design system

**Customize if needed:**

```kotlin
GrantDialog(
    handler = viewModel.cameraGrant,
    rationaleTitle = "Camera Required",
    rationaleConfirm = "Allow Camera",
    settingsTitle = "Enable Camera",
    settingsConfirm = "Go to Settings"
)
```

📖 **See [grant-compose/README.md](grant-compose/README.md) for full documentation**

---

#### Option B: Manual UI (For custom frameworks or advanced use cases)

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val state by viewModel.cameraGrant.state.collectAsState()

    // Rationale Dialog (soft denial)
    if (state.showRationale) {
        AlertDialog(
            title = { Text("Camera Permission") },
            text = { Text(state.rationaleMessage ?: "We need camera access") },
            confirmButton = {
                Button(onClick = { viewModel.cameraGrant.onRationaleConfirmed() }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cameraGrant.onDismiss() }) {
                    Text("Cancel")
                }
            },
            onDismissRequest = { viewModel.cameraGrant.onDismiss() }
        )
    }

    // Settings Guide Dialog (permanent denial)
    if (state.showSettingsGuide) {
        AlertDialog(
            title = { Text("Enable Camera") },
            text = { Text(state.settingsMessage ?: "Enable in Settings") },
            confirmButton = {
                Button(onClick = { viewModel.cameraGrant.onSettingsConfirmed() }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cameraGrant.onDismiss() }) {
                    Text("Later")
                }
            },
            onDismissRequest = { viewModel.cameraGrant.onDismiss() }
        )
    }

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
```

## 📱 Supported Grants

| Grant | Android | iOS | GrantType Enum |
|------------|---------|-----|-------------------|
| 📷 Camera | ✅ | ✅ | `CAMERA` |
| 🖼️ Gallery | ✅ | ✅ | `GALLERY` |
| 📍 Location | ✅ | ✅ | `LOCATION` |
| 📍 Location (Always) | ✅ | ✅ | `LOCATION_ALWAYS` |
| 🔔 Notification | ✅ | ✅ | `NOTIFICATION` |
| 🎤 Microphone | ✅ | ✅ | `MICROPHONE` |
| 📞 Contacts | ✅ | ✅ | `CONTACTS` |
| 📅 Calendar | ✅ | ✅ | `CALENDAR` |
| 🔵 Bluetooth | ✅ | ✅ | `BLUETOOTH` |
| 🏃 Motion/Activity | ✅ | ✅ | `MOTION` |
| 💾 Storage | ✅ | N/A* | `STORAGE` |

*iOS uses sandboxed storage, no grant needed

## 🚀 Advanced Features

### 🧠 Intelligent Dialog Flow (Zero Configuration Required)

One of the most powerful features of KMP Grant is its **automatic dialog management**. Unlike other grant libraries that require manual state tracking, KMP Grant intelligently determines which dialog to show based on the grant's current state.

#### How It Works

```kotlin
// Just call request() - the library handles everything!
locationGrant.request {
    // Only runs when granted
    startLocationTracking()
}
```

The library automatically:

1. **Detects Grant State**
   - Uses `checkStatus()` to determine if grant is NOT_DETERMINED, DENIED, or DENIED_ALWAYS
   - On Android: Leverages `shouldShowRequestPermissionRationale()` for accurate state detection
   - On iOS: Maps native authorization statuses correctly

2. **Shows Appropriate Dialog**
   - **First Time (NOT_DETERMINED)** → Shows system grant dialog
   - **After Denial (DENIED)** → Shows rationale dialog explaining why the grant is needed
   - **Permanently Denied (DENIED_ALWAYS)** → Shows settings guide dialog directing user to Settings

3. **Handles User Actions**
   - Rationale Confirmed → Requests grant again
   - Settings Confirmed → Opens app settings
   - Dismissed → Cleans up and prevents memory leaks

#### Platform-Specific Intelligence

**Android:**
- Correctly handles `shouldShowRequestPermissionRationale()` logic
- Works with multi-permission requests (e.g., Location Always = FINE + COARSE + BACKGROUND)
- Detects when user ticks "Don't ask again"
- Handles API level differences (Android 11+ location, Android 13+ media permissions)

**iOS:**
- Maps iOS authorization statuses to grant states
- Handles "While Using" vs "Always" for location
- Properly detects when user denies vs. when permission is restricted

#### Example: Complete Flow

```kotlin
// ViewModel - Just 3 lines!
val cameraGrant = GrantHandler(
    grantManager = grantManager,
    grant = GrantType.CAMERA,
    scope = viewModelScope
)

fun onTakePhotoClick() {
    cameraGrant.request(
        rationaleMessage = "Camera access is needed to take photos for your profile",
        settingsMessage = "Camera is disabled. Please enable it in Settings > Permissions"
    ) {
        openCamera() // Only executes when granted
    }
}

// UI - Automatic dialog handling
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // This composable observes state and shows the right dialog automatically
    GrantDialogHandler(handler = viewModel.cameraGrant)

    // Your UI
    Button(onClick = { viewModel.onTakePhotoClick() }) {
        Text("Take Photo")
    }
}
```

**User Journey:**

1. **Click "Take Photo"** (first time)
   - System dialog appears: "Allow Camera access?"
   - User denies → Rationale dialog appears automatically
   - User clicks "Grant Permission" → System dialog appears again
   - User grants → Camera opens ✅

2. **Click "Take Photo"** (after denying twice)
   - Settings guide dialog appears automatically
   - User clicks "Open Settings" → App settings opens
   - User enables camera → Returns to app
   - Click "Take Photo" again → Camera opens ✅

**No manual state management. No if-else chains. Just works.**

### 📊 Grant Status Checking

Check grant status at any time without showing UI:

```kotlin
val cameraStatus = grantManager.checkStatus(GrantType.CAMERA)
when (cameraStatus) {
    GrantStatus.GRANTED -> showCameraButton()
    GrantStatus.NOT_DETERMINED -> showGrantPrompt()
    GrantStatus.DENIED -> showRationale()
    GrantStatus.DENIED_ALWAYS -> showSettingsGuide()
}
```

This is useful for:
- Showing/hiding features based on grant status
- Pre-checking grants before starting workflows
- Displaying grant status in settings screens

## 🏗️ Architecture

### Core Components

```
┌─────────────────────────────────────────┐
│         Feature Layer (App Code)         │
│    ViewModel + UI (Compose/SwiftUI)      │
└────────────────┬────────────────────────┘
                 │ depends on
┌────────────────▼────────────────────────┐
│        Abstraction Layer (Core)          │
│  • GrantManager (interface)              │
│  • GrantType (enum)                      │
│  • GrantStatus (enum)                    │
│  • GrantHandler (composition)            │
└────────────────┬────────────────────────┘
                 │ implemented by
┌────────────────▼────────────────────────┐
│     Implementation Layer (Custom)        │
│  • MyGrantManager                        │
│  • PlatformGrantDelegate                 │
│    - androidMain: Activity Result API    │
│    - iosMain: Framework Delegates        │
└─────────────────────────────────────────┘
```

### Key Patterns

- **Wrapper/Adapter Pattern**: `GrantManager` interface abstracts platform details
- **Composition**: `GrantHandler` eliminates ViewModel boilerplate
- **Expect/Actual**: Platform-specific implementations via `PlatformGrantDelegate`
- **State Flow**: Reactive state management for UI

## 📚 Documentation

### 📖 [Complete Documentation Index](docs/README.md)

### Essential Guides
- [✨ Best Practices](docs/BEST_PRACTICES.md) - **Start here!** Best practices for permission handling
- [📋 Changelog](CHANGELOG.md) - **What's new!** All bug fixes and enhancements

### Library Documentation (grant-core)
- [📖 Complete Grant Guide](docs/grant-core/GRANTS.md)
- [🏗️ Architecture & Design Patterns](docs/grant-core/ARCHITECTURE.md)
- [⚡ Quick Start Tutorial](docs/grant-core/QUICK_START.md)
- [🍎 iOS Setup Guide](docs/grant-core/QUICK_START_iOS.md)
- [🔧 Transparent Activity Guide](docs/grant-core/TRANSPARENT_ACTIVITY_GUIDE.md)
- [🧪 Testing Strategy](docs/grant-core/TESTING.md)

### iOS Development from Android Studio
- [🚀 iOS Setup for Android Studio](docs/ios/IOS_SETUP_ANDROID_STUDIO.md)
- [⚡ Quick Start iOS](docs/ios/QUICK_START_IOS_ANDROID_STUDIO.md)

### Demo App Documentation
- [🎨 Demo App Guide](docs/demo/DEMO_GUIDE.md)
- [⚙️ Demo Setup](docs/demo/DEMO_SETUP.md)

## 💡 Advanced Usage

### Multiple Grants

```kotlin
class ProfileViewModel(GrantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        GrantManager,
        GrantType.CAMERA,
        viewModelScope
    )

    val galleryGrant = GrantHandler(
        GrantManager,
        GrantType.GALLERY,
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

### Custom Rationale Messages

```kotlin
cameraGrant.request(
    rationaleMessage = "We need camera access to scan QR codes for faster checkout",
    settingsMessage = "Camera grant is disabled. Please enable it in Settings > Grants > Camera"
) {
    openQRScanner()
}
```

### Grant Groups

```kotlin
class LocationViewModel(GrantManager: GrantManager) : ViewModel() {
    val locationGroup = GrantGroupHandler(
        GrantManager = GrantManager,
        grants = listOf(
            GrantType.LOCATION,
            GrantType.LOCATION_ALWAYS
        ),
        scope = viewModelScope
    )

    fun onEnableTrackingClick() {
        locationGroup.request { status ->
            when (status) {
                GrantStatus.GRANTED -> startTracking()
                else -> showError()
            }
        }
    }
}
```

### Manual Status Check

```kotlin
class FeatureViewModel(
    private val GrantManager: GrantManager
) : ViewModel() {

    fun checkCameraGrant() {
        viewModelScope.launch {
            val status = GrantManager.checkStatus(GrantType.CAMERA)
            when (status) {
                GrantStatus.GRANTED -> {
                    // Camera available
                }
                GrantStatus.NOT_DETERMINED -> {
                    // Never asked before
                }
                GrantStatus.DENIED -> {
                    // Denied but can ask again
                }
                GrantStatus.DENIED_ALWAYS -> {
                    // User must go to settings
                }
            }
        }
    }
}
```

## ⚙️ Requirements

- **Kotlin**: 2.1.21+
- **Android**: API 26+ (Android 8.0)
- **iOS**: 13.0+
- **Compose Multiplatform**: 1.9.3+ (optional - works with any UI framework)
- **Koin**: 4.0.1+ (for dependency injection)

## 🧪 Testing

### Unit Testing ViewModels

```kotlin
class CameraViewModelTest {
    @Test
    fun `when grant granted, camera opens`() = runTest {
        val mockManager = mockk<GrantManager> {
            coEvery { request(GrantType.CAMERA) } returns GrantStatus.GRANTED
        }

        val viewModel = CameraViewModel(mockManager)
        viewModel.onCaptureClick()

        // Assert camera opened
        verify { viewModel.openCamera() }
    }
}
```

### Testing Grant Handler

```kotlin
class GrantHandlerTest {
    @Test
    fun `when denied, shows rationale dialog`() = runTest {
        val mockManager = mockk<GrantManager> {
            coEvery { checkStatus(any()) } returns GrantStatus.DENIED
        }

        val handler = GrantHandler(
            mockManager,
            GrantType.CAMERA,
            this
        )

        handler.request { }

        assertTrue(handler.state.value.showRationale)
    }
}
```

## 📄 License

```
Copyright 2026 Brewkits

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing grants and
limitations under the License.
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 🙏 Credits

Built with:
- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Koin](https://insert-koin.io/) - Dependency injection
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) - UI framework (optional)

Inspired by clean architecture principles and modern Android/iOS best practices.

---

**Made with ❤️ by Nguyễn Tuấn Việt at Brewkits**

🌟 If you find this library helpful, please give it a star!
