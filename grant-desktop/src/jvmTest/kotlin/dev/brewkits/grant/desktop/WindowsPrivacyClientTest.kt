package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsPrivacyClientTest {
    private class Bridge : WindowsBridgeLibrary {
        var status = 0
        var openResult = 1
        val queried = mutableListOf<Int>()
        val opened = mutableListOf<Int>()
        override fun grant_windows_bridge_version(): Int = 1
        override fun grant_windows_access_status(permission: Int): Int {
            queried += permission
            return status
        }
        override fun grant_windows_open_settings(permission: Int): Int {
            opened += permission
            return openResult
        }
    }

    @Test
    fun preserves_all_windows_statuses_without_inventing_permission_grants() {
        val bridge = Bridge()
        val client = WindowsPrivacyClient(bridge)
        val statuses = listOf(WindowsPrivacyStatus.UNSPECIFIED, WindowsPrivacyStatus.ALLOWED,
            WindowsPrivacyStatus.DENIED_BY_USER, WindowsPrivacyStatus.DENIED_BY_SYSTEM)
        statuses.forEachIndexed { raw, expected ->
            bridge.status = raw
            assertEquals(expected, client.checkStatus(AppGrant.CAMERA))
            assertEquals(expected, client.checkStatus(AppGrant.MICROPHONE))
        }
        assertTrue(bridge.opened.isEmpty(), "Status queries must not launch Settings")
    }

    @Test
    fun errors_and_unknown_values_are_unavailable_not_allowed() {
        val bridge = Bridge()
        val client = WindowsPrivacyClient(bridge)
        listOf(-1, Int.MIN_VALUE, 4, 99).forEach {
            bridge.status = it
            assertEquals(WindowsPrivacyStatus.UNAVAILABLE, client.checkStatus(AppGrant.CAMERA))
        }
    }

    @Test
    fun camera_and_microphone_have_distinct_native_ids_and_settings_pages() {
        val bridge = Bridge()
        val client = WindowsPrivacyClient(bridge)
        client.checkStatus(AppGrant.CAMERA)
        client.checkStatus(AppGrant.MICROPHONE)
        assertTrue(client.openSettings(AppGrant.CAMERA))
        assertTrue(client.openSettings(AppGrant.MICROPHONE))
        assertEquals(listOf(0, 1), bridge.queried)
        assertEquals(listOf(0, 1), bridge.opened)
    }

    @Test
    fun unsupported_permissions_never_call_native_code() {
        val bridge = Bridge()
        val client = WindowsPrivacyClient(bridge)
        AppGrant.entries.filter { it != AppGrant.CAMERA && it != AppGrant.MICROPHONE }.forEach {
            assertEquals(WindowsPrivacyStatus.UNAVAILABLE, client.checkStatus(it))
            assertFalse(client.openSettings(it))
        }
        assertTrue(bridge.queried.isEmpty())
        assertTrue(bridge.opened.isEmpty())
    }

    @Test
    fun failed_settings_launch_is_reported() {
        val bridge = Bridge().apply { openResult = 0 }
        assertFalse(WindowsPrivacyClient(bridge).openSettings(AppGrant.CAMERA))
    }
}
