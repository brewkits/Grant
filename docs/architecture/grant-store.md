# GrantStore Architecture

## Overview

`GrantStore` is the abstraction layer for permission **request-history** tracking in Grant. It is a pluggable strategy: by default Grant picks the right implementation per platform, and apps can supply their own.

The store never owns the *current* permission status — the operating system is always the source of truth for that, read fresh on every `checkStatus()`. The store only remembers the **fact that a permission has been requested before**, which is the one signal Android cannot otherwise reconstruct after process death.

---

## Why GrantStore?

On Android, `ContextCompat.checkSelfPermission()` returns `PERMISSION_DENIED` for **both** of these cases:

- `NOT_DETERMINED` — the permission has never been requested
- `DENIED` / `DENIED_ALWAYS` — it was requested and the user denied it

`Activity.shouldShowRequestPermissionRationale()` disambiguates them **only for soft denials**, and is `false` for both a fresh install *and* a permanently denied permission. So to tell "permanently denied" apart from "never asked" after the app's process has been killed, Grant must remember that it asked. That memory is what `GrantStore` provides.

If that memory lives only in RAM, it is lost on process death and the disambiguation fails — the request is silently swallowed (the "dead click", see [Issue #55](https://github.com/brewkits/Grant/issues/55) and [FIX_DEAD_CLICK_ANDROID.md](../FIX_DEAD_CLICK_ANDROID.md)).

---

## Platform Defaults

| Platform | Default store | Persists request history? |
|----------|---------------|---------------------------|
| **Android** | `SharedPreferencesGrantStore` | ✅ Yes — survives process death |
| **iOS** | `InMemoryGrantStore` | ❌ No (not needed; iOS has no equivalent dead-click) |

```kotlin
// Android — SharedPreferencesGrantStore is selected automatically
val grantManager = GrantFactory.create(applicationContext)

// iOS — InMemoryGrantStore is selected automatically
val grantManager = GrantFactory.create()
```

The same defaults apply through Koin (`grantPlatformModule`). To override, pass an explicit `store`:

```kotlin
val grantManager = GrantFactory.create(applicationContext, store = InMemoryGrantStore())
```

---

## SharedPreferencesGrantStore (Android default)

`SharedPreferencesGrantStore` persists the request-history flags to a private SharedPreferences file named `grant_request_history`. It is deliberately **history-only**:

| Data | Stored where | Persisted across restart? |
|------|--------------|---------------------------|
| "has been requested" flags (`isRequestedBefore`, raw permissions) | SharedPreferences | ✅ Yes |
| Per-session status cache (`getStatus`/`setStatus`) | In-memory map | ❌ No — OS is the source of truth |

### Startup cost, and `warmUp()`

Reading this store the first time does two blocking things: a SharedPreferences disk read (XML
parse) and a `PackageManager.getPackageInfo()` call — a binder round trip to `system_server`,
used for the install-identity stamp described below.

Both are deferred until the store is **first read**, not done when it is constructed, so
`GrantFactory.create(applicationContext)` and the Koin `single { }` stay cheap. Deferring does
not make the work free, though — it just moves it to whichever thread reads first.

Apps with a tight frame budget (camera preview, games, anything targeting 120 fps, where a
frame is ~8 ms) should pay that cost up front, off the main thread:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val store = SharedPreferencesGrantStore(this)
        applicationScope.launch(Dispatchers.IO) { store.warmUp() }   // blocking; keep it off main
        grantManager = GrantFactory.create(this, store = store)
    }
}
```

`warmUp()` is optional — skipping it is correct, and most apps will never notice. It exists so
that the one unavoidable disk-and-binder cost does not land on a frame that cannot afford it.

### Why this is safe (no status desync)

The classic argument against persisting permission state is *desync*: if you cache `CAMERA = DENIED` and the user later enables it in Settings, your cache is wrong. `SharedPreferencesGrantStore` sidesteps this entirely because it **never persists status** — only the immutable fact "this permission was requested at least once", which can never become stale. The live status is always re-read from the OS.

### Backup exclusion (handled automatically)

`SharedPreferencesGrantStore` discards history that was not written by the current
installation, so a fresh install never inherits "already asked" state from another device.

The history is stamped with the app's `firstInstallTime`. On construction — before any read —
the store compares that stamp against the current installation and clears the file if it
differs, or if no stamp is present at all (data written before this mechanism, which cannot
be shown to be local). `firstInstallTime` is stable across app *updates*, so ordinary updates
and process death keep their history; a cloud-backup restore, device transfer, or reinstall
starts clean. If the lookup itself fails, the history is left alone: wrongly clearing costs a
real user an extra prompt, which is the worse error.

Until 2.4.1 this was done with `android:fullBackupContent` / `android:dataExtractionRules` on
the library manifest instead. That was removed because a library cannot set `<application>`
attributes without colliding with apps that declare their own backup rules — and an app
resolving the collision with `tools:replace` silently dropped the exclusion, so the protection
was never dependable. Self-invalidation needs nothing from the consuming app's manifest.

---

## InMemoryGrantStore

`InMemoryGrantStore` keeps everything in RAM. It is the iOS default and is available on Android as an explicit opt-in.

**State is cleared** on process death, force-stop, app restart, and reboot. **State is not cleared** on configuration change, activity recreation, or background/foreground.

Choose it on Android only if you specifically want the OS to be the sole memory and accept that a permanently-denied permission may be re-prompted (and routed to Settings only on the second tap) after a restart.

---

## Custom Storage

Implement `GrantStore` to back history with Room, DataStore, or your own SharedPreferences layout:

```kotlin
class RoomGrantStore(private val dao: GrantDao) : GrantStore {
    private val statusCache = mutableMapOf<AppGrant, GrantStatus>()

    override fun getStatus(grant: AppGrant): GrantStatus? = statusCache[grant]
    override fun setStatus(grant: AppGrant, status: GrantStatus) { statusCache[grant] = status }

    override fun isRequestedBefore(grant: AppGrant): Boolean = dao.isRequested(grant.name)
    override fun setRequested(grant: AppGrant) { dao.markRequested(grant.name) }

    override fun isRawPermissionRequested(identifier: String): Boolean = dao.isRequested(identifier)
    override fun markRawPermissionRequested(identifier: String) { dao.markRequested(identifier) }

    override fun clear() { statusCache.clear(); dao.clearAll() }
    override fun clear(grant: AppGrant) { statusCache.remove(grant); dao.clear(grant.name) }
}

val grantManager = GrantFactory.create(applicationContext, store = RoomGrantStore(dao))
```

> **Guideline:** persist only the request-history methods (`isRequestedBefore`/`setRequested` and their raw-permission equivalents). Keep `getStatus`/`setStatus` in memory so the OS stays the source of truth for the live status. If you do persist to disk, exclude that file from backup as `grant-core` does.

---

## Testing

### Persistent store survives "restart"

A new store instance pointed at the same context models the app after process death:

```kotlin
@Test
fun `persistent store survives recreation`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

    val afterRestart = SharedPreferencesGrantStore(context)

    assertTrue(afterRestart.isRequestedBefore(AppGrant.CAMERA))
}
```

### In-memory store resets

```kotlin
@Test
fun `in-memory store resets on recreation`() {
    val store = InMemoryGrantStore()
    store.setRequested(AppGrant.CAMERA)

    val afterRestart = InMemoryGrantStore()

    assertFalse(afterRestart.isRequestedBefore(AppGrant.CAMERA))
}
```

See `SharedPreferencesGrantStoreTest`, `Issue55PermanentDenialAfterRestartTest`, and `Issue55InSessionDeniedAlwaysTest` in `grant-core/src/androidUnitTest` for the full coverage — including the regression that pins down `DENIED_ALWAYS` (not `NOT_DETERMINED`) after a permanent denial and restart, and the one that pins down the *in-session* `DENIED → DENIED_ALWAYS` transition (the OS rationale flag must be consulted before the in-memory status cache, or a second denial is masked by the first denial's cached `DENIED`).

---

## History

- **Grant 1.x** persisted request history via SharedPreferences by default.
- **Grant 2.0** switched the default to `InMemoryGrantStore` to "align with the stateless industry standard" — but this re-introduced the Android dead-click after restart for permanently denied permissions.
- **Issue #55 fix** restored persistent request-history as the **Android** default via `SharedPreferencesGrantStore`, while keeping iOS in-memory. Because only history (never status) is persisted and the file is excluded from backup, this fixes the dead-click without the desync downsides that motivated the 2.0 change.

---

## Resources

- [GrantStore Interface](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/GrantStore.kt)
- [SharedPreferencesGrantStore (Android default)](../../grant-core/src/androidMain/kotlin/dev/brewkits/grant/SharedPreferencesGrantStore.kt)
- [InMemoryGrantStore](../../grant-core/src/commonMain/kotlin/dev/brewkits/grant/InMemoryGrantStore.kt)
- [FIX_DEAD_CLICK_ANDROID.md](../FIX_DEAD_CLICK_ANDROID.md)

---

**Summary:** Android persists request history (`SharedPreferencesGrantStore`) so denials survive process death; iOS stays in memory. Only the immutable "was requested" fact is persisted — never the status — so there is no desync, and the OS remains the single source of truth for the current permission state. 🎯
