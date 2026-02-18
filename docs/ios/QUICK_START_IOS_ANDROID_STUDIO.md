# 🚀 Quick Start: Run iOS App from Android Studio

## ⚡ 3 Simple Steps

```
┌─────────────────────────────────────────────┐
│  Step 1: Restart Android Studio             │
│  ─────────────────────────────────────────  │
│  File → Invalidate Caches → Restart        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Step 2: Select Configuration               │
│  ─────────────────────────────────────────  │
│  Toolbar → [demo] ▼ → iosApp               │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Step 3: Click Run                          │
│  ─────────────────────────────────────────  │
│  Click ▶️ button (or Shift+F10)            │
└─────────────────────────────────────────────┘
```

## 📱 Available Configurations

After restart, you will see in the dropdown:

```
📱 iosApp                      → iPhone 16 (default)
📱 iosApp (iPhone 16 Pro)      → iPhone 16 Pro
📱 iosApp (iPad Pro)           → iPad Pro 11-inch
🔧 Build iOS Framework Only    → Build framework only
```

## 🎯 Demo Run

### Run on iPhone 16 (Default)

1. Select **iosApp** from dropdown
2. Click **▶️ Run**
3. Console will display:

```
════════════════════════════════════════════════════
🎯 Running iOS App on: iPhone 16
════════════════════════════════════════════════════

📦 [1/3] Building Kotlin framework...
✅ Kotlin framework built successfully

🔨 [2/3] Building iOS app with Xcode...
✅ iOS app built successfully

🚀 [3/3] Launching app on iPhone 16...

════════════════════════════════════════════════════
✅ SUCCESS! App is running on iPhone 16
════════════════════════════════════════════════════
```

4. iOS Simulator will automatically open with the app running! 🎉

## 🔧 Select Different Simulator

Want to run on iPhone 16 Pro Max?

**Option 1: Create new configuration**
1. **Run** → **Edit Configurations**
2. Click **iosApp** → **Copy** 📋
3. Rename: `iosApp (iPhone 16 Pro Max)`
4. **Script options**: `"iPhone 16 Pro Max"`
5. **OK**

**Option 2: Use terminal**
```bash
./ios-quick-run.sh "iPhone 16 Pro Max"
```

## ❓ FAQ

**Q: Can't see configurations in dropdown?**
A: Restart Android Studio (**File** → **Invalidate Caches**)

**Q: "Simulator not found" error?**
A: Run `./list-ios-simulators.sh` to see exact names

**Q: Build succeeds but app doesn't launch?**
A: Check if Simulator app is open, or boot manually:
```bash
xcrun simctl boot "iPhone 16"
open -a Simulator
```

**Q: Want to debug iOS app?**
A: Use Xcode:
```bash
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
open demo/iosApp/GrantDemo/GrantDemo.xcodeproj
```

## 📚 Full Guide

See details at: [IOS_SETUP_ANDROID_STUDIO.md](IOS_SETUP_ANDROID_STUDIO.md)

---

**Happy iOS Development from Android Studio! 🎉**
