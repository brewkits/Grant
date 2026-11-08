package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.handlers.DesktopPermissionHandlerRegistry
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D6 on desktop: `openSettings(grant)` must reach an opener that is told WHICH grant, so Camera and
 * Microphone can open their own privacy pages instead of whichever one a no-argument opener picked.
 */
class JvmOpenSettingsForGrantTest {

    private val delegate = PlatformGrantDelegate(InMemoryGrantStore())

    @AfterTest
    fun reset() {
        DesktopPermissionHandlerRegistry.permissionSettingsOpener = null
        DesktopPermissionHandlerRegistry.settingsOpener = null
    }

    @Test
    fun `openSettings(grant) hands the grant to the per-permission opener`() {
        val opened = mutableListOf<GrantPermission>()
        DesktopPermissionHandlerRegistry.permissionSettingsOpener = { opened += it }

        delegate.openSettings(AppGrant.MICROPHONE)
        delegate.openSettings(AppGrant.CAMERA)

        assertEquals(listOf<GrantPermission>(AppGrant.MICROPHONE, AppGrant.CAMERA), opened)
    }

    @Test
    fun `openSettings(grant) falls back to the no-argument opener when no per-permission one exists`() {
        var calls = 0
        DesktopPermissionHandlerRegistry.settingsOpener = { calls++ }

        delegate.openSettings(AppGrant.MICROPHONE)

        assertEquals(1, calls)
    }

    @Test
    fun `the no-argument openSettings still uses the no-argument opener`() {
        var calls = 0
        val opened = mutableListOf<GrantPermission>()
        DesktopPermissionHandlerRegistry.settingsOpener = { calls++ }
        DesktopPermissionHandlerRegistry.permissionSettingsOpener = { opened += it }

        delegate.openSettings()

        assertEquals(1, calls)
        assertEquals(emptyList<GrantPermission>(), opened)
    }
}
