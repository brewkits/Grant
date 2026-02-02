# TODO for v1.1.0

## Security & Code Quality

### CodeQL Alerts (Non-Critical)

**Priority: Low** - Defer to v1.1.0 or later major release

#### 1. Deprecated BluetoothAdapter.getDefaultAdapter()
- **Location:** `grant-core/src/androidMain/kotlin/dev/brewkits/grant/impl/PlatformServiceDelegate.android.kt:68`
- **Severity:** Note (Low)
- **Status:** Deferred
- **Fix:** Replace with BluetoothManager API
```kotlin
// Current (deprecated)
val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

// Fix (modern API)
val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
val bluetoothAdapter = bluetoothManager.adapter
```
- **Backward Compatibility:** ✅ BluetoothManager available since API 18 (Grant supports API 24+)
- **Risk:** Low - deprecated API still works, not removed yet

#### 2. Demo App Backup Enabled
- **Location:** `demo/src/androidMain/AndroidManifest.xml:45`
- **Severity:** High (but demo only)
- **Status:** Deferred
- **Fix:** Add backup rules or disable backup
```xml
<!-- Option A: Disable -->
android:allowBackup="false"

<!-- Option B: Add rules -->
android:fullBackupContent="@xml/demo_backup_rules"
```
- **Risk:** Low - Demo app has no sensitive data

---

## Dependency Updates (Deferred from Dependabot PRs)

**Priority: Low** - Only if needed, test thoroughly before updating

### Major Version Bumps (Closed PRs)
- Kotlin 2.1.21 → 2.3.0 (PR #11) - Breaking changes
- Gradle 8.14.3 → 9.3.1 (PR #12) - Breaking changes
- Kover 0.7.5 → 0.9.4 (PR #13) - Breaking changes
- Compose 1.9.3 → 1.10.0 (PR #7) - Breaking changes

**Strategy:**
- Wait for stable versions
- Update when Grant needs new features from these versions
- Test thoroughly with all platforms
- Plan migration guide if breaking changes affect users

---

## Potential Features (Ideas)

### Community Requests
- [ ] GitHub Issue #1: Location + GPS flow - ✅ Documented (recipes provided)
- [ ] Additional recipes for other common patterns?
- [ ] More demo examples?

### Technical Improvements
- [ ] Kotlin 2.x migration (when stable)
- [ ] Compose 1.10+ features (if beneficial)
- [ ] Performance optimizations (if needed)

---

## When to Release v1.1.0?

**Trigger events:**
1. **Bug fixes accumulate** - 3+ significant bugs fixed
2. **New features needed** - Community requests features
3. **Major dependency stable** - Kotlin 2.2 stable, Compose 1.10 mature
4. **Security patches** - If critical vulnerabilities found
5. **API additions** - New non-breaking features

**NOT triggered by:**
- ❌ Minor CodeQL alerts (low severity)
- ❌ Documentation updates
- ❌ Demo app changes
- ❌ Dependabot PRs that fail tests

---

## Release Checklist (When Ready)

### Before v1.1.0:
- [ ] Fix CodeQL alerts (deprecated API, backup)
- [ ] Review dependency updates (Kotlin, Compose, Gradle)
- [ ] Test on real devices (Android 14, 15, iOS 17, 18)
- [ ] Update CHANGELOG.md
- [ ] Update README.md if needed
- [ ] Run full test suite
- [ ] Verify no breaking changes
- [ ] Test demo app on both platforms

### Release:
- [ ] Bump version to 1.1.0
- [ ] Tag release
- [ ] Publish to Maven Central
- [ ] Create GitHub release with notes
- [ ] Announce in community

---

## Current Status

**Version:** 1.0.0 (Stable)
**Next Release:** v1.1.0 (TBD - no rush)
**Priority:** Focus on documentation, community support, bug fixes

**Philosophy:** Stay stable, don't break users, iterate carefully.
