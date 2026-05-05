# Migration Guide to Grant

**Version:** 1.3.1
**Last Updated:** April 29, 2026

This guide helps you migrate from previous versions of Grant or other permission libraries.

---

## 📚 Table of Contents

1. [Upgrading from Grant 1.2.x to 1.3.1](#upgrading-from-grant-12x-to-130)
2. [From moko-permissions](#from-moko-permissions)
3. [From Google Accompanist](#from-google-accompanist)
4. [From Custom Implementation](#from-custom-implementation)
5. [From Native Android APIs](#from-native-android-apis)
6. [Common Migration Patterns](#common-migration-patterns)
7. [Troubleshooting](#troubleshooting)

---

## 1️⃣ Upgrading from Grant 1.2.x to 1.3.1

### Overview

Version 1.3.1 is a major release focusing on architectural purity and iOS stability. The biggest change is the extraction of Koin into its own module.

### What Changed?

- **Koin Extraction:** `grant-core` no longer contains Koin modules. These are now in `grant-core-koin`.
- **iOS Linker Reliability:** Fixed crashes when building iOS frameworks for projects not using Koin.
- **Health Services:** Added `ServiceType.HEALTH` support.

### Step-by-Step Upgrade

#### 1. Update Version
Update your `build.gradle.kts` to version `1.3.1`.

#### 2. Handle Koin (If you use it)
If you were using `grantModule` or `grantPlatformModule`, you must now add the `grant-core-koin` dependency:

```kotlin
// shared/build.gradle.kts
commonMain.dependencies {
    implementation("dev.brewkits:grant-core:1.3.1")
    implementation("dev.brewkits:grant-core-koin:1.3.1") // New module!
}
```

If you **don't** use Koin, you don't need to change anything! Your `grant-core` dependency is now even lighter.

#### 3. iOS Linking
If you are building an iOS app, run `./gradlew clean` to ensure the new framework structure and linker flags are applied correctly.

### Breaking Changes
- **Package Movement**: DI modules moved from `dev.brewkits.grant.di` in `grant-core` to the same package in `grant-core-koin`. If you were importing `grantModule`, you may need to refresh your imports.

---

## 2️⃣ From moko-permissions
... (rest of the content remains valid) ...
