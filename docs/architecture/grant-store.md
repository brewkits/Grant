# GrantStore Architecture

## Overview

`GrantStore` is the abstraction layer for permission state persistence in Grant. It provides a pluggable storage strategy, allowing apps to choose between **in-memory** (default) or **custom persistence** based on their needs.

---

## Why GrantStore?

### The Problem

Different apps have different requirements for permission state:

1. **Session-scoped apps** - State only needed during app session
2. **Persistent apps** - State should survive app restarts
3. **Custom storage apps** - Need to integrate with existing storage (Room, DataStore, etc.)

Grant provides flexibility through the `GrantStore` interface.

---

## Default Behavior: InMemoryGrantStore

### What is InMemoryGrantStore?

`InMemoryGrantStore` is the **default** storage implementation that keeps permission state **in memory only**.

```kotlin
// Default usage (automatic)
val grantManager = GrantFactory.create(context)
// Uses InMemoryGrantStore internally
```

### Characteristics

| Characteristic | Behavior |
|----------------|----------|
| **Persistence** | None - state cleared on app restart |
| **Storage Location** | RAM only |
| **Survives Process Death** | No |
| **Survives App Restart** | No |
| **Survives Reinstall** | No |
| **Performance** | Fastest (no I/O) |
| **Sync Risk** | None (always fresh) |

### When to Use InMemoryGrantStore

✅ **Recommended for**:
- Most apps (90% of use cases)
- Apps that want OS as single source of truth
- Apps that prefer to avoid state desync issues
- Apps following Google's Accompanist pattern

❌ **Not recommended for**:
- Apps needing state persistence across restarts
- Apps wanting to avoid "dead click" on first launch after restart

---

## Industry Standard

**90% of permission libraries** use stateless/in-memory approach:

| Library | Platform | Persistence? |
|---------|----------|--------------|
| **Google Accompanist** | Android/Compose | No (in-memory) |
| iOS Native APIs | iOS | No (OS only) |
| Android Official | Android | No (OS only) |
| Flutter permission_handler | Flutter | No (in-memory) |
| React Native permissions | React Native | No (in-memory) |
| moko-permissions | KMP | No (in-memory) |
| **Grant (default)** | **KMP** | **No (in-memory)** ✅ |

**Why?** To avoid state desynchronization issues.

---

## Custom Storage

If you need persistence, you can provide a custom `GrantStore` implementation.

### Example: SharedPreferences Storage

```kotlin
class SharedPrefsGrantStore(context: Context) : GrantStore {
    private val prefs = context.getSharedPreferences("grant_state", Context.MODE_PRIVATE)
    private val memoryCache = mutableMapOf<AppGrant, GrantStatus>()

    override fun getStatus(grant: AppGrant): GrantStatus? {
        // Check memory cache first
        return memoryCache[grant]
    }

    override fun setStatus(grant: AppGrant, status: GrantStatus) {
        memoryCache[grant] = status
    }

    override fun isRequestedBefore(grant: AppGrant): Boolean {
        // Check disk for persistence
        return prefs.getBoolean("requested_${grant.name}", false)
    }

    override fun setRequested(grant: AppGrant) {
        prefs.edit()
            .putBoolean("requested_${grant.name}", true)
            .putLong("timestamp_${grant.name}", System.currentTimeMillis())
            .apply()
    }

    override fun clear() {
        memoryCache.clear()
        prefs.edit().clear().apply()
    }

    override fun clear(grant: AppGrant) {
        memoryCache.remove(grant)
        prefs.edit()
            .remove("requested_${grant.name}")
            .remove("timestamp_${grant.name}")
            .apply()
    }
}

// Usage
val grantManager = GrantFactory.create(
    context = context,
    store = SharedPrefsGrantStore(context)
)
```

---

## State Lifecycle & Reset Behavior

### InMemoryGrantStore (Default)

**State is cleared**:
- ✅ When app process is killed
- ✅ When user force-stops app
- ✅ When app is restarted
- ✅ When device reboots

**State is NOT cleared**:
- ❌ On configuration change (rotation, dark mode)
- ❌ On activity recreation
- ❌ On background/foreground

**Key Insight**: State resets frequently, but this is **intentional** to avoid desync.

---

### Custom Persistent Storage

**State is cleared**:
- ✅ When app is uninstalled
- ✅ When user clears app data
- ✅ When you explicitly call `store.clear()`

**State is NOT cleared**:
- ❌ On app restart
- ❌ On process death
- ❌ On device reboot
- ❌ On app update

**Desync Risks**:
- ⚠️ User grants permission in Settings → Store doesn't know
- ⚠️ User clears data → Store cleared, but OS remembers denial
- ⚠️ User reinstalls app → Store lost, but OS remembers

---

## Backup Rules (Android)

If you use persistent storage (like SharedPreferences), consider **Android Auto Backup** behavior.

### Default Auto Backup

Android automatically backs up:
- SharedPreferences files
- Files in `getFilesDir()`
- Database files

**Impact on Grant**:
- User uninstalls app
- User reinstalls app
- **State is restored** from backup
- But **OS permission state is NOT restored**
- = **Potential desync**

### Excluding Grant from Backup

To avoid desync, exclude Grant's storage from auto backup:

```xml
<!-- res/xml/backup_rules.xml -->
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- Exclude Grant state from backup -->
    <exclude domain="sharedpref" path="grant_state.xml"/>
</full-backup-content>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules">
    ...
</application>
```

**Recommendation**: If using persistent storage, **always exclude it from backup** to prevent desync.

---

## Comparison: In-Memory vs Persistent

| Aspect | InMemoryGrantStore | Custom Persistent |
|--------|-------------------|------------------|
| **Setup Complexity** | None (default) | Medium (implement interface) |
| **Desync Risk** | None | Medium-High |
| **First Launch Experience** | May show "dead click" after restart | No dead clicks |
| **State Accuracy** | Always accurate (no desync) | Risk of desync |
| **Performance** | Fastest | Slower (I/O) |
| **Backup Handling** | Not needed | Must exclude from backup |
| **Maintenance** | None | Need to handle migrations |
| **Recommended** | ✅ Yes (90% of apps) | ⚠️ Only if needed |

---

## Best Practices

### 1. Use InMemoryGrantStore (Default)

```kotlin
// ✅ GOOD: Default, no persistence
val grantManager = GrantFactory.create(context)
```

**Why?**
- Aligns with 90% of libraries
- Avoids desync issues
- Follows Google's guidance

---

### 2. Only Use Persistence If Truly Needed

```kotlin
// ⚠️ ONLY if you absolutely need persistence
val grantManager = GrantFactory.create(
    context = context,
    store = SharedPrefsGrantStore(context)
)
```

**Ask yourself**:
- Do I really need state across restarts?
- Am I okay with potential desync?
- Can I handle backup exclusion?

If no, use default.

---

### 3. If Using Persistence, Exclude from Backup

```xml
<!-- backup_rules.xml -->
<exclude domain="sharedpref" path="grant_state.xml"/>
```

**Critical** to avoid desync on reinstall.

---

### 4. Add Validation Logic

If using persistent storage, validate on app start:

```kotlin
class ValidatedGrantStore(private val delegate: GrantStore) : GrantStore by delegate {
    suspend fun validateOnStart(grantManager: GrantManager) {
        AppGrant.entries.forEach { grant ->
            val cached = getStatus(grant)
            val actual = grantManager.checkStatus(grant)

            if (cached != actual) {
                // Desync detected! Update cache
                setStatus(grant, actual)
            }
        }
    }
}
```

---

## Migration from 1.x to 2.0

Grant 1.x used SharedPreferences by default. Grant 2.0 uses InMemoryGrantStore by default.

### Breaking Change

```kotlin
// Grant 1.x (old)
val grantManager = GrantFactory.create(context)
// Used SharedPreferences automatically

// Grant 2.0 (new)
val grantManager = GrantFactory.create(context)
// Uses InMemoryGrantStore (different behavior!)
```

### Migration Path

If you relied on persistence in 1.x:

```kotlin
// Option 1: Accept new behavior (recommended)
val grantManager = GrantFactory.create(context)
// State no longer persists - aligns with industry standard

// Option 2: Restore old behavior (if needed)
val grantManager = GrantFactory.create(
    context = context,
    store = SharedPrefsGrantStore(context)
)
```

**Recommendation**: Migrate to Option 1 (default) to avoid desync issues.

---

## Testing

### Test InMemoryGrantStore

```kotlin
@Test
fun `test in-memory store resets on recreation`() = runTest {
    val store = InMemoryGrantStore()

    // Set state
    store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
    store.setRequested(AppGrant.CAMERA)

    // Verify state exists
    assertEquals(GrantStatus.GRANTED, store.getStatus(AppGrant.CAMERA))
    assertTrue(store.isRequestedBefore(AppGrant.CAMERA))

    // Simulate app restart (create new store instance)
    val newStore = InMemoryGrantStore()

    // State is gone
    assertNull(newStore.getStatus(AppGrant.CAMERA))
    assertFalse(newStore.isRequestedBefore(AppGrant.CAMERA))
}
```

### Test Custom Persistent Store

```kotlin
@Test
fun `test persistent store survives recreation`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = SharedPrefsGrantStore(context)

    // Set state
    store.setRequested(AppGrant.CAMERA)

    // Simulate app restart (create new store instance)
    val newStore = SharedPrefsGrantStore(context)

    // State persists
    assertTrue(newStore.isRequestedBefore(AppGrant.CAMERA))
}
```

---

## FAQ

### Q: Why did Grant change from SharedPreferences to InMemoryGrantStore?

**A**: To align with industry best practices and avoid desync issues. 90% of permission libraries use stateless approach, including Google's Accompanist.

---

### Q: Will InMemoryGrantStore cause "dead clicks"?

**A**: Potentially yes, but:
1. This is **expected Android behavior** (even Google's Accompanist has this)
2. It's a **trade-off** for avoiding desync
3. Users still get **immediate feedback** (not a true "dead click")

**Note**: The term "dead click" is somewhat misleading. With InMemoryGrantStore, the first click after app restart may show Settings dialog instead of requesting permission, but the user still gets feedback immediately.

---

### Q: Can I create my own GrantStore using Room/DataStore?

**A**: Yes! Implement the `GrantStore` interface:

```kotlin
class RoomGrantStore(private val dao: GrantDao) : GrantStore {
    override suspend fun getStatus(grant: AppGrant): GrantStatus? {
        return dao.getStatus(grant.name)?.let { GrantStatus.valueOf(it) }
    }
    // ... implement other methods
}
```

---

### Q: Should I persist permission state?

**A**: **No** for 90% of apps. Only persist if:
- You absolutely need state across restarts
- You can handle backup exclusion
- You're okay with potential desync

---

## Resources

- [GrantStore Interface](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantStore.kt)
- [InMemoryGrantStore Implementation](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/InMemoryGrantStore.kt)
- [Testing Guide](../advanced/testing.md)

---

**Summary**: Use the default `InMemoryGrantStore` unless you have a specific need for persistence. It's simpler, safer, and aligns with industry standards. 🎯
