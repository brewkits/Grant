# ⚡ Quick Start: Run iOS từ Android Studio

## TL;DR - 3 Bước Đơn Giản:

### 1️⃣ Tạo Xcode Project (1 lần duy nhất)

```bash
# Open Xcode
open -a Xcode

# Hoặc dùng terminal
cd iosApp
```

**Trong Xcode:**
- File > New > Project > iOS App
- Name: `GrantDemo`
- Bundle ID: `dev.brewkits.grant.demo`
- Interface: SwiftUI
- Save trong folder `iosApp/`

### 2️⃣ Setup Run Configuration trong Android Studio

**Android Studio > Run > Edit Configurations > + > Shell Script**

Điền:
- **Name**: `iOS Simulator`
- **Script text**:
  ```bash
  cd "$PROJECT_DIR" && ./setup-ios-app.sh
  ```

Click **OK**

### 3️⃣ Run!

Click **Run** button ▶️ với configuration "iOS Simulator"

---

## Chi Tiết Hơn

### Lần Đầu Tiên Setup (5 phút)

**Bước 1: Tạo Xcode Project**

```bash
open -a Xcode
```

1. **File** > **New** > **Project**
2. **iOS** > **App** > **Next**
3. **Product Name**: `GrantDemo`
4. **Organization Identifier**: `dev.brewkits`
5. **Interface**: **SwiftUI**
6. **Language**: **Swift**
7. **Next** > Save in `iosApp/` folder

**Bước 2: Link Framework**

1. Select project "GrantDemo" in navigator
2. Select target "GrantDemo"
3. **General** tab
4. Scroll to **Frameworks, Libraries, and Embedded Content**
5. Click **+**
6. Click **Add Other...** > **Add Files...**
7. Navigate to:
   ```
   ../demo/build/bin/iosSimulatorArm64/debugFramework/GrantDemo.framework
   ```
   (Nếu folder này chưa có, run `./gradlew :demo:linkDebugFrameworkIosSimulatorArm64` trước)
8. Click **Open**
9. Ensure **Embed & Sign** is selected

**Bước 3: Update App Code**

Open `ContentView.swift` và replace với:

```swift
import SwiftUI
import GrantDemo

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all, edges: .all)
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // Kotlin exports with swift_name attribute, so use the Swift name
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No-op
    }
}

#Preview {
    ContentView()
}
```

**Bước 4: Update Info.plist**

File này đã có sẵn trong `iosApp/Info.plist`. Copy nó vào Xcode project:
- Drag & drop `iosApp/Info.plist` vào Xcode project

Hoặc add grants manually:
1. Select project > Target > Info tab
2. Add custom iOS target properties:
   - `NSCameraUsageDescription`: "Camera is needed for demo"
   - `NSMicrophoneUsageDescription`: "Microphone is needed for demo"
   - `NSLocationWhenInUseUsageDescription`: "Location is needed for demo"
   - `NSContactsUsageDescription`: "Contacts is needed for demo"
   - `NSPhotoLibraryUsageDescription`: "Photo library is needed for demo"

**Bước 5: Test Build từ Xcode**

1. Select **iPhone 16 Pro** simulator (or any iPhone)
2. Click **Run** button (⌘R)
3. Verify app launches successfully

✅ **Setup xong!** Giờ có thể run từ Android Studio.

---

### Setup Run Configuration trong Android Studio

**Method 1: Shell Script (Recommended)**

1. **Run** > **Edit Configurations...**
2. Click **+** > **Shell Script**
3. Fill in:
   - **Name**: `iOS Simulator`
   - **Script text**:
     ```bash
     cd "$PROJECT_DIR"
     ./setup-ios-app.sh
     ```
   - **Working directory**: `$PROJECT_DIR$`
4. Click **OK**

**Method 2: External Tool**

1. **Settings** > **Tools** > **External Tools**
2. Click **+**
3. Fill:
   - **Name**: `iOS Simulator`
   - **Program**: `/bin/bash`
   - **Arguments**: `$ProjectFileDir$/setup-ios-app.sh`
   - **Working directory**: `$ProjectFileDir$`
4. **OK**

Access via: **Tools** > **External Tools** > **iOS Simulator**

---

### Run iOS App từ Android Studio

1. Select **iOS Simulator** run configuration
2. Click **Run** ▶️

Script sẽ:
- ✅ Build KMP framework
- ✅ Build iOS app với xcodebuild
- ✅ Launch simulator
- ✅ Install app
- ✅ Run app

---

## Keyboard Shortcuts

Setup keyboard shortcut:

1. **Settings** > **Keymap**
2. Search "iOS Simulator"
3. Right-click > **Add Keyboard Shortcut**
4. Suggest: **⌥⌘R** (Option+Cmd+R)

---

## Troubleshooting 🔧

### "xcodebuild: command not found"

Install Xcode Command Line Tools:
```bash
xcode-select --install
```

### "Framework not found"

Build framework first:
```bash
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
```

### "No simulator found"

```bash
# List available simulators
xcrun simctl list devices

# Install more simulators via Xcode:
# Xcode > Settings > Platforms
```

### Build errors in Xcode

1. Open Xcode
2. Product > Clean Build Folder (⇧⌘K)
3. Rebuild (⌘B)

---

## VS Android Workflow 📊

| Step | Android | iOS |
|------|---------|-----|
| **1. Build** | Gradle auto | Gradle + xcodebuild |
| **2. Install** | ADB auto | simctl |
| **3. Launch** | ADB auto | simctl |
| **4. Run from AS** | ▶️ Native | ▶️ Via Script |
| **5. Debug** | Built-in | Xcode or logs |

---

**Xong! Giờ có thể test iOS và Android cùng lúc!** 🎉
