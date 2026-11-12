# Apple App Store Rejection: Unused Permission Frameworks

> **Related:** Issue #25, Issue #38, Issue #45  
> **Severity:** 🔴 Critical (causes App Store rejection at upload time)  
> **Status:** ✅ RESOLVED in 3.0.0 — Contacts/Calendar/Motion isolated in v2.0.0; Bluetooth + always-Location in v2.2.0 (see [Section 5](#5-resolution--v200--v220)); when-in-use Location, Photos and AVFoundation in 3.0.0 (see [Section 6](#6-known-gap--frameworks-still-in-grant-core-issue-91), Issue #91). CI verifies it on real release binaries.

---

## 1. Problem Description

Apps using Grant receive App Store Connect rejections at upload time (before the app runs), even when they only use a small number of permissions:

```
ITMS-90683: Missing purpose string in Info.plist file.
Your app's code references one or more APIs that access sensitive user data.
The Info.plist file should contain:
  - NSCalendarsUsageDescription
  - NSContactsUsageDescription
  - NSMotionUsageDescription
```

This happens even when the app only requests `LOCATION`, `BLUETOOTH`, and `NOTIFICATION` — no Calendar, Contacts, or Motion code anywhere. Grant is the only source of these references.

---

## 2. Root Cause — Three Distinct Layers

Understanding the fix requires understanding exactly where in the build pipeline the problem occurs. There are three distinct layers, and only the deepest one matters for App Store rejection.

### Layer 1 — Kotlin/Native `import platform.X.*` (compile time → `.klib` metadata)

When a `.kt` file in `iosMain` contains:

```kotlin
import platform.EventKit.EKEventStore
import platform.CoreMotion.CMMotionActivityManager
import platform.Contacts.CNContactStore
```

The K/N ObjC interop toolchain processes these declarations at **compile time** and registers the framework names as metadata inside the output `.klib`. This happens before DCE, before the linker, before anything else. The framework name string is baked into the klib artifact.

**This is the layer Apple's static analyzer reads.** It does not matter whether the class is instantiated at runtime, whether it is reachable by DCE, or whether it is linked strongly or weakly. The metadata string is present in the artifact and that is sufficient for the scanner to require the corresponding `NSUsageDescription` key.

### Layer 2 — Kotlin/Native DCE (build time)

Dead Code Elimination runs after compilation. It strips **Kotlin class bodies** that are unreachable from the call graph. DCE operates on Kotlin-level constructs — it can remove a handler class (`MotionPermissionHandler`) if nothing calls it.

**DCE does not touch compile-time ObjC interop metadata.** The framework name strings emitted by `import platform.X.*` during compilation are already in the `.klib` at this point. DCE has no mechanism to retract them.

### Layer 3 — Linker flags (`-weak_framework`)

`-weak_framework CoreMotion` changes the Mach-O load command from `LC_LOAD_DYLIB` to `LC_LOAD_WEAK_DYLIB`. This affects runtime behavior (the OS does not abort if the framework is missing) and influences `otool -L` output.

**`-weak_framework` does not affect Apple's static analyzer.** The analyzer reads the ObjC interop metadata embedded by the compiler, not the Mach-O load commands written by the linker. The framework name strings are still present regardless of link strength.

### Summary

```
import platform.CoreMotion.*
        │
        ▼  K/N ObjC interop (COMPILE TIME)
   .klib metadata: "CoreMotion" ◄── Apple static analyzer reads HERE
        │
        ▼  K/N DCE (BUILD TIME)
   Strips Kotlin class bodies — cannot retract metadata already emitted
        │
        ▼  Linker (-weak_framework)
   Changes LC_LOAD_DYLIB → LC_LOAD_WEAK_DYLIB — analyzer unaffected
        │
        ▼  Apple static analyzer (UPLOAD TIME)
   Finds "CoreMotion" in metadata → requires NSMotionUsageDescription
        │
        ▼  Runtime
   hasInfoPlistKey guard — prevents crash, too late for App Store
```

---

## 3. Why Previous Fixes Were Incomplete

### v1.3.0 / v1.3.1 — Handler Pattern

Moved each permission's `import platform.X.*` declarations into dedicated handler files (`CalendarPermissionHandler.kt`, `MotionPermissionHandler.kt`, etc.) instead of a monolithic `PlatformGrantDelegate.ios.kt`.

**What it fixed:** Code organization, single-responsibility design.

**What it did not fix:** All handler files still lived in `grant-core`. Every `.kt` file in `grant-core/src/iosMain/` is compiled into `grant-core.klib`. The `import platform.EventKit.*` in `CalendarPermissionHandler.kt` was still processed by K/N ObjC interop at compile time and the framework name was still registered in the klib metadata — regardless of which handler file the import lived in.

### Attempted v1.5.0 approach — Opt-in Builder DSL (PR #39)

Proposed `GrantFactory.create { location(); bluetooth() }` where each `actual fun GrantBuilder.contacts()` only references `ContactsPermissionHandler`. If a consumer never calls `contacts()`, K/N DCE strips `ContactsPermissionHandler` from the binary.

**What it fixed:** Kotlin-level DCE correctly removes unreachable handler class bodies.

**What it did not fix:** `ContactsPermissionHandler.kt` still lives in `grant-core/src/iosMain/`. It is still compiled into `grant-core.klib`. The `import platform.Contacts.*` declaration is still processed by K/N ObjC interop at compile time and the framework name is still registered in the klib metadata. DCE operates after compilation and cannot retract it. This was confirmed by testing: the handler Kotlin classes were stripped, but the App Store scanner still found the framework references.

> **Key insight, confirmed by user testing (issue #38, RoryKelly):**  
> DCE removes *class bodies*. It does not remove *framework metadata strings* that were emitted during ObjC interop compilation. These are separate artifacts produced at separate build stages. A fix that relies on DCE alone is insufficient.

---

## 4. Why the Correct Fix Must Happen at the Dependency Graph Level

The only way to prevent a framework name from appearing in the final binary is to ensure the source file containing `import platform.X.*` is **never compiled as part of the consuming app's build** in the first place.

This requires a **dependency graph boundary** — the source file must be in a separate artifact that the consumer simply does not add to their build. If the file exists in any artifact the consumer depends on (directly or transitively), K/N ObjC interop will process it and emit the framework name.

The corollary: no amount of DCE, linker flags, conditional initialization, or dispatch-table tricks can fix this once the source file is in a dependency. The fix is architectural — move the source file out of the dependency.

---

## 5. Resolution — v2.0.0 / v2.2.0

### What changed

Permission handlers whose mere presence in the binary triggers an Apple `NSUsageDescription` requirement were moved out of `grant-core` into independent Gradle/Maven modules. v2.0.0 isolated Contacts, Calendar, and Motion; v2.2.0 (Issue #45) isolated Bluetooth and "always" (background) Location:

| Module | Source moved | What is isolated | Since |
|---|---|---|---|
| `grant-contacts` | `ContactsPermissionHandler.kt` | `Contacts.framework` | v2.0.0 |
| `grant-calendar` | `CalendarPermissionHandler.kt` | `EventKit.framework` | v2.0.0 |
| `grant-motion` | `MotionPermissionHandler.kt` | `CoreMotion.framework` | v2.0.0 |
| `grant-bluetooth` | `BluetoothPermissionHandler.kt` | `CoreBluetooth.framework` | v2.2.0 |
| `grant-location-always` | `LocationAlwaysPermissionHandler.kt` | the `requestAlwaysAuthorization` selector (background location) | v2.2.0 |
| `grant-location` | `LocationPermissionHandler.kt`, `LocationManagerDelegate.kt` | `CoreLocation.framework` (when in use, `LOCATION_GPS`) | 3.0.0 |
| `grant-gallery` | `PhotoPermissionHandler.kt` | `Photos.framework` | 3.0.0 |
| `grant-camera` | `AVPermissionHandler.kt` | `AVFoundation.framework` (camera, microphone) | 3.0.0 |

Since 3.0.0, `grant-core` keeps only Notification (`UserNotifications`, which needs no usage-description key). Through 2.x it also kept Camera (`AVFoundation`), Gallery (`Photos`) and foreground Location (`CoreLocation`), on the assumption that most apps use them. Issue #91 disproved that: a camera-only app still got `CoreLocation` linked. See [Section 6](#6-known-gap--frameworks-still-in-grant-core-issue-91).

For Location specifically (v2.2.0; in 3.0.0 this when-in-use path itself moved on to `grant-location`): `grant-core` then called **only** `requestWhenInUseAuthorization` (an explicit, statically-linked call). The `requestAlwaysAuthorization` selector lives solely in `grant-location-always`, so an app that only needs foreground location never has that selector in its binary and is never asked for `NSLocationAlwaysAndWhenInUseUsageDescription`. Status-checking in core still reads the `kCLAuthorizationStatusAuthorizedAlways` enum constant (a harmless value comparison — Apple's scanner inspects `__objc_methname` method selectors, not enum reads).

> **Rejected alternative:** keeping `requestAlwaysAuthorization` in `grant-core` but hiding it via `performSelector(NSSelectorFromString("request" + "AlwaysAuthorization"))`. This is a review-circumvention technique that risks rejection under App Store Review Guideline 2.3.1 (hidden/undocumented functionality) and breaks the project's own module-isolation convention. Module isolation is the only sanctioned fix.

### Why this works

A consumer who only adds `grant-core` to their dependencies never downloads `grant-contacts.klib`, `grant-calendar.klib`, or `grant-motion.klib`. The source files containing `import platform.Contacts.*`, `import platform.EventKit.*`, and `import platform.CoreMotion.*` are never processed by K/N ObjC interop for that consumer's project. The framework name strings are never emitted into the consumer's binary. Apple's scanner finds nothing.

```
Consumer build.gradle.kts:
  implementation("dev.brewkits:grant-core:2.5.0")
  // grant-contacts NOT added

        ▼  Dependency resolution
  grant-core.klib downloaded ✅
  grant-contacts.klib NOT downloaded — never compiled

        ▼  K/N ObjC interop
  ContactsPermissionHandler.kt NEVER processed
  "Contacts" framework string NEVER emitted into metadata

        ▼  Apple static analyzer
  No "Contacts" metadata found
  NSContactsUsageDescription NOT required ✅
```

### How to use optional modules

Only add the modules your app actually uses:

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:2.5.0")

    // Add only what you use:
    implementation("dev.brewkits:grant-contacts:2.5.0")  // Contacts
    implementation("dev.brewkits:grant-calendar:2.5.0")  // Calendar / EventKit
    implementation("dev.brewkits:grant-motion:2.5.0")    // CoreMotion / Step Counter
    // 3.0.0+: Location / Gallery / Camera+Microphone are opt-in too
    // implementation("dev.brewkits:grant-location:3.0.0")
    // implementation("dev.brewkits:grant-gallery:3.0.0")
    // implementation("dev.brewkits:grant-camera:3.0.0")
}
```

On iOS, call `initialize()` once at app start for each module you add:

```kotlin
// iosMain — call once, e.g. in ApplicationDelegate
GrantContacts.initialize()
GrantCalendar.initialize()
GrantMotion.initialize()
// 3.0.0+: GrantLocation.initialize(), GrantGallery.initialize(), GrantCamera.initialize()
```

Android requires no changes — the module split has no effect on Android builds.

### Info.plist keys required per module

Only add keys for modules you actually include:

| Module added | Required Info.plist key |
|---|---|
| `grant-contacts` | `NSContactsUsageDescription` |
| `grant-calendar` | `NSCalendarsUsageDescription` |
| `grant-motion` | `NSMotionUsageDescription` |
| `grant-bluetooth` | `NSBluetoothAlwaysUsageDescription` |
| `grant-location-always` | `NSLocationAlwaysAndWhenInUseUsageDescription` |
| `grant-tracking` | `NSUserTrackingUsageDescription` |
| `grant-location` | `NSLocationWhenInUseUsageDescription` |
| `grant-gallery` | `NSPhotoLibraryUsageDescription`, plus `NSPhotoLibraryAddUsageDescription` for `GALLERY_ADD_ONLY` |
| `grant-camera` | `NSCameraUsageDescription` and/or `NSMicrophoneUsageDescription` |

If you don't add the module, **do not add the key**. An app with a usage description key but no corresponding code path will show that permission in the iOS Privacy Report, which is inaccurate and misleading to users.

---

## 6. Known Gap — Frameworks Still in `grant-core` (Issue #91)

> **Resolved in 3.0.0.** The heading is kept so existing links still work.

Through 2.x, three frameworks were **not** isolated, and every iOS app that depended on
`grant-core` linked them, whether or not it ever requested the matching permission:

| Framework | Kept reachable by (2.x) | Module since 3.0.0 |
|---|---|---|
| `CoreLocation` | `PlatformGrantDelegate.handlerFor()` (`AppGrant.LOCATION`), `PlatformServiceDelegate` (`ServiceType.LOCATION_GPS`), the public `LocationTemporaryFullAccuracyHandler` | `grant-location` |
| `Photos` | `handlerFor()` → `PhotoPermissionHandler` (`GALLERY*`, `STORAGE`) | `grant-gallery` |
| `AVFoundation` | `handlerFor()` → `AVPermissionHandler` (`CAMERA`, `MICROPHONE`) | `grant-camera` |

Not referencing `AppGrant.LOCATION` in your own code did not help: `handlerFor()` was one `when`
over every `AppGrant`, so any use of `GrantManager` kept every branch reachable. `by lazy`
deferred *construction*, not linkage.

The `-weak_framework` flags that `grant-core/build.gradle.kts` used to set did not help either.
They applied only to the `GrantCore` framework this repository builds for itself; linker options
on a `binaries.framework { }` block are not carried in the published `.klib`. They were removed
in 3.0.0.

**Evidence.** Issue #91 (2.6.0, Release archive of a camera + gallery app): `nm -u` listed
`_OBJC_CLASS_$_CLLocationManager`, `otool -L` listed `CoreLocation.framework`, and `strings`
found `requestWhenInUseAuthorization`. The same leak was reproduced in this repository on a
camera-only probe app before the fix.

**How the fix is verified.** `scripts/check-ios-linkage.sh` runs in `ios.yml` against two
release-built probe apps:

- `:linkage-probe` uses grant-core + grant-camera, CAMERA only.
- `:linkage-probe-core` uses grant-core alone, NOTIFICATION + `LOCATION_GPS`.

Each must contain the framework it really uses (the control) and none from an opt-in module it
does not use. The check was shown to fail when it should: re-adding one `CLLocationManager` call
to `grant-core` turns the core profile red. To check your own app:

```bash
BIN="MyApp.xcarchive/Products/Applications/MyApp.app/MyApp"
nm -u "$BIN" | grep -E 'CLLocationManager|PHPhotoLibrary|AVCaptureDevice'
otool -L "$BIN" | grep -E 'CoreLocation|Photos|AVFoundation'
```

**Still on 2.x?** If App Store Connect flags ITMS-90683 for one of these keys, add that key to
`Info.plist`. Apple's own guidance for this error says a purpose string is required even when the
reference comes from a third-party library and your app never calls the API. Write a truthful
string. On 3.0.0, leave out the module instead.

---

## 7. Build Stage Reference

| Build stage | Who runs it | What it produces | Affects App Store rejection? |
|---|---|---|---|
| K/N ObjC interop | `kotlinc` | Framework names in `.klib` metadata | **Yes — this is what the scanner reads** |
| Kotlin/Native DCE | K/N linker | Strips unreachable Kotlin class bodies | No — runs after metadata is already emitted |
| `-weak_framework` | Xcode linker | `LC_LOAD_WEAK_DYLIB` Mach-O record | No — scanner reads metadata, not Mach-O load commands |
| `hasInfoPlistKey` guard | Runtime | Prevents SIGABRT crash | No — upload rejection is before any runtime |
| Dependency exclusion | Gradle | Source files never compiled | **Yes — the only complete fix** |

---

## 8. Key Takeaways

> **Lesson 1 — `import platform.X.*` registers the framework at compile time, not link time.**  
> The K/N ObjC interop toolchain emits framework name metadata into the `.klib` when it processes `import` declarations. DCE and linker flags run later and cannot retract this metadata.

> **Lesson 2 — DCE removes class bodies, not interop metadata.**  
> K/N DCE can prove `MotionPermissionHandler` is unreachable and strip its Kotlin bytecode. It cannot remove the `CoreMotion` framework name string that was registered in the `.klib` metadata when the source file was compiled.

> **Lesson 3 — Apple audits compile-time metadata, not runtime code paths.**  
> The App Store static analyzer runs before the app executes even once. It reads the ObjC interop metadata embedded by the compiler. Runtime guards like `hasInfoPlistKey` protect against crashes but cannot prevent the upload-time rejection.

> **Lesson 4 — The fix must be at the dependency graph level.**  
> Any source file containing `import platform.X.*` that is part of a dependency the consumer adds will cause the framework to be registered — regardless of DCE, lazy initialization, dispatch tables, or linker flags. The only complete fix is to put the source file in a separate artifact the consumer does not add unless they need it.

> **Lesson 5 — Handler pattern inside a single module is necessary but not sufficient.**  
> Isolating framework imports into dedicated handler files (v1.3.1) improves code organization and is a prerequisite for the module split. But as long as those handler files are compiled into the same `.klib`, the framework metadata is still present for every consumer of that artifact.

---

## 9. References

- [Issue #25](https://github.com/brewkits/Grant/issues/25) — Original App Store rejection report
- [Issue #38](https://github.com/brewkits/Grant/issues/38) — Regression confirmed in v1.4.1; DCE limitation discovered via testing
- [PR #39](https://github.com/brewkits/Grant/pull/39) — Opt-in Builder DSL (considered, found insufficient due to compile-time metadata)
- [Issue #91](https://github.com/brewkits/Grant/issues/91) — `CoreLocation` linked by `grant-core` in a camera-only app; fixed in 3.0.0, see Section 6
- [Apple — Describing use of required reason APIs](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files/describing_use_of_required_reason_api)
- [Apple — Privacy manifest files](https://developer.apple.com/documentation/bundleresources/privacy_manifest_files)
- [Kotlin/Native — Interoperability with Objective-C](https://kotlinlang.org/docs/native-objc-interop.html)
- [Apple — Mach-O Programming Topics](https://developer.apple.com/library/archive/documentation/DeveloperTools/Conceptual/MachOTopics/1-Articles/executing_files.html)
