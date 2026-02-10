# [BUG] GrantHandler only accepts AppGrant, breaks RawPermission support

**Labels:** `bug`, `critical`, `v1.0.1`
**Priority:** ⭐⭐⭐⭐⭐ CRITICAL

## 🐛 Bug Description

GrantHandler constructor only accepts `AppGrant` enum, preventing users from using `RawPermission` for custom permissions. This breaks the extensibility promise in README.

## 📍 Location

**File:** `grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantHandler.kt:132`

## 🔍 Current Behavior

```kotlin
class GrantHandler(
    private val grantManager: GrantManager,
    private val grant: AppGrant,  // ❌ Only accepts AppGrant
    scope: CoroutineScope,
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate()
)
```

**Problem:**
```kotlin
// This does NOT compile:
val customPermission = RawPermission(
    identifier = "CUSTOM",
    androidPermissions = listOf("android.permission.CUSTOM"),
    iosUsageKey = null
)

val handler = GrantHandler(
    grantManager = grantManager,
    grant = customPermission,  // ❌ Compilation error!
    scope = viewModelScope
)
```

## ✅ Expected Behavior

```kotlin
class GrantHandler(
    private val grantManager: GrantManager,
    private val grant: GrantPermission,  // ✅ Accept sealed interface
    scope: CoroutineScope,
    private val savedStateDelegate: SavedStateDelegate = NoOpSavedStateDelegate()
)
```

**Now this works:**
```kotlin
val handler = GrantHandler(
    grantManager = grantManager,
    grant = customPermission,  // ✅ Compiles successfully
    scope = viewModelScope
)
```

## 💥 Impact

- [x] Critical - Breaks core functionality
- **Severity:** HIGH - Feature completely broken
- **Affected Users:** Anyone trying to use RawPermission with GrantHandler
- **Marketing Impact:** README promises RawPermission works everywhere, but it doesn't

## 🧪 Reproduction Steps

1. Create a RawPermission instance
2. Try to create GrantHandler with it
3. Compilation fails with type mismatch error

## 🔧 Proposed Fix

**Changes Required:**

1. **GrantHandler.kt:132** - Change parameter type
   ```kotlin
   - private val grant: AppGrant,
   + private val grant: GrantPermission,
   ```

2. **GrantHandler.kt** - Update all internal usages (should work as-is since AppGrant implements GrantPermission)

3. **Add test** in GrantHandlerTest.kt:
   ```kotlin
   @Test
   fun `should work with RawPermission`() {
       val customPermission = RawPermission(
           identifier = "CUSTOM",
           androidPermissions = listOf("android.permission.CUSTOM"),
           iosUsageKey = null
       )

       val handler = GrantHandler(
           grantManager = FakeGrantManager(),
           grant = customPermission,
           scope = testScope
       )

       // Should compile and run without errors
       handler.request { /* success */ }
   }
   ```

4. **Update documentation**:
   - Update README.md examples
   - Update docs/grant-core/GRANTS.md

## 📊 Additional Context

- Found in comprehensive code review
- This is a simple type change (backward compatible)
- No runtime behavior changes needed
- Estimated effort: 2 hours

## ✅ Definition of Done

- [ ] GrantHandler accepts GrantPermission parameter
- [ ] All existing tests pass
- [ ] New test added for RawPermission usage
- [ ] Documentation updated
- [ ] Changelog updated

---

**Review Finding Reference:** CODE_REVIEW_FINDINGS.md - Bug #1
