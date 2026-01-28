# Custom Permissions with RawPermission

## Overview

Grant v1.1.0+ supports custom permissions beyond the built-in `AppGrant` enum through the `RawPermission` API. This allows you to:
- Use new Android/iOS permissions before library updates
- Request enterprise/proprietary permissions
- Prototype with experimental permissions

## The Problem

`AppGrant` is an enum - you cannot extend it:

```kotlin
enum class AppGrant {
    CAMERA,
    LOCATION,
    // ... 13 built-in permissions
}

// ❌ Cannot add custom permissions
// User must wait for library update
```

**Issues:**
- New Android 15 permission? Wait for library update
- Enterprise custom permission? Fork the library
- Experimental permission? Not possible

## The Solution

`GrantPermission` sealed interface + `RawPermission`:

```kotlin
// ✅ Built-in permissions (recommended)
val status = grantManager.request(AppGrant.CAMERA)

// ✅ Custom permissions (extensibility)
val customPermission = RawPermission(
    identifier = "CUSTOM_FEATURE",
    androidPermissions = listOf("android.permission.CUSTOM"),
    iosUsageKey = "NSCustomUsageDescription"
)
val status = grantManager.request(customPermission)
```

## Quick Start

### Android-Only Permission

```kotlin
import dev.brewkits.grant.RawPermission

// Example: Android 15 hypothetical permission
val aiProcessor = RawPermission(
    identifier = "AI_PROCESSOR",
    androidPermissions = listOf("android.permission.ACCESS_AI_PROCESSOR"),
    iosUsageKey = null  // Android-only
)

// Request it
val status = grantManager.request(aiProcessor)

when (status) {
    GrantStatus.GRANTED -> useAiProcessor()
    GrantStatus.DENIED -> showRationale()
    GrantStatus.DENIED_ALWAYS -> openSettings()
    else -> {}
}
```

### iOS-Only Permission

```kotlin
// Example: HealthKit (iOS-only)
val healthKit = RawPermission(
    identifier = "HEALTH_KIT",
    androidPermissions = emptyList(),  // iOS-only
    iosUsageKey = "NSHealthShareUsageDescription"
)

val status = grantManager.request(healthKit)
```

### Cross-Platform Permission

```kotlin
// Example: Biometric authentication
val biometric = RawPermission(
    identifier = "BIOMETRIC",
    androidPermissions = listOf("android.permission.USE_BIOMETRIC"),
    iosUsageKey = "NSFaceIDUsageDescription"
)

val status = grantManager.request(biometric)
```

## API Reference

### RawPermission

```kotlin
data class RawPermission(
    val identifier: String,
    val androidPermissions: List<String>,
    val iosUsageKey: String? = null
) : GrantPermission
```

#### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `identifier` | String | Human-readable ID for logging/debugging |
| `androidPermissions` | List<String> | Android Manifest.permission strings |
| `iosUsageKey` | String? | iOS Info.plist usage description key |

### GrantPermission Interface

```kotlin
sealed interface GrantPermission {
    val identifier: String
}
```

- `AppGrant` implements `GrantPermission` (backward compatible)
- `RawPermission` implements `GrantPermission` (custom)

## Real-World Examples

### Example 1: Android 14 Partial Photo Access

```kotlin
// Android 14+ allows users to select specific photos
val partialPhotoAccess = RawPermission(
    identifier = "PARTIAL_PHOTO_ACCESS",
    androidPermissions = listOf(
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    ),
    iosUsageKey = null
)

val status = grantManager.request(partialPhotoAccess)
```

### Example 2: Enterprise Custom Permission

```kotlin
// Company-specific permission
val internalApi = RawPermission(
    identifier = "COMPANY_INTERNAL_API",
    androidPermissions = listOf("com.company.permission.INTERNAL_API"),
    iosUsageKey = "NSCompanyInternalAPIUsageDescription"
)

val status = grantManager.request(internalApi)
```

### Example 3: Multiple Android Permissions

```kotlin
// Request multiple related permissions
val advancedLocation = RawPermission(
    identifier = "ADVANCED_LOCATION",
    androidPermissions = listOf(
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION"
    ),
    iosUsageKey = "NSLocationAlwaysAndWhenInUseUsageDescription"
)

val status = grantManager.request(advancedLocation)
```

### Example 4: Experimental Permission

```kotlin
// Testing new permission during development
val experimentalFeature = RawPermission(
    identifier = "EXPERIMENTAL_FEATURE",
    androidPermissions = listOf("android.permission.EXPERIMENTAL"),
    iosUsageKey = "NSExperimentalFeatureUsageDescription"
)

val status = grantManager.request(experimentalFeature)
```

## Platform Compatibility

### Android

`androidPermissions` must be valid Android permission strings:
- Manifest.permission constants
- Custom permissions (com.company.permission.*)
- Signature permissions
- System permissions

**Requirements:**
1. Declare in `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.CUSTOM" />
   ```
2. Request at runtime (Android 6+)

### iOS

`iosUsageKey` must be valid Info.plist usage description key:
- NSCameraUsageDescription
- NSLocationWhenInUseUsageDescription
- Custom keys (NSCustomFeatureUsageDescription)

**Requirements:**
1. Add to `Info.plist`:
   ```xml
   <key>NSCustomUsageDescription</key>
   <string>We need this to...</string>
   ```
2. Request at runtime

## Current Limitations (v1.1.0)

⚠️ **Important**: Platform implementations don't fully handle `RawPermission` yet.

**Current Behavior:**
- Returns `DENIED` with warning log
- Safe fallback - no crashes

**Future Enhancement (v1.2.0):**
- Full Android platform handling
- Full iOS platform handling
- Dynamic permission mapping

**Workaround (if needed now):**
```kotlin
// Use AppGrant for production
val status = grantManager.request(AppGrant.CAMERA)

// Use RawPermission for prototyping/testing
val customStatus = grantManager.request(customPermission)  // Currently returns DENIED
```

## Best Practices

### ✅ DO

- Use `AppGrant` for common permissions (recommended)
- Use `RawPermission` for new OS permissions not yet in library
- Use `RawPermission` for enterprise custom permissions
- Document your custom permissions clearly
- Test on both platforms

### ❌ DON'T

- Don't use `RawPermission` for permissions already in `AppGrant`
- Don't request invalid permissions (will be denied by OS)
- Don't forget to declare permissions in manifest/Info.plist
- Don't rely on `RawPermission` for production until v1.2.0

## Migration Path

### From Hardcoded Permissions

```kotlin
// Before (direct Android API call)
if (ContextCompat.checkSelfPermission(context, "android.permission.CUSTOM")
    == PackageManager.PERMISSION_GRANTED) {
    useCustomFeature()
}

// After (Grant with RawPermission)
val customPermission = RawPermission(
    identifier = "CUSTOM",
    androidPermissions = listOf("android.permission.CUSTOM"),
    iosUsageKey = null
)

when (grantManager.checkStatus(customPermission)) {
    GrantStatus.GRANTED -> useCustomFeature()
    else -> requestPermission()
}
```

### From Library Fork

```kotlin
// Before (forked library with custom enum)
enum class MyCustomGrant {
    CUSTOM_FEATURE
}

// After (RawPermission - no fork needed)
val customPermission = RawPermission(
    identifier = "CUSTOM_FEATURE",
    androidPermissions = listOf("com.company.permission.CUSTOM"),
    iosUsageKey = "NSCustomUsageDescription"
)
```

## Testing

### Unit Tests

```kotlin
@Test
fun `RawPermission - Android-only permission`() {
    val permission = RawPermission(
        identifier = "TEST",
        androidPermissions = listOf("android.permission.TEST"),
        iosUsageKey = null
    )

    assertEquals("TEST", permission.identifier)
    assertEquals(1, permission.androidPermissions.size)
    assertNull(permission.iosUsageKey)
}

@Test
fun `GrantPermission - polymorphism works`() {
    val permissions: List<GrantPermission> = listOf(
        AppGrant.CAMERA,  // Enum
        RawPermission("CUSTOM", emptyList(), null)  // Custom
    )

    assertEquals(2, permissions.size)
}
```

### Integration Tests

```kotlin
@Test
fun `request custom permission`() = runTest {
    val customPermission = RawPermission(
        identifier = "CUSTOM",
        androidPermissions = listOf("android.permission.CUSTOM"),
        iosUsageKey = null
    )

    // Currently returns DENIED (not yet fully implemented)
    val status = grantManager.request(customPermission)

    // In v1.2.0, this will work properly
    // assertEquals(GrantStatus.GRANTED, status)
}
```

## FAQ

### Q: When should I use RawPermission?

**A:** Use it when:
- New OS permission not yet in `AppGrant`
- Enterprise/proprietary permission
- Prototyping with experimental permissions
- Testing future OS features

### Q: Why not just update AppGrant enum?

**A:** Enums cannot be extended by library users. You'd have to:
- Fork the library
- Wait for library update
- Cannot use proprietary permissions

### Q: Does RawPermission work today (v1.1.0)?

**A:** Partially:
- ✅ Interface works - you can define custom permissions
- ✅ Type system works - compiler accepts it
- ⚠️ Platform handling incomplete - returns DENIED with warning
- ⏳ Full support coming in v1.2.0

### Q: Can I use both AppGrant and RawPermission together?

**A:** Yes! They're both `GrantPermission`:

```kotlin
val permissions: List<GrantPermission> = listOf(
    AppGrant.CAMERA,  // Standard
    AppGrant.LOCATION,  // Standard
    RawPermission("CUSTOM", listOf("..."), null)  // Custom
)

permissions.forEach { permission ->
    grantManager.request(permission)
}
```

### Q: Will this break in future versions?

**A:** No. The API is stable:
- v1.1.0: Interface + data structure (done)
- v1.2.0: Platform implementation (coming)
- Backward compatible - existing code works

## Roadmap

### v1.1.0 (Current)
- ✅ `GrantPermission` sealed interface
- ✅ `RawPermission` data class
- ✅ `AppGrant` implements interface
- ✅ API accepts `GrantPermission`
- ⚠️ Platform implementations stub (returns DENIED)

### v1.2.0 (Planned)
- ⏳ Full Android handling for RawPermission
- ⏳ Full iOS handling for RawPermission
- ⏳ Dynamic permission mapping
- ⏳ Runtime validation

## See Also

- [GrantPermission Interface](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantPermission.kt)
- [AppGrant Enum](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantType.kt)
- [Android Permissions Guide](https://developer.android.com/guide/topics/permissions/overview)
- [iOS Usage Descriptions](https://developer.apple.com/documentation/bundleresources/information_property_list)
