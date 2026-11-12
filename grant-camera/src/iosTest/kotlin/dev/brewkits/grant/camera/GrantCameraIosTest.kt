package dev.brewkits.grant.camera

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import dev.brewkits.grant.handlers.AVPermissionHandler

/**
 * iOS tests for grant-camera.
 *
 * Calls go through the REAL [PlatformGrantDelegate] after [GrantCamera.initialize], each wrapped in
 * `withTimeout` (CLAUDE.md rule 4), so a silent deadlock becomes a failing test.
 *
 * They also prove registration. If the module's handler were not registered, these grants
 * would throw `AssertionError` in this debug test binary (grant-core's fail-fast for modules
 * split out in 3.0.0), so reaching any status at all means the module's handler answered.
 * The test host has no usage-description keys, so the plist guard normally short-circuits to
 * DENIED_ALWAYS; the assertions accept any valid status rather than depend on that.
 */
class GrantCameraIosTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED,
    )

    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        GrantCamera.initialize()
        delegate = PlatformGrantDelegate(InMemoryGrantStore())
    }

    @Test
    fun `initialize is idempotent`() {
        GrantCamera.initialize()
        GrantCamera.initialize()
    }

    /** Moved from grant-core's IosHandlerPatternTest with the handler. */
    @Test
    fun `camera and microphone handlers are distinct instances`() {
        val camera = AVPermissionHandler.camera()
        val microphone = AVPermissionHandler.microphone()
        assertTrue(camera !== microphone, "camera and microphone handlers must be distinct instances")
    }

    @Test
    fun `checkStatus CAMERA returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.CAMERA) }
        assertTrue(status in validStatuses, "checkStatus(CAMERA) must resolve, got $status")
    }

    @Test
    fun `request CAMERA returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.CAMERA) }
        assertTrue(status in validStatuses, "request(CAMERA) must resolve, got $status")
    }

    @Test
    fun `checkStatus MICROPHONE returns a valid status without hanging`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.MICROPHONE) }
        assertTrue(status in validStatuses, "checkStatus(MICROPHONE) must resolve, got $status")
    }

    @Test
    fun `request MICROPHONE returns a valid status without hanging`() = runTest {
        // Goes through the per-permission mutex; must never re-acquire it (Issue #29).
        val status = withTimeout(5_000L) { delegate.request(AppGrant.MICROPHONE) }
        assertTrue(status in validStatuses, "request(MICROPHONE) must resolve, got $status")
    }
}
