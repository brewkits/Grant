package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GrantDesktopCapabilityTest {
    @Test
    fun camera_and_microphone_require_a_loaded_bridge() {
        val available = NativeBridgeLoader.library != null
        assertEquals(available, GrantDesktop.isSupported(AppGrant.CAMERA))
        assertEquals(available, GrantDesktop.isSupported(AppGrant.MICROPHONE))
    }

    @Test
    fun all_other_permissions_are_unsupported_even_on_a_mac() {
        AppGrant.entries.filter { it != AppGrant.CAMERA && it != AppGrant.MICROPHONE }.forEach {
            assertFalse(GrantDesktop.isSupported(it), "$it has no built-in Desktop handler")
        }
    }
}
