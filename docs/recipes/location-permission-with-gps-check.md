# Location Permission with GPS Service Check

## Overview

This recipe shows how to request location permission AND ensure GPS is enabled, a common pattern in location-based apps.

**Grant's Philosophy:** Grant provides permission primitives. You compose them to build custom flows that fit your app's needs.

---

## Basic Flow (Recommended)

Use Grant's existing APIs to build a complete location flow:

```kotlin
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.ServiceManager

class LocationHelper(
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {
    suspend fun requestLocationWithServiceCheck(): LocationFlowResult {
        // Step 1: Request permission
        val permissionStatus = grantManager.request(AppGrant.LOCATION)

        when (permissionStatus) {
            GrantStatus.DENIED -> {
                return LocationFlowResult.PermissionDenied(canRetry = true)
            }
            GrantStatus.DENIED_ALWAYS -> {
                return LocationFlowResult.PermissionDenied(canRetry = false)
            }
            GrantStatus.NOT_DETERMINED -> {
                return LocationFlowResult.Error("Unexpected state")
            }
            GrantStatus.GRANTED -> {
                // Continue to GPS check
            }
        }

        // Step 2: Check if GPS is enabled
        if (!serviceManager.isLocationEnabled()) {
            return LocationFlowResult.GpsDisabled
        }

        // Both permission AND GPS are ready!
        return LocationFlowResult.Ready
    }

    fun openLocationSettings() {
        serviceManager.openLocationSettings()
    }
}

// Result types
sealed interface LocationFlowResult {
    object Ready : LocationFlowResult
    data class PermissionDenied(val canRetry: Boolean) : LocationFlowResult
    object GpsDisabled : LocationFlowResult
    data class Error(val message: String) : LocationFlowResult
}
```

**Usage in ViewModel:**

```kotlin
class MapViewModel(
    private val locationHelper: LocationHelper
) : ViewModel() {

    val locationState = MutableStateFlow<LocationState>(LocationState.Idle)

    fun requestLocation() {
        viewModelScope.launch {
            locationState.value = LocationState.Loading

            when (val result = locationHelper.requestLocationWithServiceCheck()) {
                LocationFlowResult.Ready -> {
                    // Start location tracking
                    startLocationUpdates()
                    locationState.value = LocationState.Ready
                }

                is LocationFlowResult.PermissionDenied -> {
                    if (result.canRetry) {
                        locationState.value = LocationState.ShowRationale
                    } else {
                        locationState.value = LocationState.ShowSettingsPrompt
                    }
                }

                LocationFlowResult.GpsDisabled -> {
                    locationState.value = LocationState.ShowGpsPrompt
                }

                is LocationFlowResult.Error -> {
                    locationState.value = LocationState.Error(result.message)
                }
            }
        }
    }

    fun openLocationSettings() {
        locationHelper.openLocationSettings()
    }
}
```

---

## Advanced: Google Play Services Integration (Android)

For apps that want the in-app GPS dialog (instead of manual Settings navigation):

### Step 1: Add Dependency

```gradle
// app/build.gradle.kts
dependencies {
    implementation("dev.brewkits:grant-core:1.0.2")
    implementation("com.google.android.gms:play-services-location:21.1.0")
}
```

### Step 2: SettingsClient Integration

```kotlin
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await

class LocationHelperWithPlayServices(
    private val context: Context,
    private val grantManager: GrantManager,
    private val serviceManager: ServiceManager
) {

    suspend fun requestLocationWithGpsDialog(): LocationFlowResult {
        // Step 1: Request permission first
        val permissionStatus = grantManager.request(AppGrant.LOCATION)

        if (permissionStatus != GrantStatus.GRANTED) {
            return LocationFlowResult.PermissionDenied(
                canRetry = permissionStatus != GrantStatus.DENIED_ALWAYS
            )
        }

        // Step 2: Check GPS via Play Services
        val gpsResult = checkGpsViaPlayServices()

        return when (gpsResult) {
            GpsCheckResult.Enabled -> LocationFlowResult.Ready
            GpsCheckResult.UserDeclined -> LocationFlowResult.GpsDisabled
            GpsCheckResult.PlayServicesUnavailable -> {
                // Fallback to manual settings
                if (!serviceManager.isLocationEnabled()) {
                    LocationFlowResult.GpsDisabled
                } else {
                    LocationFlowResult.Ready
                }
            }
        }
    }

    private suspend fun checkGpsViaPlayServices(): GpsCheckResult {
        if (!isPlayServicesAvailable()) {
            return GpsCheckResult.PlayServicesUnavailable
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // 10 seconds
        ).build()

        val settingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .build()

        val client = LocationServices.getSettingsClient(context)

        return try {
            client.checkLocationSettings(settingsRequest).await()
            GpsCheckResult.Enabled
        } catch (e: ApiException) {
            when (e.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    // GPS is off, but can show dialog
                    try {
                        val resolvable = e as ResolvableApiException
                        resolvable.startResolutionForResult(
                            context as Activity,
                            REQUEST_CHECK_SETTINGS
                        )
                        // Wait for result in Activity.onActivityResult()
                        GpsCheckResult.UserDeclined // Assume declined for now
                    } catch (e: Exception) {
                        GpsCheckResult.PlayServicesUnavailable
                    }
                }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                    GpsCheckResult.PlayServicesUnavailable
                }
                else -> GpsCheckResult.PlayServicesUnavailable
            }
        }
    }

    private fun isPlayServicesAvailable(): Boolean {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    companion object {
        const val REQUEST_CHECK_SETTINGS = 9001
    }
}

enum class GpsCheckResult {
    Enabled,
    UserDeclined,
    PlayServicesUnavailable
}
```

### Step 3: Handle Dialog Result

```kotlin
class LocationActivity : ComponentActivity() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LocationHelperWithPlayServices.REQUEST_CHECK_SETTINGS) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    // User enabled GPS via dialog
                    startLocationTracking()
                }
                Activity.RESULT_CANCELED -> {
                    // User declined
                    showGpsRequiredMessage()
                }
            }
        }
    }
}
```

---

## Compose Integration

```kotlin
@Composable
fun LocationPermissionScreen(
    viewModel: LocationViewModel
) {
    val locationState by viewModel.locationState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val state = locationState) {
            LocationState.Idle -> {
                Button(onClick = { viewModel.requestLocation() }) {
                    Text("Request Location")
                }
            }

            LocationState.Loading -> {
                CircularProgressIndicator()
            }

            LocationState.Ready -> {
                Text("Location ready! 🎯")
                // Show map or location features
            }

            LocationState.ShowRationale -> {
                Text("We need location access to show nearby places")
                Button(onClick = { viewModel.requestLocation() }) {
                    Text("Allow Location")
                }
            }

            LocationState.ShowSettingsPrompt -> {
                Text("Location permission denied permanently")
                Button(onClick = { viewModel.openLocationSettings() }) {
                    Text("Open Settings")
                }
            }

            LocationState.ShowGpsPrompt -> {
                Text("GPS is disabled")
                Button(onClick = { viewModel.openLocationSettings() }) {
                    Text("Enable GPS")
                }
            }

            is LocationState.Error -> {
                Text("Error: ${state.message}")
            }
        }
    }
}

sealed interface LocationState {
    object Idle : LocationState
    object Loading : LocationState
    object Ready : LocationState
    object ShowRationale : LocationState
    object ShowSettingsPrompt : LocationState
    object ShowGpsPrompt : LocationState
    data class Error(val message: String) : LocationState
}
```

---

## Platform Differences

### Android

**Separate Permission & Service:**
- Permission: `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- Service: GPS/Network location provider

**Two approaches:**
1. **Manual Settings** (Simple)
   - Use `serviceManager.openLocationSettings()`
   - User navigates to Settings manually

2. **Play Services Dialog** (Advanced)
   - Uses `SettingsClient` API
   - Shows in-app dialog to enable GPS
   - Requires Play Services dependency

### iOS

**Unified System:**
- `CLLocationManager` handles both permission AND service
- No separate GPS check needed
- No "enable GPS" dialog exists

**iOS Implementation:**

```kotlin
// iOS doesn't need GPS check
suspend fun requestLocationIOS(): LocationFlowResult {
    val status = grantManager.request(AppGrant.LOCATION)

    return when (status) {
        GrantStatus.GRANTED -> LocationFlowResult.Ready
        GrantStatus.DENIED -> LocationFlowResult.PermissionDenied(canRetry = true)
        GrantStatus.DENIED_ALWAYS -> LocationFlowResult.PermissionDenied(canRetry = false)
        else -> LocationFlowResult.Error("Unexpected state")
    }
}
```

---

## China Market (Huawei/HMS)

For devices without Google Play Services:

```kotlin
fun requestLocationChinaMarket(): LocationFlowResult {
    // Check if Play Services available
    if (!isPlayServicesAvailable(context)) {
        // Fallback to manual GPS check
        return if (serviceManager.isLocationEnabled()) {
            LocationFlowResult.Ready
        } else {
            // Guide user to Settings manually
            serviceManager.openLocationSettings()
            LocationFlowResult.GpsDisabled
        }
    }

    // Use Play Services if available
    return requestLocationWithGpsDialog()
}
```

---

## Testing

```kotlin
class LocationHelperTest {

    private lateinit var fakeGrantManager: FakeGrantManager
    private lateinit var fakeServiceManager: FakeServiceManager
    private lateinit var locationHelper: LocationHelper

    @Before
    fun setup() {
        fakeGrantManager = FakeGrantManager()
        fakeServiceManager = FakeServiceManager()
        locationHelper = LocationHelper(fakeGrantManager, fakeServiceManager)
    }

    @Test
    fun `when permission granted and GPS enabled, returns Ready`() = runTest {
        // Given
        fakeGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.GRANTED)
        fakeServiceManager.setLocationEnabled(true)

        // When
        val result = locationHelper.requestLocationWithServiceCheck()

        // Then
        assertEquals(LocationFlowResult.Ready, result)
    }

    @Test
    fun `when permission denied, returns PermissionDenied`() = runTest {
        // Given
        fakeGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.DENIED)

        // When
        val result = locationHelper.requestLocationWithServiceCheck()

        // Then
        assertTrue(result is LocationFlowResult.PermissionDenied)
        assertTrue((result as LocationFlowResult.PermissionDenied).canRetry)
    }

    @Test
    fun `when permission granted but GPS disabled, returns GpsDisabled`() = runTest {
        // Given
        fakeGrantManager.setStatus(AppGrant.LOCATION, GrantStatus.GRANTED)
        fakeServiceManager.setLocationEnabled(false)

        // When
        val result = locationHelper.requestLocationWithServiceCheck()

        // Then
        assertEquals(LocationFlowResult.GpsDisabled, result)
    }
}
```

---

## Best Practices

1. **Always request permission first** - Check GPS only after permission granted
2. **Provide clear UI feedback** - Explain why GPS is needed
3. **Handle all states** - Permission denied, GPS disabled, Play Services unavailable
4. **Fallback gracefully** - Use manual Settings if Play Services unavailable
5. **Test without Play Services** - Ensure app works on AOSP/HMS devices
6. **Platform-specific code** - Use expect/actual for different implementations

---

## See Also

- [ServiceManager Guide](../grant-core/SERVICES.md) - Check device services
- [Best Practices](../BEST_PRACTICES.md) - General permission patterns
- [Testing Guide](../TESTING.md) - Test permission flows

---

## Questions?

This recipe is maintained by the Grant team. If you have questions or improvements, please:
- Open an issue: https://github.com/brewkits/Grant/issues
- Discussions: https://github.com/brewkits/Grant/discussions
