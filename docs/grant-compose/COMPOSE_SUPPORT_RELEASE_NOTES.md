# 🎉 Grant Compose - First-Class Compose Support Release

**Release Date**: January 26, 2026
**Version**: 1.0.0

## 🚀 What's New

We're excited to announce **grant-compose** - a new module that brings first-class Jetpack Compose / Compose Multiplatform support to the Grant permission management library!

### ✨ Key Features

1. **One-Line Dialog Handling**
   - Just add `GrantDialog(handler)` - no manual state management needed
   - Automatically shows rationale and settings dialogs at the right time
   - Zero boilerplate code

2. **Material 3 Design System**
   - Beautiful, consistent dialogs out of the box
   - Follows Material 3 design guidelines
   - Seamlessly integrates with your existing Compose UI

3. **Fully Customizable**
   - Override dialog titles, messages, and button labels
   - Use individual dialog components for advanced customization
   - Complete control over UI while keeping logic clean

4. **Multiplatform Support**
   - Works on both Android and iOS
   - Same API across all platforms
   - No platform-specific code needed

5. **Headless Architecture**
   - Logic in `grant-core` (UI-agnostic)
   - UI in `grant-compose` (Compose-specific)
   - Clean separation of concerns
   - Easy to test independently

## 📦 Module Structure

The Grant library now consists of two modules:

```
grant/
├── grant-core/           # Core logic (UI-agnostic)
│   ├── GrantManager     # Platform-agnostic permission management
│   ├── GrantHandler     # ViewModel helper with StateFlow
│   └── AppGrant         # Permission types enum
│
└── grant-compose/        # Compose UI components (NEW!)
    ├── GrantDialog      # All-in-one dialog handler
    ├── GrantRationaleDialog    # Individual rationale dialog
    ├── GrantSettingsDialog     # Individual settings dialog
    └── Extensions       # Compose helper extensions
```

## 🎯 Before & After Comparison

### Before (Manual Dialog Handling)

```kotlin
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    val state by viewModel.cameraGrant.state.collectAsState()

    // 40+ lines of boilerplate dialog code
    if (state.showRationale) {
        AlertDialog(
            title = { Text("Camera Permission") },
            text = { Text(state.rationaleMessage ?: "...") },
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

    if (state.showSettingsGuide) {
        AlertDialog(
            // ... another 20+ lines
        )
    }

    // Your UI
    Button(onClick = { viewModel.onCaptureClick() }) {
        Text("Take Photo")
    }
}
```

### After (With grant-compose) ✨

```kotlin
import dev.brewkits.grant.compose.GrantDialog

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // ✅ ONE LINE - handles everything
    GrantDialog(handler = viewModel.cameraGrant)

    // Your UI
    Button(onClick = { viewModel.onCaptureClick() }) {
        Text("Take Photo")
    }
}
```

**Result**: **40+ lines → 1 line** (98% reduction in boilerplate!)

## 📝 Implementation Details

### Files Created

1. **Module Structure**
   - `grant-compose/build.gradle.kts` - Gradle build configuration
   - `grant-compose/README.md` - Comprehensive documentation

2. **Source Code**
   - `GrantComposeExtensions.kt` - Extension functions for cleaner code
   - `GrantDialogs.kt` - Dialog components (GrantDialog, GrantRationaleDialog, GrantSettingsDialog)

3. **Demo Updates**
   - `MinimalGrantDemoScreen.kt` - Minimal example showcasing grant-compose
   - Updated `SimpleGrantDemoScreen.kt` to use grant-compose
   - Updated `GrantDemoScreen.kt` to use grant-compose
   - Removed internal `GrantDialogHandler` (now in grant-compose)

4. **Documentation**
   - Updated main `README.md` with grant-compose installation and usage
   - Added comparison table (Before/After)
   - Created release notes (this document)

### Build Configuration

- **Group**: `dev.brewkits`
- **Version**: `1.0.0`
- **Platforms**: Android (API 26+), iOS (iosX64, iosArm64, iosSimulatorArm64)
- **Dependencies**:
  - `grant-core` (required)
  - Compose Runtime, Foundation, Material 3, UI

## 🎓 Usage Examples

### Basic Usage

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    GrantDialog(handler = viewModel.cameraGrant)

    Button(onClick = {
        viewModel.cameraGrant.request {
            openCamera()
        }
    }) {
        Text("Take Photo")
    }
}
```

### Customized

```kotlin
GrantDialog(
    handler = viewModel.locationGrant,
    rationaleTitle = "Location Required",
    rationaleConfirm = "Allow Location",
    settingsTitle = "Enable Location",
    settingsConfirm = "Go to Settings"
)
```

### Advanced (Individual Components)

```kotlin
val state by handler.collectAsState()

if (state.showRationale) {
    GrantRationaleDialog(
        message = state.rationaleMessage ?: "Custom message",
        onConfirm = { handler.onRationaleConfirmed() },
        onDismiss = { handler.onDismiss() }
    )
}
```

## 🏗️ Architecture Benefits

### Headless UI Pattern

The separation between `grant-core` and `grant-compose` follows the **Headless UI** pattern:

```
┌─────────────────────────────────────────┐
│         grant-compose (UI)              │
│  GrantDialog, Extensions                │
└──────────────┬──────────────────────────┘
               │ observes StateFlow
┌──────────────▼──────────────────────────┐
│      grant-core (Logic)                 │
│  GrantHandler, GrantManager             │
└─────────────────────────────────────────┘
```

**Benefits**:
- ✅ Logic is platform-agnostic and testable independently
- ✅ UI can be swapped or customized without touching logic
- ✅ Clean separation of concerns
- ✅ Easy to create custom UI frameworks (SwiftUI, Cupertino, etc.)

## 📊 Code Quality Metrics

- **Build Status**: ✅ BUILD SUCCESSFUL
- **Compilation**: All targets (Android, iOS x64, iOS ARM64, iOS Simulator ARM64)
- **Zero Breaking Changes**: Fully backward compatible with grant-core
- **Type Safety**: 100% Kotlin with strong type checking
- **Documentation Coverage**: Complete with examples

## 🔄 Migration Guide

### If you were using manual dialogs

**Before:**
```kotlin
// 40+ lines of manual dialog code
if (state.showRationale) { ... }
if (state.showSettingsGuide) { ... }
```

**After:**
```kotlin
// Add dependency
implementation("dev.brewkits:grant-compose:1.0.1")

// Replace with one line
GrantDialog(handler = viewModel.cameraGrant)
```

### If you were using grant-core only

No changes needed! `grant-core` remains unchanged. `grant-compose` is an optional addition.

## 🎯 Next Steps for Users

1. **Add Dependency**
   ```kotlin
   implementation("dev.brewkits:grant-compose:1.0.1")
   ```

2. **Import GrantDialog**
   ```kotlin
   import dev.brewkits.grant.compose.GrantDialog
   ```

3. **Replace Manual Dialogs**
   ```kotlin
   GrantDialog(handler = yourHandler)
   ```

4. **Enjoy** - No more dialog boilerplate!

## 📖 Documentation

- Main README: [README.md](README.md)
- Compose Module README: [grant-compose/README.md](grant-compose/README.md)
- Demo App: See `demo/` for working examples
- Minimal Example: `MinimalGrantDemoScreen.kt`

## 🙏 Credits

Built with:
- Kotlin Multiplatform 2.1.21
- Jetpack Compose / Compose Multiplatform 1.9.3
- Material 3 Design System
- Clean Architecture principles

## 📄 License

Apache 2.0 - See LICENSE file for details.

---

**Happy Coding with Grant Compose!** 🎉
