# Grant Compose

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-1.7.1-green)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Maven Central](https://img.shields.io/maven-central/v/dev.brewkits.grant/grant-compose)](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-compose)

**Compose Multiplatform UI Layer for Permission Management**

One-line permission dialogs. Material 3 design. Fully customizable. Built with Headless UI architecture for maximum flexibility.

Grant Compose provides first-class Compose support for the Grant permission library, making permission handling in Compose UI simple and elegant.

---

## Why Grant Compose?

### The Problem with Traditional Approaches

```kotlin
// ❌ OLD WAY: Complex state management + Custom dialogs
@Composable
fun CameraScreen() {
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val permissionState = rememberPermissionState(Permission.CAMERA)

    // Manual dialog management
    if (showRationale) { /* Show rationale dialog */ }
    if (showSettings) { /* Show settings dialog */ }

    // BindEffect boilerplate
    PermissionRequestEffect(...)
}
```

### The Grant Compose Way

```kotlin
// ✅ GRANT COMPOSE: One line, handles everything
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    GrantDialog(handler = viewModel.cameraGrant) // That's it!

    Button(onClick = { viewModel.onTakePhotoClick() }) {
        Text("Take Photo")
    }
}
```

**Automatic handling** of:
- Rationale dialogs when user denies
- Settings dialogs when permanently denied
- User interactions (confirm/dismiss)
- State management

---

## Features

### Minimal Code
- **One-line integration** - `GrantDialog(handler)` handles all dialogs
- **Zero boilerplate** - No manual state management
- **Automatic UI** - Shows right dialog at right time

### Material 3 Design
- **Modern dialogs** - Consistent with Material 3 guidelines
- **Platform-aware** - Adapts to Android/iOS design language
- **Customizable** - Override titles, messages, button labels

### Headless Architecture
- **Logic in grant-core** - Platform-agnostic, testable
- **UI in grant-compose** - Swappable, customizable
- **Clean separation** - Business logic decoupled from UI

### Multiplatform
- **Android** - Full Material 3 support
- **iOS** - Native iOS styling
- **Compose Multiplatform** - Works everywhere

---

## Installation

Add the dependency to your `commonMain` sourceset:

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits.grant:grant-core:1.0.2")
            implementation("dev.brewkits.grant:grant-compose:1.0.2")
        }
    }
}
```

---

## Quick Start

### 1. Setup ViewModel

```kotlin
import dev.brewkits.grant.*

class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun onTakePhotoClick() {
        cameraGrant.request {
            // This runs ONLY when permission is granted
            openCamera()
        }
    }
}
```

### 2. Add GrantDialog to Your Screen

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // ✅ One line - handles all dialogs automatically
    GrantDialog(handler = viewModel.cameraGrant)

    // Your UI
    Column {
        Button(onClick = { viewModel.onTakePhotoClick() }) {
            Text("Take Photo")
        }
    }
}
```

**That's it!** The `GrantDialog` component automatically:
- Shows rationale dialog when user denies permission
- Shows settings guide when permission is permanently denied
- Handles all user interactions (confirm/dismiss)
- Updates UI based on permission state

---

## Advanced Usage

### Headless Pattern - Complete UI Control

For maximum flexibility, use the **headless pattern** to build completely custom UI:

```kotlin
@Composable
fun MyScreen(handler: GrantHandler) {
    val state by handler.collectAsState()

    // ✅ Complete control - no Material 3 dependency
    if (state.showRationale) {
        // Use your own custom dialog, bottom sheet, or any UI
        MyCustomDialog(
            message = state.rationaleMessage ?: "Permission needed",
            onAccept = { handler.onRationaleConfirmed() },
            onDecline = { handler.onDismiss() }
        )
    }

    if (state.showSettingsGuide) {
        // Use bottom sheet instead of dialog
        MyCustomBottomSheet(
            message = state.settingsMessage ?: "Enable in Settings",
            onOpenSettings = { handler.onSettingsConfirmed() },
            onCancel = { handler.onDismiss() }
        )
    }

    // Your UI
    Button(onClick = {
        handler.request(
            rationaleMessage = "Camera is needed to scan QR codes",
            settingsMessage = "Please enable Camera in Settings to scan QR codes"
        ) {
            openCamera()
        }
    }) {
        Text("Scan QR Code")
    }
}

@Composable
fun MyCustomDialog(
    message: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // Your completely custom UI - any design system
    // Could use Cupertino for iOS-style, your design system, etc.
}
```

**Benefits of Headless Pattern**:
- **No UI dependency** - Not forced to use Material 3
- **Complete flexibility** - Use any dialog, bottom sheet, or custom UI
- **Design system agnostic** - Works with Cupertino, Material 2, custom themes
- **Platform-specific UI** - Show iOS-style on iOS, Material on Android

---

### Custom Messages

Override dialog titles and button labels when using built-in Material 3 dialogs:

```kotlin
GrantDialog(
    handler = viewModel.locationGrant,
    rationaleTitle = "Location Access Required",
    rationaleMessage = "We need your location to show nearby restaurants",
    rationaleConfirm = "Allow Location",
    settingsTitle = "Enable Location in Settings",
    settingsMessage = "Please enable location access in Settings to use this feature",
    settingsConfirm = "Go to Settings"
)
```

### Custom Dialog UI

For complete UI control, use individual dialog components:

```kotlin
@Composable
fun MyCustomPermissionFlow(handler: GrantHandler) {
    val state by handler.collectAsState()

    // Custom rationale dialog
    if (state.showRationale) {
        GrantRationaleDialog(
            message = state.rationaleMessage ?: "We need this permission",
            title = "Permission Required",
            confirmText = "Continue",
            dismissText = "Not Now",
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }

    // Custom settings dialog
    if (state.showSettingsGuide) {
        GrantSettingsDialog(
            message = state.settingsMessage ?: "Enable in Settings",
            title = "Permission Denied",
            confirmText = "Open Settings",
            dismissText = "Cancel",
            onConfirm = { handler.onSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }
}
```

### Multiple Permissions

Handle multiple permissions with `GrantGroupHandler`:

```kotlin
class VideoRecordingViewModel(grantManager: GrantManager) : ViewModel() {
    val videoGrants = GrantGroupHandler(
        grantManager = grantManager,
        grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
        scope = viewModelScope
    )

    fun onRecordVideoClick() {
        videoGrants.requestAll {
            // This runs ONLY when ALL permissions are granted
            startVideoRecording()
        }
    }
}

@Composable
fun VideoScreen(viewModel: VideoRecordingViewModel) {
    GrantDialog(handler = viewModel.videoGrants)

    Button(onClick = { viewModel.onRecordVideoClick() }) {
        Text("Record Video")
    }
}
```

---

## Extension Functions

The module includes helpful extension functions for cleaner code:

```kotlin
// Collect UI state as Compose State
val state by handler.collectAsState()

// Collect permission status as Compose State
val status by handler.collectStatusAsState()

// Check specific states
if (state.showRationale) { /* ... */ }
if (state.showSettingsGuide) { /* ... */ }

// Get messages
val message = state.rationaleMessage
val settingsMessage = state.settingsMessage
```

---

## Architecture

Grant Compose follows the **Headless UI** pattern for maximum flexibility:

```
┌─────────────────────────────────────────┐
│         grant-compose (UI Layer)        │
│  GrantDialog, GrantRationaleDialog,     │
│  GrantSettingsDialog, Extensions        │
│                                         │
│  • Material 3 components                │
│  • Platform-aware styling               │
│  • Customizable messages                │
└──────────────┬──────────────────────────┘
               │ observes StateFlow<GrantUiState>
┌──────────────▼──────────────────────────┐
│      grant-core (Logic Layer)           │
│  GrantHandler, GrantManager,            │
│  GrantUiState, AppGrant                 │
│                                         │
│  • Business logic                       │
│  • State management                     │
│  • Platform delegates                   │
└─────────────────────────────────────────┘
```

**Benefits**:
- **Logic is testable** - Unit test business logic without UI
- **UI is swappable** - Replace dialogs without touching logic
- **Clean separation** - Changes isolated to appropriate layer
- **Platform-agnostic** - Core logic works on all platforms

---

## API Reference

### GrantDialog

Main component that handles all dialog states automatically.

**Parameters**:
```kotlin
@Composable
fun GrantDialog(
    handler: GrantHandler,                      // or GrantGroupHandler
    rationaleTitle: String = "Permission Required",
    rationaleConfirm: String = "Continue",
    rationaleDismiss: String = "Cancel",
    settingsTitle: String = "Permission Denied",
    settingsConfirm: String = "Open Settings",
    settingsDismiss: String = "Cancel"
)
```

**Usage**:
```kotlin
GrantDialog(handler = viewModel.cameraGrant)
```

---

### GrantRationaleDialog

Shows rationale when permission is soft-denied (user can still approve).

**Parameters**:
```kotlin
@Composable
fun GrantRationaleDialog(
    message: String,
    title: String = "Permission Required",
    confirmText: String = "Continue",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

**Usage**:
```kotlin
GrantRationaleDialog(
    message = "We need camera access to take photos",
    onConfirm = { handler.onRationaleConfirmed() },
    onDismiss = { handler.onDismiss() }
)
```

---

### GrantSettingsDialog

Shows settings guide when permission is permanently denied.

**Parameters**:
```kotlin
@Composable
fun GrantSettingsDialog(
    message: String,
    title: String = "Permission Denied",
    confirmText: String = "Open Settings",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
)
```

**Usage**:
```kotlin
GrantSettingsDialog(
    message = "Please enable camera access in Settings",
    onConfirm = { handler.onSettingsConfirmed() },
    onDismiss = { handler.onDismiss() }
)
```

---

## UI Approaches Comparison

Grant Compose offers **three approaches** to fit your needs:

| Approach | Flexibility | Code | Best For |
|----------|-------------|------|----------|
| **GrantDialog** | Low | 1 line | Quick prototypes, standard Material 3 apps |
| **Custom Messages** | Medium | 5-10 lines | Material 3 apps with branded messages |
| **Headless Pattern** | High | 20-30 lines | Custom design systems, iOS-style UI, complete control |

### Approach 1: GrantDialog (Quick & Easy)
```kotlin
GrantDialog(handler = viewModel.cameraGrant) // One line, Material 3 UI
```

### Approach 2: Custom Messages (Branded)
```kotlin
GrantDialog(
    handler = viewModel.cameraGrant,
    rationaleTitle = "Camera Access Required",
    rationaleMessage = "We need camera to scan QR codes",
    settingsTitle = "Enable Camera in Settings"
)
```

### Approach 3: Headless Pattern (Complete Control)
```kotlin
val state by handler.collectAsState()
if (state.showRationale) {
    MyCustomUI(state, handler)
}
```

---

## Best Practices

### 1. Use ViewModel for Grant Handlers

```kotlin
// ✅ GOOD: Handler in ViewModel (survives config changes)
class MyViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(grantManager, AppGrant.CAMERA, viewModelScope)
}

// ❌ BAD: Handler in Composable (recreated on every recomposition)
@Composable
fun MyScreen() {
    val handler = remember { GrantHandler(...) } // Don't do this
}
```

### 2. Request on User Action

```kotlin
// ✅ GOOD: Request when user clicks button
Button(onClick = { viewModel.requestCamera() }) { Text("Take Photo") }

// ❌ BAD: Request on screen launch
LaunchedEffect(Unit) { viewModel.requestCamera() } // Don't do this
```

### 3. Provide Clear Rationale

```kotlin
// ✅ GOOD: Explain why you need the permission
cameraGrant.request(
    rationaleMessage = "Camera access is needed to scan QR codes for secure login"
)

// ❌ BAD: Generic message
cameraGrant.request(
    rationaleMessage = "This app needs camera permission"
)
```

### 4. Handle All States

```kotlin
// ✅ GOOD: Handle all permission states
when (status) {
    GrantStatus.GRANTED -> openCamera()
    GrantStatus.DENIED -> showRationale()
    GrantStatus.DENIED_ALWAYS -> showSettingsGuide()
    GrantStatus.NOT_DETERMINED -> { /* Initial state */ }
}

// ❌ BAD: Only handle granted
if (status == GrantStatus.GRANTED) { openCamera() }
```

---

## Testing

Grant Compose components are fully testable:

```kotlin
@Test
fun `test camera permission flow`() = runTest {
    val fakeManager = FakeGrantManager()
    val handler = GrantHandler(fakeManager, AppGrant.CAMERA, this)

    // Test rationale shown on first denial
    fakeManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
    handler.request()
    assertTrue(handler.uiState.value.showRationale)

    // Test settings shown on permanent denial
    fakeManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
    handler.onRationaleConfirmed()
    assertTrue(handler.uiState.value.showSettingsGuide)
}
```

See [Testing Guide](../docs/TESTING.md) for comprehensive examples.

---

## Examples

See the [demo app](../demo/) for complete examples:
- [MinimalGrantDemoScreen.kt](../demo/src/commonMain/kotlin/dev/brewkits/grant/demo/MinimalGrantDemoScreen.kt) - Minimal example
- [SimpleGrantDemoScreen.kt](../demo/src/commonMain/kotlin/dev/brewkits/grant/demo/SimpleGrantDemoScreen.kt) - Full-featured example

---

## Documentation

- [Main README](../README.md)
- [grant-core Documentation](../grant-core/README.md)
- [Quick Start Guide](../docs/getting-started/quick-start.md)
- [Compose Support Release Notes](../docs/grant-compose/COMPOSE_SUPPORT_RELEASE_NOTES.md)
- [Best Practices](../docs/BEST_PRACTICES.md)

---

## License

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

## Related Modules

- **[grant-core](../grant-core/)** - Core permission management engine
- **[demo](../demo/)** - Sample application with examples

---

<div align="center">

**Grant Compose - Permission Dialogs for Compose Multiplatform**

[Star on GitHub](https://github.com/brewkits/Grant) • [Maven Central](https://central.sonatype.com/artifact/dev.brewkits.grant/grant-compose)

</div>
