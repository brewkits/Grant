package dev.brewkits.grant.tracking

import kotlin.test.Test

/**
 * `initialize()` must be safe to call repeatedly and from any platform in a shared source set —
 * on Android it is a documented no-op, since there is no runtime permission for cross-app
 * tracking there. This mirrors `GrantContactsTest` for the other opt-in modules.
 *
 * The iOS behaviour that actually matters — `ATTrackingManager` status mapping and the
 * foreground-active requirement — is not unit-testable: `trackingAuthorizationStatus` reads
 * real device/simulator TCC state that a test cannot set, and the prompt requires a human. It
 * is covered by `TrackingPermissionHandler`'s documented mapping and must be exercised on a
 * device before this module is advertised as verified.
 */
class GrantTrackingTest {

    @Test
    fun initialize_is_idempotent() {
        GrantTracking.initialize()
        GrantTracking.initialize()
        GrantTracking.initialize()
    }
}
