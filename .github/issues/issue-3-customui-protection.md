# [ISSUE] requestWithCustomUi shows double dialogs (missing first-request protection)

**Labels:** `bug`, `UX`, `v1.0.1`
**Priority:** ⭐⭐⭐⭐ HIGH

## 🐛 Issue Description

The `requestWithCustomUi()` method doesn't have first-request protection, causing poor UX where users see TWO dialogs in a row: system dialog → rationale dialog.

## 📍 Location

**File:** `grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantHandler.kt:546`

## 🔍 Current Behavior

**Regular request() method (CORRECT):**
```kotlin
GrantStatus.DENIED -> {
    if (!isFirstRequest) {  // ✅ Protection exists
        hasShownRationaleDialog = true
        updateState { /* show rationale */ }
    } else {
        // Just denied - don't show rationale yet
        resetState()
        onGrantedCallback = null
    }
}
```

**requestWithCustomUi() method (MISSING):**
```kotlin
GrantStatus.DENIED -> {
    // ❌ No isFirstRequest check - always shows rationale
    val message = rationaleMessage ?: "This permission is required..."
    onShowRationale(message, onConfirm, onDismiss)
}
```

## 😞 User Experience (Current)

1. User clicks "Take Photo" button
2. **System permission dialog** appears
3. User clicks "Deny"
4. **Rationale dialog IMMEDIATELY appears** ❌
5. User feels bombarded with dialogs
6. Bad UX!

## 😊 Expected User Experience

1. User clicks "Take Photo" button
2. **System permission dialog** appears
3. User clicks "Deny"
4. **No additional dialog** ✅
5. User clicks "Take Photo" again (second time)
6. **Now rationale dialog appears** ✅
7. Better UX!

## 💥 Impact

- [x] High - Affects user experience
- **Severity:** MEDIUM-HIGH
- **Affected Users:** Anyone using `requestWithCustomUi()` method
- **Perception:** App feels spammy with too many dialogs

## 🧪 Reproduction Steps

1. Use `requestWithCustomUi()` with rationale callback
2. Request a permission for the first time
3. Deny in system dialog
4. Observe rationale dialog appearing immediately
5. User sees 2 dialogs in quick succession

## 🔧 Proposed Fix

**Add `isFirstRequest` parameter to `handleStatusWithCustomUi()`:**

```kotlin
private suspend fun handleStatusWithCustomUi(
    status: GrantStatus,
    rationaleMessage: String?,
    settingsMessage: String?,
    onShowRationale: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
    onShowSettings: (message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) -> Unit,
    isFirstRequest: Boolean = false  // ✅ Add parameter
) {
    when (status) {
        GrantStatus.GRANTED -> {
            onGrantedCallback?.invoke()
            onGrantedCallback = null
        }

        GrantStatus.NOT_DETERMINED -> {
            val result = grantManager.request(grant)
            _status.value = result

            if (result != GrantStatus.NOT_DETERMINED) {
                // Mark as first request
                handleStatusWithCustomUi(
                    status = result,
                    rationaleMessage = rationaleMessage,
                    settingsMessage = settingsMessage,
                    onShowRationale = onShowRationale,
                    onShowSettings = onShowSettings,
                    isFirstRequest = true  // ✅ Pass flag
                )
            }
        }

        GrantStatus.DENIED -> {
            // ✅ Add protection
            if (!isFirstRequest) {
                val message = rationaleMessage ?: "This permission is required..."

                val onConfirm: () -> Unit = {
                    scope.launch {
                        val newStatus = grantManager.request(grant)
                        _status.value = newStatus
                        refreshStatus()
                        handleStatusWithCustomUi(
                            status = newStatus,
                            rationaleMessage = rationaleMessage,
                            settingsMessage = settingsMessage,
                            onShowRationale = onShowRationale,
                            onShowSettings = onShowSettings,
                            isFirstRequest = true  // Mark as first
                        )
                    }
                }

                val onDismiss: () -> Unit = {
                    onGrantedCallback = null
                }

                onShowRationale(message, onConfirm, onDismiss)
            } else {
                // ✅ Just denied from system dialog - don't show rationale yet
                onGrantedCallback = null
            }
        }

        GrantStatus.DENIED_ALWAYS -> {
            // Similar logic...
        }
    }
}
```

## 📊 Testing Strategy

**Add test in GrantHandlerTest.kt:**
```kotlin
@Test
fun `requestWithCustomUi should not show rationale on first denial`() = runTest {
    var rationaleShown = false
    var settingsShown = false

    val handler = GrantHandler(
        grantManager = FakeGrantManager(defaultStatus = GrantStatus.NOT_DETERMINED),
        grant = AppGrant.CAMERA,
        scope = this
    )

    // First request - user denies
    handler.requestWithCustomUi(
        onShowRationale = { _, _, _ ->
            rationaleShown = true  // Should NOT be called
        },
        onShowSettings = { _, _, _ ->
            settingsShown = true
        }
    ) {
        // onGranted - won't be called
    }

    // Wait for flow to complete
    advanceUntilIdle()

    assertFalse(rationaleShown, "Rationale should NOT show on first denial")
    assertFalse(settingsShown)
}

@Test
fun `requestWithCustomUi should show rationale on second denial`() = runTest {
    var rationaleShown = false

    val fakeManager = FakeGrantManager(defaultStatus = GrantStatus.DENIED)
    val handler = GrantHandler(
        grantManager = fakeManager,
        grant = AppGrant.CAMERA,
        scope = this
    )

    // Second request - should show rationale
    handler.requestWithCustomUi(
        onShowRationale = { _, _, _ ->
            rationaleShown = true  // Should be called now
        },
        onShowSettings = { _, _, _ -> }
    ) {
        // onGranted
    }

    advanceUntilIdle()

    assertTrue(rationaleShown, "Rationale SHOULD show on second denial")
}
```

## 📚 Documentation Updates

- [ ] Update docs/BEST_PRACTICES.md to explain UX reasoning
- [ ] Update GrantHandler KDoc for requestWithCustomUi
- [ ] Add example in README showing correct usage

## ✅ Definition of Done

- [ ] isFirstRequest protection added to handleStatusWithCustomUi
- [ ] Tests added for both scenarios (first vs second denial)
- [ ] All existing tests pass
- [ ] Documentation updated
- [ ] UX validated manually on both Android and iOS

---

**Review Finding Reference:** CODE_REVIEW_FINDINGS.md - Issue #3
**Estimated Effort:** 1 hour
