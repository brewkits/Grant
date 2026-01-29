# Quick Start Guide

Get started with Grant in 5 minutes!

## Prerequisites

- **Kotlin**: 2.0.0 or higher
- **Compose Multiplatform**: 1.6.0 or higher (optional)
- **Android**: MinSDK 24 (Android 7.0)
- **iOS**: iOS 13.0 or higher

## Installation

See [Installation Guide](installation.md) for detailed setup instructions.

## Basic Setup

### 1. Create GrantManager

```kotlin
import dev.brewkits.grant.*

// In your ViewModel, Repository, or Composable
val grantManager = GrantFactory.create(context)
```

**That's it!** No Fragment, no Activity, no binding needed.

### 2. Check Permission Status

```kotlin
suspend fun checkCameraPermission() {
    val status = grantManager.checkStatus(AppGrant.CAMERA)

    when (status) {
        GrantStatus.GRANTED -> {
            // Permission granted - proceed with camera
            openCamera()
        }
        GrantStatus.NOT_DETERMINED -> {
            // Never asked before - safe to request
            println("Permission not requested yet")
        }
        GrantStatus.DENIED -> {
            // User denied, but can ask again
            // Show rationale and request again
            showRationale()
        }
        GrantStatus.DENIED_ALWAYS -> {
            // Permanently denied (user clicked "Don't ask again")
            // Must open Settings
            showSettingsPrompt()
        }
    }
}
```

### 3. Request Permission

```kotlin
suspend fun requestCameraPermission() {
    val status = grantManager.request(AppGrant.CAMERA)

    when (status) {
        GrantStatus.GRANTED -> openCamera()
        GrantStatus.DENIED -> showRationale()
        GrantStatus.DENIED_ALWAYS -> grantManager.openSettings()
        GrantStatus.NOT_DETERMINED -> { /* Shouldn't happen after request */ }
    }
}
```

## Compose Integration

### Simple Button Example

```kotlin
@Composable
fun CameraButton() {
    val context = LocalContext.current
    val grantManager = remember { GrantFactory.create(context) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                when (grantManager.request(AppGrant.CAMERA)) {
                    GrantStatus.GRANTED -> openCamera()
                    GrantStatus.DENIED -> showRationale()
                    GrantStatus.DENIED_ALWAYS -> grantManager.openSettings()
                    GrantStatus.NOT_DETERMINED -> {}
                }
            }
        }
    ) {
        Text("Take Photo")
    }
}
```

### With State Management

```kotlin
@Composable
fun CameraFeature() {
    val grantManager = rememberGrantManager()
    val scope = rememberCoroutineScope()
    var cameraStatus by remember { mutableStateOf(GrantStatus.NOT_DETERMINED) }

    // Check status on composition
    LaunchedEffect(Unit) {
        cameraStatus = grantManager.checkStatus(AppGrant.CAMERA)
    }

    Column {
        Text("Camera Status: $cameraStatus")

        Button(
            onClick = {
                scope.launch {
                    cameraStatus = grantManager.request(AppGrant.CAMERA)
                }
            },
            enabled = cameraStatus != GrantStatus.GRANTED
        ) {
            Text(when (cameraStatus) {
                GrantStatus.GRANTED -> "Camera Ready"
                GrantStatus.DENIED_ALWAYS -> "Open Settings"
                else -> "Request Camera"
            })
        }
    }
}
```

## Multiple Permissions

### Sequential Requests

```kotlin
suspend fun requestMediaPermissions() {
    // Request camera first
    if (grantManager.request(AppGrant.CAMERA) != GrantStatus.GRANTED) {
        return // User denied camera
    }

    // Then microphone
    if (grantManager.request(AppGrant.MICROPHONE) != GrantStatus.GRANTED) {
        return // User denied microphone
    }

    // All granted!
    startMediaRecording()
}
```

### Group Requests

```kotlin
suspend fun requestAllMediaPermissions() {
    val handler = GrantGroupHandler(
        grantManager = grantManager,
        grants = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.GALLERY
        )
    )

    val results = handler.requestAll()

    when {
        results.allGranted -> {
            // All permissions granted
            startApp()
        }
        results.allDeniedAlways -> {
            // All permanently denied
            grantManager.openSettings()
        }
        else -> {
            // Mixed results
            println("Granted: ${results.grantedGrants}")
            println("Denied: ${results.deniedGrants}")
        }
    }
}
```

## Service Checking

Grant includes built-in service checking:

```kotlin
val serviceManager = ServiceFactory.create(context)

// Check if GPS is enabled
if (!serviceManager.isLocationEnabled()) {
    // GPS is off - open settings
    serviceManager.openLocationSettings()
    return
}

// GPS is on - now check permission
val status = grantManager.request(AppGrant.LOCATION)
```

## Common Patterns

### Camera with Rationale

```kotlin
@Composable
fun SmartCameraButton() {
    val grantManager = rememberGrantManager()
    val scope = rememberCoroutineScope()
    var showRationale by remember { mutableStateOf(false) }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Camera Permission") },
            text = { Text("We need camera access to take photos for your profile") },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    scope.launch {
                        grantManager.request(AppGrant.CAMERA)
                    }
                }) {
                    Text("Grant")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Button(onClick = {
        scope.launch {
            val status = grantManager.checkStatus(AppGrant.CAMERA)
            when (status) {
                GrantStatus.GRANTED -> openCamera()
                GrantStatus.NOT_DETERMINED -> {
                    // First time - request directly
                    grantManager.request(AppGrant.CAMERA)
                }
                GrantStatus.DENIED -> {
                    // Show rationale before requesting again
                    showRationale = true
                }
                GrantStatus.DENIED_ALWAYS -> {
                    // Must open settings
                    grantManager.openSettings()
                }
            }
        }
    }) {
        Text("Take Photo")
    }
}
```

### Location with Service + Permission

```kotlin
suspend fun startLocationTracking(): Boolean {
    val serviceManager = ServiceFactory.create(context)

    // Step 1: Check service
    if (!serviceManager.isLocationEnabled()) {
        withContext(Dispatchers.Main) {
            showDialog("Please enable GPS in Settings")
        }
        serviceManager.openLocationSettings()
        return false
    }

    // Step 2: Check permission
    val status = grantManager.request(AppGrant.LOCATION)
    if (status != GrantStatus.GRANTED) {
        withContext(Dispatchers.Main) {
            if (status == GrantStatus.DENIED_ALWAYS) {
                showDialog("Location permission denied. Please enable in Settings")
                grantManager.openSettings()
            } else {
                showDialog("Location permission is required")
            }
        }
        return false
    }

    // All good!
    return true
}
```

## Next Steps

- [Android Setup](android-setup.md) - Configure Android permissions
- [iOS Setup](ios-setup.md) - Configure Info.plist
- [Permission Guide](../guides/permissions-guide.md) - Deep dive into permissions
- [Best Practices](../guides/best-practices.md) - Production-ready patterns

## Troubleshooting

### "Permission always returns NOT_DETERMINED"

**Android**: Check `AndroidManifest.xml` - ensure permission is declared
**iOS**: Check `Info.plist` - ensure usage description is added

### "Permission request does nothing"

**Android**: Ensure you're calling from a coroutine scope
**iOS**: Check Xcode console for errors

### "Dead click on Android 13+"

This should not happen with Grant! If it does, please [file an issue](https://github.com/brewkits/Grant/issues).

See [Dead Click Fix](../platform-specific/android/dead-click-fix.md) for technical details.
