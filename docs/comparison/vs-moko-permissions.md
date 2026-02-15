# Grant vs moko-permissions: Comprehensive Comparison

## Summary

Grant addresses several known issues found in moko-permissions, including iOS deadlocks, Android dead clicks, and memory management concerns.

| Area | Grant | moko-permissions |
|------|-------|------------------|
| **Memory Management** | Uses application context | May retain Activity |
| **Dead Clicks** | Addressed | Present on Android 13+ |
| **iOS Deadlocks** | Addressed | Present (#129) |

---

## Detailed Comparison

### Architecture

| Feature | Grant | moko-permissions |
|---------|-------|------------------|
| **Fragment/Activity Required** | ❌ No | ✅ Yes |
| **BindEffect Boilerplate** | ❌ No | ✅ Yes |
| **State Management** | Enum-based | Exception-based |
| **Memory Management** | Leak-free | Activity retention risk |
| **Compose Integration** | First-class | Limited support |

### Platform Support

| Feature | Grant | moko-permissions |
|---------|-------|------------------|
| **Android 12+ Dead Click** | ✅ Fixed | ❌ Present |
| **Android 14 Partial Gallery** | ✅ Supported | ❌ Not supported |
| **iOS Permission Deadlock** | ✅ Fixed (#129) | ❌ Present |
| **Granular Permissions** | ✅ Yes | ❌ No |
| **Service Checking** | ✅ Built-in | ❌ Manual |

---

## Issues Fixed in Grant

### Critical (P0) - 4 bugs fixed

#### 1. iOS Deadlock #129 ✅ FIXED
**Problem**: Camera/Microphone permissions hang forever on first request

**moko-permissions**:
```kotlin
// Causes deadlock on iOS
return suspendCancellableCoroutine { continuation ->
    CoroutineScope(Dispatchers.Main).launch {  // ❌ Nested coroutine
        AVCaptureDevice.requestAccessForMediaType(...)
    }
}
```

**Grant**:
```kotlin
// Works correctly
return suspendCancellableCoroutine { continuation ->
    AVCaptureDevice.requestAccessForMediaType(...)  // ✅ Direct call
}
```

**Impact**: Camera and Microphone work on first attempt in Grant.

---

#### 2. Android 13+ Dead Clicks ✅ FIXED
**Problem**: First click after app restart does nothing on Android 13+

**Root Cause**: `shouldShowRequestPermissionRationale()` returns `false` for both "never asked" and "permanently denied" states.

**moko-permissions**: No solution, users experience dead clicks

**Grant**: Uses `InMemoryGrantStore` to track "requested before" state, distinguishing:
- `NOT_DETERMINED` = Never asked (show dialog)
- `DENIED` = Previously denied (can ask again)
- `DENIED_ALWAYS` = Permanently denied (go to Settings)

**Impact**: Zero dead clicks in Grant.

See [Dead Click Fix Documentation](../platform-specific/android/dead-click-fix.md)

---

#### 3. Gallery Silent Denial #178 ✅ FIXED
**Problem**: Requesting both `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` when only one is declared in manifest causes silent denial

**moko-permissions**:
```kotlin
// Always requests both - fails if only one declared
GALLERY -> listOf(
    READ_MEDIA_IMAGES,
    READ_MEDIA_VIDEO
)
```

**Grant**:
```kotlin
// Granular options prevent silent denial
AppGrant.GALLERY_IMAGES_ONLY  // Only images
AppGrant.GALLERY_VIDEO_ONLY   // Only videos
AppGrant.GALLERY             // Both (backward compatible)
```

**Impact**: No more silent denials in Grant.

---

#### 4. iOS Settings API #185 ✅ FIXED
**Problem**: Using deprecated `openURL()` API (deprecated since iOS 10)

**moko-permissions**:
```kotlin
UIApplication.sharedApplication.openURL(settingsUrl)  // ❌ Deprecated
```

**Grant**:
```kotlin
UIApplication.sharedApplication.openURL(
    settingsUrl,
    options = emptyMap(),
    completionHandler = { success -> ... }
)  // ✅ Modern API
```

**Impact**: No deprecation warnings in Grant.

---

### High Priority (P1) - 5 bugs fixed

#### 5. Memory Leak #181 ✅ FIXED
**Problem**: Activity reference retained during config changes

**moko-permissions**: Retains Fragment/Activity context

**Grant**: Uses `applicationContext` only
```kotlin
actual val grantPlatformModule = module {
    single {
        PlatformGrantDelegate(
            context = get<Context>().applicationContext  // ✅ App context
        )
    }
}
```

---

#### 6. Exception-based Flow #154 ✅ FIXED
**Problem**: `DeniedAlwaysException` extends `DeniedException` causing confusing error handling

**moko-permissions**:
```kotlin
try {
    permission.request()
} catch (e: DeniedException) {
    // Is it DENIED or DENIED_ALWAYS? 🤷
}
```

**Grant**:
```kotlin
when (grantManager.request(AppGrant.CAMERA)) {
    GrantStatus.DENIED -> showRationale()        // ✅ Clear
    GrantStatus.DENIED_ALWAYS -> openSettings()  // ✅ Clear
}
```

---

#### 7. Bluetooth Error Handling #164 ✅ FIXED
**Problem**: Any Bluetooth error returns `DENIED_ALWAYS` (too severe)

**Grant**: Differentiates error types:
- `BluetoothTimeoutException` → `DENIED` (retryable)
- `BluetoothInitializationException` → `DENIED` (retryable)
- `BluetoothPoweredOffException` → `DENIED` (user can enable)
- Only actual denial → `DENIED_ALWAYS`

**Impact**: Better error handling, faster timeout (10s vs 60s)

---

#### 8. RECORD_AUDIO Safety #165 ✅ HARDENED
**Problem**: Audio recording may fail on edge cases

**Grant improvements**:
- Try-catch around authorization checks
- Logging for parental controls
- Better error messages (Camera vs Microphone)
- Exception handling in request flow

---

#### 9. LOCATION_ALWAYS Two-Step #139 ✅ FIXED
**Problem**: Throws `DeniedAlwaysException` on fresh install when requesting background location

**Grant**: Implements proper two-step flow
```kotlin
// Step 1: Request foreground
if (!hasForeground) {
    request(ACCESS_FINE_LOCATION)
    return
}

// Step 2: Request background
request(ACCESS_BACKGROUND_LOCATION)
```

**Impact**: No false denials in Grant.

---

### Already Superior (8 bugs never existed in Grant)

#### 10. No UI Binding Requirement #186 ✅
**moko-permissions**: Requires Fragment binding

**Grant**: Works anywhere
```kotlin
// In ViewModel
class MyViewModel(private val grantManager: GrantManager) {
    suspend fun requestCamera() = grantManager.request(AppGrant.CAMERA)
}
```

---

#### 11. Deprecated iOS Location API #148 ✅
**moko-permissions**: Uses deprecated `authorizationStatus()`

**Grant**: Uses `CLLocationManager.authorizationStatus()` (static, not deprecated)

---

#### 12. iOS 18 Crash #153 ✅
**moko-permissions**: Crashes on unknown authorization status

**Grant**: Uses `else` clause
```kotlin
when (authorizationStatus) {
    Authorized -> GRANTED
    Denied -> DENIED_ALWAYS
    else -> NOT_DETERMINED  // ✅ Safe fallback
}
```

---

#### 13. Location Suspend Forever #177 ✅
**moko-permissions**: Location request hangs indefinitely

**Grant**: Uses dedicated `LocationManagerDelegate` with timeout

---

#### 14. Module Dependency Conflicts #156 ✅
**moko-permissions**: Separate modules cause build issues

**Grant**: Single `grant-core` module (no conflicts)

---

#### 15. Service Checking #131 ✅
**moko-permissions**: No service checking

**Grant**: Built-in `ServiceManager`
```kotlin
serviceManager.isLocationEnabled()
serviceManager.openLocationSettings()
```

---

## API Comparison

### Requesting Permission

**moko-permissions**:
```kotlin
// Requires Fragment
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this)

    fun requestCamera() {
        lifecycleScope.launch {
            permissionHelper.bindEffect()  // Boilerplate
            try {
                permissionHelper.providePermission(Permission.CAMERA)
                openCamera()
            } catch (e: DeniedAlwaysException) {
                openSettings()
            } catch (e: DeniedException) {
                showRationale()
            }
        }
    }
}
```

**Grant**:
```kotlin
// Works anywhere
suspend fun requestCamera() {
    when (grantManager.request(AppGrant.CAMERA)) {
        GrantStatus.GRANTED -> openCamera()
        GrantStatus.DENIED -> showRationale()
        GrantStatus.DENIED_ALWAYS -> openSettings()
        GrantStatus.NOT_DETERMINED -> {}
    }
}
```

### Compose Integration

**moko-permissions**:
```kotlin
@Composable
fun CameraButton() {
    val permissionState = rememberPermissionState(Permission.CAMERA)

    // Need BindEffect
    PermissionRequestEffect(
        permissionState = permissionState,
        onGranted = { openCamera() },
        onDenied = { showRationale() }
    )

    Button(onClick = { permissionState.request() }) {
        Text("Camera")
    }
}
```

**Grant**:
```kotlin
@Composable
fun CameraButton() {
    val grantManager = rememberGrantManager()

    Button(onClick = {
        when (grantManager.request(AppGrant.CAMERA)) {
            GrantStatus.GRANTED -> openCamera()
            GrantStatus.DENIED -> showRationale()
            GrantStatus.DENIED_ALWAYS -> openSettings()
        }
    }) {
        Text("Camera")
    }
}
```

---

## Performance Notes

Grant uses a lightweight custom implementation without external dependencies, resulting in smaller binary size. Both libraries handle permissions efficiently in typical use cases.

---

## Migration Guide

### From moko-permissions to Grant

1. **Remove moko-permissions dependency**:
```kotlin
// build.gradle.kts
// Remove:
// implementation("dev.icerock.moko:permissions:...")

// Add:
implementation("dev.brewkits.grant:grant-core:1.0.0")
```

2. **Replace Fragment-based code**:
```kotlin
// OLD
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this)
}

// NEW
class MyViewModel(private val grantManager: GrantManager) {
    // No Fragment needed!
}
```

3. **Replace exception handling**:
```kotlin
// OLD
try {
    permission.request()
} catch (e: DeniedAlwaysException) { ... }

// NEW
when (grantManager.request(...)) {
    GrantStatus.DENIED_ALWAYS -> ...
}
```

See [Migration Guide](migration-from-moko.md) for complete details.

---

## Conclusion

**Grant offers:**
- No Fragment/binding requirement
- Addressed Android 13+ dead clicks
- Application context usage
- Enum-based API (no exceptions)
- Built-in service checking
- Compose-first design

**moko-permissions offers:**
- Battle-tested in production
- Broader platform support (JS/Desktop)
- Larger community

Both libraries are production-ready. Choose based on your project's specific needs and priorities.

---

## References

- [moko-permissions GitHub Issues](https://github.com/icerockdev/moko-permissions/issues)
- [Grant Documentation](../README.md)
- [Dead Click Fix](../platform-specific/android/dead-click-fix.md)
- [Architecture Overview](../architecture/overview.md)
