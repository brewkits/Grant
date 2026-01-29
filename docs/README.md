# Grant Documentation

Welcome to Grant's comprehensive documentation!

## 📚 Documentation Structure

### 🚀 Getting Started
**New to Grant? Start here!**

- [Quick Start Guide](getting-started/quick-start.md) - Get running in 5 minutes
- [Quick Start (iOS)](grant-core/QUICK_START_iOS.md) - iOS-specific setup
- [iOS Setup in Android Studio](ios/IOS_SETUP_ANDROID_STUDIO.md) - Complete iOS setup guide

### 📖 Guides
**Learn how to use Grant effectively**

- [Permission Types](grant-core/GRANTS.md) - All supported permissions
- [Service Checking](grant-core/SERVICES.md) - GPS, Bluetooth services
- [Compose Integration](grant-compose/COMPOSE_SUPPORT_RELEASE_NOTES.md) - Compose support
- [Best Practices](BEST_PRACTICES.md) - Production patterns

### 🏗️ Architecture
**Understanding Grant's design**

- [Architecture Overview](grant-core/ARCHITECTURE.md) - System design and patterns
- [GrantStore System](architecture/grant-store.md) - State management, persistence, backup rules ⚠️
- [Transparent Activity Guide](grant-core/TRANSPARENT_ACTIVITY_GUIDE.md) - Android transparent activity pattern

### 📱 Platform-Specific

#### Android
- [Dead Click Fix](FIX_DEAD_CLICK_ANDROID.md) - Fixing Android 12+ dead clicks
- [Transparent Activity Guide](grant-core/TRANSPARENT_ACTIVITY_GUIDE.md) - Android transparent activity pattern

#### iOS
- [Info.plist Setup](platform-specific/ios/info-plist.md) - ⚠️ **CRITICAL - App crashes if keys missing**
- [Simulator Limitations](ios/SIMULATOR_LIMITATIONS.md) - iOS simulator restrictions
- [Android Studio Setup](ios/IOS_SETUP_ANDROID_STUDIO.md) - Complete iOS setup guide
- [Info.plist Localization](ios/INFO_PLIST_LOCALIZATION.md) - Localizing permission messages

### 🔬 Advanced Topics

- [Testing Guide](TESTING.md) - Unit testing with FakeGrantManager
- [Dependency Management](DEPENDENCY_MANAGEMENT.md) - Handling version conflicts
- [Custom GrantStore](architecture/grant-store.md) - State management and persistence options

### 📊 Comparison & Analysis

- [Grant vs moko-permissions](comparison/vs-moko-permissions.md) - Feature comparison
- [Detailed Issue Analysis](comparison/moko-issues-detailed.md) - Bug fixes and improvements
- [Comparison and Learnings](COMPARISON_AND_LEARNINGS.md) - Technical insights

---

## 🎯 Quick Access

**"I want to request Camera permission"**
→ [Quick Start](getting-started/quick-start.md)

**"Android 13+ dead clicks"**
→ [Dead Click Fix](FIX_DEAD_CLICK_ANDROID.md)

**"Check if GPS enabled"**
→ [Service Checking](grant-core/SERVICES.md)

**"Coming from moko-permissions"**
→ [Comparison and Learnings](COMPARISON_AND_LEARNINGS.md)

**"App crashes on iOS when requesting permission"** ⚠️
→ [iOS Info.plist Setup](platform-specific/ios/info-plist.md)

**"Need persistence across app restarts"**
→ [GrantStore Architecture](architecture/grant-store.md)

**"Disable logs for production"**
→ `GrantLogger.isEnabled = false`

---

[← Back to Main README](../README.md)
