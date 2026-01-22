# 🎭 Transparent Activity - Complete Guide

Complete guide for creating the perfect transparent activity for grant requests.

## 🎯 Goal

Create an activity that is **completely invisible** to the user:
- ❌ No flash/blink when starting
- ❌ No visible UI elements
- ❌ No entry in recent apps
- ❌ No back stack history
- ✅ 100% transparent user experience

---

## 📐 Architecture

### Why Transparent Activity?

**Problem**: Activity Result API requires an Activity context to request grants.

**Solution**: Use a transparent, no-UI activity that:
1. Launches from anywhere (ViewModel, Repository, etc.)
2. Shows system grant dialog
3. Returns result via StateFlow
4. Finishes immediately
5. User never sees it

---

## 🎨 Perfect Transparent Theme

### styles.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Transparent" parent="android:Theme.Translucent.NoTitleBar">
        <!-- Core transparency -->
        <item name="android:windowIsTranslucent">true</item>
        <item name="android:windowBackground">@android:color/transparent</item>

        <!-- Remove all decorations -->
        <item name="android:windowContentOverlay">@null</item>
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>

        <!-- Prevent dimming -->
        <item name="android:windowIsFloating">true</item>
        <item name="android:backgroundDimEnabled">false</item>
        <item name="android:backgroundDimAmount">0.0</item>

        <!-- 🔑 KEY: Remove animations to prevent blink/flash -->
        <item name="android:windowAnimationStyle">@null</item>
        <item name="android:windowEnterAnimation">@null</item>
        <item name="android:windowExitAnimation">@null</item>

        <!-- Transparent system bars (API 21+) -->
        <item name="android:windowDrawsSystemBarBackgrounds">false</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>

        <!-- 🔑 KEY: Disable preview to prevent blink -->
        <item name="android:windowDisablePreview">true</item>

        <!-- Additional settings -->
        <item name="android:windowSwipeToDismiss">false</item>
        <item name="android:windowCloseOnTouchOutside">false</item>
    </style>
</resources>
```

### Key Attributes Explained

| Attribute | Purpose | Impact |
|-----------|---------|--------|
| `windowIsTranslucent` | Make window transparent | **Critical** - Core transparency |
| `windowAnimationStyle` | Disable all animations | **Critical** - Prevents blink/flash |
| `windowDisablePreview` | Disable window preview | **Critical** - Prevents preview flash |
| `backgroundDimEnabled` | Disable background dimming | **Important** - No visual impact |
| `windowIsFloating` | Float above other windows | **Important** - Minimal UI impact |
| `windowNoTitle` | Remove title bar | **Required** - Clean UI |

---

## 📱 AndroidManifest.xml

### Perfect Configuration

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity
            android:name="dev.brewkits.grant.impl.GrantRequestActivity"
            android:theme="@style/Theme.Transparent"

            <!-- 🔑 Exclude from recent apps -->
            android:excludeFromRecents="true"

            <!-- 🔑 Not exported (internal use only) -->
            android:exported="false"

            <!-- 🔑 Single instance to prevent duplicates -->
            android:launchMode="singleTop"

            <!-- 🔑 Don't keep in back stack -->
            android:noHistory="true"

            <!-- 🔑 Separate task affinity -->
            android:taskAffinity=""

            <!-- 🔑 Handle config changes without recreating -->
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"

            <!-- 🔑 Don't save/restore state -->
            android:stateNotNeeded="true"

            <!-- 🔑 Hide keyboard -->
            android:windowSoftInputMode="stateHidden|adjustNothing" />
    </application>
</manifest>
```

### Attribute Explanations

#### 🔐 Security & Privacy
- `android:exported="false"` - Only this app can launch it
- `android:excludeFromRecents="true"` - Hidden from overview/recent apps

#### 🎭 Transparency & UX
- `android:theme="@style/Theme.Transparent"` - Use transparent theme
- `android:noHistory="true"` - No back stack entry
- `android:taskAffinity=""` - Separate from main app task

#### ⚡ Performance
- `android:launchMode="singleTop"` - Reuse instance if exists
- `android:stateNotNeeded="true"` - No state saving overhead
- `android:configChanges="..."` - Handle config changes without recreate

#### 🎯 Edge Cases
- `android:windowSoftInputMode="stateHidden|adjustNothing"` - Prevent keyboard
- Config changes handled to prevent activity recreation

---

## 💻 Activity Implementation

### Best Practices

```kotlin
class GrantRequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔑 KEY: Don't set any content view!
        // Keep activity completely empty for zero visual impact

        // 🔑 KEY: Start grant request immediately
        // Minimize time activity is alive
        requestGrantsImmediately()
    }

    private fun requestGrantsImmediately() {
        // Register launcher
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultipleGrants()
        ) { results ->
            // Handle results
            handleResults(results)

            // 🔑 KEY: Finish immediately after getting result
            finish()
        }

        // Launch grant request
        launcher.launch(grants)
    }
}
```

### Critical Implementation Points

#### ✅ DO:
1. **Don't set content view** - Keep activity empty
2. **Request immediately** - Start grant request in onCreate
3. **Finish fast** - Close activity as soon as result received
4. **Use StateFlow** - Communicate results without callback hell
5. **Cleanup properly** - Unregister launchers in onDestroy

#### ❌ DON'T:
1. **Don't inflate layout** - Any layout causes visible flash
2. **Don't use animations** - Causes blink/flash
3. **Don't delay** - Long-lived transparent activity can cause issues
4. **Don't store state** - Keep activity stateless
5. **Don't handle back press** - Let system handle it

---

## 🧪 Testing Transparency

### Visual Test

```kotlin
@Test
fun testActivityIsInvisible() {
    // Launch activity
    val scenario = ActivityScenario.launch(GrantRequestActivity::class.java)

    // Take screenshot
    val screenshot = takeScreenshot()

    // Activity should not be visible in screenshot
    // Only grant dialog should be visible
    assertFalse(screenshot.containsActivity())
}
```

### Manual Test Checklist

1. ✅ **No flash/blink** when activity starts
2. ✅ **No background dimming**
3. ✅ **Not in recent apps** after finishing
4. ✅ **Back button** doesn't return to it
5. ✅ **Rotation** doesn't show activity
6. ✅ **Multi-window** works correctly
7. ✅ **Grant dialog** appears immediately
8. ✅ **Activity finishes** right after grant granted/denied

---

## 🐛 Common Issues & Solutions

### Issue 1: White flash when activity starts

**Cause**: Window preview enabled or animations not disabled

**Solution**:
```xml
<item name="android:windowDisablePreview">true</item>
<item name="android:windowAnimationStyle">@null</item>
```

### Issue 2: Activity visible in recent apps

**Cause**: `excludeFromRecents` not set

**Solution**:
```xml
android:excludeFromRecents="true"
```

In Intent:
```kotlin
intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
```

### Issue 3: Background dims when activity shows

**Cause**: Background dimming enabled

**Solution**:
```xml
<item name="android:backgroundDimEnabled">false</item>
<item name="android:backgroundDimAmount">0.0</item>
```

### Issue 4: Activity blinks on rotation

**Cause**: Activity recreated on config change

**Solution**:
```xml
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
```

### Issue 5: Activity appears in task overview

**Cause**: Task affinity same as main app

**Solution**:
```xml
android:taskAffinity=""
```

---

## 📊 Performance Optimization

### Launch Time

```kotlin
// ✅ GOOD: Minimal overhead
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // No layout inflation
    // No view setup
    // Direct to grant request
    requestGrants()
}

// ❌ BAD: Unnecessary overhead
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.transparent) // ❌ Don't do this!
    setupViews() // ❌ Don't do this!
    requestGrants()
}
```

### Memory Usage

- **Don't store references** to Views (no views exist!)
- **Cleanup launchers** in onDestroy
- **Remove from pending results** after consumption
- **Use ConcurrentHashMap** for thread-safe result storage

### Best Practices

1. **Singleton pattern** for result storage (companion object)
2. **UUID** for unique request IDs
3. **StateFlow** for reactive result handling
4. **Lifecycle observer** for automatic cleanup
5. **Early finish** - close activity ASAP

---

## 🔐 Security Considerations

### 1. Exported False

```xml
android:exported="false"
```

Only your app can launch this activity.

### 2. Task Affinity

```xml
android:taskAffinity=""
```

Prevents task hijacking attacks.

### 3. No State Saving

```xml
android:stateNotNeeded="true"
```

Prevents state restoration attacks.

### 4. Intent Validation

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Validate intent data
    val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: run {
        finish()
        return
    }

    // Validate grants array
    val grants = intent.getStringArrayExtra(EXTRA_GRANTS) ?: run {
        setResult(requestId, GrantResult.ERROR)
        finish()
        return
    }
}
```

---

## 📚 References

- [Android Grants Best Practices](https://developer.android.com/training/grants/requesting)
- [Activity Lifecycle](https://developer.android.com/guide/components/activities/activity-lifecycle)
- [Transparent Activities in Android](https://developer.android.com/guide/topics/ui/dialogs)
- [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)

---

## ✅ Checklist for Perfect Transparency

### Theme (styles.xml)
- [x] `windowIsTranslucent` = true
- [x] `windowBackground` = transparent
- [x] `windowAnimationStyle` = null
- [x] `windowDisablePreview` = true
- [x] `backgroundDimEnabled` = false

### Manifest
- [x] `theme` = @style/Theme.Transparent
- [x] `excludeFromRecents` = true
- [x] `exported` = false
- [x] `noHistory` = true
- [x] `taskAffinity` = ""
- [x] `configChanges` = orientation|screenSize|...
- [x] `stateNotNeeded` = true

### Activity Code
- [x] No `setContentView()`
- [x] Request grants immediately
- [x] Finish after result received
- [x] Cleanup in onDestroy
- [x] Thread-safe result storage

### Testing
- [x] No visual flash
- [x] Not in recent apps
- [x] No back stack entry
- [x] Works on rotation
- [x] Works in multi-window

---

**Perfect transparency achieved!** 🎭✨
