package dev.brewkits.grant.desktop

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.RawPermission
import kotlin.test.Test
import kotlin.test.assertEquals

class MacPrivacyAnchorTest {
    @Test
    fun camera_and_microphone_open_their_own_privacy_pages() {
        assertEquals("Privacy_Camera", GrantDesktop.macPrivacyAnchor(AppGrant.CAMERA))
        assertEquals("Privacy_Microphone", GrantDesktop.macPrivacyAnchor(AppGrant.MICROPHONE))
    }

    @Test
    fun anything_else_falls_back_to_the_camera_page() {
        assertEquals("Privacy_Camera", GrantDesktop.macPrivacyAnchor(AppGrant.LOCATION))
        assertEquals("Privacy_Camera", GrantDesktop.macPrivacyAnchor(RawPermission("x", emptyList())))
    }
}
