package dev.brewkits.grant.desktop

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Probe #1 from the Tier 2 review (ROADMAP.md v2.6.0): before trusting any other reading
 * through this bridge, confirm the JNA/Kotlin-Native-dylib ABI itself is sound by reading a
 * real `AVAuthorizationStatus` and checking it lands in the documented 0-3 range, not a
 * garbage value from a miscompiled call signature.
 *
 * **These tests branch on the host rather than assuming macOS/arm64**, because the suite runs
 * on Linux in `ci.yml` (`./gradlew allTests`) and could run on an Intel Mac. An earlier version
 * asserted the dylib always loads and would have turned every Linux CI run red. The non-macOS
 * branch is not a skip — it pins the honest-degradation contract: no bundled slice for this
 * host means [NativeBridgeLoader.library] is `null`, which is what makes `grant-core`'s
 * delegate report `DENIED_ALWAYS` instead of guessing.
 *
 * **What these tests deliberately do NOT prove**: that a *request* works, or that a reading
 * reflects this app's own TCC record. A Gradle test JVM has no app bundle and no code-signing
 * identity of its own, so macOS attributes its permission state to whatever launched it — in
 * practice the Gradle daemon's, or the terminal's. That is why the value here is only range-
 * checked, never compared against an expected permission state. The identity-sensitive half is
 * verified through `desktop-harness`'s packaged `.app` instead; see its `build.gradle.kts`.
 */
class NativeBridgeAbiProbeTest {

    private val isMacArm64: Boolean = run {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val arch = System.getProperty("os.arch")?.lowercase().orEmpty()
        os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"))
    }

    @Test
    fun bridge_loads_on_macos_arm64_and_is_absent_everywhere_else() {
        if (isMacArm64) {
            assertNotNull(NativeBridgeLoader.library, "expected the bundled dylib to load on macOS/aarch64")
        } else {
            assertNull(
                NativeBridgeLoader.library,
                "no dylib slice is bundled for this host, so the loader must report null " +
                    "(the honest-degradation path) rather than half-loading something",
            )
        }
    }

    @Test
    fun camera_status_returns_a_real_authorization_status_not_garbage() {
        val bridge = NativeBridgeLoader.library ?: return
        val status = bridge.grant_camera_status()
        assertTrue(status in 0..3, "AVAuthorizationStatus must be 0-3, got $status — likely an ABI mismatch, not a real reading")
    }

    @Test
    fun microphone_status_returns_a_real_authorization_status_not_garbage() {
        val bridge = NativeBridgeLoader.library ?: return
        val status = bridge.grant_microphone_status()
        assertTrue(status in 0..3, "AVAuthorizationStatus must be 0-3, got $status — likely an ABI mismatch, not a real reading")
    }
}
