# Grant Library — Roadmap

> Last updated: 2026-05-15 | Current stable: **v2.1.0** | Next major: **v2.1.0**

---

## 🛠️ In Progress / Upcoming

### v2.1.0 — Granular DCE + Expanded Ecosystem
*Focus: Opt-in handler DSL, Wearables, Large Screens, and Fine-grained UX control.*

**1. Opt-in Handler Registration DSL (from PR #39 by @RoryKelly)**
- [ ] **`GrantFactory.create { }` block API** — per-permission `expect/actual` extension functions (`location()`, `bluetooth()`, `camera()`, …) so K/N DCE can strip *any* unused handler, not just the three covered by v2.1.0 module isolation. Apps that only use Bluetooth + Notification will no longer link `AVFoundation`, `Photos`, or `CoreLocation` either.
- [ ] Layer the DSL on top of v2.1.0 module split — unconditional safety by default, optional granular control for consumers who want to minimize binary size further.
- [ ] Backward-compatible: `create()` no-arg stays functional via `registerAll()` shim.

**2. Platform Expansion**
- [ ] **Wear OS support** — minimal permission surface, sensor-only grants.
- [ ] **Android TV / Large screen** — `requestWithCustomUi()` examples for non-phone form factors.

**3. UX & Analytics**
- [ ] **Analytics hooks** — optional `GrantEventListener` for tracking permission funnel drop-off.
- [ ] **iOS XCTest snapshot tests** for `GrantDialog` Compose UI.

**4. Maintenance**
- [ ] **Android 16 photo picker (`PICK_IMAGES` intent) — full library integration**. Recipe is shipped at `docs/recipes/photo-picker-fallback.md`; turning it into a built-in `AppGrant`-level surface.

## ✅ Released

### v2.1.0 (2026-05-15)
- **iOS Framework Isolation**: `Contacts.framework`, `EventKit.framework`, `CoreMotion.framework` moved to opt-in modules (`grant-contacts`, `grant-calendar`, `grant-motion`). Apps that don't add these modules never link these frameworks — Apple's static scanner no longer requires the corresponding `NSUsageDescription` keys.
- **New modules**: `grant-contacts`, `grant-calendar`, `grant-motion` as separate Gradle/Maven artifacts.
- **`IosPermissionHandlerRegistry`**: Registry fix — `checkStatus()` for `RawPermission` now correctly dispatches to registered custom handlers.
- **Test suite expanded**: 1131 tests across 6 modules (Android JVM + iOS Simulator), 100% pass rate.
- **Breaking change**: iOS apps using Contacts/Calendar/Motion permissions must add the new optional module and call `initialize()` once. Android is unaffected.

### v1.4.2 (2026-05-13)
- **FINAL FIX**: Resolved 60s timeout in `LOCATION_ALWAYS` flow (Issue #33).
- **Hardening**: Immediate state reset in `GrantRequestActivity` and 10s fail-safe guard.
- **Logic**: Corrected `PARTIAL_GRANTED` upgrade path to allow system dialogs.
- **Android 15**: Optimized transitions and lifecycle handling.

### v1.4.1 (2026-05-12)
- HOTFIX: Initial mitigation for duplicate background location requests.

### v1.4.0 (2026-05-09)
- **Process Death Recovery**: `SavedStateHandle` integration in `GrantRequestActivity`.
- **Activity Launch Guard**: Prevented overlapping Activity instances.
- **IosPermissionHandlerRegistry**: Custom handlers for `RawPermission` on iOS.
- **NEARBY_WIFI_DEVICES**: Full Android 13+ support.
- **Material 3**: Upgraded all Compose dialogs to `BasicAlertDialog`.
- **New APIs**: `requestSuspend()` and `requestFlow()`.

### v1.3.1 (2026-05-05)
- HOTFIX: iOS `request()` mutex deadlock resolution (Issue #29).
- Regression tests for non-reentrant mutex patterns.

