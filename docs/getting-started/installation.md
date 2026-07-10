# Installation Guide

This guide walks you through adding Grant to a Kotlin Multiplatform (KMP) project
targeting **Android** and **iOS**.

## Prerequisites

- **Kotlin**: 2.1.0 or higher (the library itself is built with Kotlin 2.4.0; consumers on any Kotlin 2.x line can use it)
- **Compose Multiplatform**: 1.6.0 or higher (only if you use `grant-compose`)
- **Android**: MinSDK 24 (Android 7.0)
- **iOS**: iOS 13.0 or higher
- **JVM target**: 17

## Repository

Grant is published to **Maven Central**, so no custom repository configuration is
required. Make sure `mavenCentral()` is present in your build:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

## Add the Dependencies

Add the modules you need to your shared module's `commonMain` source set. Only
`grant-core` is required — every other module is optional.

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:2.3.0")            // Required

            implementation("dev.brewkits:grant-compose:2.3.0")         // Optional: Compose dialogs
            implementation("dev.brewkits:grant-core-koin:2.3.0")       // Optional: Koin DI support

            // Optional: add only the permission modules you actually use on iOS.
            // Omitting a module means its iOS framework is never linked — no phantom
            // NSUsageDescription keys, no App Store rejections.
            implementation("dev.brewkits:grant-contacts:2.3.0")        // Optional: Contacts (iOS CNContactStore)
            implementation("dev.brewkits:grant-calendar:2.3.0")        // Optional: Calendar (iOS EventKit)
            implementation("dev.brewkits:grant-motion:2.3.0")          // Optional: Motion (iOS CoreMotion)
            implementation("dev.brewkits:grant-bluetooth:2.3.0")       // Optional: Bluetooth (iOS CoreBluetooth)
            implementation("dev.brewkits:grant-location-always:2.3.0") // Optional: background "always" location (iOS requestAlwaysAuthorization)
        }
    }
}
```

### Which modules do I need?

| Module | When to add it |
|---|---|
| `grant-core` | Always. Camera, foreground Location, Gallery, Microphone, Notifications, Storage, etc. |
| `grant-compose` | You want ready-made `GrantDialog` / `GrantGroupDialog` composables. |
| `grant-core-koin` | You use Koin for dependency injection. |
| `grant-contacts` | Your app reads/writes Contacts (links iOS `Contacts.framework`). |
| `grant-calendar` | Your app reads/writes Calendar events (links iOS `EventKit.framework`). |
| `grant-motion` | Your app uses motion / activity recognition (links iOS `CoreMotion.framework`). |
| `grant-bluetooth` | Your app uses Bluetooth (links iOS `CoreBluetooth.framework`). |
| `grant-location-always` | Your app needs background ("always") location (links iOS `requestAlwaysAuthorization`). |

> **Why the optional split?** On iOS, Apple's static scanner flags any linked
> framework and requires the matching `NSUsageDescription` key. By keeping these
> frameworks in separate modules, apps that don't add them never link them — so
> they're never asked for permissions they don't use. See the
> [Migration Guide](../MIGRATION_GUIDE.md) for details.

> **Web (JS) or Desktop (JVM) targets?** Use an intermediate `mobileMain` source
> set so the iOS/Android dependencies aren't linked on unsupported platforms.
> See [Dependency Management](../DEPENDENCY_MANAGEMENT.md).

## Platform Setup

### Android

Declare the permissions you request in `AndroidManifest.xml`. Grant maps each
`AppGrant` to the correct manifest string — declare the ones for the permissions
your app uses. For example:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

See [GRANTS.md](../grant-core/GRANTS.md) for the full permission → manifest mapping.

### iOS

1. Add the matching `NSUsageDescription` keys to your `Info.plist` for every
   permission you request. See [Info.plist setup](../platform-specific/ios/info-plist.md).

2. For each **optional** module you added, call `initialize()` once at app
   startup (this is what links the framework intentionally):

   ```swift
   // iOS — AppDelegate / @main entry point
   GrantContacts.shared.initialize()        // if you added grant-contacts
   GrantCalendar.shared.initialize()        // if you added grant-calendar
   GrantMotion.shared.initialize()          // if you added grant-motion
   GrantBluetooth.shared.initialize()       // if you added grant-bluetooth
   GrantLocationAlways.shared.initialize()  // if you added grant-location-always
   ```

   `grant-core` permissions (Camera, foreground Location, Gallery, Microphone,
   Notifications) need no `initialize()` call.

## Verify the Setup

Create a `GrantManager` and check a permission status — no UI needed:

```kotlin
import dev.brewkits.grant.*

// Android requires a Context; iOS does not.
val grantManager = GrantFactory.create(context)

val status = grantManager.checkStatus(AppGrant.CAMERA)
println("Camera permission: $status")
```

For service checks (GPS / Bluetooth hardware state):

```kotlin
val serviceManager = ServiceFactory.createServiceManager(context)
```

Using Koin instead of the factory? Register both `grantModule` and
`grantPlatformModule` from `grant-core-koin`.

## Next Steps

- [Quick Start](quick-start.md) — request your first permission in 5 minutes
- [Permission Guide (GRANTS.md)](../grant-core/GRANTS.md) — every supported permission and its mapping
- [iOS Info.plist Setup](../platform-specific/ios/info-plist.md) — read before shipping to the App Store
- [Migration Guide](../MIGRATION_GUIDE.md) — upgrading to 2.3.0 (and from v1.x → 2.x)
- [Best Practices](../BEST_PRACTICES.md) — production-ready patterns
