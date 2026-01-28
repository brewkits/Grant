# iOS Info.plist Configuration

## Overview

iOS requires you to declare **usage descriptions** in `Info.plist` for each permission before requesting it. **If you forget to add these keys, your app will crash immediately** when requesting the permission.

Grant provides helpful logging to detect missing keys before release, but it's critical to add them during development.

---

## Required Keys by Permission

### Camera (AppGrant.CAMERA)

```xml
<key>NSCameraUsageDescription</key>
<string>We need camera access to take photos and scan QR codes</string>
```

**⚠️ Critical**: App will **crash immediately** if this key is missing when requesting camera permission.

**Best Practice**: Be specific about why you need camera access:
- ✅ "Camera is needed to scan QR codes for secure login"
- ✅ "We need camera access to capture receipts for expense reports"
- ❌ "This app needs camera permission" (too generic)

---

### Microphone (AppGrant.MICROPHONE)

```xml
<key>NSMicrophoneUsageDescription</key>
<string>We need microphone access to record audio messages</string>
```

**⚠️ Critical**: App will **crash immediately** if missing.

**Examples**:
- "Microphone is needed to record voice memos"
- "We need microphone access for video calls"

---

### Photo Library (AppGrant.GALLERY, GALLERY_IMAGES_ONLY, GALLERY_VIDEO_ONLY)

```xml
<key>NSPhotoLibraryUsageDescription</key>
<string>We need photo library access to select images for your profile</string>

<!-- iOS 14+ for adding photos -->
<key>NSPhotoLibraryAddUsageDescription</key>
<string>We need permission to save photos to your library</string>
```

**⚠️ Critical**: App will **crash immediately** if `NSPhotoLibraryUsageDescription` is missing.

**Note**:
- `NSPhotoLibraryUsageDescription` - Required for **reading** photos
- `NSPhotoLibraryAddUsageDescription` - Required for **writing** photos (optional, only if you save photos)

---

### Location (AppGrant.LOCATION)

```xml
<!-- When-in-use (foreground) location -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need your location to show nearby restaurants</string>
```

**⚠️ Critical**: App will **crash immediately** if missing when requesting location.

**Examples**:
- "Location is needed to show weather for your area"
- "We use your location to find nearby stores"

---

### Background Location (AppGrant.LOCATION_ALWAYS)

```xml
<!-- Required: When-in-use description -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>We need your location to track your routes</string>

<!-- Required: Always description -->
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>We need background location to track your runs even when the app is closed</string>

<!-- Optional but recommended: Explain why background is needed -->
<key>NSLocationAlwaysUsageDescription</key>
<string>We track your location in the background to record your entire workout route</string>
```

**⚠️ Critical**: Both keys required. App crashes if missing.

**Important**: Background location is highly sensitive. Be very clear about:
- **Why** you need background location
- **What** you'll do with the data
- **When** location tracking happens

**Good Examples**:
- "Background location tracks your running routes when the app is closed, so you can review your workout later"
- "We track your delivery location in the background to provide accurate ETAs to customers"

---

### Contacts (AppGrant.CONTACTS)

```xml
<key>NSContactsUsageDescription</key>
<string>We need access to your contacts to help you find friends</string>
```

**⚠️ Critical**: App will **crash immediately** if missing.

**Examples**:
- "Access to contacts helps you invite friends to the app"
- "We use contacts to auto-fill recipient information"

---

### Bluetooth (AppGrant.BLUETOOTH)

```xml
<!-- iOS 13+ -->
<key>NSBluetoothAlwaysUsageDescription</key>
<string>We need Bluetooth to connect to your fitness tracker</string>

<!-- iOS 13+ (alternative, if you only need it when app is active) -->
<key>NSBluetoothPeripheralUsageDescription</key>
<string>We use Bluetooth to sync with nearby devices</string>
```

**⚠️ Critical**: At least one key required. App crashes if missing.

**Note**:
- Use `NSBluetoothAlwaysUsageDescription` for most cases
- `NSBluetoothPeripheralUsageDescription` is for peripheral mode (less common)

---

### Calendar (If you add calendar permissions in future)

```xml
<key>NSCalendarsUsageDescription</key>
<string>We need calendar access to schedule your appointments</string>
```

---

### Motion / Activity (AppGrant.MOTION)

```xml
<key>NSMotionUsageDescription</key>
<string>We need motion data to track your steps and activity</string>
```

**⚠️ Critical**: App will **crash immediately** if missing.

---

## Complete Example Info.plist

Here's a complete example for an app using camera, location, and contacts:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <!-- Your app configuration -->
    <key>CFBundleDisplayName</key>
    <string>My Awesome App</string>

    <!-- Grant Permission Descriptions -->
    <key>NSCameraUsageDescription</key>
    <string>Camera is needed to scan QR codes for secure login</string>

    <key>NSMicrophoneUsageDescription</key>
    <string>Microphone is needed to record audio for video messages</string>

    <key>NSPhotoLibraryUsageDescription</key>
    <string>Photo library access lets you select images for your profile</string>

    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Location is needed to show nearby restaurants and stores</string>

    <key>NSContactsUsageDescription</key>
    <string>Access to contacts helps you find and invite friends</string>

    <key>NSBluetoothAlwaysUsageDescription</key>
    <string>Bluetooth is used to connect to your fitness tracker</string>

    <key>NSMotionUsageDescription</key>
    <string>Motion data is used to track your steps and activity level</string>
</dict>
</plist>
```

---

## Debugging Missing Keys

### Enable Grant Logging

Add this **during development** to catch missing keys:

```kotlin
// In your App initialization (Application.onCreate() or similar)
import dev.brewkits.grant.utils.GrantLogger

fun initializeApp() {
    // Enable logging during development
    GrantLogger.isEnabled = true  // ⚠️ Set to false for production
}
```

### What Happens if Key is Missing?

**Without Grant logging**:
- App requests permission
- **App crashes immediately** with error like:
  ```
  This app has crashed because it attempted to access privacy-sensitive data
  without a usage description. The app's Info.plist must contain an
  NSCameraUsageDescription key with a string value explaining to the user
  how the app uses this data.
  ```

**With Grant logging enabled**:
- Grant detects missing key **before** requesting
- Logs warning: `⚠️ [Grant] Missing Info.plist key: NSCameraUsageDescription`
- Returns `DENIED_ALWAYS` instead of crashing
- You can fix it before testing

---

## Production Checklist

Before releasing your app, verify:

- [ ] All Info.plist keys added for permissions you use
- [ ] Usage descriptions are specific and user-friendly (not generic)
- [ ] Descriptions explain **why** you need the permission
- [ ] Grant logging disabled: `GrantLogger.isEnabled = false`
- [ ] Tested on real iOS device (not just simulator)
- [ ] Tested permission flow: deny → settings → re-enable

---

## Platform-Specific Notes

### iOS Simulator Limitations

Some permissions behave differently in simulator:
- **Camera**: Shows "Simulated Camera" dialog
- **Bluetooth**: May not work properly
- **Motion**: Limited or no data

**Always test on real devices** before release.

---

### iOS Version Requirements

| Permission | Minimum iOS | Notes |
|------------|-------------|-------|
| Camera | iOS 7.0+ | Standard |
| Microphone | iOS 7.0+ | Standard |
| Photo Library | iOS 8.0+ | `NSPhotoLibraryAddUsageDescription` requires iOS 14+ |
| Location | iOS 8.0+ | `NSLocationAlwaysAndWhenInUseUsageDescription` requires iOS 11+ |
| Contacts | iOS 9.0+ | Standard |
| Bluetooth | iOS 13.0+ | `NSBluetoothAlwaysUsageDescription` |
| Motion | iOS 7.0+ | Standard |

Grant supports **iOS 13.0+**, so all keys are compatible.

---

## FAQ

### Q: What if I forget a key and ship to production?

**A**: Your app will crash for users when they try to use that feature. Apple may also **reject your app** during review if they test permissions and encounter crashes.

**Prevention**:
- Enable Grant logging during development
- Test all permission flows before release
- Use CI/CD to validate Info.plist has required keys

---

### Q: Can I use the same description for multiple permissions?

**A**: Technically yes, but **not recommended**. Each permission should have a specific, relevant description:

❌ **Bad** (generic):
```xml
<key>NSCameraUsageDescription</key>
<string>This app needs access to function</string>
<key>NSMicrophoneUsageDescription</key>
<string>This app needs access to function</string>
```

✅ **Good** (specific):
```xml
<key>NSCameraUsageDescription</key>
<string>Camera is needed to scan QR codes</string>
<key>NSMicrophoneUsageDescription</key>
<string>Microphone is needed to record voice messages</string>
```

---

### Q: How do I test if my Info.plist is correct?

1. Enable Grant logging: `GrantLogger.isEnabled = true`
2. Run app on real iOS device
3. Trigger each permission request
4. Check Xcode console for warnings
5. Verify no crashes occur

---

## Resources

- [Apple's Info.plist Documentation](https://developer.apple.com/documentation/bundleresources/information_property_list)
- [App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/)
- [Grant iOS Setup Guide](ios-setup.md)

---

**Remember**: Missing Info.plist keys = **instant crash**. Always test thoroughly! 🚨
