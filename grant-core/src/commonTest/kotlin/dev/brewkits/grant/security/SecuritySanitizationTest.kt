package dev.brewkits.grant.security

import dev.brewkits.grant.*
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Security tests for Identity Sanitization and Store Privacy.
 *
 * Verifies that the library prevents permission identity clashing and
 * maintains strict isolation between different permission types.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecuritySanitizationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val mockGrantManager = FakeGrantManager()
    private val store = InMemoryGrantStore()

    @Test
    fun `Security - identity clashing protection`() {
        // AppGrant.CAMERA has identifier "CAMERA"
        val camera = AppGrant.CAMERA
        
        // Malicious attempt to create a RawPermission with the same ID as a built-in one
        val maliciousRaw = RawPermission(
            identifier = "CAMERA",
            androidPermissions = listOf("android.permission.INJECT_EVENTS"), // Very dangerous
            iosUsageKey = "NSCameraUsageDescription"
        )
        
        // Verify they are distinct objects
        // In Kotlin, comparing incompatible types with === might cause a compiler error or always be false.
        // We can cast to Any to satisfy the compiler or use assertNotSame.
        assertNotSame(camera as Any, maliciousRaw as Any, "RawPermission should not be the same instance as AppGrant")
        assertNotEquals(camera as Any, maliciousRaw as Any, "RawPermission should not be equal to AppGrant")
    }

    @Test
    fun `Security - Store isolation`() = runTest {
        // Marks camera as requested
        store.setRequested(AppGrant.CAMERA)
        
        // Malicious raw permission claiming to be camera ID
        val maliciousRaw = RawPermission("CAMERA", emptyList(), null)
        
        // Verify that setting state for one DOES NOT share requested state.
        // Isolation is maintained because Store uses different maps for AppGrant and RawPermission.
        assertFalse(store.isRequestedBefore(maliciousRaw), "Security: RawPermission state must be isolated from AppGrant state")
    }

    @Test
    fun `Security - InMemoryStore privacy`() {
        // Verify store is truly in-memory (starts fresh for every test instance)
        val freshStore = InMemoryGrantStore()
        assertFalse(freshStore.isRequestedBefore(AppGrant.CAMERA))
        
        freshStore.setRequested(AppGrant.CAMERA)
        assertTrue(freshStore.isRequestedBefore(AppGrant.CAMERA))
    }
}
