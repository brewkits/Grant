# Grant Demo App - User Guide

## Current Status

### ✅ **Grant Implementation** - WORKING
- Custom implementation with full permission support
- Can test system grant dialog, rationale dialog and settings dialog
- `openSettings()` works correctly
- Supports both Android and iOS

---

## How to Test Demo App (Android)

### 1. Open App
```bash
adb shell am start -n dev.brewkits.grant.demo/dev.brewkits.grant.demo.MainActivity
```

### 2. Test Grant Flow

#### Test Case: Request Camera

**Step 1: First Request**
```
1. Click "Request Camera → Microphone"
2. ✅ System Camera Grant Dialog appears:
   "Allow GrantDemo to take pictures and record video?"
   [While using the app] [Only this time] [Don't allow]
```

**Step 2: Deny Once → Custom Rationale Dialog Appears**
```
3. Select "Don't allow"
4. ✅ Custom Rationale Dialog appears:
   "Camera is required to capture video for your recordings"
   [Grant Permission] [Cancel]
```

**Step 3: Deny Twice → Custom Settings Dialog Appears**
```
5. Click "Grant Permission" in Rationale Dialog
6. ✅ System Camera Grant Dialog appears AGAIN. Select "Don't allow" again.
7. ✅ Custom Settings Dialog appears:
   "Camera access is disabled. Enable it in Settings > Permissions > Camera"
   [Open Settings] [Cancel]
```

**Step 4: Click Open Settings → App Settings Opens**
```
8. Click "Open Settings"
9. ✅ Android Settings app opens (specifically GrantDemo app settings)
10. From Settings, you need to MANUALLY GRANT Camera permission (toggle switch)
11. Return to demo app
12. Click "Request Camera → Microphone" AGAIN (3rd time)
13. ✅ Grant GRANTED! Success message appears
```

---

## Reset for Re-testing

Click **"Reset All Results"** button at the bottom to:
- Clear all grant states
- Reset request counts
- Test again from scratch

---

## Troubleshooting

### "Don't see dialog"
→ Ensure app has permission to display overlay (if needed).
→ Click button and follow system/custom dialog instructions.

### "Click Open Settings does nothing"
→ Settings DID open! Check recent apps.
→ Swipe to see Settings app running in background.

---

## Summary

✅ **Grant Implementation** works on both Android and iOS!
✅ Full test flow: system dialog → rationale → settings
✅ Production-ready with comprehensive error handling

**Test now:** Click button to see the complete flow with system and custom dialogs!
