# Grant vs moko-permissions - Comparison and Learnings

This document outlines what Grant library learned from moko-permissions, what we do better, and honest comparison between the two libraries.

---

## 📚 What We Learned from moko-permissions

moko-permissions is a well-established library that taught us valuable lessons:

### 1. Architecture Patterns

#### Delegate Pattern for iOS (✅ Learned and Applied)
```kotlin
// iOS requires delegates for async permission callbacks
internal class LocationManagerDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    private val locationManager = CLLocationManager()
    private var continuation: Continuation<CLAuthorizationStatus>? = null

    // Handle async authorization changes
    override fun locationManagerDidChangeAuthorization(...) {
        continuation?.resume(status)
    }
}
```

**What we learned:**
- iOS CoreLocation, CoreBluetooth require delegate pattern
- Must handle async callbacks with continuations
- Need proper memory management (retain/release cycles)

#### Transparent Activity Pattern for Android (✅ Learned and Applied)
```kotlin
// Android uses transparent activity for permission requests
class GrantRequestActivity : ComponentActivity() {
    private var requestMultipleGrantsLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestMultipleGrantsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            // Handle result
        }
    }
}
```

**What we learned:**
- Activity Result API is the modern way (not onRequestPermissionsResult)
- Transparent activity provides better UX than fragment-based approach
- Need lifecycle safety to prevent memory leaks

### 2. Platform-Specific Threading

#### iOS Main Thread Requirement (✅ Learned and Applied)
```kotlin
internal object MainRunDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        NSRunLoop.mainRunLoop.performBlock {
            block.run()
        }
    }
}
```

**What we learned:**
- iOS framework APIs MUST run on main thread
- CoreLocation, CoreBluetooth will crash if called from background thread
- Need custom dispatcher to ensure thread safety

### 3. Permission State Management

#### Request ID Pattern (✅ Learned and Applied)
```kotlin
// Each request gets unique ID to handle concurrent requests
companion object {
    private val pendingResults = ConcurrentHashMap<String, MutableStateFlow<GrantResult>>()

    fun requestGrants(context: Context, grants: Array<String>): String {
        val requestId = UUID.randomUUID().toString()
        val resultFlow = MutableStateFlow(GrantResult.PENDING)
        pendingResults[requestId] = resultFlow
        return requestId
    }
}
```

**What we learned:**
- Multiple simultaneous permission requests need unique tracking
- ConcurrentHashMap prevents race conditions
- StateFlow provides reactive updates

---

## 🏆 What Grant Does BETTER than moko-permissions

### 1. Built-in UI Dialog Handling (Major Improvement)

#### moko-permissions (Manual Handling)
```kotlin
// User must manually show dialogs and handle all states
try {
    val status = permissionsController.providePermission(Permission.CAMERA)
} catch (e: DeniedException) {
    // Show rationale dialog manually
    showRationaleDialog()
} catch (e: DeniedAlwaysException) {
    // Show settings dialog manually
    showSettingsDialog()
}
```

#### Grant (Automatic Handling) ✅
```kotlin
// GrantHandler automatically shows rationale and settings dialogs
cameraGrant.request(
    rationaleMessage = "Camera is needed for photos",
    settingsMessage = "Enable Camera in Settings"
) {
    // Success callback
    takePhoto()
}
```

**Why Grant is better:**
- ✅ Zero boilerplate - no try-catch needed
- ✅ Automatic rationale dialog when user denies
- ✅ Automatic settings dialog when permanently denied
- ✅ Cleaner API - single request() function
- ✅ Consistent UX across all permissions

### 2. Android 11+ Location Handling (Better Implementation)

#### moko-permissions (Problematic)
```kotlin
// Requests both FINE and BACKGROUND location at once
// This causes background permission to be IGNORED on Android 11+
permissionsController.providePermission(
    Permission.LOCATION,
    Permission.LOCATION_BACKGROUND
)
```

#### Grant (Proper 2-Step Flow) ✅
```kotlin
// Step 1: Request foreground first
locationGrant.request(...) {
    // Step 2: Only request background AFTER foreground granted
    locationAlwaysGrant.request(...) {
        // Now background location works!
    }
}
```

**Why Grant is better:**
- ✅ Follows Android 11+ best practices
- ✅ Automatic 2-step flow for background location
- ✅ Background permission actually works (moko fails here)
- ✅ Proper user education about privacy implications

### 3. Service Status Checking (Exclusive Feature)

#### moko-permissions (Missing Feature)
```kotlin
// ❌ Cannot check if Bluetooth/Location services are enabled
// Can only check permission status
val status = permissionsController.getPermissionState(Permission.BLUETOOTH)
// Returns GRANTED but Bluetooth might be OFF!
```

#### Grant (Full Service Support) ✅
```kotlin
// ✅ Check both permission AND service status
val permissionGranted = grantManager.checkStatus(AppGrant.BLUETOOTH) == GrantStatus.GRANTED
val serviceEnabled = serviceManager.checkBluetoothService() == ServiceStatus.ENABLED

if (!serviceEnabled) {
    // Guide user to enable Bluetooth service
    serviceManager.openBluetoothSettings()
}
```

**Why Grant is better:**
- ✅ Distinguishes between permission and service status
- ✅ Prevents "granted but not working" confusion
- ✅ Direct links to service settings
- ✅ Better user experience

### 4. iOS Simulator Support (Better Developer Experience)

#### moko-permissions (Simulator Issues)
```kotlin
// Bluetooth fails on iOS Simulator
// Motion sensor fails on iOS Simulator
// Developers must test on real devices only
```

#### Grant (Smart Mocking) ✅
```kotlin
// Automatically detects simulator and grants hardware permissions
internal object SimulatorDetector {
    fun isSimulator(): Boolean = TARGET_OS_SIMULATOR == 1L
}

// Auto-grant Bluetooth/Motion on simulator
if (SimulatorDetector.isSimulator()) {
    return GrantStatus.GRANTED
}
```

**Why Grant is better:**
- ✅ Developers can test on simulator
- ✅ Faster development iteration
- ✅ Only mocks hardware-dependent permissions
- ✅ Camera/Location still work normally

### 5. Status-Based Error Handling (Cleaner API)

#### moko-permissions (Exception-Based)
```kotlin
try {
    permissionsController.providePermission(Permission.CAMERA)
} catch (e: DeniedException) {
    // Soft denial - show rationale
    handleSoftDenial()
} catch (e: DeniedAlwaysException) {
    // Hard denial - open settings
    handleHardDenial()
}
```

#### Grant (Status Enum) ✅
```kotlin
val status = grantManager.request(AppGrant.CAMERA)
when (status) {
    GrantStatus.GRANTED -> openCamera()
    GrantStatus.DENIED -> showRationale()
    GrantStatus.DENIED_ALWAYS -> openSettings()
    GrantStatus.NOT_DETERMINED -> {}
}
```

**Why Grant is better:**
- ✅ No try-catch required
- ✅ Cleaner, more functional approach
- ✅ Easier to read and understand
- ✅ Type-safe with sealed classes

### 6. Compose Integration (First-Class Support)

#### moko-permissions (Basic Compose Support)
```kotlin
// moko-permissions-compose has basic integration
val permissionsController = rememberPermissionsController()
// But still requires manual dialog handling
```

#### Grant (Complete Compose Module) ✅
```kotlin
// grant-compose: Complete UI solution
GrantDialog(handler = cameraGrant) // Automatic dialogs
GrantPermissionButton(handler = cameraGrant) // Pre-built button
GrantStatusIndicator(handler = cameraGrant) // Status UI

// Everything just works
```

**Why Grant is better:**
- ✅ Complete grant-compose module
- ✅ Pre-built composables for common use cases
- ✅ Material 3 design
- ✅ Automatic dialog management

---

## ⚖️ What moko-permissions Does Better

To be fair and honest, here are areas where moko-permissions excels:

### 1. Maturity and Battle-Testing
- ✅ Used in production by many apps for years
- ✅ More edge cases discovered and fixed
- ✅ Larger community and more GitHub stars
- ✅ More real-world feedback

**Verdict:** moko-permissions is more proven in production. Grant is newer (2026) but built on lessons learned from moko.

### 2. Broader Platform Support
- ✅ moko supports more KMP targets (JS, Desktop, etc.)
- ⚠️ Grant focuses on mobile-only (Android + iOS)

**Verdict:** If you need JS/Desktop support, use moko. For mobile-only apps, Grant is better.

### 3. Documentation and Examples
- ✅ moko has extensive documentation
- ✅ More blog posts and tutorials available
- ✅ Larger ecosystem of examples

**Verdict:** moko has more learning resources. Grant documentation is comprehensive but newer.

---

## 📊 Feature Comparison Table

| Feature | moko-permissions | Grant | Winner |
|---------|-----------------|-------|---------|
| **Core Functionality** |
| Check permission status | ✅ | ✅ | Tie |
| Request permissions | ✅ | ✅ | Tie |
| Open app settings | ✅ | ✅ Better error handling | 🏆 Grant |
| Multiple permissions | ✅ | ✅ | Tie |
| **UI/UX** |
| Automatic rationale dialog | ❌ Manual | ✅ Automatic | 🏆 Grant |
| Automatic settings dialog | ❌ Manual | ✅ Automatic | 🏆 Grant |
| Compose integration | Basic | ✅ Complete module | 🏆 Grant |
| Pre-built UI components | ❌ | ✅ | 🏆 Grant |
| **Android Features** |
| Android 11+ background location | ⚠️ Broken | ✅ Proper 2-step flow | 🏆 Grant |
| Transparent Activity pattern | ✅ | ✅ | Tie |
| Activity Result API | ✅ | ✅ | Tie |
| **iOS Features** |
| CoreLocation delegate | ✅ | ✅ | Tie |
| CoreBluetooth delegate | ✅ | ✅ | Tie |
| Main thread safety | ✅ | ✅ | Tie |
| Simulator support | ⚠️ Limited | ✅ Smart mocking | 🏆 Grant |
| **Service Management** |
| Check Bluetooth service | ❌ | ✅ ServiceManager | 🏆 Grant |
| Check Location service | ❌ | ✅ ServiceManager | 🏆 Grant |
| Open service settings | ❌ | ✅ | 🏆 Grant |
| **Developer Experience** |
| Error handling | Exception-based | Status-based | 🏆 Grant |
| API simplicity | Complex try-catch | Single request() | 🏆 Grant |
| Type safety | ✅ | ✅ | Tie |
| Zero dependencies | ❌ (moko-resources) | ✅ Pure Kotlin | 🏆 Grant |
| **Production Readiness** |
| Battle-tested | ✅ Years in production | ⚠️ Newer (2026) | 🏆 moko |
| Community size | ✅ Large | 🆕 Growing | 🏆 moko |
| Platform support | ✅ All KMP targets | Android + iOS only | 🏆 moko |
| Documentation | ✅ Extensive | ✅ Comprehensive | Tie |

**Score:** Grant wins 13, moko wins 3, Tie 9

---

## 🎯 Which Library Should You Use?

### Use Grant if:
- ✅ You're building a **mobile-only** KMP app (Android + iOS)
- ✅ You want **automatic UI handling** (rationale + settings dialogs)
- ✅ You need **Android 11+ background location** to work properly
- ✅ You want **service status checking** (Bluetooth/Location enabled/disabled)
- ✅ You prefer **clean, functional API** without try-catch
- ✅ You're using **Jetpack Compose** and want pre-built components
- ✅ You want **faster simulator development** (auto-mock hardware permissions)

### Use moko-permissions if:
- ✅ You need **JS/Desktop/Web support** (broader platform support)
- ✅ You want a **battle-tested** library with years of production use
- ✅ You prefer **larger community** and more GitHub stars
- ✅ You want more **blog posts and tutorials** available
- ✅ You need **proven stability** over newer features

---

## 🤝 Conclusion

**Grant library learned valuable architectural patterns from moko-permissions:**
- Delegate pattern for iOS
- Transparent Activity pattern for Android
- Threading requirements and safety
- Permission state management

**Grant improved upon moko in key areas:**
- Automatic UI dialog handling (major DX improvement)
- Android 11+ background location (fixed broken behavior)
- Service status checking (exclusive feature)
- iOS Simulator support (better DX)
- Cleaner API without exceptions

**moko-permissions remains strong in:**
- Production maturity and battle-testing
- Broader platform support (JS/Desktop)
- Larger community

Both are excellent libraries. Grant is the **better choice for modern mobile-only KMP apps** that prioritize developer experience and automatic UI handling. moko-permissions is the **safer choice for maximum platform support and proven stability**.

---

*Last Updated: 2026-01-26*
