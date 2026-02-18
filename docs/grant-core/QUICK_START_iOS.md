# ⚡ Quick Start: Run iOS from Android Studio

## TL;DR - 3 Simple Steps:

### 1️⃣ Create Xcode Project (one-time only)

```bash
# Open Xcode
open -a Xcode

# Or use terminal
cd iosApp
```

**In Xcode:**
- File > New > Project > iOS App
- Name: `GrantDemo`
- Bundle ID: `dev.brewkits.grant.demo`
- Interface: SwiftUI
- Save in `iosApp/` folder

### 2️⃣ Setup Run Configuration in Android Studio

**Android Studio > Run > Edit Configurations > + > Shell Script**

Fill in:
- **Name**: `iOS Simulator`
- **Script text**:
  ```bash
  cd "$PROJECT_DIR" && ./setup-ios-app.sh
  ```

Click **OK**

### 3️⃣ Run!

Click **Run** button ▶️ with "iOS Simulator" configuration

---

## More Details

### First-Time Setup (5 minutes)

**Step 1: Create Xcode Project**

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

**Step 2: Link Framework**

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
   (If this folder doesn't exist yet, run `./gradlew :demo:linkDebugFrameworkIosSimulatorArm64` first)
8. Click **Open**
9. Ensure **Embed & Sign** is selected

**Step 3: Update App Code**

Open `ContentView.swift` and replace with:

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

**Step 4: Update Info.plist**

This file already exists in `iosApp/Info.plist`. Copy it to Xcode project:
- Drag & drop `iosApp/Info.plist` into Xcode project

Or add grants manually:
1. Select project > Target > Info tab
2. Add custom iOS target properties:
   - `NSCameraUsageDescription`: "Camera is needed for demo"
   - `NSMicrophoneUsageDescription`: "Microphone is needed for demo"
   - `NSLocationWhenInUseUsageDescription`: "Location is needed for demo"
   - `NSContactsUsageDescription`: "Contacts is needed for demo"
   - `NSPhotoLibraryUsageDescription`: "Photo library is needed for demo"

**Step 5: Test Build from Xcode**

1. Select **iPhone 16 Pro** simulator (or any iPhone)
2. Click **Run** button (⌘R)
3. Verify app launches successfully

✅ **Setup complete!** Now you can run from Android Studio.

---

### Setup Run Configuration in Android Studio

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

### Run iOS App from Android Studio

1. Select **iOS Simulator** run configuration
2. Click **Run** ▶️

The script will:
- ✅ Build KMP framework
- ✅ Build iOS app with xcodebuild
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

**Done! Now you can test iOS and Android at the same time!** 🎉
