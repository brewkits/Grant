package dev.brewkits.grant.location

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import dev.brewkits.grant.ServiceFactory
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import kotlin.test.assertNotEquals

/**
 * iOS tests for grant-location.
 *
 * Calls go through the REAL [PlatformGrantDelegate] after [GrantLocation.initialize], each wrapped in
 * `withTimeout` (CLAUDE.md rule 4), so a silent deadlock becomes a failing test.
 *
 * They also prove registration. If the module's handler were not registered, these grants
 * would throw `AssertionError` in this debug test binary (grant-core's fail-fast for modules
 * split out in 3.0.0), so reaching any status at all means the module's handler answered.
 * The test host has no usage-description keys, so the plist guard normally short-circuits to
 * DENIED_ALWAYS; the assertions accept any valid status rather than depend on that.
 */
class GrantLocationIosTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED,
    )

    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        GrantLocation.initialize()
        delegate = PlatformGrantDelegate(InMemoryGrantStore())
    }

    @Test
    fun `initialize is idempotent`() {
        GrantLocation.initialize()
        GrantLocation.initialize()
    }

    @Test
    fun `checkStatus LOCATION returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.LOCATION) }
        assertTrue(status in validStatuses, "checkStatus(LOCATION) must resolve, got $status")
    }

    @Test
    fun `request LOCATION returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.LOCATION) }
        assertTrue(status in validStatuses, "request(LOCATION) must resolve, got $status")
    }

    /**
     * Without grant-location, grant-core reports UNKNOWN for LOCATION_GPS. With it, the answer
     * must be a real reading: CLLocationManager.locationServicesEnabled() is ENABLED or DISABLED.
     */
    @Test
    fun `LOCATION_GPS is answered by the registered check and is not UNKNOWN`() = runTest {
        val status = withTimeout(3_000L) {
            ServiceFactory.createServiceManager().checkServiceStatus(ServiceType.LOCATION_GPS)
        }
        assertNotEquals(ServiceStatus.UNKNOWN, status, "LOCATION_GPS must be read via grant-location")
        assertTrue(status == ServiceStatus.ENABLED || status == ServiceStatus.DISABLED, "got $status")
    }
}
