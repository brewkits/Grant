# Grant Library — Roadmap

> Last updated: 2026-05-13 | Current stable: **v1.4.2** | Next major: **v1.5.0**

---

## 🛠️ In Progress / Upcoming

### v1.5.0 — Expanded Ecosystem & UX
*Focus: Wearables, Large Screens, and Fine-grained UX control.*

**1. Platform Expansion**
- [ ] **Wear OS support** — minimal permission surface, sensor-only grants.
- [ ] **Android TV / Large screen** — `requestWithCustomUi()` examples for non-phone form factors.

**2. UX & Analytics**
- [ ] **Analytics hooks** — optional `GrantEventListener` for tracking permission funnel drop-off.
- [ ] **iOS XCTest snapshot tests** for `GrantDialog` Compose UI.

**3. Maintenance**
- [ ] **Android 16 photo picker (`PICK_IMAGES` intent) — full library integration**. Recipe is shipped at `docs/recipes/photo-picker-fallback.md`; turning it into a built-in `AppGrant`-level surface.

## ✅ Released

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

