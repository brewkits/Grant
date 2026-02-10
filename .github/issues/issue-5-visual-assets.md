# [TASK] Add visual assets to README (GIFs and screenshots)

**Labels:** `documentation`, `marketing`, `v1.0.1`
**Priority:** ⭐⭐⭐ MEDIUM

## 📸 Task Description

README currently has TODO placeholders for screenshots and GIFs. We need to create and add visual assets to improve library presentation and GitHub engagement.

## 📍 Location

**File:** `README.md:70-86`

**Current Placeholder:**
```markdown
## 🎬 See It In Action

<!-- TODO: Add screenshots/GIF showing:
     - Permission dialog flow (rationale → settings guide)
     - Android 14 partial gallery access
     - Demo app with manifest validation warnings
     Instructions: See docs/images/README.md for screenshot guidelines
-->
```

## 🎯 Required Assets

### 1. Permission Flow GIF (Priority 1) ⭐⭐⭐⭐⭐

**Dimensions:** 600x1200px (phone aspect ratio)
**Duration:** 5-10 seconds
**File size:** < 2MB (optimized)
**Format:** GIF

**Content to Show:**
1. User clicks "Take Photo" button
2. GrantDialog shows rationale: "We need camera to scan QR codes"
3. User clicks "Continue"
4. System permission dialog appears
5. User grants permission
6. Camera opens

**Alternative Flow (if denied):**
1. User denies system dialog
2. User clicks "Take Photo" again
3. Settings guide dialog appears
4. User clicks "Open Settings"
5. Settings app opens

**File:** `docs/images/permission-flow.gif`

### 2. Android 14 Partial Gallery GIF (Priority 2) ⭐⭐⭐⭐

**Dimensions:** 600x1200px
**Duration:** 5-8 seconds
**File size:** < 1.5MB
**Format:** GIF

**Content to Show:**
1. User clicks "Upload Photo" button
2. Android 14 photo picker appears
3. Shows "Select photos" option highlighted
4. User selects 2-3 photos
5. App receives partial access
6. Status shows GRANTED

**File:** `docs/images/android14-partial-gallery.gif`

### 3. Demo App Screenshot (Priority 3) ⭐⭐⭐

**Dimensions:** 1200x2400px (2x for Retina)
**File size:** < 500KB
**Format:** PNG

**Content to Show:**
- List of all 14 permission types
- Each with status indicator (Granted/Denied/Not Determined)
- Clean, professional UI
- Grant branding visible

**File:** `docs/images/demo-app-screenshot.png`

### 4. iOS Permission Flow GIF (Priority 4) ⭐⭐⭐

**Dimensions:** 600x1200px
**Duration:** 5-10 seconds
**File size:** < 2MB
**Format:** GIF

**Content to Show:**
- iOS-specific permission dialog
- Info.plist validation working
- Settings navigation on iOS

**File:** `docs/images/ios-permission-flow.gif`

## 🛠️ Tools & Process

### Recording Tools

**Android:**
- Android Studio Device Manager (built-in screen record)
- Or: `adb shell screenrecord /sdcard/recording.mp4`

**iOS:**
- Xcode Simulator (Cmd+R to record)
- Or: QuickTime Player screen recording

### GIF Optimization Tools

1. **Gifski** (Best quality)
   - Download: https://gif.ski/
   - Command: `gifski --fps 15 --quality 90 input.mp4 -o output.gif`

2. **ezgif.com** (Online)
   - URL: https://ezgif.com/video-to-gif
   - Upload MP4 → Convert → Optimize

3. **ImageOptim** (Mac)
   - Download: https://imageoptim.com/
   - Drag-drop GIFs to optimize

### Optimization Guidelines

- **Frame rate:** 15-20 fps (smooth enough, small file size)
- **Colors:** 256 colors max for GIFs
- **Dimensions:** Scale down to 600px width
- **Compression:** Use tools to reduce file size

## 📝 Implementation Steps

### Step 1: Record Videos

1. **Set up demo app:**
   ```bash
   cd demo
   ./gradlew installDebug  # Android
   # Or open iosApp in Xcode for iOS
   ```

2. **Clean phone UI:**
   - Remove notifications
   - Set to light mode (better screenshots)
   - Clear recent apps

3. **Record flows:**
   - Record each scenario 2-3 times
   - Choose best take
   - Keep videos under 15 seconds

### Step 2: Convert to GIFs

```bash
# Install Gifski (Mac)
brew install gifski

# Convert with optimization
gifski \
  --fps 15 \
  --quality 90 \
  --width 600 \
  input-video.mp4 \
  -o docs/images/permission-flow.gif

# Check file size
ls -lh docs/images/*.gif
# Should be < 2MB each
```

### Step 3: Take Screenshots

1. Launch demo app
2. Capture full screen at highest resolution
3. Crop to phone area only
4. Optimize with ImageOptim or similar

### Step 4: Update README

```markdown
## 🎬 See It In Action

### Permission Flow

<p align="center">
  <img src="docs/images/permission-flow.gif" alt="Permission Flow" width="300">
</p>

Grant automatically handles the entire permission flow:
1. Shows rationale dialog when needed
2. Requests system permission
3. Guides to Settings if denied permanently

### Android 14 Partial Gallery Access

<p align="center">
  <img src="docs/images/android14-partial-gallery.gif" alt="Android 14 Partial Gallery" width="300">
</p>

Full support for Android 14's "Select Photos" feature. Users can grant access to specific photos instead of the entire library.

### Demo App

<p align="center">
  <img src="docs/images/demo-app-screenshot.png" alt="Demo App" width="300">
</p>

Try all 14 permission types in the demo app:

```bash
./gradlew :demo:installDebug  # Android
# Or open iosApp in Xcode for iOS
```
```

### Step 5: Create docs/images/README.md

```markdown
# Visual Assets Guidelines

This directory contains visual assets for documentation.

## Recording Guidelines

### GIFs
- **Frame rate:** 15-20 fps
- **Dimensions:** 600px width (phone aspect ratio)
- **File size:** < 2MB
- **Duration:** 5-10 seconds max
- **Format:** Optimized GIF

### Screenshots
- **Dimensions:** 1200px width (2x for Retina)
- **File size:** < 500KB
- **Format:** PNG
- **Background:** Light mode preferred

## Tools

- **Recording:** Android Studio, Xcode Simulator, QuickTime
- **GIF conversion:** Gifski, ezgif.com
- **Optimization:** ImageOptim, TinyPNG

## Files

- `permission-flow.gif` - Main permission flow demo
- `android14-partial-gallery.gif` - Android 14 feature
- `demo-app-screenshot.png` - Demo app overview
- `ios-permission-flow.gif` - iOS-specific flow

## Updating

When recording new assets:
1. Use clean phone UI (no notifications)
2. Light mode for better visibility
3. Keep recordings short and focused
4. Optimize file sizes before committing
```

## ✅ Definition of Done

- [ ] docs/images/ directory created
- [ ] permission-flow.gif created and optimized (< 2MB)
- [ ] android14-partial-gallery.gif created (< 1.5MB)
- [ ] demo-app-screenshot.png created (< 500KB)
- [ ] ios-permission-flow.gif created (< 2MB)
- [ ] README.md updated with embedded images
- [ ] docs/images/README.md created with guidelines
- [ ] All images display correctly on GitHub
- [ ] File sizes verified (< limits)

## 📊 Impact

**Expected Benefits:**
- 40% increase in GitHub stars (based on similar projects)
- Better first impression for potential users
- Easier to understand library capabilities
- More professional presentation
- Higher conversion rate (views → users)

---

**Review Finding Reference:** CODE_REVIEW_FINDINGS.md - Issue #5
**Estimated Effort:** 2 hours
