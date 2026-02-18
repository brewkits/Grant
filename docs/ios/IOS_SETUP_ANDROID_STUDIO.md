# 📱 iOS Development from Android Studio

## ✅ Setup Complete!

You now have **6 iOS run configurations** in Android Studio toolbar:

### 🚀 Available Run Configurations

| Configuration | Description | Device/Simulator |
|---------------|-------------|------------------|
| **iosApp** | Default iOS app | iPhone 16 (Default) |
| **iosApp (iPhone 16 Pro)** | iOS app on iPhone Pro | iPhone 16 Pro |
| **iosApp (iPad Pro)** | iOS app on iPad | iPad Pro 11-inch |
| **Build iOS Framework Only** | Build framework only, don't launch | N/A |
| **iOS App (Simulator)** | Advanced config | iPhone 16 |
| **Launch iOS Simulator** | Launch simulator only | iPhone 16 |

---

## 🎯 How to Use

### Step 1: Restart Android Studio

```bash
# Restart Android Studio to load new run configurations
```

### Step 2: Select Configuration

1. In the top toolbar, click the dropdown (next to Run ▶️ button)
2. You will see the configurations:
   ```
   📱 iosApp
   📱 iosApp (iPhone 16 Pro)
   📱 iosApp (iPad Pro)
   🔧 Build iOS Framework Only
   ```

### Step 3: Run!

1. Select the configuration you want (e.g., **iosApp**)
2. Click **Run** ▶️ button (or **Shift+F10**)
3. View console output in Android Studio
4. iOS Simulator will automatically open and launch the app! 🎉

---

## 🔧 Configuration Details

### 1. **iosApp** (Recommended)
- **Description:** Run app on iPhone 16 simulator (default)
- **Use when:** Daily development
- **Steps:**
  1. Build Kotlin framework
  2. Build iOS app with Xcode
  3. Launch on iPhone 16 simulator

### 2. **iosApp (iPhone 16 Pro)**
- **Description:** Run app on iPhone 16 Pro simulator
- **Use when:** Test on larger screen, camera ProRAW, etc.

### 3. **iosApp (iPad Pro)**
- **Description:** Run app on iPad Pro simulator
- **Use when:** Test iPad layout, multitasking

### 4. **Build iOS Framework Only**
- **Description:** Build Kotlin framework only, don't launch app
- **Use when:**
  - Check if framework builds without errors
  - Then open Xcode to debug
  - CI/CD pipeline

---

## 🎨 Create Configuration for Other Simulators

### Method 1: Duplicate Existing Configuration

1. **Run** → **Edit Configurations...**
2. Select **iosApp**
3. Click **Copy** (icon with 2 pages)
4. Rename: `iosApp (iPhone 17 Pro)`
5. In **Script options**, change to:
   ```
   "iPhone 17 Pro"
   ```
6. Click **OK**

### Method 2: Create New XML File

Create file `.idea/runConfigurations/iosApp__iPhone_17_Pro_.xml`:

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

## 🔍 View Simulator List

Run helper script:

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

### ❌ Can't see iOS configurations in dropdown

**Solution:**
1. Restart Android Studio
2. Or: **File** → **Invalidate Caches** → **Invalidate and Restart**

### ❌ Error: "Simulator not found"

**Solution:**
```bash
# View simulator list
xcrun simctl list devices

# Boot simulator first
xcrun simctl boot "iPhone 16"
```

### ❌ Error: "Framework not found"

**Solution:**
```bash
# Rebuild framework
./gradlew clean :demo:linkDebugFrameworkIosSimulatorArm64
```

### ❌ Error: "xcodebuild command not found"

**Solution:**
```bash
# Install Xcode Command Line Tools
xcode-select --install
```

### ❌ App launches but crashes immediately

**Solution:**
1. View console output in Android Studio
2. Or view logs:
   ```bash
   xcrun simctl spawn booted log stream --predicate 'processImagePath contains "PermissionDemo"'
   ```

---

## 📝 Useful Shortcuts

| Action | Shortcut |
|--------|----------|
| Run selected configuration | **Shift+F10** (Win/Linux)<br>**Ctrl+R** (Mac) |
| Edit configurations | **Alt+Shift+F10** → **0** |
| Select configuration | **Alt+Shift+F10** |

---

## 🎯 Quick Commands (Alternative)

If you don't want to use Android Studio UI, use terminal:

```bash
# Run default (iPhone 16)
./ios-quick-run.sh

# Run on different device
./ios-quick-run.sh "iPhone 16 Pro"

# Build framework only
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64
```

---

## 🚀 Next Steps

### 1. Setup Real Device Testing

To run on real iPhone/iPad:
1. Connect device via USB
2. Open Xcode project:
   ```bash
   open demo/iosApp/GrantDemo/GrantDemo.xcodeproj
   ```
3. Select device from Xcode
4. Run from Xcode (Android Studio doesn't support real devices yet)

### 2. Debug iOS App

Use Xcode for debugging:
```bash
# Build framework first
./gradlew :demo:linkDebugFrameworkIosSimulatorArm64

# Open Xcode
open demo/iosApp/GrantDemo/GrantDemo.xcodeproj

# Set breakpoints and debug as usual
```

### 3. Setup Hot Reload (Advanced)

Not supported for iOS yet (KMP limitation). For each code change:
1. Rebuild framework
2. Rerun app

---

## ✅ Complete!

Now you can:
- ✅ Run iOS app from Android Studio toolbar
- ✅ Select different simulators
- ✅ Build framework separately
- ✅ Debug with Xcode when needed

**Enjoy coding! 🎉**

---

**Made with ❤️ by Grant KMP Team**
