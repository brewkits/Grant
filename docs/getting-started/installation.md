# Installation Guide

This guide walks you through adding Grant to a Kotlin Multiplatform (KMP) project
targeting **Android** and **iOS**.

## Prerequisites

- **Kotlin**: 2.1.0 or higher (the library itself is built with Kotlin 2.4.0; consumers on any Kotlin 2.x line can use it)
- **Compose Multiplatform**: 1.6.0 or higher (only if you use `grant-compose`)
- **Android**: minSdk 26 (Android 8.0)
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
            implementation("dev.brewkits:grant-core:2.4.0")            // Required

            implementation("dev.brewkits:grant-compose:2.4.0")         // Optional: Compose dialogs
            implementation("dev.brewkits:grant-core-koin:2.4.0")       // Optional: Koin DI support

            // Optional: add only the permission modules you actually use on iOS.
            // Omitting a module means its iOS framework is never linked — no phantom
            // NSUsageDescription keys, no App Store rejections.
            implementation("dev.brewkits:grant-contacts:2.4.0")        // Optional: Contacts (iOS CNContactStore)
            implementation("dev.brewkits:grant-calendar:2.4.0")        // Optional: Calendar (iOS EventKit)
            implementation("dev.brewkits:grant-motion:2.4.0")          // Optional: Motion (iOS CoreMotion)
            implementation("dev.brewkits:grant-bluetooth:2.4.0")       // Optional: Bluetooth (iOS CoreBluetooth)
            implementation("dev.brewkits:grant-location-always:2.4.0") // Optional: background "always" location (iOS requestAlwaysAuthorization)
            implementation("dev.brewkits:grant-tracking:2.4.0")        // Optional: App Tracking Transparency (iOS ATTrackingManager)
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
| `grant-tracking` | Your app asks for cross-app tracking consent — ad attribution or measurement (links iOS `AppTrackingTransparency`, which makes Apple require `NSUserTrackingUsageDescription`). No-op on Android, which has no runtime permission for this. |

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
   GrantTracking.shared.initialize()        // if you added grant-tracking
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

## Permissions this library adds to your app: none

`grant-core` declares **no `<uses-permission>` entries**. Manifest merger copies a library's
permission declarations into every app that depends on it, where they appear on the Play
Store listing and in every security review — for permissions the app never asked for. A
permission library doing that is the problem it exists to solve, so the count stays at zero.

Two entries were removed in 2.4.1:

| Permission | Why it is gone |
|---|---|
| `VIBRATE` | Never used by production code. It was only a stand-in permission in the instrumented tests, which now declare it themselves. |
| `ACCESS_WIFI_STATE` | Needed only by the optional `ServiceType.WIFI` check. |

**If you use `ServiceType.WIFI`**, declare it yourself:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
```

Without it, `WifiManager.isWifiEnabled` throws `SecurityException`, which Grant catches and
reports as `ServiceStatus.UNKNOWN` — your app degrades rather than crashing, but the WiFi
check silently stops working. Every other `ServiceType` is unaffected.

Runtime permissions your app requests through Grant (`CAMERA`, `LOCATION`, …) must still be
declared in your own manifest, as always — Grant requests them, it does not declare them
for you.

## Why there is no SPM or CocoaPods support

Grant is consumed with **Gradle, from a Kotlin Multiplatform module**. It is not published as a
Swift package or a CocoaPod, and this is deliberate rather than a gap waiting to be filled.

A `Package.swift` existed between v1.4.0 and v2.3.0. It never worked — it referenced a v1.4.2
binary with a placeholder checksum, and no release ever attached an `.xcframework` asset. It was
removed in v2.4.0 rather than repaired, because repairing it is not possible without giving up
something more valuable.

**The technical reason.** Kotlin/Native statically copies a dependency's code into every framework
that uses it. `grant-core` classes such as `IosPermissionHandlerRegistry` and `GrantLogger` are
therefore present inside `GrantContacts.framework` as well as inside `GrantCore.framework`. An app
linking both would hold **two registry singletons with separate state**: `GrantContacts.initialize()`
would register its handler into one copy while `grant-core` reads the other, so the handler would
silently never be found. Shipping one xcframework per module is not viable.

The alternative — a single umbrella xcframework containing everything — would re-link
`Contacts`, `EventKit`, `CoreMotion` and `CoreBluetooth` into every consumer. That is exactly what
the opt-in module architecture exists to prevent (see
[Apple App Store Rejection: Unused Permission Frameworks](../ios/APPLE_FRAMEWORK_LINKING_ISSUE.md)), and it would put every app back in front
of Apple's static scanner asking for usage-description keys it does not need.

**If your app is Swift-only (no KMP):** Grant is not the right fit, and that is a genuine
recommendation rather than a deflection. You would be linking a Kotlin/Native runtime and a
6 MB framework for a permission wrapper, and half of the iOS-relevant permissions
(`CONTACTS`, `CALENDAR`, `MOTION`, `BLUETOOTH`, `LOCATION_ALWAYS`, …) live in optional modules that
SPM cannot reach at all. A small native Swift helper or a Swift-native permissions library will
serve you better.

**If you already use KMP:** you do not need SPM. Your iOS app consumes your own shared framework,
which already contains `grant-core` through its Gradle dependency. Nothing extra is required.

## Next Steps

- [Quick Start](quick-start.md) — request your first permission in 5 minutes
- [Permission Guide (GRANTS.md)](../grant-core/GRANTS.md) — every supported permission and its mapping
- [iOS Info.plist Setup](../platform-specific/ios/info-plist.md) — read before shipping to the App Store
- [Migration Guide](../MIGRATION_GUIDE.md) — upgrading to 2.3.0 (and from v1.x → 2.x)
- [Best Practices](../BEST_PRACTICES.md) — production-ready patterns
