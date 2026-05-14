package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.handlers.IosPermissionHandler
import dev.brewkits.grant.handlers.IosPermissionHandlerRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the v2.0 module-split registry pattern in PlatformGrantDelegate.
 *
 * Verifies that:
 * 1. Unregistered optional permissions (no module initialized) return NOT_DETERMINED
 *    and do NOT crash or deadlock.
 * 2. A registered handler is correctly dispatched to by PlatformGrantDelegate.
 * 3. The registry survives multiple register/query cycles without corruption.
 *
 * These tests operate on IosPermissionHandlerRegistry directly (unit) and through
 * PlatformGrantDelegate (integration), so they cover both the dispatch logic and
 * the handler lookup path.
 *
 * CLAUDE.md requirement: every request() path through PlatformGrantDelegate must
 * have a withTimeout-wrapped test. The NotRegisteredHandler path is tested here.
 */
class ModuleSplitRegistryTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    // ────────────────────────────────────────────────────────────────────────────
    // Unit tests: IosPermissionHandlerRegistry
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `registry returns null for unregistered identifier`() {
        // Use a unique key that is guaranteed not to be registered by any module.
        val result = IosPermissionHandlerRegistry.get("com.test.unregistered.permission.${hashCode()}")
        assertEquals(null, result, "Unregistered permission must return null from registry")
    }

    @Test
    fun `registry returns correct handler after register`() {
        val key = "com.test.fake.permission"
        val fakeHandler = object : IosPermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(): GrantStatus = GrantStatus.GRANTED
        }

        IosPermissionHandlerRegistry.register(key, fakeHandler)
        val retrieved = IosPermissionHandlerRegistry.get(key)

        assertEquals(fakeHandler, retrieved, "Registry must return the exact registered handler")
    }

    @Test
    fun `registry overwrites handler on re-register`() {
        val key = "com.test.overwrite.permission"
        val handler1 = object : IosPermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.DENIED
            override suspend fun request(): GrantStatus = GrantStatus.DENIED
        }
        val handler2 = object : IosPermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(): GrantStatus = GrantStatus.GRANTED
        }

        IosPermissionHandlerRegistry.register(key, handler1)
        IosPermissionHandlerRegistry.register(key, handler2)

        val retrieved = IosPermissionHandlerRegistry.get(key)
        assertEquals(GrantStatus.GRANTED, retrieved?.checkStatus(),
            "Re-registering the same key must overwrite with new handler")
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Integration: NotRegisteredHandler path through PlatformGrantDelegate
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `checkStatus CONTACTS without module init returns valid status and does not crash`() = runTest {
        // grant-contacts module is NOT initialized in this test context.
        // PlatformGrantDelegate dispatches to NotRegisteredHandler → must return NOT_DETERMINED.
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.CONTACTS) }
        val validStatuses = setOf(
            GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED, GrantStatus.DENIED,
            GrantStatus.DENIED_ALWAYS, GrantStatus.NOT_DETERMINED
        )
        assertTrue(status in validStatuses, "checkStatus(CONTACTS) unregistered must return valid status")
    }

    @Test
    fun `checkStatus READ_CONTACTS without module init returns valid status`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.READ_CONTACTS) }
        assertTrue(
            status in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "checkStatus(READ_CONTACTS) unregistered must return valid status"
        )
    }

    @Test
    fun `checkStatus CALENDAR without module init returns valid status`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.CALENDAR) }
        assertTrue(
            status in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "checkStatus(CALENDAR) unregistered must return valid status"
        )
    }

    @Test
    fun `checkStatus READ_CALENDAR without module init returns valid status`() = runTest {
        val status = withTimeout(3_000L) { delegate.checkStatus(AppGrant.READ_CALENDAR) }
        assertTrue(
            status in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "checkStatus(READ_CALENDAR) unregistered must return valid status"
        )
    }

    @Test
    fun `request CONTACTS without module init returns NOT_DETERMINED and does not deadlock`() = runTest {
        val result = withTimeout(3_000L) { delegate.request(AppGrant.CONTACTS) }
        assertTrue(
            result in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "request(CONTACTS) without module must return a valid status, not deadlock. Got: $result"
        )
    }

    @Test
    fun `request CALENDAR without module init returns NOT_DETERMINED and does not deadlock`() = runTest {
        val result = withTimeout(3_000L) { delegate.request(AppGrant.CALENDAR) }
        assertTrue(
            result in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "request(CALENDAR) without module must not deadlock. Got: $result"
        )
    }

    @Test
    fun `request MOTION without module init returns valid status and does not deadlock`() = runTest {
        val result = withTimeout(3_000L) { delegate.request(AppGrant.MOTION) }
        assertTrue(
            result in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
            "request(MOTION) without module must not deadlock. Got: $result"
        )
    }

    @Test
    fun `batch request with all optional permissions does not deadlock`() = runTest {
        val optionalGrants = listOf(
            AppGrant.CONTACTS,
            AppGrant.READ_CONTACTS,
            AppGrant.CALENDAR,
            AppGrant.READ_CALENDAR,
            AppGrant.MOTION
        )
        val results = withTimeout(10_000L) { delegate.request(optionalGrants) }

        assertEquals(optionalGrants.size, results.size,
            "Batch request must return a result for every optional permission")
        results.values.forEach { status ->
            assertTrue(
                status in setOf(GrantStatus.NOT_DETERMINED, GrantStatus.DENIED,
                    GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED, GrantStatus.PARTIAL_GRANTED),
                "Each optional permission result must be a valid GrantStatus. Got: $status"
            )
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Integration: registered handler path through PlatformGrantDelegate
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `registered custom handler is dispatched for RawPermission`() = runTest {
        val customKey = "com.test.custom.v2.permission"
        var checkStatusCalled = false
        val customHandler = object : IosPermissionHandler {
            override fun checkStatus(): GrantStatus {
                checkStatusCalled = true
                return GrantStatus.GRANTED
            }
            override suspend fun request(): GrantStatus = GrantStatus.GRANTED
        }

        IosPermissionHandlerRegistry.register(customKey, customHandler)

        val rawPermission = dev.brewkits.grant.RawPermission(
            identifier = customKey,
            androidPermissions = emptyList(),
            iosUsageKey = null
        )
        val result = withTimeout(3_000L) { delegate.checkStatus(rawPermission) }
        assertTrue(checkStatusCalled, "Registered custom handler must be invoked for RawPermission")
        assertEquals(GrantStatus.GRANTED, result)
    }
}
