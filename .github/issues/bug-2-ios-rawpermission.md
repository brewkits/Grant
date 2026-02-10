# [BUG] iOS RawPermission always returns DENIED - not implemented

**Labels:** `bug`, `critical`, `iOS`, `v1.0.1`
**Priority:** ⭐⭐⭐⭐ HIGH

## 🐛 Bug Description

iOS implementation always returns `GrantStatus.DENIED` for RawPermission with a warning log. Feature is completely non-functional on iOS.

## 📍 Location

**File:** `grant-core/src/iosMain/kotlin/dev/brewkits/grant/impl/PlatformGrantDelegate.ios.kt:115-117`

## 🔍 Current Behavior

```kotlin
actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
    // Handle RawPermission (custom permissions) - Not yet fully implemented
    if (grant is RawPermission) {
        GrantLogger.w("iOSGrant", "RawPermission support not yet implemented: ${grant.identifier}")
        return GrantStatus.DENIED  // ❌ Always returns DENIED
    }
    // ...
}
```

**Same in request() method (line 144-147):**
```kotlin
if (grant is RawPermission) {
    GrantLogger.w("iOSGrant", "RawPermission support not yet implemented: ${grant.identifier}")
    return GrantStatus.DENIED  // ❌ Always returns DENIED
}
```

## ✅ Expected Behavior

RawPermission should work on iOS:

```kotlin
actual suspend fun checkStatus(grant: GrantPermission): GrantStatus {
    if (grant is RawPermission) {
        GrantLogger.i("iOSGrant", "Checking RawPermission: ${grant.identifier}")

        val usageKey = grant.iosUsageKey
        if (usageKey != null) {
            // Validate Info.plist key exists
            if (!validateInfoPlistKey(usageKey, grant.identifier)) {
                return GrantStatus.DENIED_ALWAYS
            }

            // For now, return NOT_DETERMINED
            // User will call request() to trigger actual permission dialog
            return GrantStatus.NOT_DETERMINED
        }

        // No iOS usage key - assume iOS doesn't require permission
        return GrantStatus.GRANTED
    }
    // ...
}
```

## 💥 Impact

- [x] Critical - Breaks core functionality on iOS
- **Severity:** MEDIUM-HIGH
- **Affected Users:** iOS users trying to use custom permissions
- **Scope:** RawPermission feature (rare but documented use case)

## 🧪 Reproduction Steps

1. Create RawPermission with iOS usage key
2. Call `grantManager.checkStatus(customPermission)` on iOS
3. Always returns DENIED regardless of actual permission status
4. Call `grantManager.request(customPermission)` on iOS
5. Always returns DENIED without showing permission dialog

## 🔧 Proposed Fix

### Option A: Basic Implementation (Recommended for v1.0.1)

**Changes Required:**

1. **checkStatus() method:**
   ```kotlin
   if (grant is RawPermission) {
       GrantLogger.i("iOSGrant", "Checking RawPermission: ${grant.identifier}")

       val usageKey = grant.iosUsageKey
       if (usageKey != null) {
           // Validate Info.plist key
           if (!validateInfoPlistKey(usageKey, grant.identifier)) {
               return GrantStatus.DENIED_ALWAYS
           }
           return GrantStatus.NOT_DETERMINED
       }

       // No iOS key = no permission needed
       return GrantStatus.GRANTED
   }
   ```

2. **request() method:**
   ```kotlin
   if (grant is RawPermission) {
       GrantLogger.i("iOSGrant", "Requesting RawPermission: ${grant.identifier}")

       val usageKey = grant.iosUsageKey
       if (usageKey != null) {
           // Validate Info.plist key
           if (!validateInfoPlistKey(usageKey, grant.identifier)) {
               return GrantStatus.DENIED_ALWAYS
           }

           // Log that user needs to implement custom permission handling
           GrantLogger.w(
               "iOSGrant",
               "RawPermission '${grant.identifier}' detected. " +
               "iOS doesn't have a generic permission request API. " +
               "You may need to call native iOS APIs directly for this permission."
           )

           // Return NOT_DETERMINED to indicate permission can be requested
           return GrantStatus.NOT_DETERMINED
       }

       // No iOS key = no permission needed
       return GrantStatus.GRANTED
   }
   ```

3. **Add helper method:**
   ```kotlin
   private fun validateInfoPlistKey(key: String, identifier: String): Boolean {
       val bundle = NSBundle.mainBundle
       val value = bundle.objectForInfoDictionaryKey(key)

       if (value == null) {
           GrantLogger.e(
               "iOSGrant",
               """
               ⚠️ CRITICAL: Missing required Info.plist key for RawPermission '$identifier'

               Required key: $key

               Add this to your Info.plist file:
               <key>$key</key>
               <string>Describe why your app needs this permission</string>
               """.trimIndent()
           )
           return false
       }

       return true
   }
   ```

### Option B: Full Implementation (For v1.2.0)

Implement full permission checking for common iOS permissions by inferring from iosUsageKey.

## 📊 Testing Strategy

**Add test in GrantPermissionTest.kt:**
```kotlin
@Test
fun `iOS RawPermission should validate Info plist key`() {
    val customPermission = RawPermission(
        identifier = "HEALTH_KIT",
        androidPermissions = emptyList(),
        iosUsageKey = "NSHealthShareUsageDescription"
    )

    // Should not automatically return DENIED
    val status = delegate.checkStatus(customPermission)
    assertTrue(status != GrantStatus.DENIED)
}

@Test
fun `iOS RawPermission without usage key should return GRANTED`() {
    val customPermission = RawPermission(
        identifier = "ANDROID_ONLY",
        androidPermissions = listOf("android.permission.CUSTOM"),
        iosUsageKey = null  // No iOS key
    )

    val status = delegate.checkStatus(customPermission)
    assertEquals(GrantStatus.GRANTED, status)
}
```

## 📚 Documentation Updates Needed

- [ ] Update docs/grant-core/GRANTS.md with iOS RawPermission behavior
- [ ] Add example in README.md for iOS custom permissions
- [ ] Update CHANGELOG.md

## ✅ Definition of Done

- [ ] iOS checkStatus() handles RawPermission correctly
- [ ] iOS request() handles RawPermission correctly
- [ ] Info.plist validation works for RawPermission
- [ ] Tests added and passing
- [ ] Documentation updated
- [ ] No regression in existing iOS permissions

---

**Review Finding Reference:** CODE_REVIEW_FINDINGS.md - Bug #2
**Estimated Effort:** 3 hours
