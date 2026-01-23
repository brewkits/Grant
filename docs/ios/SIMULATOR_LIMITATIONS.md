# iOS Simulator Limitations

## 🚨 Overview

iOS Simulator has several limitations compared to real devices when testing permissions. This document explains which permissions work, which don't, and how Grant library handles these limitations.

---

## 📊 Permission Support Matrix

| Permission | Simulator Support | Grant Library Behavior | Notes |
|-----------|------------------|----------------------|-------|
| **Camera** | ✅ Works | Normal flow | System dialog appears |
| **Microphone** | ✅ Works | Normal flow | System dialog appears |
| **Photos/Gallery** | ✅ Works | Normal flow | Can select from simulated library |
| **Contacts** | ✅ Works | Normal flow | Can access simulated contacts |
| **Notifications** | ✅ Works | Normal flow | System dialog appears |
| **Location** | ⚠️ Limited | Normal flow | Can simulate locations, but limited |
| **Motion** | ⚠️ Limited | **Returns GRANTED** | Dialog works but data may not |
| **Bluetooth** | ❌ Not Supported | **Returns GRANTED** | Hardware not available |

---

## 🔴 Critical: Bluetooth on Simulator

### The Problem

iOS Simulator **does not have Bluetooth hardware**. When checking Bluetooth permission:
- `CBCentralManager.state` returns `CBManagerStateUnsupported`
- This maps to `GrantStatus.DENIED_ALWAYS`
- Testing becomes impossible - every Bluetooth test fails!

### Grant Library Solution ✅

**Grant library automatically detects simulator and returns mock status:**

```kotlin
// On Simulator
checkStatus(AppGrant.BLUETOOTH)
// → Returns: GrantStatus.GRANTED (mock)

request(AppGrant.BLUETOOTH)
// → Returns: GrantStatus.GRANTED (mock)
```

**On Real Device:**
- Normal Bluetooth permission flow
- System dialog appears
- Actual hardware state checked

### Implementation

**File:** `grant-core/src/iosMain/kotlin/dev/brewkits/grant/delegates/BluetoothManagerDelegate.kt`

```kotlin
fun checkStatus(): GrantStatus {
    // iOS Simulator doesn't support Bluetooth hardware
    if (SimulatorDetector.isSimulator) {
        GrantLogger.i(
            "BluetoothDelegate",
            "Running on Simulator - Returning GRANTED for testing"
        )
        return GrantStatus.GRANTED
    }

    // Real device: Check actual Bluetooth state
    val tempManager = CBCentralManager(delegate = null, queue = null)
    // ... actual implementation
}
```

### Why Return GRANTED Instead of NOT_DETERMINED?

**Option 1: Return NOT_DETERMINED**
- ❌ `request()` would be called
- ❌ Would try to create CBCentralManager
- ❌ Would still return UNSUPPORTED
- ❌ User sees error, can't test

**Option 2: Return GRANTED** ✅ (Current)
- ✅ `checkStatus()` immediately returns success
- ✅ Callback executes
- ✅ UI shows granted state
- ✅ User can continue testing other features
- ✅ Developers can test full app flow on simulator

---

## ⚠️ Motion & Fitness

### Support Level: Limited → **Now Mocked**

Motion permission (step counting, activity recognition) has **limited support** on simulator:
- Permission dialog may appear
- Authorization can be granted
- **BUT**: Actual motion data doesn't work
- `CMMotionActivityManager` won't return real data

### Grant Library Behavior ✅ **Updated**

**Now:** Returns GRANTED on simulator (mock)
- No dialog shown on simulator
- Returns `GRANTED` immediately
- Allows testing without blocking
- App can test full flow

**Why Mock?**
- Motion data doesn't work on simulator anyway
- Permission flow can be tested on real device
- Mocking allows testing other features
- Developers can test app without hardware blocks

### Testing Recommendation

Test Motion permissions on simulator for:
- ✅ App flow without blocking (**NEW!**)
- ✅ UI states
- ✅ Other features integration

Test on real device for:
- ❌ Actual permission dialog
- ❌ Actual motion data
- ❌ Step counting
- ❌ Activity recognition

---

## 🟡 Location

### Support Level: Partial

Location permission **works** on simulator but with limitations:
- Permission dialog appears ✅
- Authorization flow works ✅
- Can simulate locations ✅
- **BUT**: Background location tracking limited
- **BUT**: Simulated locations only

### Grant Library Behavior

**Normal flow** - No special handling needed
- `LOCATION` (When In Use) - Fully supported
- `LOCATION_ALWAYS` (Background) - Permission dialog works, tracking limited

### Testing Recommendation

Simulator is **good enough** for:
- ✅ Permission request flow
- ✅ Authorization handling
- ✅ Dialog states (denied, granted, etc.)
- ✅ Location simulation (Debug → Location → Custom Location)

Real device needed for:
- ❌ Real GPS data
- ❌ Background location tracking
- ❌ Geofencing

---

## ✅ Fully Supported Permissions

These work **perfectly** on simulator:

### Camera
- Permission dialog appears
- Can use simulated camera
- Grant flow identical to device

### Microphone
- Permission dialog appears
- Can record audio (Mac microphone)
- Grant flow identical to device

### Photos/Gallery
- Permission dialog appears
- Can select from Photos app
- Can add photos to simulator
- Grant flow identical to device

### Contacts
- Permission dialog appears
- Can access/modify contacts
- Simulator has contacts app
- Grant flow identical to device

### Notifications
- Permission dialog appears
- Push notifications work (via simulator)
- Grant flow identical to device

---

## 🔧 Simulator Detection

### How It Works

**File:** `grant-core/src/iosMain/kotlin/dev/brewkits/grant/utils/SimulatorDetector.kt`

```kotlin
object SimulatorDetector {
    val isSimulator: Boolean by lazy {
        val model = UIDevice.currentDevice.model
        model.contains("Simulator", ignoreCase = true)
    }
}
```

**Detection Method:**
- Checks `UIDevice.currentDevice.model`
- Simulator model always contains "Simulator"
- Real devices: "iPhone", "iPad", "iPod"
- Simulator: "iPhone 15 Pro Simulator", etc.

---

## 📋 Testing Checklist

### On Simulator ✅

Test these permissions:
- [x] Camera
- [x] Microphone
- [x] Photos/Gallery
- [x] Contacts
- [x] Notifications
- [x] Location (basic flow)
- [x] Motion (dialog flow only)
- [x] Bluetooth (mocked as GRANTED)

Verify:
- [x] Permission dialogs appear
- [x] Grant flow works (request → dialog → grant/deny)
- [x] Rationale dialogs show correctly
- [x] Settings guide works
- [x] UI updates after permission changes

### On Real Device ⚠️

Must test these on real device:
- [ ] Bluetooth (actual hardware)
- [ ] Motion (actual step data)
- [ ] Location (background tracking, geofencing)
- [ ] Camera (real camera hardware)
- [ ] All permissions (final validation)

---

## 🚀 Best Practices

### For Developers Using Grant Library

1. **Simulator for Initial Development** ✅
   - Test permission flows
   - Test UI states
   - Test dialog handling
   - Test rationale/settings logic

2. **Real Device Before Release** ✅
   - Test all permissions end-to-end
   - Test Bluetooth functionality
   - Test Motion data collection
   - Test Location tracking
   - Verify hardware-specific features

3. **Don't Assume Simulator = Reality**
   - Bluetooth won't work on simulator
   - Motion data won't work
   - Location is simulated
   - Always verify on real device!

### For Library Contributors

1. **Add Simulator Detection When Needed**
   ```kotlin
   if (SimulatorDetector.isSimulator) {
       // Return mock status
       return GrantStatus.GRANTED
   }

   // Real implementation
   ```

2. **Log Simulator Behavior**
   ```kotlin
   GrantLogger.i(
       "SomeDelegate",
       "Running on ${SimulatorDetector.simulatorType} - Mock behavior"
   )
   ```

3. **Document Limitations**
   - Update this file
   - Add comments in code
   - Note in CHANGELOG

---

## 📖 Related Documentation

- [iOS Quick Start](QUICK_START_IOS_ANDROID_STUDIO.md)
- [iOS Setup](IOS_SETUP_ANDROID_STUDIO.md)
- [Testing Guide](../grant-core/TESTING.md)
- [Best Practices](../BEST_PRACTICES.md)

---

## ❓ FAQ

### Q: Why does Bluetooth always show "Granted" on simulator?
**A:** Simulator has no Bluetooth hardware. Grant library mocks it as GRANTED to allow testing other features.

### Q: Can I test Bluetooth on simulator?
**A:** No - simulator returns GRANTED automatically (mock). Test on real device for actual Bluetooth functionality.

### Q: Should I test on simulator or real device?
**A:** Both! Simulator for quick iteration, real device for final validation.

### Q: Will my app work on real device if it works on simulator?
**A:** For most permissions: YES. For Bluetooth and Motion: TEST ON REAL DEVICE!

### Q: Can I test Motion on simulator?
**A:** No - simulator returns GRANTED automatically (mock). Test on real device for actual motion data.

### Q: Can I disable simulator mock behavior?
**A:** No, it's automatic. The mock applies to Bluetooth and Motion (hardware limitations).

---

## 🎯 Summary

**Simulator is great for:**
- ✅ Quick testing during development
- ✅ Permission flow validation (most permissions)
- ✅ UI state testing
- ✅ Full app flow without hardware blocks (**NEW!**)
- ✅ Most permissions work perfectly

**Real device required for:**
- ❌ Bluetooth functionality
- ❌ Motion data collection
- ❌ Background location tracking
- ❌ Actual permission dialogs (Bluetooth, Motion)
- ❌ Final validation before release

**Grant library handles:**
- ✅ Automatic simulator detection
- ✅ Mock Bluetooth status on simulator (**returns GRANTED**)
- ✅ Mock Motion status on simulator (**returns GRANTED**)
- ✅ Normal flow on real device
- ✅ Clear logging of simulator behavior

---

*Last updated: 2026-01-23*
*iOS Simulator Version: Tested on Xcode 15+*
*Grant Library: v1.0.0*
