# Feature: Persistent GrantStore (v1.1)

## Summary

Add optional persistent storage for `GrantStore` to survive app restarts.

**Milestone:** v1.1.0
**Priority:** Medium
**Complexity:** Low-Medium
**Estimated Effort:** 4-5 weeks

---

## Problem Statement

Currently, `InMemoryGrantStore` is session-scoped and data is cleared on app restart. While this design is intentional and matches industry standards (Accompanist, native iOS, etc.), some users have requested persistent storage option.

**Current Behavior:**
- Permission status cached in memory
- "Requested before" flag lost on app restart
- Works perfectly for 90% of use cases

**Requested Behavior:**
- Optional persistent storage
- Survive app restarts
- Default remains in-memory (no breaking changes)

---

## Proposed Solution

### Design Document

See: [`docs/internal/PERSISTENT_GRANTSTORE_DESIGN_v1.1.md`](../../docs/internal/PERSISTENT_GRANTSTORE_DESIGN_v1.1.md)

### API Design

```kotlin
// Option 1: Default (no changes for existing users)
val grantManager = GrantFactory.create(context)

// Option 2: v1.1 opt-in to persistence
val grantManager = GrantFactory.create(context, persistent = true)

// Option 3: Custom storage (advanced)
val grantManager = GrantFactory.create(context, store = CustomStore())
```

### Platform Storage

- **Android:** SharedPreferences (default), DataStore (v1.2)
- **iOS:** UserDefaults
- **Both:** Excluded from backup by default to avoid stale data

---

## Implementation Plan

### Phase 1: Core Implementation (Week 1)
- [ ] Create `PersistentStorage` interface
- [ ] Implement `SharedPreferencesStorage` (Android)
- [ ] Implement `UserDefaultsStorage` (iOS)
- [ ] Create `PersistentGrantStore` class
- [ ] Update `GrantFactory` with `persistent` parameter
- [ ] Write-through cache pattern

### Phase 2: Testing (Week 2)
- [ ] Unit tests for `PersistentGrantStore`
- [ ] Unit tests for storage implementations
- [ ] Integration tests (real SharedPrefs/UserDefaults)
- [ ] Performance benchmarks
- [ ] Migration testing (v1.0 → v1.1)

### Phase 3: Documentation (Week 1)
- [ ] API documentation (KDoc)
- [ ] Update README with persistence example
- [ ] Migration guide (v1.0 → v1.1)
- [ ] Backup configuration guide
- [ ] Update comparison tables

### Phase 4: Release (Week 1)
- [ ] Update CHANGELOG.md
- [ ] Update version to 1.1.0
- [ ] Publish to Maven Central
- [ ] Create release notes
- [ ] Announce on GitHub/social media

---

## Technical Details

### Architecture

```kotlin
interface PersistentStorage {
    suspend fun loadAll(): Map<String, String>
    suspend fun save(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()
}

class PersistentGrantStore(
    private val storage: PersistentStorage
) : GrantStore {
    // Write-through cache (memory + disk)
    // Lazy initialization
    // Async disk operations
}
```

### Performance Impact

- First read (cold): ~2-5ms
- Subsequent reads: ~0.01ms (cached)
- Writes: ~1-3ms (async)
- **Verdict:** Negligible for typical usage

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Stale data across devices | Exclude from backup by default |
| Migration bugs | Keep default as in-memory (safe) |
| Performance regression | In-memory cache + async I/O |
| Platform API changes | Use standard APIs |

---

## Success Criteria

- [ ] Zero breaking changes (backward compatible)
- [ ] Performance < 5ms overhead on cold start
- [ ] All existing tests pass
- [ ] New tests achieve 90%+ coverage
- [ ] Documentation complete
- [ ] Migration path clear

---

## Related Issues

- Addresses user requests for persistent storage
- Complements Android "dead click" fix (already uses SharedPreferences for "requested" flag)
- Foundation for future features (TTL, custom policies)

---

## Open Questions

1. Should we provide DataStore support for Android?
   - **Recommendation:** Start with SharedPreferences, add DataStore in v1.2 if requested

2. Should we encrypt stored data?
   - **Recommendation:** No - data is not sensitive (just flags). Apps can implement custom GrantStore if needed.

3. Should we add TTL (time-to-live) for cached status?
   - **Recommendation:** No - OS is source of truth. Apps should check status before showing UI.

---

## References

- Design Document: `docs/internal/PERSISTENT_GRANTSTORE_DESIGN_v1.1.md`
- Tech Debt Resolution: `docs/internal/TECH_DEBT_RESOLUTION_2026-02-15.md`
- Current Implementation: `grant-core/src/commonMain/kotlin/dev/brewkits/grant/InMemoryGrantStore.kt`

---

**Labels:** `enhancement`, `v1.1`, `storage`, `api-addition`
**Assignee:** TBD
**Target Release:** Q2 2026
