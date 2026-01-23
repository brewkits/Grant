# Fix "Dead Click" Issue on Android

## 🐛 Vấn đề

**Tình huống:**
1. User mở app lần 1 → Click request Camera → Deny
2. App lưu trạng thái vào `statusCache` (in-memory): `CAMERA = DENIED`
3. App bị kill (restart)
4. `statusCache` bị reset (mất hết data)
5. User mở app lần 2 → Click request Camera
6. `checkStatus()` return `NOT_DETERMINED` (vì cache mất rồi)
7. `request()` show system dialog → System return `DENIED_ALWAYS` ngay
8. `handleStatus(DENIED_ALWAYS, isFirstRequest=true)` → Không show dialog gì!
9. **User thấy: Click vào không có gì xảy ra = "Dead Click"** 💀

---

## 🎯 Nguyên nhân

**Android không có API để phân biệt:**
- `NOT_DETERMINED`: Chưa từng xin quyền
- `DENIED`: Đã xin nhưng bị từ chối

Cả 2 trường hợp đều:
```kotlin
ContextCompat.checkSelfPermission(context, permission) == PERMISSION_DENIED
```

**Giải pháp cũ (statusCache):**
- ✅ Hoạt động tốt trong session hiện tại
- ❌ Mất hết khi app restart
- ❌ Gây "Dead Click" sau restart

---

## ✅ Giải pháp

**Dùng SharedPreferences để nhớ "đã từng request"**

### Tại sao an toàn?

**❌ NGUY HIỂM: Lưu status**
```kotlin
// BAD - Inconsistent with system
prefs.putString("camera_status", "DENIED")
// User có thể vào Settings enable lại → Status cũ sai!
```

**✅ AN TOÀN: Lưu boolean "đã request"**
```kotlin
// GOOD - This is a fact that never changes
prefs.putBoolean("requested_camera", true)
// Fact: "Đã từng xin quyền này" → Không bao giờ sai!
```

### Logic Implementation

**File:** `grant-core/src/androidMain/kotlin/dev/brewkits/grant/impl/PlatformGrantDelegate.android.kt`

#### 1. Thêm SharedPreferences
```kotlin
// Lines 28-42
private val prefs by lazy {
    context.getSharedPreferences("grant_request_history", Context.MODE_PRIVATE)
}

private fun isRequestedBefore(grant: AppGrant): Boolean {
    return prefs.getBoolean("requested_${grant.name}", false)
}

private fun setRequested(grant: AppGrant) {
    prefs.edit().putBoolean("requested_${grant.name}", true).apply()
}
```

#### 2. Update checkStatus()
```kotlin
// Lines 112-121
// 5. Check SharedPreferences to see if we've requested before (survives app restart)
// ✅ FIX: This solves "Dead Click" issue after app restart
// If not granted AND requested before → User must have denied it → Return DENIED
// This allows UI to show rationale/settings dialog instead of system dialog again
if (isRequestedBefore(grant)) {
    return GrantStatus.DENIED
}

// 6. Not granted, no cache, never requested - must be NOT_DETERMINED (first time)
return GrantStatus.NOT_DETERMINED
```

#### 3. Update request()
```kotlin
// Lines 145-148
// ✅ FIX: Mark as "requested" before showing system dialog
// This ensures checkStatus() will return DENIED after app restart (not NOT_DETERMINED)
// Prevents "Dead Click" issue where clicking does nothing after restart
setRequested(grant)
```

---

## 📊 Flow So Sánh

### ❌ TRƯỚC FIX (Dead Click)

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
2. request() → System dialog → DENIED_ALWAYS ngay
3. handleStatus(DENIED_ALWAYS, isFirstRequest=true) → Không show dialog
4. User: "Click vào không có gì?" 😕 DEAD CLICK!
```

---

### ✅ SAU FIX (Works Perfectly)

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
5. User: "Oh có dialog hướng dẫn!" ✅ WORKS!
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
adb shell
run-as dev.brewkits.grantdemo
cat shared_prefs/grant_request_history.xml
```

**Expected content after requesting Camera:**
```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="requested_CAMERA" value="true" />
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

- [IMPACT_ANALYSIS.md](IMPACT_ANALYSIS.md) - How fixes affect iOS/Android
- [CHANGELOG_IOS_DENIED_ALWAYS_UX.md](CHANGELOG_IOS_DENIED_ALWAYS_UX.md) - iOS double dialog fix
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

*Last Updated: 2026-01-23*
*Fix By: Grant Library Team*
*Issue: "Dead Click" after app restart on Android*
*Solution: SharedPreferences to track "has requested" boolean*
*Status: ✅ FIXED*
