# Grant Library — Testing Documentation

Comprehensive test coverage for the Grant permission management library.

---

## Test Coverage Summary

### Total Tests (v2.0.0)

| Module | Android | iOS Simulator | Total |
|--------|---------|---------------|-------|
| `grant-core` | 416 | 379 | **795** |
| `grant-contacts` | 54 | 54 | **108** |
| `grant-calendar` | 60 | 60 | **120** |
| `grant-motion` | 52 | 52 | **104** |
| `grant-compose` | 1 | 1 | **2** |
| `grant-core-koin` | 1 | 1 | **2** |
| **Total** | **584** | **547** | **1131** |

- **Pass rate**: 100% (1131 / 1131)
- **Platforms**: Android JVM (Robolectric) + iOS Simulator (arm64)

---

## Test Organization

Tests in each module follow a consistent package structure:

| Package | Content |
|---------|---------|
| `(root)` | Unit tests per public class |
| `fakes/` | `FakeGrantManager` — reuse in new tests |
| `integration/` | Multi-class interaction tests |
| `system/` | Full end-to-end flow tests |
| `performance/` | Concurrency and throughput tests |
| `stress/` | Race condition and rapid-cycling tests |
| `regression/` | Bug regression tests keyed to issues |
| `security/` | Callback cleanup, isolation, anti-bypass tests |

Android-specific tests (Robolectric) live in `androidUnitTest/`.
iOS-specific tests live in `iosTest/`.

---

## grant-core Test Suites

### Unit Tests
- `GrantTypeTest` — all `AppGrant` enum values and properties
- `GrantStatusTest` — `GrantStatus` enum
- `GrantPermissionTest` — `RawPermission` data class
- `GrantHandlerTest` — state machine: NOT_DETERMINED → GRANTED / DENIED / DENIED_ALWAYS
- `GrantGroupHandlerTest` — multi-permission sequential flow
- `GrantFactoryTest`, `ServiceManagerTest`, `GrantAndServiceCheckerTest`

### Integration Tests
- `GrantHandlerIntegrationTest` — GrantHandler + FakeGrantManager end-to-end
- `GrantGroupHandlerIntegrationTest` — multi-grant flows
- `GrantHandlerSavedStateTest` — process-death recovery via `SavedStateDelegate`

### Regression Tests
- `Issue29IosMutexDeadlockTest` — reentrant mutex deadlock (Issue #29)
- `Issue32MutexDoubleUnlockTest` — double-unlock guard (Issue #32)
- `Issue33PartialGrantedSettingsTest` — LOCATION_ALWAYS partial upgrade path (Issue #33)
- `V2ModuleSplitRegressionTest` — optional module identifiers, concurrent flows, PARTIAL_GRANTED

### Security Tests
- `SecurityIntegrityTest` — rationale loop prevention, callback cleanup, store isolation
- `SecuritySanitizationTest` — state corruption resistance
- `RawPermissionInputValidationTest` — identifier edge cases (empty, long, control chars)

### iOS Tests (`iosTest/`)
- `IosGrantDelegateTest` — real platform delegate calls wrapped in `withTimeout` (deadlock detection)
- `ModuleSplitRegistryTest` — `IosPermissionHandlerRegistry` + `NotRegisteredHandler` behavior

---

## Optional Module Test Suites

Each of `grant-contacts`, `grant-calendar`, and `grant-motion` includes:

### Unit Tests (`GrantContactsTest`, `GrantCalendarTest`, `GrantMotionTest`)
- `initialize_doesNotThrow` / `initialize_isIdempotent`
- Identifier uniqueness and `requiresBackgroundUpgrade` defaults
- Basic handler request / refresh / dismiss flows

### Integration Tests
- `checkStatus` returns valid `GrantStatus` per grant type
- `request` NOT_DETERMINED → GRANTED invokes callback
- `request` when already GRANTED skips system dialog
- DENIED_ALWAYS shows settings guide
- Independence between related grants (e.g. CONTACTS vs READ_CONTACTS)
- Least-privilege: read-only variant preferred over full-access variant

### System Tests
- Full end-to-end flows: picker, sync, batch with `GrantGroupHandler`
- Recovery: DENIED then user grants on second attempt
- `requestSuspend` returns status in coroutine context

### Performance Tests
- 1000 sequential `checkStatus` calls
- 1000 parallel `requestSuspend` calls via `async/awaitAll`
- 100 independent handlers (memory pressure)
- Large batch: 500 grants via `GrantGroupHandler`

### Stress Tests
- 300 concurrent requests on same handler
- 300 parallel `requestSuspend` calls
- 100 alternating request / dismiss cycles
- Rapid status flapping (NOT_DETERMINED ↔ GRANTED ↔ DENIED_ALWAYS)

### Regression Tests
- v2.0 module split: idempotent `initialize()`, distinct identifiers, no cross-contamination
- DENIED state does not permanently block future requests
- `onDismiss` clears state without triggering another request
- Calendar-specific: `PARTIAL_GRANTED` invokes callback with PARTIAL_GRANTED (not GRANTED)

### Security Tests
- SEC-x01: Callback cleared on dismiss — not invoked after late grant
- SEC-x02: Strict status isolation between related permissions
- SEC-x03: PARTIAL_GRANTED does not auto-escalate to GRANTED (Calendar)
- SEC-x04: Multiple `initialize()` calls do not double-register handlers
- SEC-x05: DENIED_ALWAYS never triggers a system dialog
- SEC-x06: Callback receives correct status (PARTIAL_GRANTED ≠ GRANTED)
- SEC-x07: Repeated DENIED_ALWAYS requests never bypass the block
- SEC-x08: Status stable across repeated `refreshStatus` calls

---

## Running Tests

```bash
# All tests — Android JVM + iOS Simulator
./gradlew :grant-core:allTests :grant-contacts:allTests :grant-calendar:allTests :grant-motion:allTests

# Android unit tests only (fast, no Xcode required)
./gradlew :grant-core:testDebugUnitTest

# iOS Simulator tests only (requires macOS + Xcode)
./gradlew :grant-core:iosSimulatorArm64Test

# Optional modules — Android only
./gradlew :grant-contacts:testDebugUnitTest :grant-calendar:testDebugUnitTest :grant-motion:testDebugUnitTest

# Single test class
./gradlew :grant-core:testDebugUnitTest --tests "dev.brewkits.grant.impl.DefaultGrantManagerTest"

# Single common test (iOS path)
./gradlew :grant-core:iosSimulatorArm64Test --tests "dev.brewkits.grant.GrantHandlerTest"
```

---

## Test Infrastructure

### FakeGrantManager (per module)

Each module has a local `FakeGrantManager` in `fakes/`:

```kotlin
class FakeGrantManager(
    var mockStatus: GrantStatus = GrantStatus.NOT_DETERMINED,
    var mockRequestResult: GrantStatus = GrantStatus.GRANTED
) : GrantManager {
    val requestedGrants = mutableListOf<GrantPermission>()
    var requestCalled = false

    fun configure(grant: GrantPermission, status: GrantStatus, result: GrantStatus = status)
    fun reset()
}
```

### CLAUDE.md Mandates for iOS Tests

Per CLAUDE.md, every method on `PlatformGrantDelegate` (including `request()`) must have at least one test in `IosGrantDelegateTest` or `ModuleSplitRegistryTest` wrapped in `withTimeout`. This converts silent deadlocks into detectable test failures.

---

*Last updated: 2026-05-15 — v2.0.0 (1131 tests, 100% pass rate)*
