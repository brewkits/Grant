# Changelog

All notable changes to the Grant library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.0.0] - 2026-01-23

### ✨ Enhancements

#### iOS Simulator Support for Hardware-Limited Permissions
**Issue**: iOS Simulator lacks certain hardware → Some permissions always fail → Blocks testing

**Affected Permissions:**
- **Bluetooth**: No hardware → Always `DENIED_ALWAYS`
- **Motion**: Limited support → May not return real data

**Fix**: Automatic simulator detection with mock status
- File: `grant-core/src/iosMain/kotlin/dev/brewkits/grant/utils/SimulatorDetector.kt` (new)
- File: `grant-core/src/iosMain/kotlin/dev/brewkits/grant/delegates/BluetoothManagerDelegate.kt` (updated)
- File: `grant-core/src/iosMain/kotlin/dev/brewkits/grant/impl/PlatformGrantDelegate.ios.kt` (updated)

**Behavior:**
- Detects simulator via `UIDevice.currentDevice.model`
- **On Simulator**: Returns `GRANTED` for Bluetooth and Motion (mock)
- **On Real Device**: Normal permission flow

**Benefits:**
- ✅ Test full app flow on simulator without hardware blocking
- ✅ No code changes needed - automatic
- ✅ Clear logging when running on simulator
- ✅ Production behavior unchanged (real device works normally)

**Documentation:** `docs/ios/SIMULATOR_LIMITATIONS.md`

**Impact**: ✅ Developers can test entire app on simulator without hardware errors

---

### 🧹 Production Cleanup

#### Removed Debug Logs
**Files Cleaned:**
- `grant-core/src/androidMain/kotlin/dev/brewkits/grant/impl/GrantRequestActivity.kt` (6 println removed)
- `grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantHandler.kt` (9 println removed)
- `grant-core/src/commonMain/kotlin/dev/brewkits/grant/impl/SimpleGrantManager.kt` (6 println removed)

**Impact**: ✅ Production-ready code, no debug noise in logs

#### Added Backup Configuration
**Files Created:**
- `grant-core/src/androidMain/res/xml/backup_rules.xml`
- `grant-core/src/androidMain/res/xml/data_extraction_rules.xml`

**Purpose**: Exclude `grant_request_history` SharedPreferences from backup
- Prevents false "already requested" state after app reinstall
- Each installation starts fresh with correct permission state
- Works on Android 6+ (fullBackup) and Android 12+ (dataExtraction)

**Files Updated:**
- `grant-core/src/androidMain/AndroidManifest.xml`
  - Added `android:fullBackupContent="@xml/backup_rules"`
  - Added `android:dataExtractionRules="@xml/data_extraction_rules"`
  - Comprehensive comments explaining backup exclusion

**Impact**: ✅ Prevents UX inconsistency after app reinstall

---

### 🐛 Bug Fixes

#### Android: Dead Click After App Restart
**Issue**: After denying permission and restarting app, clicking request button does nothing.

**Root Cause**: `statusCache` (in-memory) lost on app restart → `checkStatus()` returns `NOT_DETERMINED` → System immediately returns `DENIED_ALWAYS` → UI respects `isFirstRequest` flag → No dialog shown.

**Fix**: Added SharedPreferences to persist "has requested" boolean flag
- File: `grant-core/src/androidMain/kotlin/dev/brewkits/grant/impl/PlatformGrantDelegate.android.kt`
- Added `isRequestedBefore()` and `setRequested()` methods
- `checkStatus()` now checks SharedPreferences if cache empty
- Returns `DENIED` instead of `NOT_DETERMINED` if requested before
- Allows rationale/settings dialog to show correctly after restart

**Impact**: ✅ Fixes "dead click" issue, maintains all existing behavior

---

#### iOS: Double Dialog After First Denial
**Issue**: When user denies permission for first time on iOS, Settings guide dialog appears immediately (annoying 2 dialogs in a row).

**Root Cause**: iOS has no "soft denial" - first denial = `DENIED_ALWAYS` immediately. Original code always showed Settings guide for `DENIED_ALWAYS` regardless of timing.

**Fix**: Respect `isFirstRequest` flag in `DENIED_ALWAYS` handler
- File: `grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantHandler.kt`
- Added `hasShownRationaleDialog` flag (line 147)
- Updated `DENIED_ALWAYS` handler to check: `hasShownRationaleDialog || !isFirstRequest`
- First denial → No Settings dialog (respectful UX)
- Second click → Settings dialog appears (makes sense!)

**Platform Differences**:
- **Android**: User goes through rationale flow first → Settings guide appears after
- **iOS**: No rationale (first denial = permanent) → Wait for second click to show Settings

**Impact**: ✅ Better iOS UX, no breaking changes

---

#### iOS: openSettings() Deprecated API Warning
**Issue**: iOS console shows `BUG IN CLIENT OF UIKIT: The caller of UIApplication.openURL(_:) needs to migrate...` and Settings may not open.

**Root Cause**: Using deprecated `openURL(_:)` API.

**Fix**: Migrated to modern `openURL(_:options:completionHandler:)` API
- File: `grant-core/src/iosMain/kotlin/dev/brewkits/grant/impl/PlatformGrantDelegate.ios.kt`
- Uses 3-parameter API with options and completion handler
- Logs success/failure for debugging
- No deprecated warnings

**Impact**: ✅ Settings opens reliably, no console warnings

---

### ✨ Enhancements

#### Success Feedback Snackbar
**Issue**: When user grants permission, no visual confirmation. Users confused: "Did it work?"

**Fix**: Added Snackbar notifications in demo app
- File: `demo/src/commonMain/kotlin/dev/brewkits/grant/demo/SimpleGrantDemoScreen.kt`
- Added `SnackbarHostState` and `Scaffold`
- Pass `onSuccess` callback to all `GrantCard` components
- Shows "✓ [Permission] granted successfully!" message
- Material 3 themed, auto-dismisses after 2-3 seconds

**Impact**: ✅ Clear user feedback, professional UX

---

### 📚 Documentation

#### Added
- `docs/FIX_DEAD_CLICK_ANDROID.md` - Detailed analysis of Dead Click fix
- `docs/COMPARISON_WITH_MOKO.md` - Feature comparison with moko-permissions
- `docs/BEST_PRACTICES.md` - Permission best practices guide
- `docs/ios/INFO_PLIST_LOCALIZATION.md` - Localization guide

#### Updated
- `docs/grant-core/TESTING.md` - Added test cases for all fixes
- `README.md` - Updated features and comparison table
- `docs/README.md` - Added new docs to index

---

### 🔍 Technical Details

#### Architecture Decisions

**Why SharedPreferences is Safe**:
- ✅ Only stores boolean "has been requested" (immutable fact)
- ✅ Does NOT store permission status (system is source of truth)
- ✅ Survives app restart (fixes dead click)
- ✅ Cleared on app reinstall (fresh start)

**hasShownRationaleDialog Flag**:
- Tracks if user saw rationale dialog in current session
- Helps distinguish iOS first vs second denial
- Platform-agnostic solution (no if/else for iOS/Android)
- Resets when permission granted

**Three-Tier Status Tracking**:
1. **System State** (source of truth): `ContextCompat.checkSelfPermission()`
2. **SharedPreferences** (survives restart): `isRequestedBefore()`
3. **statusCache** (in-memory, fast): `statusCache[grant]`

Lookup order: System → statusCache → SharedPreferences → Default to NOT_DETERMINED

---

### 🧪 Testing

All fixes verified with manual testing:
- ✅ Android: Dead click after restart → Fixed
- ✅ iOS: Double dialog issue → Fixed
- ✅ iOS: openSettings() → Works correctly
- ✅ Both platforms: Success feedback → Shows correctly

See `docs/grant-core/TESTING.md` for detailed test cases.

---

### 🎯 Comparison with moko-permissions

Grant library is now **superior** to moko-permissions in:
- ✅ Better Android 11+ location handling (proper 2-step flow)
- ✅ Built-in UI dialogs with `GrantHandler` (moko requires manual)
- ✅ Platform-aware UX (respects iOS/Android differences)
- ✅ ServiceManager (exclusive feature for checking BT/Location services)
- ✅ Better error handling and user guidance
- ✅ Production-ready demo app

See `docs/COMPARISON_WITH_MOKO.md` for full comparison.

---

### ⚠️ Breaking Changes

**None!** All changes are backward compatible.

---

### 🙏 Acknowledgments

Fixes inspired by real-world iOS Simulator testing and user feedback. Special thanks to the moko-permissions library for reference implementation patterns.

---

## [0.1.0] - Initial Release

### Added
- Core grant management system
- Support for 10+ permission types
- Android & iOS platform implementations
- GrantHandler for ViewModel composition
- GrantGroupHandler for bulk requests
- ServiceManager for checking device services
- Transparent Activity pattern for Android
- Compose UI dialog components
- Demo app with comprehensive examples

### Supported Permissions
- Camera
- Microphone
- Location (When In Use)
- Location (Always / Background)
- Storage / Gallery
- Contacts
- Notifications
- Bluetooth
- Motion & Fitness

### Platforms
- Android API 24+
- iOS 13.0+
- Kotlin Multiplatform

---

*For more details, see individual documentation files in the `docs/` directory.*
