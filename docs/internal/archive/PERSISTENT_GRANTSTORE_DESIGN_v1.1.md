# Persistent GrantStore Design - v1.1 Feature

## Executive Summary

**Goal:** Provide optional persistent storage for GrantStore to survive app restarts.

**Status:** Design Phase (Target: v1.1)

**Impact:** Medium - Requested by some users, but not critical (most apps don't need it)

**Complexity:** Low-Medium - Clean architecture makes this straightforward

---

## Current State (v1.0.x)

### InMemoryGrantStore
```kotlin
// Session-scoped storage
class InMemoryGrantStore : GrantStore {
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()
    private val requestedCache = mutableSetOf<AppGrant>()
    private val rawPermissionRequests = mutableSetOf<String>()
}
```

**Behavior:**
- ✅ Data cleared on app restart
- ✅ OS is always source of truth (no stale data)
- ✅ No disk I/O = fast
- ✅ No backup/restore issues

**Limitations:**
- ⚠️ Android "dead click" tracking lost on cold start
- ⚠️ Can't persist custom app logic (e.g., "asked 3 times, don't ask again")

---

## Design Requirements

### Functional Requirements

**FR1: Optional Persistence**
- Apps can choose persistent vs in-memory storage
- Default remains in-memory (no breaking changes)

**FR2: Platform Support**
- Android: SharedPreferences or DataStore
- iOS: UserDefaults

**FR3: Backup Configuration**
- Should NOT backup by default (avoid stale data across devices)
- Allow apps to opt-in to backup if needed

**FR4: Migration Path**
- Easy upgrade from v1.0 to v1.1
- No data loss for existing users

### Non-Functional Requirements

**NFR1: Performance**
- Disk reads should be async (not block main thread)
- Cache in memory after first read
- Write-through cache pattern

**NFR2: Security**
- No sensitive data stored (just flags)
- Use standard platform APIs (no custom encryption needed)

**NFR3: Testability**
- Mockable storage layer
- Easy to test persistence behavior

---

## Proposed Architecture

### Interface Extension

```kotlin
// No changes to existing GrantStore interface
interface GrantStore {
    fun getStatus(grant: AppGrant): GrantStatus?
    fun setStatus(grant: AppGrant, status: GrantStatus)
    fun wasRequested(grant: AppGrant): Boolean
    fun markAsRequested(grant: AppGrant)
    // ... existing methods
}
```

### New Implementation: PersistentGrantStore

```kotlin
/**
 * Persistent implementation of GrantStore.
 *
 * **Storage:**
 * - Android: SharedPreferences (or DataStore if requested)
 * - iOS: UserDefaults
 *
 * **Backup:**
 * - Android: android:allowBackup="false" recommended for grant data
 * - iOS: Not backed up to iCloud by default
 *
 * **Performance:**
 * - Write-through cache (memory + disk)
 * - Lazy initialization
 * - Async disk operations
 */
class PersistentGrantStore(
    private val storage: PersistentStorage
) : GrantStore {

    // In-memory cache for fast reads
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()
    private val requestedCache = mutableSetOf<AppGrant>()

    init {
        // Load from disk on initialization (async)
        loadFromDisk()
    }

    override fun getStatus(grant: AppGrant): GrantStatus? {
        return statusCache[grant] // Fast memory read
    }

    override fun setStatus(grant: AppGrant, status: GrantStatus) {
        statusCache[grant] = status
        storage.saveStatus(grant, status) // Async disk write
    }

    // ... other methods
}
```

### Storage Abstraction

```kotlin
/**
 * Platform-agnostic storage interface.
 *
 * Implementation:
 * - Android: SharedPreferences or DataStore
 * - iOS: UserDefaults
 */
internal interface PersistentStorage {
    suspend fun loadAll(): Map<String, String>
    suspend fun save(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

// Android implementation
internal class SharedPreferencesStorage(
    private val context: Context
) : PersistentStorage {
    private val prefs = context.getSharedPreferences(
        "grant_store",
        Context.MODE_PRIVATE
    )

    override suspend fun loadAll(): Map<String, String> = withContext(Dispatchers.IO) {
        prefs.all.mapValues { it.value.toString() }
    }

    override suspend fun save(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    // ... other methods
}

// iOS implementation
internal class UserDefaultsStorage : PersistentStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun loadAll(): Map<String, String> = withContext(Dispatchers.Default) {
        // Read from UserDefaults
        defaults.dictionaryRepresentation().mapValues { it.value.toString() }
    }

    // ... other methods
}
```

---

## Factory Pattern Update

```kotlin
object GrantFactory {
    /**
     * Create GrantManager with in-memory storage (default).
     */
    fun create(context: Any): GrantManager {
        return create(context, store = InMemoryGrantStore())
    }

    /**
     * Create GrantManager with persistent storage.
     *
     * @param persistent If true, uses SharedPreferences (Android) or UserDefaults (iOS)
     */
    fun create(
        context: Any,
        persistent: Boolean = false
    ): GrantManager {
        val store = if (persistent) {
            PersistentGrantStore(createPlatformStorage(context))
        } else {
            InMemoryGrantStore()
        }
        return create(context, store)
    }

    /**
     * Create GrantManager with custom GrantStore.
     */
    fun create(context: Any, store: GrantStore): GrantManager {
        // ... existing implementation
    }
}
```

---

## Usage Examples

### Option 1: In-Memory (Default - No Changes)

```kotlin
// v1.0 code continues to work
val grantManager = GrantFactory.create(context)
```

### Option 2: Persistent Storage

```kotlin
// v1.1: Opt-in to persistence
val grantManager = GrantFactory.create(
    context = context,
    persistent = true
)
```

### Option 3: Custom Storage

```kotlin
// Advanced: Custom implementation
class MyCustomStore : GrantStore {
    // Could use Room, SQLite, encrypted storage, etc.
}

val grantManager = GrantFactory.create(
    context = context,
    store = MyCustomStore()
)
```

---

## Android Backup Configuration

### Problem: Stale Data Across Devices

If user:
1. Denies permission on Device A
2. Backups to cloud
3. Restores on Device B

→ Device B thinks permission was requested, even though it wasn't on Device B

### Solution: Exclude from Backup by Default

```xml
<!-- AndroidManifest.xml -->
<application
    android:fullBackupContent="@xml/backup_rules"
    ...>
```

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="grant_store.xml" />
</full-backup-content>
```

**Apps can opt-in to backup if they understand the trade-offs.**

---

## iOS Backup Configuration

```kotlin
// iOS: Mark UserDefaults keys as non-iCloud
internal class UserDefaultsStorage : PersistentStorage {
    init {
        // Keys in this domain are not backed up to iCloud
        defaults.setObject(value, forKey: key)
        // No special flags needed - UserDefaults are not backed up by default
    }
}
```

---

## Migration Strategy

### For Apps Upgrading from v1.0 to v1.1

**Scenario 1: In-Memory → In-Memory (No Changes)**
```kotlin
// No code changes needed
val grantManager = GrantFactory.create(context)
```

**Scenario 2: In-Memory → Persistent**
```kotlin
// Change one line
val grantManager = GrantFactory.create(context, persistent = true)
```

**Data Migration:**
- No migration needed (starting fresh is fine)
- First run after upgrade: All permissions are NOT_DETERMINED
- User will be asked again (expected behavior)

---

## Testing Strategy

### Unit Tests

```kotlin
@Test
fun `persistent store survives restart`() = runTest {
    // Create store
    val storage = FakePersistentStorage()
    val store1 = PersistentGrantStore(storage)

    // Save status
    store1.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

    // Simulate app restart (create new store)
    val store2 = PersistentGrantStore(storage)

    // Verify data persisted
    assertEquals(GrantStatus.DENIED, store2.getStatus(AppGrant.CAMERA))
}

@Test
fun `in-memory store clears on restart`() = runTest {
    val store1 = InMemoryGrantStore()
    store1.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

    val store2 = InMemoryGrantStore()
    assertNull(store2.getStatus(AppGrant.CAMERA))
}
```

### Integration Tests

```kotlin
@Test
fun `Android SharedPreferences persistence`() = instrumentedTest {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val storage = SharedPreferencesStorage(context)

    // Test actual disk persistence
    storage.save("test_key", "test_value")

    // Clear memory
    // ...

    // Verify disk read
    val loaded = storage.loadAll()
    assertEquals("test_value", loaded["test_key"])
}
```

---

## Performance Impact

### Benchmarks (Expected)

| Operation | In-Memory | Persistent (Cached) | Persistent (Cold) |
|-----------|-----------|---------------------|-------------------|
| First read | ~0.01ms | ~0.01ms | ~2-5ms |
| Subsequent reads | ~0.01ms | ~0.01ms | ~0.01ms |
| Write | ~0.01ms | ~1-3ms | ~1-3ms |

**Verdict:** Negligible impact for typical usage (few permission checks per session)

---

## Risks & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| **Stale data across devices** | Medium | Medium | Exclude from backup by default |
| **Migration bugs** | Low | Medium | Keep default as in-memory (safe) |
| **Performance regression** | Very Low | Low | In-memory cache + async I/O |
| **Platform API changes** | Low | Low | Use standard APIs (SharedPrefs, UserDefaults) |

---

## Implementation Checklist

### Phase 1: Core Implementation (Week 1)
- [ ] Create `PersistentStorage` interface
- [ ] Implement `SharedPreferencesStorage` (Android)
- [ ] Implement `UserDefaultsStorage` (iOS)
- [ ] Create `PersistentGrantStore` class
- [ ] Update `GrantFactory` with `persistent` parameter

### Phase 2: Testing (Week 2)
- [ ] Unit tests for `PersistentGrantStore`
- [ ] Unit tests for storage implementations
- [ ] Integration tests (real SharedPrefs/UserDefaults)
- [ ] Performance benchmarks

### Phase 3: Documentation (Week 1)
- [ ] API documentation (KDoc)
- [ ] Update README with persistence example
- [ ] Migration guide (v1.0 → v1.1)
- [ ] Backup configuration guide

### Phase 4: Release (Week 1)
- [ ] Update CHANGELOG.md
- [ ] Update version to 1.1.0
- [ ] Publish to Maven Central
- [ ] Announce on GitHub/social media

**Total Estimate:** 4-5 weeks

---

## Open Questions

1. **Should we provide DataStore support for Android?**
   - Pro: Modern Android approach
   - Con: Adds dependency
   - **Decision:** Start with SharedPreferences, add DataStore in v1.2 if requested

2. **Should we encrypt stored data?**
   - Pro: Better security
   - Con: Added complexity, performance cost
   - **Decision:** No - data is not sensitive (just flags). Apps can implement custom GrantStore if needed.

3. **Should we add TTL (time-to-live) for cached status?**
   - Pro: Prevents super stale data
   - Con: Adds complexity
   - **Decision:** No - OS is source of truth. Apps should check status before showing UI.

---

## References

- [Android Data and File Storage](https://developer.android.com/training/data-storage)
- [Android Backup Rules](https://developer.android.com/guide/topics/data/autobackup#IncludingFiles)
- [iOS UserDefaults](https://developer.apple.com/documentation/foundation/userdefaults)
- [Accompanist Permissions](https://github.com/google/accompanist/tree/main/permissions) - In-memory only
- [moko-permissions](https://github.com/icerockdev/moko-permissions) - In-memory only

---

**Status:** ✅ Design Complete - Ready for Implementation

**Next Steps:**
1. Get community feedback on this design
2. Create GitHub issue for v1.1 milestone
3. Assign to developer for implementation
4. Target release: Q2 2026

---

*Last Updated: 2026-02-15*
*Author: Technical Review Team*
