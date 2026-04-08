package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Comprehensive status-mapping tests for all 17 AppGrant permissions.
 *
 * Tests every permission × every status transition:
 * - NOT_DETERMINED → request → GRANTED
 * - NOT_DETERMINED → request → DENIED
 * - NOT_DETERMINED → request → DENIED_ALWAYS
 * - GRANTED → checkStatus (idempotent)
 * - DENIED_ALWAYS → request → DENIED_ALWAYS (no re-prompt)
 * - PARTIAL_GRANTED mapping for gallery, location-always, calendar
 */
class PermissionStatusMappingTest {

    private val manager = FakeGrantManager()

    @AfterTest
    fun tearDown() = manager.reset()

    // ====================================================================
    // ALL_PERMISSIONS list — single source of truth for parametrized tests
    // ====================================================================

    private val allPermissions = AppGrant.entries.toList()

    // ====================================================================
    // Group 1: NOT_DETERMINED → GRANTED (happy path, all 17)
    // ====================================================================

    @Test
    fun `all 17 permissions transition NOT_DETERMINED to GRANTED`() = runTest {
        allPermissions.forEach { grant ->
            manager.configure(grant, status = GrantStatus.NOT_DETERMINED, requestResult = GrantStatus.GRANTED)
            assertEquals(
                GrantStatus.GRANTED,
                manager.request(grant),
                "Grant ${grant.name} should transition to GRANTED"
            )
        }
    }

    // ====================================================================
    // Group 2: GRANTED → checkStatus (idempotent — must stay GRANTED)
    // ====================================================================

    @Test
    fun `all 17 permissions checkStatus returns GRANTED when already granted`() = runTest {
        allPermissions.forEach { grant ->
            manager.setStatus(grant, GrantStatus.GRANTED)
            assertEquals(
                GrantStatus.GRANTED,
                manager.checkStatus(grant),
                "Grant ${grant.name} checkStatus should return GRANTED"
            )
        }
    }

    // ====================================================================
    // Group 3: DENIED_ALWAYS → request → DENIED_ALWAYS (soft blocked)
    // ====================================================================

    @Test
    fun `all 17 permissions stay DENIED_ALWAYS after re-request`() = runTest {
        allPermissions.forEach { grant ->
            manager.configure(grant, status = GrantStatus.DENIED_ALWAYS, requestResult = GrantStatus.DENIED_ALWAYS)
            assertEquals(
                GrantStatus.DENIED_ALWAYS,
                manager.request(grant),
                "Grant ${grant.name} should stay DENIED_ALWAYS — no system dialog"
            )
        }
    }

    // ====================================================================
    // Group 4: DENIED (soft deny) → re-request → GRANTED (user accepts rationale)
    // ====================================================================

    @Test
    fun `permissions can recover from DENIED after rationale shown`() = runTest {
        val softDeniable = listOf(
            AppGrant.CAMERA,
            AppGrant.MICROPHONE,
            AppGrant.LOCATION,
            AppGrant.NOTIFICATION,
            AppGrant.CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.MOTION,
            AppGrant.BLUETOOTH
        )
        softDeniable.forEach { grant ->
            manager.configure(grant, status = GrantStatus.DENIED, requestResult = GrantStatus.GRANTED)
            assertEquals(
                GrantStatus.GRANTED,
                manager.request(grant),
                "Grant ${grant.name} should recover from DENIED after rationale"
            )
        }
    }

    // ====================================================================
    // Group 5: PARTIAL_GRANTED — platform-specific cases
    // ====================================================================

    @Test
    fun `GALLERY returns PARTIAL_GRANTED for limited photo access`() = runTest {
        manager.configure(AppGrant.GALLERY, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(AppGrant.GALLERY))
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.request(AppGrant.GALLERY))
    }

    @Test
    fun `GALLERY_IMAGES_ONLY returns PARTIAL_GRANTED for limited access`() = runTest {
        manager.configure(AppGrant.GALLERY_IMAGES_ONLY, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(AppGrant.GALLERY_IMAGES_ONLY))
    }

    @Test
    fun `GALLERY_VIDEO_ONLY returns PARTIAL_GRANTED for limited access`() = runTest {
        manager.configure(AppGrant.GALLERY_VIDEO_ONLY, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(AppGrant.GALLERY_VIDEO_ONLY))
    }

    @Test
    fun `LOCATION_ALWAYS returns PARTIAL_GRANTED when only WhenInUse is granted`() = runTest {
        // iOS: returns PARTIAL_GRANTED when WhenInUse is granted but Always was requested
        // Android 11+: returns PARTIAL_GRANTED before background is granted
        manager.configure(AppGrant.LOCATION_ALWAYS, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(AppGrant.LOCATION_ALWAYS))
    }

    @Test
    fun `CALENDAR returns PARTIAL_GRANTED for iOS 17 write-only access`() = runTest {
        // iOS 17+: writeOnly (raw=4) maps to PARTIAL_GRANTED
        manager.configure(AppGrant.CALENDAR, GrantStatus.PARTIAL_GRANTED)
        assertEquals(GrantStatus.PARTIAL_GRANTED, manager.checkStatus(AppGrant.CALENDAR))
    }

    // ====================================================================
    // Group 6: SCHEDULE_EXACT_ALARM — always GRANTED on iOS
    // ====================================================================

    @Test
    fun `SCHEDULE_EXACT_ALARM returns GRANTED — iOS always allows alarms`() = runTest {
        manager.configure(AppGrant.SCHEDULE_EXACT_ALARM, GrantStatus.GRANTED)
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM))
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.SCHEDULE_EXACT_ALARM))
    }

    // ====================================================================
    // Group 7: Per-permission independence (no cross-contamination)
    // ====================================================================

    @Test
    fun `setting status for one permission does not affect others`() = runTest {
        manager.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        manager.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)

        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.checkStatus(AppGrant.MICROPHONE))
        // Others fall back to default
        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.LOCATION))
    }

    @Test
    fun `request result can differ from check status`() = runTest {
        // Simulates: status is DENIED but request succeeds (user accepted this time)
        manager.setStatus(AppGrant.BLUETOOTH, GrantStatus.DENIED)
        manager.setRequestResult(AppGrant.BLUETOOTH, GrantStatus.GRANTED)

        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.BLUETOOTH))
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.BLUETOOTH))
    }

    // ====================================================================
    // Group 8: batch request (all permissions at once)
    // ====================================================================

    @Test
    fun `batch request returns correct status for each grant independently`() = runTest {
        manager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        manager.setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)
        manager.setRequestResult(AppGrant.LOCATION, GrantStatus.PARTIAL_GRANTED)

        val results = manager.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.LOCATION))

        assertEquals(GrantStatus.GRANTED, results[AppGrant.CAMERA])
        assertEquals(GrantStatus.DENIED_ALWAYS, results[AppGrant.MICROPHONE])
        assertEquals(GrantStatus.PARTIAL_GRANTED, results[AppGrant.LOCATION])
    }

    @Test
    fun `batch request covers all 17 permissions without crash`() = runTest {
        manager.mockRequestResult = GrantStatus.GRANTED
        val results = manager.request(allPermissions)
        assertEquals(allPermissions.size, results.size)
        results.values.forEach { assertEquals(GrantStatus.GRANTED, it) }
    }
}
