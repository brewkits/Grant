package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.handlers.DesktopPermissionHandler
import dev.brewkits.grant.handlers.DesktopPermissionHandlerRegistry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Calls through the **real** [PlatformGrantDelegate] — not a fake — mirroring the rule
 * CLAUDE.md already holds iOS and web to: every method on the platform delegate needs at
 * least one test that exercises the real implementation.
 *
 * `grant-core`'s own `jvmMain` never links a macOS/Windows framework, so what's actually under
 * test here is the [DesktopPermissionHandlerRegistry] dispatch — the mechanism `grant-desktop`
 * (Tier 2, not yet built) will plug real handlers into — and the fallback behavior for
 * everything that mechanism hasn't been given a handler for.
 *
 * [DesktopPermissionHandlerRegistry] is a process-wide singleton with no `clear()` (matching
 * `IosPermissionHandlerRegistry`, which has none either); tests that register a handler use a
 * unique per-test [RawPermission] identifier (`ModuleSplitRegistryTest`'s existing convention
 * for the iOS registry) instead of a fixed [AppGrant], so registrations from one test can never
 * leak into another regardless of run order.
 */
class JvmGrantDelegateTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    /**
     * This is the test that would fail if `PlatformGrantDelegate.jvm.kt` regressed to a
     * Calf-style silent no-op: with nothing registered for this permission, both
     * checkStatus and request must resolve to DENIED_ALWAYS, never a fabricated GRANTED.
     */
    @Test
    fun unregistered_permission_reports_deniedAlways_not_a_silent_grant() = runTest {
        val permission = RawPermission(
            identifier = "com.test.unregistered.${hashCode()}",
            androidPermissions = emptyList(),
        )

        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.checkStatus(permission))
        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.request(permission))
    }

    @Test
    fun registered_handler_is_actually_used_for_checkStatus() = runTest {
        val permission = RawPermission(
            identifier = "com.test.checkStatus.${hashCode()}",
            androidPermissions = emptyList(),
        )
        DesktopPermissionHandlerRegistry.register(permission.identifier, object : DesktopPermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(): GrantStatus = GrantStatus.GRANTED
        })

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(permission))
    }

    @Test
    fun registered_handler_is_actually_used_for_request() = runTest {
        val permission = RawPermission(
            identifier = "com.test.request.${hashCode()}",
            androidPermissions = emptyList(),
        )
        DesktopPermissionHandlerRegistry.register(permission.identifier, object : DesktopPermissionHandler {
            override fun checkStatus(): GrantStatus = GrantStatus.NOT_DETERMINED
            override suspend fun request(): GrantStatus = GrantStatus.DENIED_ALWAYS
        })

        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.request(permission))
    }

    @Test
    fun batch_request_resolves_every_grant_without_throwing() = runTest {
        val results = delegate.request(listOf(AppGrant.CAMERA, AppGrant.LOCATION, AppGrant.CONTACTS))
        assertEquals(3, results.size)
        results.values.forEach { assertEquals(GrantStatus.DENIED_ALWAYS, it) }
    }

    @Test
    fun openSettings_does_not_throw_without_grant_desktop_registered() {
        delegate.openSettings()
    }
}
