# 📚 Grant Library Documentation

Tất cả tài liệu của Grant KMP Library được tổ chức ở đây.

---

## 📖 Table of Contents

### 🚀 Getting Started

1. **[Main README](../README.md)** - Overview và quick start
2. **[Grant Core Quick Start](grant-core/QUICK_START.md)** - Hướng dẫn sử dụng nhanh
3. **[iOS Quick Start](grant-core/QUICK_START_iOS.md)** - Setup cho iOS

### ⭐ Essential Guides (Start Here!)

- **[Best Practices](BEST_PRACTICES.md)** - ⭐ Permission patterns, Android/iOS guidelines
- **[Changelog](../CHANGELOG.md)** - All notable changes and bug fixes

### 🏗️ Architecture & Design

- **[Architecture Guide](grant-core/ARCHITECTURE.md)** - Clean architecture và design patterns
- **[Grant System](grant-core/GRANTS.md)** - Chi tiết về grant handling
- **[Service System](grant-core/SERVICES.md)** - System service checking
- **[Transparent Activity](grant-core/TRANSPARENT_ACTIVITY_GUIDE.md)** - Android grant activity pattern

### 🧪 Development

- **[Testing Guide](grant-core/TESTING.md)** - Unit testing và mocking strategies

### 📱 iOS Development

- **[iOS Setup for Android Studio](ios/IOS_SETUP_ANDROID_STUDIO.md)** - Run iOS app từ Android Studio
- **[Quick Start iOS Android Studio](ios/QUICK_START_IOS_ANDROID_STUDIO.md)** - Quick guide
- **[Info.plist Localization](ios/INFO_PLIST_LOCALIZATION.md)** - ⭐ Localize permission descriptions
- **[Simulator Limitations](ios/SIMULATOR_LIMITATIONS.md)** - ⭐ **NEW!** Which permissions work on simulator

### 🎨 Demo App

- **[Demo Guide](demo/DEMO_GUIDE.md)** - Hướng dẫn chạy demo app
- **[Demo Setup](demo/DEMO_SETUP.md)** - Setup complete documentation

---

## 📂 Documentation Structure

```
Grant/
├── CHANGELOG.md                       ← ⭐ All changes and fixes
├── README.md                          ← Main documentation
└── docs/
    ├── README.md                      ← Bạn đang ở đây
    ├── BEST_PRACTICES.md              ← ⭐ Essential reading!
    ├── FIX_DEAD_CLICK_ANDROID.md      ← ⭐ Critical Android fix
    ├── ios/                           ← iOS development guides
    │   ├── IOS_SETUP_ANDROID_STUDIO.md
    │   ├── QUICK_START_IOS_ANDROID_STUDIO.md
    │   └── INFO_PLIST_LOCALIZATION.md ← ⭐ Localize permissions
    ├── grant-core/                    ← Library documentation
    │   ├── ARCHITECTURE.md
    │   ├── GRANTS.md
    │   ├── QUICK_START.md
    │   ├── QUICK_START_iOS.md
    │   ├── SERVICES.md
    │   ├── TESTING.md                 ← ⭐ Includes latest test cases
    │   └── TRANSPARENT_ACTIVITY_GUIDE.md
    └── demo/                          ← Demo app documentation
        ├── DEMO_GUIDE.md
        └── DEMO_SETUP.md
```

---

## 🔗 Quick Links

### For New Users
- ⭐ **[Best Practices](BEST_PRACTICES.md)** - Start here! Permission patterns, platform differences
- [Installation](grant-core/QUICK_START.md#installation)
- [Basic Usage](grant-core/QUICK_START.md#basic-usage)
- [Supported Grants](grant-core/GRANTS.md#supported-grants)
- **[Changelog](../CHANGELOG.md)** - What's new and fixed

### For Bug Fixes & Issues
- **[Dead Click Fix (Android)](FIX_DEAD_CLICK_ANDROID.md)** - SharedPreferences solution
- **[Testing Guide](grant-core/TESTING.md)** - Includes test cases for all fixes

### For Contributors
- [Architecture Overview](grant-core/ARCHITECTURE.md)
- [Testing](grant-core/TESTING.md)
- [Adding New Grants](grant-core/GRANTS.md#adding-new-grants)

### For iOS Developers
- [iOS Setup](grant-core/QUICK_START_iOS.md)
- [Run from Android Studio](ios/IOS_SETUP_ANDROID_STUDIO.md)
- [Info.plist Localization](ios/INFO_PLIST_LOCALIZATION.md)

---

## 📝 Contributing to Docs

Khi thêm documentation mới:

1. Đặt file vào thư mục phù hợp:
   - `docs/ios/` - iOS-specific guides
   - `docs/grant-core/` - Library documentation
   - `docs/demo/` - Demo app guides

2. Update file này (docs/README.md) với link mới

3. Update main [README.md](../README.md) nếu cần

---

**Last updated:** 2026-01-23
