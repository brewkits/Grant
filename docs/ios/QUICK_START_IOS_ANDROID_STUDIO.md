# 🚀 Quick Start: Run iOS App từ Android Studio

## ⚡ 3 Bước Đơn Giản

```
┌─────────────────────────────────────────────┐
│  Step 1: Restart Android Studio             │
│  ─────────────────────────────────────────  │
│  File → Invalidate Caches → Restart        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Step 2: Chọn Configuration                 │
│  ─────────────────────────────────────────  │
│  Toolbar → [demo] ▼ → iosApp               │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│  Step 3: Click Run                          │
│  ─────────────────────────────────────────  │
│  Click ▶️ button (hoặc Shift+F10)          │
└─────────────────────────────────────────────┘
```

## 📱 Configurations Có Sẵn

Sau khi restart, bạn sẽ thấy trong dropdown:

```
📱 iosApp                      → iPhone 16 (default)
📱 iosApp (iPhone 16 Pro)      → iPhone 16 Pro
📱 iosApp (iPad Pro)           → iPad Pro 11-inch
🔧 Build iOS Framework Only    → Chỉ build framework
```

## 🎯 Demo Run

### Run trên iPhone 16 (Default)

1. Chọn **iosApp** từ dropdown
2. Click **▶️ Run**
3. Console sẽ hiển thị:

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

4. iOS Simulator sẽ tự động mở với app đang chạy! 🎉

## 🔧 Chọn Simulator Khác

Muốn run trên iPhone 16 Pro Max?

**Option 1: Tạo configuration mới**
1. **Run** → **Edit Configurations**
2. Click **iosApp** → **Copy** 📋
3. Đổi tên: `iosApp (iPhone 16 Pro Max)`
4. **Script options**: `"iPhone 16 Pro Max"`
5. **OK**

**Option 2: Dùng terminal**
```bash
./ios-quick-run.sh "iPhone 16 Pro Max"
```

## ❓ FAQ

**Q: Không thấy configurations trong dropdown?**
A: Restart Android Studio (**File** → **Invalidate Caches**)

**Q: Lỗi "Simulator not found"?**
A: Chạy `./list-ios-simulators.sh` để xem tên chính xác

**Q: Build thành công nhưng app không launch?**
A: Kiểm tra Simulator app đã mở chưa, hoặc boot manual:
```bash
xcrun simctl boot "iPhone 16"
open -a Simulator
```

**Q: Muốn debug iOS app?**
A: Dùng Xcode:
```bash
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
open demo/iosApp/GrantDemo/GrantDemo.xcodeproj
```

## 📚 Full Guide

Xem chi tiết tại: [IOS_SETUP_ANDROID_STUDIO.md](IOS_SETUP_ANDROID_STUDIO.md)

---

**Happy iOS Development from Android Studio! 🎉**
