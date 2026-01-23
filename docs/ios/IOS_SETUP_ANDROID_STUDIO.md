# 📱 iOS Development từ Android Studio

## ✅ Setup Hoàn Tất!

Bạn đã có **6 iOS run configurations** trong Android Studio toolbar:

### 🚀 Run Configurations Có Sẵn

| Configuration | Mô tả | Device/Simulator |
|---------------|-------|------------------|
| **iosApp** | Default iOS app | iPhone 16 (Default) |
| **iosApp (iPhone 16 Pro)** | iOS app trên iPhone Pro | iPhone 16 Pro |
| **iosApp (iPad Pro)** | iOS app trên iPad | iPad Pro 11-inch |
| **Build iOS Framework Only** | Chỉ build framework, không launch | N/A |
| **iOS App (Simulator)** | Advanced config | iPhone 16 |
| **Launch iOS Simulator** | Chỉ launch simulator | iPhone 16 |

---

## 🎯 Cách Sử Dụng

### Bước 1: Restart Android Studio

```bash
# Restart Android Studio để load các run configurations mới
```

### Bước 2: Chọn Configuration

1. Ở toolbar trên cùng, click vào dropdown (bên cạnh nút Run ▶️)
2. Bạn sẽ thấy các configuration:
   ```
   📱 iosApp
   📱 iosApp (iPhone 16 Pro)
   📱 iosApp (iPad Pro)
   🔧 Build iOS Framework Only
   ```

### Bước 3: Run!

1. Chọn configuration bạn muốn (ví dụ: **iosApp**)
2. Click nút **Run** ▶️ (hoặc **Shift+F10**)
3. Xem console output trong Android Studio
4. iOS Simulator sẽ tự động mở và launch app! 🎉

---

## 🔧 Chi Tiết Các Configuration

### 1. **iosApp** (Recommended)
- **Mô tả:** Run app trên iPhone 16 simulator (default)
- **Sử dụng khi:** Development hàng ngày
- **Steps:**
  1. Build Kotlin framework
  2. Build iOS app với Xcode
  3. Launch trên iPhone 16 simulator

### 2. **iosApp (iPhone 16 Pro)**
- **Mô tả:** Run app trên iPhone 16 Pro simulator
- **Sử dụng khi:** Test trên màn hình lớn hơn, camera ProRAW, etc.

### 3. **iosApp (iPad Pro)**
- **Mô tả:** Run app trên iPad Pro simulator
- **Sử dụng khi:** Test iPad layout, multitasking

### 4. **Build iOS Framework Only**
- **Mô tả:** Chỉ build Kotlin framework, không launch app
- **Sử dụng khi:**
  - Muốn check framework build có lỗi không
  - Sau đó tự mở Xcode để debug
  - CI/CD pipeline

---

## 🎨 Tạo Thêm Configuration Cho Simulator Khác

### Cách 1: Duplicate Configuration Có Sẵn

1. **Run** → **Edit Configurations...**
2. Chọn **iosApp**
3. Click **Copy** (icon giống 2 tờ giấy)
4. Đổi tên: `iosApp (iPhone 17 Pro)`
5. Ở **Script options**, thay đổi:
   ```
   "iPhone 17 Pro"
   ```
6. Click **OK**

### Cách 2: Tạo File XML Mới

Tạo file `.idea/runConfigurations/iosApp__iPhone_17_Pro_.xml`:

```xml
&lt;component name="ProjectRunConfigurationManager"&gt;
  &lt;configuration default="false" name="iosApp (iPhone 17 Pro)" type="ShellConfigurationType"&gt;
    &lt;option name="SCRIPT_PATH" value="$PROJECT_DIR$/.run-ios-from-studio.sh" /&gt;
    &lt;option name="SCRIPT_OPTIONS" value="&amp;quot;iPhone 17 Pro&amp;quot;" /&gt;
    &lt;option name="SCRIPT_WORKING_DIRECTORY" value="$PROJECT_DIR$" /&gt;
    &lt;option name="INTERPRETER_PATH" value="/bin/bash" /&gt;
    &lt;option name="EXECUTE_IN_TERMINAL" value="true" /&gt;
    &lt;option name="EXECUTE_SCRIPT_FILE" value="true" /&gt;
    &lt;method v="2" /&gt;
  &lt;/configuration&gt;
&lt;/component&gt;
```

Restart Android Studio.

---

## 🔍 Xem Danh Sách Simulators

Chạy script helper:

```bash
./list-ios-simulators.sh
```

Output:
```
📱 Available iOS Simulators:
    iPhone 16 Pro
    iPhone 16 Pro Max
    iPhone 17 Pro
    iPad Pro 11-inch (M4)
    ...
```

---

## 🐛 Troubleshooting

### ❌ Không thấy iOS configurations trong dropdown

**Giải pháp:**
1. Restart Android Studio
2. Hoặc: **File** → **Invalidate Caches** → **Invalidate and Restart**

### ❌ Lỗi: "Simulator not found"

**Giải pháp:**
```bash
# Xem danh sách simulators
xcrun simctl list devices

# Boot simulator trước
xcrun simctl boot "iPhone 16"
```

### ❌ Lỗi: "Framework not found"

**Giải pháp:**
```bash
# Build lại framework
./gradlew clean :demo:linkDebugFrameworkIosSimulatorArm64
```

### ❌ Lỗi: "xcodebuild command not found"

**Giải pháp:**
```bash
# Cài Xcode Command Line Tools
xcode-select --install
```

### ❌ App launch nhưng crash ngay

**Giải pháp:**
1. Xem console output trong Android Studio
2. Hoặc xem logs:
   ```bash
   xcrun simctl spawn booted log stream --predicate 'processImagePath contains "PermissionDemo"'
   ```

---

## 📝 Shortcuts Hữu Ích

| Action | Shortcut |
|--------|----------|
| Run selected configuration | **Shift+F10** (Win/Linux)<br>**Ctrl+R** (Mac) |
| Edit configurations | **Alt+Shift+F10** → **0** |
| Select configuration | **Alt+Shift+F10** |

---

## 🎯 Quick Commands (Alternative)

Nếu không muốn dùng Android Studio UI, dùng terminal:

```bash
# Run default (iPhone 16)
./ios-quick-run.sh

# Run trên device khác
./ios-quick-run.sh "iPhone 16 Pro"

# Chỉ build framework
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
```

---

## 🚀 Next Steps

### 1. Setup Real Device Testing

Để run trên real iPhone/iPad:
1. Kết nối device qua USB
2. Open Xcode project:
   ```bash
   open demo/iosApp/GrantDemo/GrantDemo.xcodeproj
   ```
3. Select device từ Xcode
4. Run từ Xcode (Android Studio chưa support real device)

### 2. Debug iOS App

Dùng Xcode cho debugging:
```bash
# Build framework trước
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64

# Mở Xcode
open demo/iosApp/GrantDemo/GrantDemo.xcodeproj

# Set breakpoints và debug như bình thường
```

### 3. Setup Hot Reload (Advanced)

Chưa support cho iOS (limitation của KMP). Mỗi lần thay đổi code:
1. Rebuild framework
2. Rerun app

---

## ✅ Hoàn Tất!

Giờ bạn đã có thể:
- ✅ Run iOS app từ Android Studio toolbar
- ✅ Chọn simulators khác nhau
- ✅ Build framework riêng biệt
- ✅ Debug với Xcode khi cần

**Enjoy coding! 🎉**

---

**Made with ❤️ by Grant KMP Team**
