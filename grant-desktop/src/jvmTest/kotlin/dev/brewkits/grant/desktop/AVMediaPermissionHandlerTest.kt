package dev.brewkits.grant.desktop

import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AVMediaPermissionHandlerTest {
    private class Bridge : GrantDesktopBridgeLibrary {
        var cameraStatus = 0
        var microphoneStatus = 0
        var requestThread: Thread? = null
        var cameraRequests = 0
        var microphoneRequests = 0

        override fun grant_camera_status(): Int = cameraStatus
        override fun grant_microphone_status(): Int = microphoneStatus
        override fun grant_camera_request_blocking(): Int {
            requestThread = Thread.currentThread()
            cameraRequests++
            return cameraStatus
        }
        override fun grant_microphone_request_blocking(): Int {
            requestThread = Thread.currentThread()
            microphoneRequests++
            return microphoneStatus
        }
        override fun grant_open_privacy_settings(anchor: String): Boolean = true
    }

    @Test
    fun camera_and_microphone_map_every_native_status() {
        val bridge = Bridge()
        val camera = AVMediaPermissionHandler.camera(bridge)
        val microphone = AVMediaPermissionHandler.microphone(bridge)
        val expected = listOf(
            GrantStatus.NOT_DETERMINED, GrantStatus.DENIED_ALWAYS,
            GrantStatus.DENIED_ALWAYS, GrantStatus.GRANTED,
        )
        expected.forEachIndexed { raw, status ->
            bridge.cameraStatus = raw
            bridge.microphoneStatus = raw
            assertEquals(status, camera.checkStatus())
            assertEquals(status, microphone.checkStatus())
        }
    }

    @Test
    fun unknown_native_status_is_never_granted() {
        val bridge = Bridge().apply { cameraStatus = -1; microphoneStatus = 99 }
        assertEquals(GrantStatus.NOT_DETERMINED, AVMediaPermissionHandler.camera(bridge).checkStatus())
        assertEquals(GrantStatus.NOT_DETERMINED, AVMediaPermissionHandler.microphone(bridge).checkStatus())
    }

    @Test
    fun camera_request_uses_camera_bridge_off_the_calling_thread() = runTest {
        val caller = Thread.currentThread()
        val bridge = Bridge().apply { cameraStatus = 3; microphoneStatus = 2 }
        assertEquals(GrantStatus.GRANTED, AVMediaPermissionHandler.camera(bridge).request())
        assertNotEquals(caller, bridge.requestThread)
        assertEquals(1, bridge.cameraRequests)
        assertEquals(0, bridge.microphoneRequests)
    }

    @Test
    fun microphone_request_preserves_denial_and_uses_its_own_bridge() = runTest {
        val caller = Thread.currentThread()
        val bridge = Bridge().apply { cameraStatus = 3; microphoneStatus = 2 }
        assertEquals(GrantStatus.DENIED_ALWAYS, AVMediaPermissionHandler.microphone(bridge).request())
        assertNotEquals(caller, bridge.requestThread)
        assertEquals(0, bridge.cameraRequests)
        assertEquals(1, bridge.microphoneRequests)
    }
}
