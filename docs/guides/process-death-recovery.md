# Process Death State Restoration

## Overview

Android can kill your app's process at any time to reclaim memory. When this happens during a permission request, dialog state is lost. Grant v1.1.0+ provides optional state restoration through the `SavedStateDelegate` pattern.

## The Problem

Without state restoration:
1. User opens your app
2. Clicks "Take Photo" → Permission dialog appears
3. Android kills process (low memory)
4. User grants permission and returns to app
5. **Dialog is gone** - user loses context

## The Solution

Grant provides `SavedStateDelegate` to automatically save and restore dialog state across process death.

## Quick Start

### Step 1: Add SavedStateHandle to ViewModel

```kotlin
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.brewkits.grant.AndroidSavedStateDelegate
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantManager

class CameraViewModel(
    private val grantManager: GrantManager,
    savedStateHandle: SavedStateHandle  // ← Inject SavedStateHandle
) : ViewModel() {

    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope,
        savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle)  // ← Add this
    )

    fun onTakePhotoClick() {
        cameraGrant.request {
            openCamera()
        }
    }
}
```

### Step 2: That's It!

Dialog state is automatically saved and restored. No additional code needed.

## How It Works

### Saved State

`GrantHandler` saves these properties:
- `isVisible`: Whether any dialog is shown
- `showRationale`: Whether rationale dialog is shown
- `showSettingsGuide`: Whether settings guide is shown

### Restoration Flow

1. **Process Death**: Android kills process
2. **State Saved**: `SavedStateHandle` persists state to Bundle
3. **Process Recreated**: Android recreates app
4. **State Restored**: `GrantHandler` reads state from `SavedStateHandle`
5. **Dialog Reappears**: User sees dialog again

### Example Scenario

```
1. User clicks "Request Camera"
2. GrantHandler shows rationale dialog
3. [PROCESS DEATH] Android kills app
4. User returns to app
5. GrantHandler init: reads SavedStateHandle
6. Dialog automatically reappears ✅
7. User clicks "Continue"
8. Permission request proceeds normally
```

## Platform Support

| Platform | Support | Implementation |
|----------|---------|----------------|
| **Android** | ✅ Full | `AndroidSavedStateDelegate` |
| **iOS** | 🔸 Not needed | Process death is rare |
| **Desktop** | 🔸 Not needed | User controls process |

## Advanced Usage

### Custom SavedStateDelegate

You can implement your own `SavedStateDelegate`:

```kotlin
class MyCustomDelegate : SavedStateDelegate {
    private val prefs = MyPreferences()

    override fun saveState(key: String, value: String) {
        prefs.putString(key, value)
    }

    override fun restoreState(key: String): String? {
        return prefs.getString(key)
    }

    override fun clear(key: String) {
        prefs.remove(key)
    }
}

val handler = GrantHandler(
    grantManager = grantManager,
    grant = AppGrant.CAMERA,
    scope = scope,
    savedStateDelegate = MyCustomDelegate()
)
```

### No-Op Delegate (Default)

If you don't provide a `SavedStateDelegate`, `NoOpSavedStateDelegate` is used:

```kotlin
class NoOpSavedStateDelegate : SavedStateDelegate {
    override fun saveState(key: String, value: String) {
        // No-op: State is not persisted
    }

    override fun restoreState(key: String): String? {
        // Always returns null
        return null
    }

    override fun clear(key: String) {
        // No-op
    }
}
```

## Testing

### Testing with SavedStateHandle

```kotlin
class CameraViewModelTest {

    @Test
    fun `state restored after process death`() {
        val savedStateHandle = SavedStateHandle()

        // First instance - show dialog
        val viewModel1 = CameraViewModel(grantManager, savedStateHandle)
        viewModel1.onTakePhotoClick()

        // Simulate process death by creating new instance with same SavedStateHandle
        val viewModel2 = CameraViewModel(grantManager, savedStateHandle)

        // Dialog should be visible
        assertTrue(viewModel2.cameraGrant.state.value.isVisible)
    }
}
```

## Best Practices

### ✅ DO

- Use `viewModelScope` for GrantHandler scope
- Inject `SavedStateHandle` through constructor
- Let ViewModel survive configuration changes

### ❌ DON'T

- Don't use `lifecycleScope` - cancelled on config changes
- Don't create GrantHandler in Fragment - won't survive process death
- Don't manually save/restore state - `SavedStateDelegate` handles it

## FAQ

### Q: Is this required?

**A:** No, it's optional. Grant works fine without it. But if you want dialogs to survive process death on Android, use it.

### Q: What happens without SavedStateDelegate?

**A:** Dialog state is lost on process death. The permission request itself survives (tracked by OS), but UI state is gone.

### Q: Does this affect performance?

**A:** No. State is saved only when dialog state changes (user interaction). Restoration happens once on init. Both are negligible operations.

### Q: Can I use it on iOS?

**A:** You can, but it's not needed. iOS rarely kills processes, and when it does, SavedStateHandle doesn't exist. The default `NoOpSavedStateDelegate` is fine.

## Migration from v1.0.0

No breaking changes! Just add the optional parameter:

```kotlin
// Before (v1.0.0)
val handler = GrantHandler(
    grantManager = grantManager,
    grant = AppGrant.CAMERA,
    scope = viewModelScope
)

// After (v1.1.0) - Optional upgrade
val handler = GrantHandler(
    grantManager = grantManager,
    grant = AppGrant.CAMERA,
    scope = viewModelScope,
    savedStateDelegate = AndroidSavedStateDelegate(savedStateHandle)  // ← Add this line
)
```

## See Also

- [GrantHandler Documentation](../architecture/grant-handler.md)
- [Android Process Death Guide](https://developer.android.com/guide/components/activities/process-lifecycle)
- [SavedStateHandle Documentation](https://developer.android.com/topic/libraries/architecture/viewmodel-savedstate)
