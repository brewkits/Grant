# Grant Documentation

Welcome to Grant's comprehensive documentation!

## 📚 Documentation Structure

### 🚀 Getting Started
**New to Grant? Start here!**

- [Quick Start Guide](getting-started/quick-start.md) - Get running in 5 minutes
- [Installation](getting-started/installation.md) - Setup and dependencies
- [Android Setup](getting-started/android-setup.md) - AndroidManifest configuration
- [iOS Setup](getting-started/ios-setup.md) - Info.plist configuration

### 📖 Guides
**Learn how to use Grant effectively**

- [Permission Handling Guide](guides/permissions-guide.md) - Complete guide
- [Service Checking](guides/service-checking.md) - GPS, Bluetooth services
- [Compose Integration](guides/compose-integration.md) - Compose support
- [Best Practices](guides/best-practices.md) - Production patterns

### 🏗️ Architecture
**Understanding Grant's design**

- [Architecture Overview](architecture/overview.md) - System design
- [GrantStore System](architecture/grant-store.md) - State management, persistence, backup rules ⚠️
- [Platform Delegates](architecture/platform-delegates.md) - Platform code

### 📱 Platform-Specific

#### Android
- [Android 12+ Handling](platform-specific/android/android-12-handling.md)
- [Transparent Activity](platform-specific/android/transparent-activity.md)
- [Dead Click Fix](platform-specific/android/dead-click-fix.md)

#### iOS
- [Info.plist Setup](platform-specific/ios/info-plist.md) - ⚠️ **CRITICAL - App crashes if keys missing**
- [Simulator Limitations](platform-specific/ios/simulator-limitations.md)
- [Android Studio Setup](platform-specific/ios/android-studio-setup.md)

### 🔬 Advanced Topics

- [Testing Guide](advanced/testing.md)
- [Dependency Injection](advanced/dependency-injection.md)
- [Custom GrantStore](advanced/custom-grant-store.md)

### 📊 Comparison & Migration

- [Grant vs moko-permissions](comparison/vs-moko-permissions.md)
- [Detailed Issue Analysis](comparison/moko-issues-detailed.md)
- [Migration Guide](comparison/migration-from-moko.md)

---

## 🎯 Quick Access

**"I want to request Camera permission"**
→ [Quick Start](getting-started/quick-start.md)

**"Android 13+ dead clicks"**
→ [Dead Click Fix](platform-specific/android/dead-click-fix.md)

**"Check if GPS enabled"**
→ [Service Checking](guides/service-checking.md)

**"Coming from moko-permissions"**
→ [Migration Guide](comparison/migration-from-moko.md)

**"App crashes on iOS when requesting permission"** ⚠️
→ [iOS Info.plist Setup](platform-specific/ios/info-plist.md)

**"Need persistence across app restarts"**
→ [GrantStore Architecture](architecture/grant-store.md)

**"Disable logs for production"**
→ `GrantLogger.isEnabled = false`

---

[← Back to Main README](../README.md)
