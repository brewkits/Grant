package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.impl.PlatformGrantDelegate
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsPrivacyHandlerTest {
    private class Bridge(var status: Int) : WindowsBridgeLibrary {
        override fun grant_windows_bridge_version(): Int = 1
        override fun grant_windows_access_status(permission: Int): Int = status
        override fun grant_windows_open_settings(permission: Int): Int = 1
    }

    @Test
    fun windows_states_map_to_grant_statuses_without_inventing_a_grant() {
        val expected = mapOf(
            WindowsPrivacyStatus.ALLOWED to GrantStatus.GRANTED,
            WindowsPrivacyStatus.DENIED_BY_USER to GrantStatus.DENIED_ALWAYS,
            WindowsPrivacyStatus.DENIED_BY_SYSTEM to GrantStatus.DENIED_ALWAYS,
            WindowsPrivacyStatus.UNSPECIFIED to GrantStatus.NOT_DETERMINED,
            WindowsPrivacyStatus.UNAVAILABLE to GrantStatus.DENIED_ALWAYS,
        )
        WindowsPrivacyStatus.entries.forEach {
            assertEquals(expected.getValue(it), WindowsPrivacyHandler.toGrantStatus(it), "for $it")
        }
    }

    /** The user-visible defect: GrantManager said DENIED_ALWAYS while Windows said Allowed. */
    @Test
    fun grant_manager_reports_the_real_windows_state_after_registration() = runBlocking {
        val bridge = Bridge(status = 1) // Allowed
        GrantDesktop.registerWindowsHandlers(WindowsPrivacyClient(bridge))
        val delegate = PlatformGrantDelegate(InMemoryGrantStore())

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.MICROPHONE))

        bridge.status = 2 // DeniedByUser
        assertEquals(GrantStatus.DENIED_ALWAYS, delegate.checkStatus(AppGrant.CAMERA))
        bridge.status = 0 // Unspecified
        assertEquals(GrantStatus.NOT_DETERMINED, delegate.checkStatus(AppGrant.MICROPHONE))
    }

    @Test
    fun request_cannot_prompt_so_it_reports_the_state_unchanged() = runBlocking {
        val bridge = Bridge(status = 0)
        GrantDesktop.registerWindowsHandlers(WindowsPrivacyClient(bridge))
        val delegate = PlatformGrantDelegate(InMemoryGrantStore())

        assertEquals(GrantStatus.NOT_DETERMINED, delegate.request(AppGrant.CAMERA))
        bridge.status = 1
        assertEquals(GrantStatus.GRANTED, delegate.request(AppGrant.CAMERA))
    }
}
