# Migration Guide to Grant

**Version:** 1.3.0
**Last Updated:** April 28, 2026

This guide helps you migrate from other permission libraries to Grant with minimal code changes and maximum benefit.

---

## 📚 Table of Contents

1. [From moko-permissions](#from-moko-permissions)
2. [From Google Accompanist](#from-google-accompanist)
3. [From Custom Implementation](#from-custom-implementation)
4. [From Native Android APIs](#from-native-android-apis)
5. [Upgrading from Grant 1.2.x to 1.3.0](#upgrading-from-grant-12x-to-130)
6. [Common Migration Patterns](#common-migration-patterns)
7. [Troubleshooting](#troubleshooting)

---

## 5️⃣ Upgrading from Grant 1.2.x to 1.3.0

### Overview

Version 1.3.0 is a major architectural release for iOS that solves the **Apple App Store Rejection** issue (Issue #25) caused by unused permission frameworks.

### What Changed?

- **Permission Framework Isolation:** Every permission (Location, Bluetooth, etc.) is now isolated into its own handler file.
- **Improved Linker Behavior:** Kotlin/Native now only links the frameworks you actually use in your code.
- **Weak Linking:** Sensitive frameworks are now "weak-linked" as a secondary safety measure.

### Step-by-Step Upgrade

1. **Update Dependency Version**
   Update your `build.gradle.kts` to use version `1.3.0`:
   ```kotlin
   implementation("dev.brewkits:grant-core:1.3.0")
   implementation("dev.brewkits:grant-compose:1.3.0")
   ```

2. **Clean up `Info.plist` (Optional but Recommended)**
   You can now remove the "dummy" usage description keys that were previously required to avoid App Store rejection for permissions you didn't use.
   - **Keep** keys for permissions you actually use (e.g., `NSCameraUsageDescription`).
   - **Remove** keys for permissions you don't use (e.g., `NSLocationWhenInUseUsageDescription`).

3. **Clean & Rebuild**
   To ensure the new linker flags are applied correctly on iOS:
   ```bash
   ./gradlew clean
   # Then rebuild your iOS app/framework
   ```

### Breaking Changes
**None.** Version 1.3.0 is 100% source and binary compatible with 1.2.x for all code in `commonMain`.

---

## 1️⃣ From moko-permissions

### Overview

If you're using [moko-permissions](https://github.com/icerockdev/moko-permissions), Grant provides a similar API with additional production-grade features.

### Key Differences

| Feature | moko-permissions | Grant | Migration Impact |
|---------|-----------------|-------|------------------|
| **Setup** | Requires binding | Zero setup | ✅ Simpler |
| **API** | `PermissionsController` | `GrantManager` | ⚠️ Rename |
| **Dead Clicks** | Not handled | Fixed | ✅ Better UX |
| **Process Death** | 60s timeout | 0ms recovery | ✅ Better UX |
| **iOS Crashes** | No validation | Validates Info.plist | ✅ Safer |
| **Custom Permissions** | Not supported | RawPermission | ✅ More flexible |

### Step-by-Step Migration

#### Before (moko-permissions)

```kotlin
// 1. Create PermissionsController
class MyViewModel(
    private val permissionsController: PermissionsController
) : ViewModel() {

    fun requestCamera() {
        viewModelScope.launch {
            // 2. Bind to lifecycle (boilerplate)
            permissionsController.bind()

            // 3. Request permission
            try {
                permissionsController.providePermission(Permission.CAMERA)
                openCamera()  // Success
            } catch (e: DeniedException) {
                showRationale()  // Denied
            }
        }
    }
}

// In UI - need to pass permissionsController
@Composable
fun CameraScreen(viewModel: MyViewModel) {
    // No built-in dialog support
    Button(onClick = { viewModel.requestCamera() }) {
        Text("Take Photo")
    }
}
```

#### After (Grant)

```kotlin
// 1. Create GrantManager (simpler)
class MyViewModel(
    grantManager: GrantManager  // Inject via DI
) : ViewModel() {

    // 2. Create GrantHandler (zero boilerplate)
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun requestCamera() {
        // 3. Request permission (cleaner)
        cameraGrant.request {
            openCamera()  // Only runs when granted
        }
    }
}

// In UI - automatic dialog handling
@Composable
fun CameraScreen(viewModel: MyViewModel) {
    // ✨ Automatic rationale + settings dialogs
    GrantDialog(handler = viewModel.cameraGrant)

    Button(onClick = { viewModel.requestCamera() }) {
        Text("Take Photo")
    }
}
```

### API Mapping

| moko-permissions | Grant | Notes |
|-----------------|-------|-------|
| `PermissionsController` | `GrantManager` | Interface name change |
| `providePermission()` | `request()` | Method name change |
| `Permission.CAMERA` | `AppGrant.CAMERA` | Enum name change |
| `DeniedException` | `GrantStatus.DENIED` | Enum-based, not exception |
| `bind()` | Not needed | ✅ Zero boilerplate |

### Migration Checklist

- [ ] Replace `PermissionsController` with `GrantManager`
- [ ] Remove all `bind()` calls (not needed)
- [ ] Replace `Permission.*` with `AppGrant.*`
- [ ] Replace try-catch with `when (status)` enum handling
- [ ] Add `GrantDialog` for automatic UI
- [ ] Add `GrantHandler` in ViewModels
- [ ] Test all permission flows
- [ ] Remove moko-permissions dependency

**Estimated Time:** 2-3 hours for typical app

---

## 2️⃣ From Google Accompanist

### Overview

If you're using [Accompanist Permissions](https://google.github.io/accompanist/permissions/), Grant provides a cross-platform alternative with additional features.

### Key Differences

| Feature | Accompanist | Grant | Migration Impact |
|---------|------------|-------|------------------|
| **Platforms** | Android only | Android + iOS | ✅ Cross-platform |
| **Dead Clicks** | Not handled | Fixed | ✅ Better UX |
| **Process Death** | In-memory (good) | In-memory + recovery | ✅ Better UX |
| **Service Checking** | Not included | Built-in | ✅ More features |
| **Compose Integration** | Built-in | grant-compose module | ⚠️ Separate module |

### Step-by-Step Migration

#### Before (Accompanist)

```kotlin
// In Composable
@Composable
fun CameraScreen() {
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    Column {
        Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
            Text("Request Camera")
        }

        when (cameraPermissionState.status) {
            is PermissionStatus.Granted -> {
                CameraPreview()
            }
            is PermissionStatus.Denied -> {
                if ((cameraPermissionState.status as PermissionStatus.Denied).shouldShowRationale) {
                    // Show rationale
                    Text("Camera needed for QR scanning")
                } else {
                    // Permanently denied
                    Text("Go to Settings to enable")
                }
            }
        }
    }
}
```

#### After (Grant)

```kotlin
// In ViewModel (better architecture)
class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun openCamera() {
        cameraGrant.request {
            // Camera granted
        }
    }
}

// In Composable (cleaner)
@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    // ✨ Automatic dialogs
    GrantDialog(handler = viewModel.cameraGrant)

    val status by viewModel.cameraGrant.status.collectAsState()

    Column {
        Button(onClick = { viewModel.openCamera() }) {
            Text("Request Camera")
        }

        when (status) {
            GrantStatus.GRANTED -> CameraPreview()
            else -> EmptyState()
        }
    }
}
```

### API Mapping

| Accompanist | Grant | Notes |
|------------|-------|-------|
| `rememberPermissionState()` | `GrantHandler` | Moved to ViewModel |
| `launchPermissionRequest()` | `request()` | Simpler name |
| `PermissionStatus.Granted` | `GrantStatus.GRANTED` | Enum name |
| `shouldShowRationale` | Handled automatically | ✅ Less code |
| N/A | `GrantDialog` | ✅ Automatic UI |

### Migration Checklist

- [ ] Add `grant-compose` dependency
- [ ] Move permission logic from Composables to ViewModels
- [ ] Replace `rememberPermissionState` with `GrantHandler`
- [ ] Replace `PermissionStatus` with `GrantStatus`
- [ ] Add `GrantDialog` for automatic rationale/settings dialogs
- [ ] Test all permission flows
- [ ] Remove Accompanist dependency

**Estimated Time:** 1-2 hours for typical app

---

## 3️⃣ From Custom Implementation

### Overview

If you have a custom permission wrapper, Grant can replace it with less code and more features.

### Common Custom Patterns

#### Pattern A: Fragment-Based Request

**Before:**
```kotlin
class MyFragment : Fragment() {
    private val permissionHelper = PermissionHelper(this)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        permissionHelper.requestCamera { granted ->
            if (granted) {
                openCamera()
            }
        }
    }

    override fun onRequestPermissionsResult(...) {
        permissionHelper.onRequestPermissionsResult(...)
    }
}
```

**After:**
```kotlin
class MyViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun requestCamera() {
        cameraGrant.request { openCamera() }
    }
}

// Fragment is now much simpler
class MyFragment : Fragment() {
    private val viewModel: MyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // No permission logic in Fragment!
    }
}
```

#### Pattern B: Callback-Based

**Before:**
```kotlin
interface PermissionCallback {
    fun onGranted()
    fun onDenied()
    fun onPermanentlyDenied()
}

class PermissionManager(private val activity: Activity) {
    fun requestCamera(callback: PermissionCallback) {
        // Complex logic with ActivityResultLauncher
        // Handle rationale
        // Handle settings
    }
}
```

**After:**
```kotlin
// Grant handles all this complexity internally
val grantManager = GrantFactory.create(context)

suspend fun requestCamera() {
    when (grantManager.request(AppGrant.CAMERA)) {
        GrantStatus.GRANTED -> onGranted()
        GrantStatus.DENIED -> onDenied()
        GrantStatus.DENIED_ALWAYS -> onPermanentlyDenied()
        GrantStatus.NOT_DETERMINED -> { /* rare */ }
    }
}
```

### Migration Checklist

- [ ] Identify all permission request code
- [ ] Replace Fragment/Activity-based logic with GrantManager
- [ ] Move permission logic to ViewModels
- [ ] Replace callbacks with suspend functions + enum
- [ ] Use GrantHandler for UI-driven flows
- [ ] Add GrantDialog for rationale/settings
- [ ] Delete custom permission wrapper code
- [ ] Test all flows

**Estimated Time:** 3-4 hours depending on complexity

---

## 4️⃣ From Native Android APIs

### Overview

If you're using `ActivityCompat.requestPermissions()` directly, Grant simplifies your code significantly.

### Before (Native Android)

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Setup launcher
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    // Show rationale
                    showRationaleDialog()
                } else {
                    // Permanently denied
                    showSettingsDialog()
                }
            }
        }
    }

    fun requestCamera() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showRationaleDialog()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(this)
            .setMessage("Camera needed for QR scanning")
            .setPositiveButton("Continue") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setMessage("Enable camera in Settings")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .show()
    }
}
```

**50+ lines of boilerplate!** 😱

### After (Grant)

```kotlin
class CameraViewModel(grantManager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = viewModelScope
    )

    fun requestCamera() {
        cameraGrant.request { openCamera() }
    }
}

@Composable
fun CameraScreen(viewModel: CameraViewModel) {
    GrantDialog(handler = viewModel.cameraGrant)

    Button(onClick = { viewModel.requestCamera() }) {
        Text("Take Photo")
    }
}
```

**10 lines total!** 🎉 (80% code reduction)

### Migration Checklist

- [ ] Remove `ActivityResultLauncher` boilerplate
- [ ] Remove `shouldShowRequestPermissionRationale` checks
- [ ] Remove custom dialog code
- [ ] Replace with GrantManager + GrantHandler
- [ ] Add GrantDialog
- [ ] Test all flows
- [ ] Celebrate code reduction! 🎉

**Estimated Time:** 30 minutes per permission

---

## 5️⃣ Common Migration Patterns

### Pattern: Checking Permission Before Feature

**Before:**
```kotlin
fun openFeature() {
    if (hasPermission()) {
        doFeature()
    } else {
        requestPermission()
    }
}
```

**After:**
```kotlin
fun openFeature() {
    grantHandler.request {
        doFeature()  // Only runs when granted
    }
}
```

### Pattern: Multiple Permissions

**Before:**
```kotlin
fun requestMultiple() {
    requestPermissions(arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.MICROPHONE
    ))
}
```

**After (Option A - Sequential):**
```kotlin
suspend fun requestMultiple() {
    val camera = grantManager.request(AppGrant.CAMERA)
    val mic = grantManager.request(AppGrant.MICROPHONE)

    if (camera == GrantStatus.GRANTED && mic == GrantStatus.GRANTED) {
        startRecording()
    }
}
```

**After (Option B - GrantGroupHandler v1.1.0+):**
```kotlin
val multiGrant = GrantGroupHandler(
    grantManager = grantManager,
    grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
    scope = viewModelScope,
    mode = GrantGroupMode.ALL_REQUIRED
)

multiGrant.request { startRecording() }
```

### Pattern: Permission + Service Check

**Before:**
```kotlin
fun requestLocation() {
    if (hasLocationPermission()) {
        if (isGpsEnabled()) {
            startLocationTracking()
        } else {
            showEnableGpsDialog()
        }
    } else {
        requestLocationPermission()
    }
}
```

**After:**
```kotlin
suspend fun requestLocation() {
    // 1. Check service first
    val serviceManager = ServiceFactory.create(context)
    if (!serviceManager.isLocationEnabled()) {
        serviceManager.openLocationSettings()
        return
    }

    // 2. Then check permission
    when (grantManager.request(AppGrant.LOCATION)) {
        GrantStatus.GRANTED -> startLocationTracking()
        else -> showError()
    }
}
```

### Pattern: Custom Permissions (Android 15+)

**Before:**
```kotlin
// Wait for library to add Android 15 permission...
// Can't use new features yet 😞
```

**After:**
```kotlin
val android15Permission = RawPermission(
    identifier = "BODY_SENSORS_BACKGROUND",
    androidPermissions = listOf(
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND"
    ),
    iosUsageKey = null
)

// Use immediately! 🎉
grantManager.request(android15Permission)
```

---

## 6️⃣ Troubleshooting

### Issue: Permissions Not Working on iOS

**Symptom:** App crashes with SIGABRT on iOS when requesting permission.

**Cause:** Missing Info.plist keys.

**Solution:**
1. Enable logging: `GrantLogger.isEnabled = true`
2. Check logs for missing keys
3. Add required keys to Info.plist

See: [iOS Info.plist Setup](platform-specific/ios/info-plist.md)

---

### Issue: Android Process Death Causing Hangs

**Symptom:** After process death, permission requests hang for 60 seconds.

**Solution:** Grant fixes this automatically! Just upgrade to Grant.

---

### Issue: Material3 Version Conflict

**Symptom:** Gradle resolution errors with Material3.

**Solution:**
```kotlin
// Option 1: Use grant-core only (no Compose UI)
dependencies {
    implementation("dev.brewkits:grant-core:1.0.2")
    // Build your own dialogs
}

// Option 2: Override Material3 version
dependencies {
    implementation("dev.brewkits:grant-compose:1.0.2")
    implementation("androidx.compose.material3:material3:YOUR_VERSION") {
        force = true
    }
}
```

See: [Dependency Management](DEPENDENCY_MANAGEMENT.md)

---

### Issue: Tests Not Working

**Symptom:** Permission tests fail.

**Solution:** Use `FakeGrantManager`:
```kotlin
@Test
fun testCameraFeature() = runTest {
    val fakeManager = FakeGrantManager(defaultStatus = GrantStatus.GRANTED)

    val handler = GrantHandler(
        grantManager = fakeManager,
        grant = AppGrant.CAMERA,
        scope = this
    )

    // Test your logic
}
```

See: [Testing Guide](TESTING.md)

---

## 📚 Additional Resources

- [Quick Start Guide](getting-started/quick-start.md)
- [Best Practices](BEST_PRACTICES.md)
- [API Documentation](https://brewkits.github.io/Grant/)
- [Example App](https://github.com/brewkits/Grant/tree/main/demo)

---

## 💬 Need Help?

- 📧 Email: datacenter111@gmail.com
- 🐛 Issues: [GitHub Issues](https://github.com/brewkits/Grant/issues)
- 💬 Discussions: [GitHub Discussions](https://github.com/brewkits/Grant/discussions)

---

**Happy Migration!** 🚀

If this guide helped you, please ⭐ star the repo on GitHub!
