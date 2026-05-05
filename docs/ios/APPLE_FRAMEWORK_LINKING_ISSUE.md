# Apple App Store Rejection: Unused Permission Frameworks

> **Related:** Issue #25 — *"Apple wants me to add why I have used them, but I have not used them"*  
> **Severity:** 🔴 Critical (causes App Store rejection)  
> **Status:** ✅ RESOLVED in v1.3.1

---

## 1. Problem Description

Users report that Apple rejects their app submission with the following message, even though they never use the listed permissions in their app:

```
Your app's Info.plist file should contain:
  - NSCalendarsUsageDescription
  - NSMotionUsageDescription
  - NSLocationAlwaysAndWhenInUseUsageDescription
  - NSLocationWhenInUseUsageDescription
  - NSBluetoothAlwaysUsageDescription
```

Simply adding Grant as a dependency is enough for Apple to require all of these keys — regardless of which permissions the app actually uses.

---

## 2. Root Cause — Kotlin/Native Static Linking

### 2.1 How Kotlin/Native compiles iOS code

Unlike JVM (where classes are loaded lazily at runtime), **Kotlin/Native compiles directly to machine code** and must **link all dependencies at compile time**.

When `PlatformGrantDelegate.ios.kt` contains:

```kotlin
import platform.CoreLocation.CLLocationManager       // → links CoreLocation.framework
import platform.EventKit.EKEventStore                // → links EventKit.framework
import platform.CoreMotion.CMMotionActivityManager   // → links CoreMotion.framework
import platform.CoreBluetooth.*                      // → links CoreBluetooth.framework
```

The Kotlin/Native compiler **must embed** all these frameworks into the binary output (`.framework` / `.kexe`) as **`LC_LOAD_DYLIB`** records — even if the user never calls the associated `AppGrant` values at runtime.

### 2.2 How Apple inspects the binary

When you upload to App Store Connect, Apple's static analyzer (equivalent to running `otool -L`) scans the binary:

```bash
otool -L YourApp.app/Frameworks/GrantCore.framework/GrantCore
# Output:
#   /System/Library/Frameworks/CoreLocation.framework/CoreLocation
#   /System/Library/Frameworks/EventKit.framework/EventKit
#   /System/Library/Frameworks/CoreMotion.framework/CoreMotion
#   /System/Library/Frameworks/CoreBluetooth.framework/CoreBluetooth
```

Apple detects these frameworks → **automatically requires** `NSLocationWhenInUseUsageDescription`, `NSCalendarsUsageDescription`, etc. — **regardless of whether the code paths are ever executed**.

### 2.3 Why the `hasInfoPlistKey` guard does not help

Grant already has this runtime guard:

```kotlin
private fun checkLocationStatus(forAlways: Boolean): GrantStatus {
    if (!hasInfoPlistKey("NSLocationWhenInUseUsageDescription")) {
        return GrantStatus.DENIED_ALWAYS  // ← prevents SIGABRT crash at runtime
    }
    // ...
}
```

This guard works correctly **at runtime** (prevents SIGABRT crashes). However, the Apple rejection happens **before the app runs even once** — at upload time. These are two entirely separate phases:

| Phase | Who checks | Effect |
|---|---|---|
| **Compile time** | Kotlin/Native compiler | Links framework into binary |
| **Upload time** | Apple static analyzer | Detects framework, requires Usage Description key |
| **Runtime** | `hasInfoPlistKey` guard | Prevents crash if key is missing |

---

## 3. Why This Is a Fundamental Architectural Trade-off

### 3.1 The "Monolithic Permission Library" problem

Grant currently bundles **all 17 permissions** into a single `grant-core` module. This is convenient for users (one dependency line) but creates an unavoidable conflict with Apple's review model:

```
User only needs CAMERA + MICROPHONE
       ↓
Add dependency: implementation("dev.brewkits:grant-core:1.3.1")
       ↓
Binary ships with: CoreLocation + EventKit + CoreMotion + CoreBluetooth + ...
       ↓
Apple: "You must declare usage descriptions for all linked frameworks."
```

### 3.2 Comparison with similar libraries

| Library | Approach | Trade-off |
|---|---|---|
| **moko-permissions** | Multi-module (`moko-permissions-location`, `moko-permissions-camera`, …) | Worse DX, technically correct |
| **flutter permission_handler** | Single plugin, native code split per file | Similar to Grant — same issue |
| **Grant** | Single module | Best DX, Apple review conflict |

---

## 4. Solution Analysis

### ❌ Option A: Conditional Compilation via Gradle Properties

**Idea:**
```kotlin
// grant-core/build.gradle.kts
val includeLocation = project.findProperty("grant.permissions.location")
    ?.toString()?.toBoolean() ?: true

if (includeLocation) {
    kotlin.srcDir("src/iosMain/location/active")
} else {
    kotlin.srcDir("src/iosMain/location/dummy")
}
```

Users add to their `gradle.properties`:
```properties
grant.permissions.location=false
grant.permissions.bluetooth=false
```

**Why this does NOT work with binary distribution:**

When published to Maven Central, the library is compiled **once** into a binary artifact (`.klib`). Users download the pre-compiled binary — they cannot change the library's `srcDirs` after the fact. The user's `gradle.properties` only affects **their own** source code compilation, not the library binary.

> **Rule:** Conditional source sets only work when users build the library from source. This does not scale for a distributed library.

---

### ✅ Option B: `weak_framework` Linker Flags

**How it works:**
```kotlin
// grant-core/build.gradle.kts
listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
    target.binaries.framework {
        baseName = "GrantCore"
        isStatic = true
        linkerOpts(
            "-weak_framework", "CoreLocation",
            "-weak_framework", "EventKit",
            "-weak_framework", "CoreMotion",
            "-weak_framework", "CoreBluetooth"
        )
    }
}
```

**Technical difference:**

| Link type | Mach-O record | Apple scanner behavior |
|---|---|---|
| Strong (default) | `LC_LOAD_DYLIB` | **Requires** Usage Description key |
| Weak | `LC_LOAD_WEAK_DYLIB` | Often **skipped** in privacy manifest check |

Verify with `otool -l`:
```bash
# Strong link (before):
cmd LC_LOAD_DYLIB
    name /System/Library/Frameworks/CoreLocation.framework/CoreLocation

# Weak link (after):
cmd LC_LOAD_WEAK_DYLIB
    name /System/Library/Frameworks/CoreLocation.framework/CoreLocation
```

**Pros:**
- No public API changes
- No breaking changes
- Minimal code modification

**Cons:**
- Apple may change scanner behavior at any time
- Not a complete technical solution — the framework is still embedded

---

### ✅✅ Option C: Handler Pattern (Recommended long-term fix)

Instead of a single `PlatformGrantDelegate.ios.kt` that imports all frameworks, split each permission into a dedicated handler file.

**Proposed structure:**
```
iosMain/kotlin/dev/brewkits/grant/
├── impl/
│   └── PlatformGrantDelegate.ios.kt     ← dispatch logic only, no native imports
└── handlers/
    ├── IosPermissionHandler.kt           ← common interface
    ├── CameraPermissionHandler.kt        ← import platform.AVFoundation.*
    ├── LocationPermissionHandler.kt      ← import platform.CoreLocation.*
    ├── CalendarPermissionHandler.kt      ← import platform.EventKit.*
    ├── MotionPermissionHandler.kt        ← import platform.CoreMotion.*
    ├── BluetoothPermissionHandler.kt     ← import platform.CoreBluetooth.*
    ├── ContactsPermissionHandler.kt      ← import platform.Contacts.*
    ├── PhotoPermissionHandler.kt         ← import platform.Photos.*
    └── NotificationPermissionHandler.kt  ← import platform.UserNotifications.*
```

**`IosPermissionHandler.kt`:**
```kotlin
internal interface IosPermissionHandler {
    fun checkStatus(): GrantStatus
    suspend fun request(): GrantStatus
}
```

**`LocationPermissionHandler.kt`** — CoreLocation imports isolated to this file:
```kotlin
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
// ... only CoreLocation imports here

internal class LocationPermissionHandler(
    private val forAlways: Boolean
) : IosPermissionHandler {
    override fun checkStatus(): GrantStatus { /* ... */ }
    override suspend fun request(): GrantStatus { /* ... */ }
}
```

**`PlatformGrantDelegate.ios.kt`** — dispatch only, zero native framework imports:
```kotlin
// No more: import platform.CoreLocation.*, import platform.EventKit.*, ...
// Only internal handler references

internal class PlatformGrantDelegate(private val store: GrantStore) {

    private val locationHandler by lazy { LocationPermissionHandler(forAlways = false) }
    private val calendarHandler by lazy { CalendarPermissionHandler() }
    // ...

    fun checkStatus(grant: AppGrant): GrantStatus = when (grant) {
        AppGrant.LOCATION  -> locationHandler.checkStatus()
        AppGrant.CALENDAR  -> calendarHandler.checkStatus()
        // ...
    }
}
```

**Why this is better:**
- Kotlin/Native can tree-shake at the **file level** when handlers are lazily initialized
- Codebase becomes easier to maintain (Single Responsibility Principle)
- Sets the foundation for a future "slim" artifact that excludes sensitive handlers

---

### ✅✅✅ Option D: Multi-artifact Distribution (Future roadmap)

Publish separate artifacts to Maven Central:
- `grant-core` — Camera, Microphone, Gallery, Notification (safe frameworks)
- `grant-core-location` — adds Location support
- `grant-core-calendar` — adds Calendar support
- `grant-core-full` — all permissions

---

## 5. Resolution

The Handler Pattern (Option C) was implemented directly — deferring to a later version would only create additional technical debt that every user would eventually have to migrate through. Since the refactor carries zero breaking API changes, there was no reason to delay it.

### What changed

Each permission's native framework imports are now **isolated to a dedicated handler file** inside `grant-core/src/iosMain/kotlin/dev/brewkits/grant/handlers/`:

| Handler file | Isolated framework |
|---|---|
| `AVPermissionHandler.kt` | `AVFoundation` (Camera + Microphone) |
| `PhotoPermissionHandler.kt` | `Photos` |
| `LocationPermissionHandler.kt` | `CoreLocation` ⚠️ |
| `CalendarPermissionHandler.kt` | `EventKit` ⚠️ |
| `MotionPermissionHandler.kt` | `CoreMotion` ⚠️ |
| `BluetoothPermissionHandler.kt` | delegates to `BluetoothManagerDelegate` (`CoreBluetooth`) ⚠️ |
| `ContactsPermissionHandler.kt` | `Contacts` |
| `NotificationPermissionHandler.kt` | `UserNotifications` |

> ⚠️ = Frameworks that previously triggered Apple's rejection. Now isolated so the linker only embeds them when they are actually referenced.

`PlatformGrantDelegate.ios.kt` now contains **zero native framework imports**. It only references handler classes and dispatches operations to them.

All handlers are initialized lazily (`by lazy`), meaning a framework's static initializers are not triggered until the handler is first accessed.

### User-side workaround (if still rejected on older Grant versions)

If using a version of Grant prior to this fix, Apple may still reject the submission. In that case, add the following keys to `Info.plist` as a temporary workaround. Apple requires these keys to exist but does not validate their content:

```xml
<key>NSCalendarsUsageDescription</key>
<string>This app does not use calendar access.</string>
<key>NSMotionUsageDescription</key>
<string>This app does not use motion sensors.</string>
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app does not use location services.</string>
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app does not use Bluetooth.</string>
```

Upgrade to the fixed version as soon as possible to remove the need for these entries.

---

## 6. Key Takeaways

> **Lesson 1 — Import means link in Kotlin/Native.**  
> Unlike JVM, there is no lazy class loading. Every `import platform.X.*` creates a hard static dependency on framework X, regardless of whether that code path is ever reached at runtime.

> **Lesson 2 — Apple audits what you link, not what you call.**  
> The App Store static analyzer runs before the app executes even once. Runtime guards like `hasInfoPlistKey` protect against crashes but cannot prevent the upload-time rejection.

> **Lesson 3 — Monolithic permission libraries conflict with Apple's privacy model.**  
> The convenience of a single dependency comes at the cost of forcing every user to declare usage descriptions for every permission the library supports — even unused ones.

> **Lesson 4 — Gradle properties cannot configure pre-compiled binaries.**  
> In binary distribution (Maven Central / CocoaPods), conditional source sets must be decided before publishing. Users consume a fixed binary and cannot influence the library's compilation.

---

## 7. References

- [Apple — Describing use of required reason APIs](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_use_of_required_reason_api)
- [Apple — Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files)
- [Apple WWDC 2023 — Privacy manifests and signatures](https://developer.apple.com/videos/play/wwdc2023/10060/)
- [Kotlin/Native — Interoperability with C](https://kotlinlang.org/docs/native-app-with-c-and-libcurl.html)
- [Apple — Mach-O Programming Topics: Weak Linking](https://developer.apple.com/library/archive/documentation/DeveloperTools/Conceptual/MachOTopics/1-Articles/executing_files.html)
- [moko-permissions — Multi-module approach reference](https://github.com/icerockdev/moko-permissions)
