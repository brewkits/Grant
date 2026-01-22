# Service Checking Guide

## Why Service Checking Matters

**Problem**: Grant GRANTED ≠ Feature Working

When a user grants location grant, it doesn't mean location features will work. The GPS service might be disabled. Same for Bluetooth, WiFi, and other system services.

**Real-world scenarios**:
- ✅ Location grant: GRANTED
- ❌ GPS service: DISABLED
- 🔴 Result: Location features don't work!

This library provides a complete solution: check both grants AND services.

---

## Quick Start

### 1. Check if Location is Ready

```kotlin
class LocationViewModel(
    private val checker: GrantAndServiceChecker
) : ViewModel() {

    fun startLocationTracking() = viewModelScope.launch {
        when (val status = checker.checkLocationReady()) {
            is LocationReadyStatus.Ready -> {
                // ✅ Everything is ready!
                startGpsTracking()
            }
            is LocationReadyStatus.GrantDenied -> {
                // ❌ Grant denied
                showGrantDialog()
            }
            is LocationReadyStatus.ServiceDisabled -> {
                // ❌ GPS disabled
                showEnableGpsDialog()
            }
            is LocationReadyStatus.BothRequired -> {
                // ❌ Both grant denied AND GPS disabled
                showBothRequiredDialog()
            }
            is LocationReadyStatus.Unknown -> {
                // ⚠️ Unable to determine status
                showErrorDialog()
            }
        }
    }
}
```

### 2. Check if Bluetooth is Ready

```kotlin
fun startBluetoothScan() = viewModelScope.launch {
    when (checker.checkBluetoothReady()) {
        is BluetoothReadyStatus.Ready -> startBleScanning()
        is BluetoothReadyStatus.GrantDenied -> showGrantDialog()
        is BluetoothReadyStatus.ServiceDisabled -> showEnableBluetoothDialog()
        is BluetoothReadyStatus.BothRequired -> showBothRequiredDialog()
        else -> showErrorDialog()
    }
}
```

### 3. Generic Service + Grant Check

```kotlin
// Check any grant + service combination
val status = checker.checkReady(
    grant = AppGrant.LOCATION,
    serviceType = ServiceType.LOCATION_GPS
)

if (status.isReady) {
    startFeature()
} else {
    showDialog(status.message) // "Grant and service both required"
}
```

---

## Service Types

The library supports checking these system services:

```kotlin
enum class ServiceType {
    LOCATION_GPS,      // GPS/Location service
    BLUETOOTH,         // Bluetooth service
    WIFI,              // Wi-Fi service
    NFC,               // NFC service (Android only)
    CAMERA_HARDWARE    // Camera hardware availability
}
```

---

## Service Status

Each service can have one of these states:

```kotlin
enum class ServiceStatus {
    ENABLED,        // ✅ Service is ready to use
    DISABLED,       // ❌ Service disabled by user (e.g., GPS off)
    NOT_AVAILABLE,  // ⚠️ Service not available on device (e.g., no NFC chip)
    UNKNOWN         // ❓ Unable to determine status
}
```

---

## Using ServiceManager Directly

For more control, use `ServiceManager` directly:

```kotlin
class MyViewModel(
    private val serviceManager: ServiceManager
) : ViewModel() {

    suspend fun checkGps() {
        val status = serviceManager.checkServiceStatus(ServiceType.LOCATION_GPS)

        when (status) {
            ServiceStatus.ENABLED -> {
                // GPS is on
            }
            ServiceStatus.DISABLED -> {
                // Prompt user to enable GPS
                if (serviceManager.openServiceSettings(ServiceType.LOCATION_GPS)) {
                    // Settings opened successfully
                }
            }
            ServiceStatus.NOT_AVAILABLE -> {
                // Device has no GPS
                showNoGpsDialog()
            }
            ServiceStatus.UNKNOWN -> {
                // Unable to check
                showErrorDialog()
            }
        }
    }

    suspend fun isWifiEnabled(): Boolean {
        return serviceManager.isServiceEnabled(ServiceType.WIFI)
    }
}
```

---

## Platform-Specific Initialization

### Android

Service checking requires Android Context. Initialize in your Application class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // REQUIRED: Initialize ServiceFactory with context
        ServiceFactory.init(this)

        // Start Koin
        startKoin {
            modules(
                grantModule,           // Includes ServiceManager
                grantPlatformModule
            )
        }
    }
}
```

### iOS

No initialization needed! ServiceFactory works automatically on iOS.

---

## Koin DI Integration

The library provides automatic DI setup:

```kotlin
val grantModule = module {
    // Grant Manager
    single<grantManager> { MygrantManager(...) }

    // Service Manager (automatically included)
    single<ServiceManager> { ServiceFactory.createServiceManager() }

    // Combined Checker (automatically included)
    single { GrantAndServiceChecker(grantManager = get(), serviceManager = get()) }
}
```

Just inject where needed:

```kotlin
class MyViewModel(
    private val checker: GrantAndServiceChecker  // Auto-injected
) : ViewModel()
```

---

## Platform Capabilities

### Android

| Service         | Can Check | Can Open Settings |
|-----------------|-----------|-------------------|
| Location/GPS    | ✅         | ✅                 |
| Bluetooth       | ✅         | ✅                 |
| Wi-Fi           | ✅         | ✅                 |
| NFC             | ✅         | ✅                 |
| Camera Hardware | ✅         | ✅ (General)       |

### iOS

| Service         | Can Check | Can Open Settings |
|-----------------|-----------|-------------------|
| Location/GPS    | ✅         | ✅ (Main Settings) |
| Bluetooth       | ⚠️ Limited | ✅ (Main Settings) |
| Wi-Fi           | ❌         | ✅ (Main Settings) |
| NFC             | ❌         | ✅ (Main Settings) |
| Camera Hardware | ❌         | ✅ (Main Settings) |

**iOS Limitations**:
- Bluetooth: Checking requires CBCentralManager, which is complex. Currently returns `ENABLED` by default.
- Wi-Fi/NFC: iOS doesn't provide APIs to check these programmatically.
- Settings: iOS can only open the main Settings app, not specific service settings.

---

## Best Practices

### 1. Always Check Both Grant AND Service

```kotlin
// ❌ BAD: Only checking grant
if (grantManager.checkStatus(AppGrant.LOCATION) == GrantStatus.GRANTED) {
    startTracking() // Might fail if GPS is off!
}

// ✅ GOOD: Check both grant and service
when (checker.checkLocationReady()) {
    LocationReadyStatus.Ready -> startTracking()
    else -> showAppropriateDialog()
}
```

### 2. Provide Clear User Guidance

```kotlin
when (checker.checkLocationReady()) {
    is LocationReadyStatus.ServiceDisabled -> {
        showDialog(
            title = "GPS is Off",
            message = "Please turn on GPS to use location features",
            action = "Enable GPS"
        ) {
            serviceManager.openServiceSettings(ServiceType.LOCATION_GPS)
        }
    }
    // ...
}
```

### 3. Handle Service Changes

Services can be enabled/disabled at any time. Check before each use:

```kotlin
fun startFeature() = viewModelScope.launch {
    // Always check before using the feature
    if (checker.checkLocationReady() is LocationReadyStatus.Ready) {
        startGpsTracking()
    } else {
        showEnableGpsPrompt()
    }
}
```

### 4. Combine with Grant Flow

```kotlin
class LocationFeatureViewModel(
    private val grantHandler: GrantHandler,
    private val checker: GrantAndServiceChecker
) : ViewModel() {

    fun enableLocationFeature() {
        // Step 1: Request grant
        grantHandler.request {
            // Step 2: Check service after grant granted
            viewModelScope.launch {
                when (checker.checkLocationReady()) {
                    LocationReadyStatus.Ready -> startFeature()
                    LocationReadyStatus.ServiceDisabled -> promptEnableGps()
                    else -> showError()
                }
            }
        }
    }
}
```

---

## UI Examples

### Compose Dialog

```kotlin
@Composable
fun LocationReadyHandler(checker: GrantAndServiceChecker, onReady: () -> Unit) {
    val status by rememberUpdatedState(checker.checkLocationReady())

    when (val s = status) {
        LocationReadyStatus.Ready -> {
            LaunchedEffect(Unit) { onReady() }
        }
        is LocationReadyStatus.ServiceDisabled -> {
            AlertDialog(
                onDismissRequest = { /* dismiss */ },
                title = { Text("GPS is Off") },
                text = { Text("Please enable GPS to continue") },
                confirmButton = {
                    Button(onClick = {
                        serviceManager.openServiceSettings(ServiceType.LOCATION_GPS)
                    }) {
                        Text("Enable GPS")
                    }
                }
            )
        }
        // ... handle other cases
    }
}
```

### Material Dialog (Android View)

```kotlin
private fun showEnableGpsDialog() {
    MaterialAlertDialogBuilder(context)
        .setTitle("GPS Required")
        .setMessage("Location features require GPS to be enabled")
        .setPositiveButton("Enable GPS") { _, _ ->
            lifecycleScope.launch {
                serviceManager.openServiceSettings(ServiceType.LOCATION_GPS)
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

---

## Testing

### Mock ServiceManager

```kotlin
class MockServiceManager : ServiceManager {
    var gpsEnabled = true
    var bluetoothEnabled = true

    override suspend fun checkServiceStatus(service: ServiceType): ServiceStatus {
        return when (service) {
            ServiceType.LOCATION_GPS ->
                if (gpsEnabled) ServiceStatus.ENABLED else ServiceStatus.DISABLED
            ServiceType.BLUETOOTH ->
                if (bluetoothEnabled) ServiceStatus.ENABLED else ServiceStatus.DISABLED
            else -> ServiceStatus.NOT_AVAILABLE
        }
    }

    override suspend fun isServiceEnabled(service: ServiceType): Boolean {
        return checkServiceStatus(service) == ServiceStatus.ENABLED
    }

    override suspend fun openServiceSettings(service: ServiceType): Boolean = true
}
```

### Unit Test Example

```kotlin
@Test
fun `when GPS disabled should show service disabled status`() = runTest {
    val mockServiceManager = MockServiceManager().apply {
        gpsEnabled = false
    }

    val checker = GrantAndServiceChecker(
        grantManager = mockgrantManager,
        serviceManager = mockServiceManager
    )

    val status = checker.checkLocationReady()

    assertTrue(status is LocationReadyStatus.ServiceDisabled)
}
```

---

## Troubleshooting

### Android: "ServiceFactory not initialized"

**Error**: `IllegalStateException: ServiceFactory not initialized`

**Solution**: Call `ServiceFactory.init(context)` in `Application.onCreate()`:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceFactory.init(this)  // Add this!
    }
}
```

### iOS: Bluetooth Always Shows ENABLED

This is expected. iOS doesn't provide a simple API to check Bluetooth status. To properly check Bluetooth on iOS, you need to use CBCentralManager, which is more complex and beyond the scope of this library's service checking.

### Service Check Returns UNKNOWN

This means the library couldn't determine the service status. Possible causes:
- Security restrictions
- Device-specific limitations
- Platform API unavailable

Treat `UNKNOWN` as "might not work" and provide appropriate fallback UI.

---

## See Also

- [Grants Guide](GRANTS.md) - Grant management
- [Quick Start](QUICK_START.md) - Basic setup
- [Architecture](ARCHITECTURE.md) - System design
