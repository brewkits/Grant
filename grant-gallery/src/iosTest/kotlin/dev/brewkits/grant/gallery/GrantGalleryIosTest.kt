package dev.brewkits.grant.gallery

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * iOS tests for grant-gallery. GALLERY_ADD_ONLY has its own suite (IosGalleryAddOnlyTest).
 *
 * Calls go through the REAL [PlatformGrantDelegate] after [GrantGallery.initialize], each wrapped in
 * `withTimeout` (CLAUDE.md rule 4), so a silent deadlock becomes a failing test.
 *
 * They also prove registration. If the module's handler were not registered, these grants
 * would throw `AssertionError` in this debug test binary (grant-core's fail-fast for modules
 * split out in 3.0.0), so reaching any status at all means the module's handler answered.
 * The test host has no usage-description keys, so the plist guard normally short-circuits to
 * DENIED_ALWAYS; the assertions accept any valid status rather than depend on that.
 */
class GrantGalleryIosTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED,
    )

    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        GrantGallery.initialize()
        delegate = PlatformGrantDelegate(InMemoryGrantStore())
    }

    @Test
    fun `initialize is idempotent`() {
        GrantGallery.initialize()
        GrantGallery.initialize()
    }

    @Test
    fun `checkStatus GALLERY returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY) }
        assertTrue(status in validStatuses, "checkStatus(GALLERY) must resolve, got $status")
    }

    @Test
    fun `request GALLERY returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.GALLERY) }
        assertTrue(status in validStatuses, "request(GALLERY) must resolve, got $status")
    }

    @Test
    fun `checkStatus GALLERY_IMAGES_ONLY returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY_IMAGES_ONLY) }
        assertTrue(status in validStatuses, "checkStatus(GALLERY_IMAGES_ONLY) must resolve, got $status")
    }

    @Test
    fun `request GALLERY_IMAGES_ONLY returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.GALLERY_IMAGES_ONLY) }
        assertTrue(status in validStatuses, "request(GALLERY_IMAGES_ONLY) must resolve, got $status")
    }

    @Test
    fun `checkStatus GALLERY_VIDEO_ONLY returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.GALLERY_VIDEO_ONLY) }
        assertTrue(status in validStatuses, "checkStatus(GALLERY_VIDEO_ONLY) must resolve, got $status")
    }

    @Test
    fun `request GALLERY_VIDEO_ONLY returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.GALLERY_VIDEO_ONLY) }
        assertTrue(status in validStatuses, "request(GALLERY_VIDEO_ONLY) must resolve, got $status")
    }

    @Test
    fun `checkStatus STORAGE returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.STORAGE) }
        assertTrue(status in validStatuses, "checkStatus(STORAGE) must resolve, got $status")
    }

    @Test
    fun `request STORAGE returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.STORAGE) }
        assertTrue(status in validStatuses, "request(STORAGE) must resolve, got $status")
    }
}
