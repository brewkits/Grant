# iOS Info.plist Localization Guide

## The Problem

iOS permission descriptions are defined in `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>Camera is used to scan QR codes</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>Location is used to show nearby stores</string>
```

**Issue:** These strings are **hardcoded in English**. How do we localize them for other languages?

---

## Solution: InfoPlist.strings File

iOS provides `InfoPlist.strings` file for localizing Info.plist values.

### Step 1: Create Base InfoPlist.strings

1. In Xcode, right-click on your iOS app folder
2. Select **New File** → **Strings File**
3. Name it: `InfoPlist.strings`
4. Save it to your iOS app directory

Initial file (English - Base):
```
/* Camera Permission */
"NSCameraUsageDescription" = "Camera is used to scan QR codes for quick login";

/* Location Permissions */
"NSLocationWhenInUseUsageDescription" = "Location is used to show nearby stores";
"NSLocationAlwaysAndWhenInUseUsageDescription" = "Location is used to track delivery routes in background";

/* Photo Library */
"NSPhotoLibraryUsageDescription" = "Photos are used to upload profile pictures";

/* Microphone */
"NSMicrophoneUsageDescription" = "Microphone is used to record audio messages";

/* Contacts */
"NSContactsUsageDescription" = "Contacts are used to find and invite your friends";

/* Bluetooth */
"NSBluetoothAlwaysUsageDescription" = "Bluetooth is used to connect to fitness trackers";

/* Motion & Fitness */
"NSMotionUsageDescription" = "Motion data is used for step counting and activity tracking";

/* Notifications */
"NSUserNotificationsUsageDescription" = "Notifications are used to send you important updates";
```

---

### Step 2: Localize InfoPlist.strings

#### Method 1: Using Xcode (Recommended)

1. Select `InfoPlist.strings` in Xcode
2. Open **File Inspector** (right panel)
3. Click **Localize...** button
4. Select **Base** language, click **Localize**
5. In File Inspector, check additional languages (e.g., Vietnamese, Japanese)
6. Xcode will create language-specific files

File structure created:
```
iosApp/
├── en.lproj/
│   └── InfoPlist.strings    (English)
├── vi.lproj/
│   └── InfoPlist.strings    (Vietnamese)
└── ja.lproj/
    └── InfoPlist.strings    (Japanese)
```

#### Method 2: Manual Creation

Create folders manually:

**English (en.lproj/InfoPlist.strings):**
```
"NSCameraUsageDescription" = "Camera is used to scan QR codes for quick login";
"NSLocationWhenInUseUsageDescription" = "Location is used to show nearby stores";
"NSPhotoLibraryUsageDescription" = "Photos are used to upload profile pictures";
```

**Vietnamese (vi.lproj/InfoPlist.strings):**
```
"NSCameraUsageDescription" = "Camera được sử dụng để quét mã QR đăng nhập nhanh";
"NSLocationWhenInUseUsageDescription" = "Vị trí được dùng để hiển thị các cửa hàng gần bạn";
"NSPhotoLibraryUsageDescription" = "Thư viện ảnh được dùng để tải ảnh đại diện";
```

**Japanese (ja.lproj/InfoPlist.strings):**
```
"NSCameraUsageDescription" = "カメラはQRコードをスキャンしてクイックログインに使用されます";
"NSLocationWhenInUseUsageDescription" = "位置情報は近くの店舗を表示するために使用されます";
"NSPhotoLibraryUsageDescription" = "フォトライブラリはプロフィール写真のアップロードに使用されます";
```

---

### Step 3: Update Info.plist

**IMPORTANT:** Keep the keys in `Info.plist` but values can be simple (will be overridden by localized strings):

```xml
<!-- Info.plist -->
<key>NSCameraUsageDescription</key>
<string>Camera access needed</string>  <!-- Fallback if localization fails -->

<key>NSLocationWhenInUseUsageDescription</key>
<string>Location access needed</string>

<key>NSPhotoLibraryUsageDescription</key>
<string>Photo library access needed</string>
```

---

### Step 4: Add Supported Languages to Project

In Xcode:
1. Select **Project** (not target)
2. Go to **Info** tab
3. Under **Localizations**, click **+**
4. Add languages: Vietnamese, Japanese, etc.

Or in `project.pbxproj`:
```ruby
knownRegions = (
    en,
    Base,
    vi,
    ja,
);
```

---

## How iOS Chooses Language

iOS automatically selects the appropriate localization based on:

1. **Device language setting** - Primary factor
2. **App's supported languages** - Must match
3. **Fallback to Base/English** - If no match found

**Example:**
- Device language: Vietnamese
- App has: en, vi, ja
- Result: Uses `vi.lproj/InfoPlist.strings`

---

## Testing Localization

### Method 1: Change Simulator Language

1. Open **Settings** app in Simulator
2. Go to **General** → **Language & Region**
3. Add language (e.g., Vietnamese)
4. Set as primary language
5. Restart your app
6. Request permission → Dialog shows Vietnamese text

### Method 2: Xcode Scheme

1. Edit Scheme → Run
2. Options tab → App Language
3. Select language (Vietnamese, Japanese, etc.)
4. Run app
5. Request permission → Dialog shows selected language

### Method 3: Using -AppleLanguages Launch Argument

Add to Run Arguments:
```
-AppleLanguages (vi)
```

---

## Complete Example

### File Structure
```
iosApp/
├── Info.plist                        (Contains all permission keys)
├── en.lproj/
│   └── InfoPlist.strings             (English descriptions)
├── vi.lproj/
│   └── InfoPlist.strings             (Vietnamese descriptions)
└── ja.lproj/
    └── InfoPlist.strings             (Japanese descriptions)
```

### Info.plist
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Camera -->
    <key>NSCameraUsageDescription</key>
    <string>Camera access needed</string>

    <!-- Location -->
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Location access needed</string>

    <key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
    <string>Background location access needed</string>

    <!-- Photos -->
    <key>NSPhotoLibraryUsageDescription</key>
    <string>Photo library access needed</string>

    <!-- Microphone -->
    <key>NSMicrophoneUsageDescription</key>
    <string>Microphone access needed</string>

    <!-- Contacts -->
    <key>NSContactsUsageDescription</key>
    <string>Contacts access needed</string>

    <!-- Bluetooth -->
    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>Bluetooth access needed</string>

    <!-- Motion -->
    <key>NSMotionUsageDescription</key>
    <string>Motion data access needed</string>
</dict>
</plist>
```

### en.lproj/InfoPlist.strings
```
/* Camera Permission */
"NSCameraUsageDescription" = "Camera is used to scan QR codes for quick login and product identification";

/* Location Permissions */
"NSLocationWhenInUseUsageDescription" = "Location is used to show nearby stores and calculate delivery distance";
"NSLocationAlwaysAndWhenInUseUsageDescription" = "Background location is used to track delivery routes even when the app is closed. This helps provide accurate delivery ETAs.";

/* Photo Library */
"NSPhotoLibraryUsageDescription" = "Photo library access is used to upload profile pictures and share product images";

/* Microphone */
"NSMicrophoneUsageDescription" = "Microphone is used to record voice messages and audio notes";

/* Contacts */
"NSContactsUsageDescription" = "Contacts access is used to help you find and invite friends to the app";

/* Bluetooth */
"NSBluetoothAlwaysUsageDescription" = "Bluetooth is used to connect to fitness trackers and smart devices";

/* Motion & Fitness */
"NSMotionUsageDescription" = "Motion data is used for step counting, activity tracking, and fitness goals";
```

### vi.lproj/InfoPlist.strings
```
/* Camera Permission */
"NSCameraUsageDescription" = "Camera được sử dụng để quét mã QR đăng nhập nhanh và nhận dạng sản phẩm";

/* Location Permissions */
"NSLocationWhenInUseUsageDescription" = "Vị trí được dùng để hiển thị các cửa hàng gần bạn và tính khoảng cách giao hàng";
"NSLocationAlwaysAndWhenInUseUsageDescription" = "Vị trí nền được dùng để theo dõi lộ trình giao hàng ngay cả khi ứng dụng đóng. Điều này giúp cung cấp thời gian giao hàng chính xác.";

/* Photo Library */
"NSPhotoLibraryUsageDescription" = "Thư viện ảnh được dùng để tải ảnh đại diện và chia sẻ ảnh sản phẩm";

/* Microphone */
"NSMicrophoneUsageDescription" = "Microphone được dùng để ghi âm tin nhắn thoại và ghi chú âm thanh";

/* Contacts */
"NSContactsUsageDescription" = "Danh bạ được dùng để giúp bạn tìm và mời bạn bè vào ứng dụng";

/* Bluetooth */
"NSBluetoothAlwaysUsageDescription" = "Bluetooth được dùng để kết nối với thiết bị theo dõi sức khỏe và thiết bị thông minh";

/* Motion & Fitness */
"NSMotionUsageDescription" = "Dữ liệu chuyển động được dùng để đếm bước chân, theo dõi hoạt động và mục tiêu thể dục";
```

---

## Best Practices

### 1. Be Specific and Honest
**❌ BAD:**
```
"NSCameraUsageDescription" = "This app needs camera access";
```

**✅ GOOD:**
```
"NSCameraUsageDescription" = "Camera is used to scan QR codes for quick login";
```

### 2. Explain the Value to User
**❌ BAD:**
```
"NSLocationAlwaysAndWhenInUseUsageDescription" = "We need your location";
```

**✅ GOOD:**
```
"NSLocationAlwaysAndWhenInUseUsageDescription" = "Background location is used to track delivery routes even when the app is closed. This helps provide accurate delivery ETAs.";
```

### 3. Use Natural Language
- Write like you're talking to a person
- Avoid technical jargon
- Keep sentences clear and concise

### 4. Maintain Consistency Across Languages
- Use same tone and length
- Don't add/remove information in translations
- Test all languages on device

### 5. Update When Features Change
- If you add new use case for camera, update the description
- Keep descriptions synced across all language files

---

## Common Mistakes

### ❌ Mistake 1: Forgetting to Add Key to Info.plist
```
// InfoPlist.strings has the key
"NSCameraUsageDescription" = "...";

// But Info.plist is missing the key!
// Result: App crashes when requesting permission
```

**Fix:** Always add key to Info.plist first, even with placeholder text.

### ❌ Mistake 2: Wrong File Name
```
// Wrong
InfoPlists.strings
info-plist.strings
InfoPList.strings

// Correct
InfoPlist.strings
```

**Fix:** File name is case-sensitive! Use exact name: `InfoPlist.strings`

### ❌ Mistake 3: Not Testing All Languages
- English works, but Vietnamese shows English fallback
- Reason: Missing `vi.lproj` folder or wrong key in translation

**Fix:** Test EVERY supported language on device/simulator.

### ❌ Mistake 4: Generic Messages
```
"NSLocationAlwaysAndWhenInUseUsageDescription" = "For better experience";
```

**Fix:** Be specific! Apple may reject vague descriptions.

---

## Verification Checklist

Before submitting to App Store:

- [ ] All permission keys exist in `Info.plist`
- [ ] All keys have corresponding entries in `InfoPlist.strings`
- [ ] All supported languages have complete `InfoPlist.strings` files
- [ ] Descriptions are specific and explain value to user
- [ ] Tested on device in each supported language
- [ ] No generic/vague descriptions (Apple may reject)
- [ ] Descriptions match actual app behavior
- [ ] No typos or grammar errors in any language

---

## Debugging Tips

### Permission Dialog Shows English When Should Be Vietnamese

**Check:**
1. Device language is set to Vietnamese
2. Project has `vi.lproj` folder
3. `vi.lproj/InfoPlist.strings` exists
4. File encoding is UTF-8
5. Keys match exactly (case-sensitive)

**Fix:**
```bash
# Check file encoding
file -I vi.lproj/InfoPlist.strings
# Should output: charset=utf-8

# Verify keys match
grep NSCameraUsageDescription Info.plist
grep NSCameraUsageDescription vi.lproj/InfoPlist.strings
```

### Permission Request Crashes App

**Error:**
```
This app has crashed because it attempted to access privacy-sensitive data without a usage description.
```

**Fix:** Add the missing key to `Info.plist`:
```xml
<key>NSCameraUsageDescription</key>
<string>Camera access needed</string>
```

---

## Reference

### All Permission Keys

```
NSCameraUsageDescription
NSPhotoLibraryUsageDescription
NSPhotoLibraryAddUsageDescription
NSLocationWhenInUseUsageDescription
NSLocationAlwaysAndWhenInUseUsageDescription
NSLocationAlwaysUsageDescription (deprecated, use above)
NSMicrophoneUsageDescription
NSContactsUsageDescription
NSCalendarsUsageDescription
NSRemindersUsageDescription
NSMotionUsageDescription
NSHealthShareUsageDescription
NSHealthUpdateUsageDescription
NSBluetoothAlwaysUsageDescription
NSBluetoothPeripheralUsageDescription (deprecated)
NSAppleMusicUsageDescription
NSSpeechRecognitionUsageDescription
NSFaceIDUsageDescription
NSSiriUsageDescription
NSUserTrackingUsageDescription (iOS 14.5+)
```

### Supported Languages in iOS

Common language codes:
- `en` - English
- `vi` - Vietnamese
- `ja` - Japanese
- `ko` - Korean
- `zh-Hans` - Simplified Chinese
- `zh-Hant` - Traditional Chinese
- `es` - Spanish
- `fr` - French
- `de` - German
- `it` - Italian
- `pt-BR` - Portuguese (Brazil)
- `ru` - Russian
- `ar` - Arabic
- `th` - Thai
- `id` - Indonesian

---

## Additional Resources

- [Apple Documentation - Localization](https://developer.apple.com/documentation/xcode/localization)
- [Apple Documentation - Info.plist](https://developer.apple.com/library/archive/documentation/General/Reference/InfoPlistKeyReference/Introduction/Introduction.html)
- [iOS Privacy Best Practices](https://developer.apple.com/design/human-interface-guidelines/privacy)

---

*Last Updated: 2026-01-23*
