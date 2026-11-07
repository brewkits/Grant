package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsNativeBridgeTest {
    @Test
    fun real_windows_bridge_loads_and_queries_current_process_without_a_prompt() {
        val windowsX64 = System.getProperty("os.name").startsWith("Windows") &&
            System.getProperty("os.arch").lowercase() in listOf("amd64", "x86_64")
        if (!windowsX64) {
            assertNull(WindowsNativeBridge.library)
            assertEquals(WindowsPrivacyStatus.UNAVAILABLE, GrantDesktop.checkWindowsPrivacyStatus(AppGrant.CAMERA))
            return
        }
        val bridge = assertNotNull(WindowsNativeBridge.library, "Windows x64 DLL must load")
        assertEquals(1, bridge.grant_windows_bridge_version())
        for (permission in 0..1) {
            val raw = bridge.grant_windows_access_status(permission)
            assertTrue(raw in 0..3, "DeviceAccessStatus expected 0..3, got $raw for permission $permission")
        }
        assertFalse(GrantDesktop.isSupported(AppGrant.CAMERA), "Windows does not implement native permission requests")
        assertEquals(-1, bridge.grant_windows_access_status(99))
        assertEquals(0, bridge.grant_windows_open_settings(99))
    }
}
