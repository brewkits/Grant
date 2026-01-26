# Grant Compose

**Jetpack Compose / Compose Multiplatform UI components for Grant library**

This module provides first-class Compose support for the Grant permission management library, making it incredibly easy to handle permission requests in your Compose UI.

## Features

✨ **Minimal Code** - Just one line to handle all permission dialogs
🎨 **Material 3 Design** - Beautiful, consistent dialogs out of the box
🔧 **Fully Customizable** - Override titles, messages, button labels
📱 **Multiplatform** - Works on Android and iOS
🧩 **Headless Architecture** - Logic in `grant-core`, UI in `grant-compose`

## Installation

Add the dependency to your `commonMain` sourceset:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":grant-core"))
            implementation(project(":grant-compose"))
        }
    }
}
```

## Quick Start

### 1. Setup ViewModel

```kotlin
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

That's it! The `GrantDialog` component automatically:
- Shows rationale dialog when user denies permission
- Shows settings guide when permission is permanently denied
- Handles all user interactions (confirm/dismiss)

## Customization

### Custom Messages

You can override dialog titles and button labels:

```kotlin
GrantDialog(
    handler = viewModel.locationGrant,
    rationaleTitle = "Location Required",
    rationaleConfirm = "Allow Location",
    settingsTitle = "Enable Location",
    settingsConfirm = "Go to Settings"
)
```

### Advanced: Custom UI

For complete UI control, use individual dialog components:

```kotlin
@Composable
fun MyCustomPermissionFlow(handler: GrantHandler) {
    val state by handler.collectAsState()

    if (state.showRationale) {
        GrantRationaleDialog(
            message = state.rationaleMessage ?: "Custom rationale",
            onConfirm = { handler.onRationaleConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }

    if (state.showSettingsGuide) {
        GrantSettingsDialog(
            message = state.settingsMessage ?: "Custom settings guide",
            onConfirm = { handler.onSettingsConfirmed() },
            onDismiss = { handler.onDismiss() }
        )
    }
}
```

## Extension Functions

The module includes helpful extension functions for cleaner code:

```kotlin
// Collect UI state
val state by handler.collectAsState()

// Collect permission status
val status by handler.collectStatusAsState()
```

## Architecture

This module follows the **Headless UI** pattern:

```
┌─────────────────────────────────────────┐
│         grant-compose (UI)              │
│  GrantDialog, GrantRationaleDialog,     │
│  GrantSettingsDialog, Extensions        │
└──────────────┬──────────────────────────┘
               │ observes StateFlow
┌──────────────▼──────────────────────────┐
│      grant-core (Logic)                 │
│  GrantHandler, GrantManager,            │
│  GrantUiState, AppGrant                 │
└─────────────────────────────────────────┘
```

**Benefits**:
- ✅ Logic is platform-agnostic and testable independently
- ✅ UI can be swapped or customized without touching logic
- ✅ Clean separation of concerns

## API Reference

### GrantDialog

Main component that handles all dialog states automatically.

```kotlin
@Composable
fun GrantDialog(
    handler: GrantHandler,
    rationaleTitle: String = "Permission Required",
    rationaleConfirm: String = "Continue",
    rationaleDismiss: String = "Cancel",
    settingsTitle: String = "Permission Denied",
    settingsConfirm: String = "Open Settings",
    settingsDismiss: String = "Cancel"
)
```

### GrantRationaleDialog

Shows rationale when permission is soft-denied.

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

### GrantSettingsDialog

Shows settings guide when permission is permanently denied.

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

## License

Apache 2.0 - See LICENSE file for details.
