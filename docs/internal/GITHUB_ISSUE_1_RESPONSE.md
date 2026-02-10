# GitHub Issue #1 Response Draft

## Response to: "Request location permission using Google Play Services"

---

Hi @egorikftp, thank you for the excellent suggestion! 🙏

After careful consideration, we've decided to **keep Grant focused on its core mission: permissions**. Here's our reasoning and what we're providing instead:

---

### Why Not a Dedicated Module?

**1. Platform Asymmetry**

The Android SettingsClient flow you described doesn't have an equivalent on iOS:
- **Android**: Separate permission + GPS check + in-app dialog
- **iOS**: `CLLocationManager` handles both automatically, no separate GPS dialog

This makes it challenging to create a unified KMP API without introducing platform-specific behavior that could confuse developers.

**2. Grant's Philosophy**

Grant provides **composable permission primitives**, not orchestration:
- ✅ Request permissions
- ✅ Check permission status
- ✅ Check service status (GPS, Bluetooth, etc.)
- ❌ Application-level flow orchestration

The flow you described (permission → GPS check → Settings dialog) is application logic that varies by use case. Some apps need high accuracy, others balanced power, some need Play Services, others work without it.

**3. You Can Build It Today**

Grant already provides all the building blocks you need:

```kotlin
suspend fun requestLocationWithGPS() {
    // Step 1: Permission
    val status = grantManager.request(AppGrant.LOCATION)
    if (status != GrantStatus.GRANTED) return

    // Step 2: GPS check
    if (!serviceManager.isLocationEnabled()) {
        serviceManager.openLocationSettings()
        return
    }

    // Ready!
}
```

For Play Services integration, add your custom SettingsClient code (just 20-30 lines).

---

### What We're Providing Instead

To help you and other developers, we've created **comprehensive resources**:

#### 📚 **1. Complete Documentation Recipe**

We've added a detailed guide showing how to build the exact flow you described:

**Location:** `docs/recipes/location-permission-with-gps-check.md`

**Includes:**
- Basic flow using Grant's APIs (7 lines of code)
- Advanced Play Services integration with SettingsClient
- Platform-specific considerations (Android vs iOS)
- China market handling (Huawei/HMS devices)
- Complete code examples
- Testing strategies

#### 🎮 **2. Working Demo App Example**

We've added a complete, production-ready example in the demo app:

**Location:** `demo/src/commonMain/kotlin/dev/brewkits/grant/demo/LocationFlowDemoScreen.kt`

**Features:**
- Complete UI flow with all states
- ViewModel showing permission + GPS orchestration
- Error handling and user guidance
- Compose UI implementation
- Ready to copy-paste

#### 💾 **3. Copy-Paste Helper Code**

Community-maintained helper class you can drop into your project:

**Location:** `docs/recipes/LocationPermissionHelper.kt`

**Features:**
- Plug-and-play location helper
- Handles permission + GPS checking
- Clean result types
- Documented and tested
- Customize as needed

---

### Benefits of This Approach

1. **Flexibility** - Customize the flow to match your app's needs
2. **No Dependencies** - Don't pull in Play Services if you don't need it
3. **Platform Awareness** - Handle Android/iOS differences explicitly
4. **Lightweight** - Grant stays focused and dependency-free
5. **Full Control** - You own the orchestration logic

---

### Getting Started

Check out the resources above and let us know if you have questions! We're happy to help you implement your custom location flow.

If you find bugs or have suggestions for improving the documentation, please open a new issue. We're committed to making Grant the best KMP permission library through great docs and focused scope.

**Links:**
- 📚 [Documentation Recipe](../docs/recipes/location-permission-with-gps-check.md)
- 🎮 [Demo App Example](../demo/src/commonMain/kotlin/dev/brewkits/grant/demo/LocationFlowDemoScreen.kt)
- 💾 [Helper Code](../docs/recipes/LocationPermissionHelper.kt)

Thanks again for pushing us to document this pattern! 🚀

---

**Status:** Closing as **documented** (resources provided)
**Labels:** `documentation`, `enhancement`, `question`
