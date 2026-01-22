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

## 🎯 Features

### 🏗️ Clean Architecture
- Core abstraction layer independent of third-party libraries
- Platform-specific delegates for Android and iOS
- No coupling between business logic and UI
- Easy to test and maintain

### 🔄 Complete Grant Flow
- Check status without UI
- Request with automatic system dialog
- Handle rationale (soft denial)
- Handle permanent denial with settings navigation
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
    grantManager: GrantManager
) : ViewModel() {

    // Compose this handler (NO boilerplate code!)
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
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

### 4. UI Integration (Compose Example)

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val grantState by viewModel.cameraGrant.state.collectAsState()

    // 1. Handle grant dialogs
    GrantDialogHandler(
        handler = viewModel.cameraGrant
    )

    // 2. Your UI
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

    // Rationale Dialog (soft denial)
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

    // Settings Guide Dialog (permanent denial)
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

### Library Documentation (grant-core)
- [📖 Complete Grant Guide](grant-core/docs/GRANTS.md)
- [🏗️ Architecture & Design Patterns](grant-core/docs/ARCHITECTURE.md)
- [⚡ Quick Start Tutorial](grant-core/docs/QUICK_START.md)
- [🍎 iOS Setup Guide](grant-core/docs/QUICK_START_iOS.md)
- [🔧 Transparent Activity Guide](grant-core/docs/TRANSPARENT_ACTIVITY_GUIDE.md)
- [🧪 Testing Strategy](grant-core/docs/TESTING.md)

### Demo App Documentation
- [🎨 Demo App Guide](demo/docs/DEMO_GUIDE.md)
- [⚙️ Demo Setup](demo/docs/DEMO_SETUP.md)

## 💡 Advanced Usage

### Multiple Grants

```kotlin
class ProfileViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager,
        GrantType.CAMERA,
        viewModelScope
    )

    val galleryGrant = GrantHandler(
        grantManager,
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
class LocationViewModel(grantManager: GrantManager) : ViewModel() {
    val locationGroup = GrantGroupHandler(
        grantManager = grantManager,
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
    private val grantManager: GrantManager
) : ViewModel() {

    fun checkCameraGrant() {
        viewModelScope.launch {
            val status = grantManager.checkStatus(GrantType.CAMERA)
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
