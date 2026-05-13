# Migration Guide to Grant

**Version:** 1.4.2
**Last Updated:** May 13, 2026

This guide helps you migrate from previous versions of Grant or other permission libraries.

---

## 📚 Table of Contents

1. [Upgrading from Grant 1.3.x to 1.4.2](#upgrading-from-grant-13x-to-142)
2. [From moko-permissions](#from-moko-permissions)
3. [From Google Accompanist](#from-google-accompanist)
4. [From Custom Implementation](#from-custom-implementation)
5. [From Native Android APIs](#from-native-android-apis)
6. [Common Migration Patterns](#common-migration-patterns)
7. [Troubleshooting](#troubleshooting)

---

## 🛡️ Upgrading from Grant 1.3.x to 1.4.2

### Overview

Version 1.4.2 is a critical stability release addressing high-impact bugs in Android permission flows and refining the architectural purity of the library.

### What Changed?

- **Issue #33 Fixed (Critical):** Resolved a 60-second timeout on Android 11+ when requesting `LOCATION_ALWAYS` by eliminating a race condition in `GrantRequestActivity`.
- **Koin Module Decoupling:** `grant-core` no longer contains Koin dependencies. Use `grant-core-koin` for DI support.
- **Partial Upgrade Logic:** Fixed `GrantHandler` to correctly trigger native OS dialogs when upgrading from `PARTIAL_GRANTED` to `GRANTED`.
- **Android 15 Compatibility:** Optimized Activity transitions and lifecycle state management for upcoming Android versions.

### Step-by-Step Upgrade

#### 1. Update Version
Update your `build.gradle.kts` to version `1.4.2`.

#### 2. Handle Koin (If you use it)
If you were using `grantModule` or `grantPlatformModule`, you must now add the `grant-core-koin` dependency:

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:1.4.2")
    implementation("dev.brewkits:grant-core-koin:1.4.2") // Separate module
}
```

#### 3. iOS Stability
Ensure you are using the latest `Package.swift` if integrating via SPM. v1.4.2 includes refined re-entrant locks for iOS delegates.

---

## 2️⃣ From moko-permissions

If you are migrating from `moko-permissions`, you'll find Grant's `GrantHandler` very similar but more focused on state flows.

### Key Mapping
| moko-permissions | Grant |
| :--- | :--- |
| `Permission` | `AppGrant` / `GrantPermission` |
| `PermissionsController` | `GrantHandler` |
| `providePermission` | `request()` |

### Example
```kotlin
// moko
controller.providePermission(Permission.CAMERA)

// Grant
handler.request()
```

---

## 3️⃣ From Google Accompanist

Accompanist Permissions is deprecated. Grant provides a more robust, multiplatform alternative with built-in rationale support.

### Mapping
| Accompanist | Grant |
| :--- | :--- |
| `rememberPermissionState` | `GrantHandler` (in ViewModel) |
| `permissionState.launchPermissionRequest()` | `handler.request()` |
| `permissionState.status` | `handler.status` (StateFlow) |

---

## 4️⃣ From Custom Implementation

If you were manually handling `ActivityResultLauncher` or `onRequestPermissionsResult`, Grant automates this via its "Transparent Activity" pattern.

**Tip**: Move your logic from Activities/Fragments into ViewModels using `GrantHandler`.

---

## 5️⃣ From Native Android APIs

Replace `ActivityCompat.requestPermissions` with `GrantHandler.request`. Grant handles the complexity of checking `shouldShowRequestPermissionRationale` and directing users to settings automatically.

---

## 6️⃣ Common Migration Patterns

### Handling "Always Denied"
Grant automatically detects when a user has permanently denied a permission and surfaces a `showSettingsGuide` event in the `GrantUiState`.

### Atomic Groups
Use `GrantGroupHandler` to request multiple permissions at once, ensuring the UI only shows one rationale dialog for the entire group.

---

## 7️⃣ Troubleshooting

### iOS Framework Linking
If you see "Koin not found" during iOS build after upgrading to 1.3.0+, ensure you have added `:grant-core-koin` to your `commonMain` dependencies and exported it if necessary.

### Android Activity Results
Ensure `GrantRequestActivity` is registered in your `AndroidManifest.xml` (automatically handled by manifest merger in most cases).
