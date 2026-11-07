package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.RawPermission
import dev.brewkits.grant.handlers.DesktopPermissionHandlerRegistry
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsSettingsRoutingTest {
    private class Bridge : WindowsBridgeLibrary {
        val opened = mutableListOf<Int>()
        override fun grant_windows_bridge_version(): Int = 1
        override fun grant_windows_access_status(permission: Int): Int = 1
        override fun grant_windows_open_settings(permission: Int): Int { opened += permission; return 1 }
    }

    @AfterTest
    fun reset() {
        DesktopPermissionHandlerRegistry.permissionSettingsOpener = null
    }

    @Test
    fun grant_manager_opens_the_camera_or_microphone_page_matching_the_grant() {
        val bridge = Bridge()
        GrantDesktop.registerWindowsSettings(WindowsPrivacyClient(bridge))
        val delegate = PlatformGrantDelegate(InMemoryGrantStore())

        delegate.openSettings(AppGrant.MICROPHONE)
        delegate.openSettings(AppGrant.CAMERA)

        assertEquals(listOf(1, 0), bridge.opened, "microphone is native id 1, camera is 0")
    }

    @Test
    fun unrelated_grants_fall_back_to_the_camera_page() {
        val bridge = Bridge()
        GrantDesktop.registerWindowsSettings(WindowsPrivacyClient(bridge))
        val delegate = PlatformGrantDelegate(InMemoryGrantStore())

        delegate.openSettings(AppGrant.LOCATION)
        delegate.openSettings(RawPermission("custom", emptyList()))

        assertEquals(listOf(0, 0), bridge.opened)
    }

    @Test
    fun nothing_is_opened_until_the_settings_route_is_registered() {
        val bridge = Bridge()
        PlatformGrantDelegate(InMemoryGrantStore()).openSettings(AppGrant.MICROPHONE)
        assertEquals(emptyList(), bridge.opened)
    }
}
