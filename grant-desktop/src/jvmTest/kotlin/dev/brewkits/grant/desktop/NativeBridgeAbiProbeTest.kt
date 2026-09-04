package dev.brewkits.grant.desktop

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Probe #1 from the Tier 2 review (ROADMAP.md v2.6.0): before trusting any other reading
 * through this bridge, confirm the JNA/Kotlin-Native-dylib ABI itself is sound by reading a
 * real `AVAuthorizationStatus` and checking it lands in the documented 0-3 range, not a
 * garbage value from a miscompiled call signature.
 *
 * This test needs no app bundle/Info.plist — it only *reads* a status, which macOS allows
 * for any process (unlike *requesting*, which needs a real TCC identity — see the Compose
 * Desktop harness this tier's manual verification uses instead of a unit test for that part).
 */
class NativeBridgeAbiProbeTest {

    @Test
    fun bridge_loads_on_this_machine() {
        assertNotNull(NativeBridgeLoader.library, "expected the dylib to load on macOS/aarch64")
    }

    @Test
    fun camera_status_returns_a_real_authorization_status_not_garbage() {
        val bridge = NativeBridgeLoader.library ?: error("bridge did not load")
        val status = bridge.grant_camera_status()
        assertTrue(status in 0..3, "AVAuthorizationStatus must be 0-3, got $status — likely an ABI mismatch, not a real reading")
    }
}
