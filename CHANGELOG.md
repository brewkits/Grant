# Changelog

All notable changes to the Grant library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.3.0] - 2026-04-29

### 🚀 Major Architectural Shift: Koin Decoupling
- **`grant-core-koin` Module**: To resolve critical Kotlin/Native linker issues, all Koin-related code has been extracted into a dedicated artifact.
- **Pure Core**: `grant-core` is now 100% free of Koin dependencies, ensuring a zero-overhead experience for developers using other DI frameworks (Hilt, Dagger) or manual injection.
- **iOS Linker Fix**: Resolved a `NullPointerException` during iOS framework linking caused by `compileOnly` Koin dependencies in previous versions.

### ✨ New Features: HealthCheck Support
- **Health Services Monitoring**: Added `ServiceType.HEALTH` for monitoring system-level health data availability.
- **Android Health Connect**: Intelligent detection of Health Connect availability via intent resolution.
- **Apple HealthKit**: Seamless integration with `HKHealthStore.isHealthDataAvailable()`.

### 🛡️ Production Hardening (QA & Senior Review)
- **iOS Status Cache Thread-Safety**: Fixed a potential TOCTOU (Time-Of-Check to Time-Of-Use) race condition in `PlatformGrantDelegate` by implementing per-permission mutex locking.
- **iOS App Extension Safety**: Refactored `PlatformServiceDelegate` to use KVC reflection when accessing `UIApplication.sharedApplication`, preventing crashes and build errors in App Extensions (Widgets, Share Extensions).
- **Test Suite Optimization**: Reached **430+ automated tests** with 100% pass rate across Android and iOS Simulator.
- **Sample App Overhaul**: Updated the Demo application to showcase the latest unified `GrantAndServiceHandler` pattern.

---

## [1.2.1] - 2026-04-10
... rest of changelog ...
