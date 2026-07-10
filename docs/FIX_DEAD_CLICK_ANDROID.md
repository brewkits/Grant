# Fix "Dead Click" Issue on Android

## 🐛 The Problem

**Scenario:**
1. User opens app (first time) → Click request Camera → Deny
2. App saves status to `statusCache` (in-memory): `CAMERA = DENIED`
3. App gets killed (restart)
4. `statusCache` is reset (all data lost)
5. User opens app (second time) → Click request Camera
6. `checkStatus()` returns `NOT_DETERMINED` (because cache is lost)
7. `request()` shows system dialog → System returns `DENIED_ALWAYS` immediately
8. `handleStatus(DENIED_ALWAYS, isFirstRequest=true)` → Doesn't show any dialog!
9. **User sees: Click does nothing = "Dead Click"** 💀

---

## 🎯 Root Cause

**Android doesn't have API to differentiate:**
- `NOT_DETERMINED`: Never requested permission
- `DENIED`: Requested but denied

Both cases return:
```kotlin
ContextCompat.checkSelfPermission(context, permission) == PERMISSION_DENIED
```

**Old solution (statusCache):**
- ✅ Works well in current session
- ❌ Lost when app restarts
- ❌ Causes "Dead Click" after restart

---

## ✅ Solution

**Use SharedPreferences to remember "has requested"**

### Why is it safe?

**❌ DANGEROUS: Store status**
```kotlin
// BAD - Inconsistent with system
prefs.putString("camera_status", "DENIED")
// User can go to Settings and enable it → Old status is wrong!
```

**✅ SAFE: Store boolean "has requested"**
```kotlin
// GOOD - This is a fact that never changes
prefs.putBoolean("requested_camera", true)
// Fact: "Have requested this permission" → Never wrong!
```

### Implementation

The persistence lives in a dedicated, pluggable `GrantStore` rather than being hardcoded into the delegate. On Android this is `SharedPreferencesGrantStore`, the **default** store (see [GrantStore Architecture](architecture/grant-store.md)).

#### 1. `SharedPreferencesGrantStore` (the Android default)

**File:** `grant-core/src/androidMain/kotlin/dev/brewkits/grant/SharedPreferencesGrantStore.kt`

```kotlin
class SharedPreferencesGrantStore(context: Context) : GrantStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("grant_request_history", Context.MODE_PRIVATE)

    // Request history is persisted...
    override fun isRequestedBefore(grant: AppGrant): Boolean =
        prefs.getStringSet("requested_grants", emptySet())?.contains(grant.name) == true

    override fun setRequested(grant: AppGrant) { /* add grant.name to the persisted set */ }

    // ...but the status cache stays in memory — the OS is the source of truth for live status.
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()
    override fun getStatus(grant: AppGrant): GrantStatus? = statusCache[grant]
}
```

It is selected automatically by `GrantFactory.create(context)` and by the Koin `grantPlatformModule`; pass an explicit `store` to override.

#### 2. `checkStatus()` consults the persisted history

In `PlatformGrantDelegate.android.kt`, the OS-persisted `shouldShowRequestPermissionRationale()` flag is checked first (it recovers *soft* denials after restart); the store's `isRequestedBefore()` is the fallback that recovers *permanent* denials after restart:

```kotlin
when {
    anyCanShowRationale -> GrantStatus.DENIED            // soft denial (OS flag survives)
    store.isRequestedBefore(appGrant) ->                 // persisted history survives process death
        if (activeActivity == null) GrantStatus.DENIED else GrantStatus.DENIED_ALWAYS
    else -> GrantStatus.NOT_DETERMINED                   // genuinely fresh install
}
```

#### 3. `request()` records the request

`requestInternal()` calls `store.setRequested(grant)` around the system dialog, so `checkStatus()` returns `DENIED`/`DENIED_ALWAYS` (not `NOT_DETERMINED`) after a restart.

---

## 📊 Flow Comparison

### ❌ BEFORE FIX (Dead Click)

**Session 1:**
```
1. checkStatus() → NOT_DETERMINED (first time)
2. request() → Show system dialog → User denies
3. statusCache[CAMERA] = DENIED_ALWAYS ✅ (in-memory)
```

**App restart → statusCache cleared 💀**

**Session 2:**
```
1. checkStatus() → statusCache empty → return NOT_DETERMINED ❌
2. request() → System dialog → DENIED_ALWAYS immediately
3. handleStatus(DENIED_ALWAYS, isFirstRequest=true) → Doesn't show dialog
4. User: "Click does nothing?" 😕 DEAD CLICK!
```

---

### ✅ AFTER FIX (Works Perfectly)

**Session 1:**
```
1. checkStatus() → NOT_DETERMINED (first time)
2. request() → setRequested(CAMERA) → prefs["requested_CAMERA"] = true ✅
3. Show system dialog → User denies
4. statusCache[CAMERA] = DENIED_ALWAYS ✅
```

**App restart → statusCache cleared, BUT prefs survive! ✅**

**Session 2:**
```
1. checkStatus() → statusCache empty
2. checkStatus() → isRequestedBefore(CAMERA) = true ✅
3. checkStatus() → return DENIED (not NOT_DETERMINED!) ✅
4. handleStatus(DENIED, isFirstRequest=false) → Show rationale dialog ✅
5. User: "Oh there's a guidance dialog!" ✅ WORKS!
```

---

## 🧪 Testing

### Test Case 1: First Request (Fresh Install)
```
1. Install app
2. Click "Request Camera"
3. EXPECTED: System dialog appears
4. Deny
5. EXPECTED: No dialog (isFirstRequest=true)
6. Click again
7. EXPECTED: Rationale dialog appears ✅
```

### Test Case 2: After App Restart (Main Fix)
```
1. Continue from Test Case 1
2. Kill app (swipe away from recents)
3. Open app again
4. Click "Request Camera"
5. EXPECTED: Rationale dialog appears immediately ✅ (NOT "Dead Click"!)
```

### Test Case 3: After Settings Enable
```
1. Continue from Test Case 2
2. Go to Settings → Enable Camera
3. Return to app
4. Click "Request Camera"
5. EXPECTED: Granted immediately, callback runs ✅
```

### Test Case 4: LOCATION_ALWAYS
```
1. Fresh install
2. Click "Request Location Always"
3. Grant foreground
4. App restart
5. Click "Request Location Always" again
6. EXPECTED: Shows dialog for background permission ✅ (not dead click)
```

---

## 🔍 Technical Details

### Persistence Strategy

**3-tier status tracking:**

1. **System State** (source of truth)
   ```kotlin
   ContextCompat.checkSelfPermission(context, permission)
   ```

2. **SharedPreferences** (survives restart)
   ```kotlin
   prefs.getBoolean("requested_CAMERA", false) // Boolean only!
   ```

3. **statusCache** (in-memory, fast)
   ```kotlin
   statusCache[grant] = GrantStatus.DENIED_ALWAYS
   ```

**Lookup order:**
```
checkStatus() {
    1. Check system → If GRANTED, return immediately
    2. Check statusCache → If DENIED/DENIED_ALWAYS, return
    3. Check SharedPreferences → If requested before, return DENIED
    4. Otherwise, return NOT_DETERMINED (first time)
}
```

### Why This Approach Works

**Combines best of both worlds:**
- ✅ Fast (statusCache for current session)
- ✅ Persistent (SharedPreferences survives restart)
- ✅ Safe (only stores boolean fact, not status)
- ✅ Consistent (always checks system first)

**Handles edge cases:**
- User enables in Settings → System check returns GRANTED
- User denies permanently → SharedPreferences remembers "requested"
- App restart → SharedPreferences still knows "requested"
- App reinstall → SharedPreferences cleared → Fresh start

---

## 📋 Key Learnings

### 1. StatusCache vs SharedPreferences

| Aspect | statusCache | SharedPreferences |
|--------|-------------|-------------------|
| Lifetime | Current session | Survives restart |
| Speed | Fast (in-memory) | Slower (disk I/O) |
| Data | Status enum | Boolean "requested" |
| Risk | None (temp) | Low (just boolean) |
| Use case | Current session | Survive restart |

### 2. What To Store

**❌ DON'T store:**
- Permission status (GRANTED/DENIED)
- User decisions
- System state

**✅ DO store:**
- Boolean "has been requested"
- Immutable facts
- Non-sensitive flags

### 3. Debugging Tips

**Check SharedPreferences:**
```bash
adb shell run-as <your.application.id> cat shared_prefs/grant_request_history.xml
```

**Expected content after requesting Camera:**
```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <set name="requested_grants">
        <string>CAMERA</string>
    </set>
</map>
```

---

## 🎓 Best Practices Applied

1. **Single Responsibility**: SharedPreferences only tracks "requested" boolean
2. **Layered Cache**: 3-tier lookup (system → memory → disk)
3. **Safe Defaults**: Unknown = NOT_DETERMINED (ask system)
4. **Clear Comments**: Explain WHY each step is needed
5. **Edge Case Handling**: LOCATION_ALWAYS special logic preserved

---

## 📖 Related Documentation

- [BEST_PRACTICES.md](BEST_PRACTICES.md) - Permission best practices

---

## ✅ Verification Checklist

- [x] SharedPreferences added with safe boolean storage
- [x] `isRequestedBefore()` implemented
- [x] `setRequested()` implemented
- [x] `checkStatus()` updated to check SharedPreferences
- [x] `request()` calls `setRequested()` before system dialog
- [x] LOCATION_ALWAYS special case handled
- [x] Comments added explaining the fix
- [x] No breaking changes to public API
- [x] Works after app restart (main fix!)

---

*Last Updated: 2026-06-27*
*Issue: "Dead Click" after app restart on Android ([#55](https://github.com/brewkits/Grant/issues/55))*
*Solution: `SharedPreferencesGrantStore` (Android default) persists request history; only the immutable "has requested" fact is stored, never status*
*Status: ✅ FIXED — verified on a physical Pixel 6 Pro (Android 16) for both soft- and permanent-denial-after-restart*
