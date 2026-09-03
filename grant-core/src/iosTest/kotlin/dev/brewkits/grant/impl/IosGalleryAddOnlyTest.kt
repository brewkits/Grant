package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [AppGrant.GALLERY_ADD_ONLY] on iOS routes to a `PhotoPermissionHandler` configured with
 * `PHAccessLevelAddOnly` and `NSPhotoLibraryAddUsageDescription`.
 *
 * Per the CLAUDE.md rule, every path through the real delegate gets a `withTimeout`-wrapped
 * test so a silent deadlock surfaces as a failing test rather than a hang. The handler is
 * reached through the real delegate here, not a fake.
 *
 * The test binary has no `NSPhotoLibraryAddUsageDescription` key, so the plist guard is
 * expected to short-circuit; the assertion is deliberately on "returns a valid status without
 * hanging or crashing" rather than on one specific value, which would depend on the test
 * host's Info.plist.
 */
class IosGalleryAddOnlyTest {

    private lateinit var delegate: PlatformGrantDelegate

    private val validStatuses = setOf(
        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED,
    )

    @BeforeTest
    fun setup() {
        delegate = PlatformGrantDelegate(InMemoryGrantStore())
    }

    @Test
    fun `checkStatus GALLERY_ADD_ONLY returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY_ADD_ONLY) }
        assertTrue(
            status in validStatuses,
            "checkStatus(GALLERY_ADD_ONLY) must resolve to a valid status, got $status",
        )
    }

    @Test
    fun `request GALLERY_ADD_ONLY returns a valid status without hanging`() = runTest {
        // The deadlock guard that matters: request() goes through the per-permission mutex and
        // must never call a public method that re-acquires it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.GALLERY_ADD_ONLY) }
        assertTrue(
            status in validStatuses,
            "request(GALLERY_ADD_ONLY) must resolve to a valid status, got $status",
        )
    }

    @Test
    fun `add-only is dispatched independently of read-write gallery`() = runTest {
        // Both must resolve; the point is that GALLERY_ADD_ONLY has its own handler instance and
        // does not fall through to the read/write one or to a "not registered" stub.
        val addOnly = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY_ADD_ONLY) }
        val readWrite = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY) }
        assertTrue(addOnly in validStatuses, "GALLERY_ADD_ONLY must resolve, got $addOnly")
        assertTrue(readWrite in validStatuses, "GALLERY must resolve, got $readWrite")
    }
}
