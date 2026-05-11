package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GrantHandlerTest {

    private lateinit var mockGrantManager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `multiple rapid requests should only invoke callback once`() = testScope.runTest {
        val delayingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                delay(1000)
                return GrantStatus.GRANTED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
        }
        val handler = GrantHandler(delayingManager, AppGrant.CAMERA, this)

        var invocationCount = 0

        handler.request { invocationCount++ }
        runCurrent()
        handler.request { invocationCount++ }
        handler.request { invocationCount++ }
        advanceUntilIdle()

        assertEquals(1, invocationCount, "Only first callback should be invoked once")
    }

    @Test
    fun `requestSuspend returns correct status`() = testScope.runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)

        val result = handler.requestSuspend()
        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `requestFlow emits correct status`() = testScope.runTest {
        mockGrantManager.mockStatus = GrantStatus.NOT_DETERMINED
        mockGrantManager.mockRequestResult = GrantStatus.DENIED
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)

        val result = handler.requestFlow().first()
        assertEquals(GrantStatus.DENIED, result)
    }

    @Test
    fun `requestWithCustomUi invokes custom rationale callback`() = testScope.runTest {
        assumeRationaleSupported {
            mockGrantManager.mockStatus = GrantStatus.DENIED
            mockGrantManager.mockRequestResult = GrantStatus.DENIED
            val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)

            var rationaleShown = false
            var settingsShown = false
            var granted = false

            handler.requestWithCustomUi(
                rationaleMessage = "Custom Rationale",
                onShowRationale = { msg, onConfirm, _ ->
                    rationaleShown = true
                    assertEquals("Custom Rationale", msg)
                    onConfirm() // Must invoke to unsuspend
                },
                onShowSettings = { _, _, _ -> settingsShown = true },
                onGranted = { granted = true }
            )
            advanceUntilIdle()

            assertTrue(rationaleShown)
            assertFalse(settingsShown)
            assertFalse(granted)
        }
    }

    @Test
    fun `requestWithCustomUi invokes custom settings callback`() = testScope.runTest {
        assumeRationaleSupported {
            mockGrantManager.mockStatus = GrantStatus.DENIED_ALWAYS
            mockGrantManager.mockRequestResult = GrantStatus.DENIED_ALWAYS
            val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this)

            var rationaleShown = false
            var settingsShown = false

            handler.requestWithCustomUi(
                settingsMessage = "Custom Settings",
                onShowRationale = { _, _, _ -> rationaleShown = true },
                onShowSettings = { msg, _, onDismiss ->
                    settingsShown = true
                    assertEquals("Custom Settings", msg)
                    onDismiss() // Must invoke to unsuspend
                },
                onGranted = {}
            )
            advanceUntilIdle()

            assertFalse(rationaleShown)
            assertTrue(settingsShown)
        }
    }

    @Test
    fun `requestSuspend handles exception gracefully and returns last status`() = testScope.runTest {
        val throwingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                return GrantStatus.NOT_DETERMINED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus {
                throw IllegalStateException("Test exception")
            }
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
        }
        val handler = GrantHandler(throwingManager, AppGrant.CAMERA, this)

        val result = handler.requestSuspend()
        assertEquals(GrantStatus.NOT_DETERMINED, result, "Should swallow exception and return last known status")
    }

    @Test
    fun `requestSuspend subsequent concurrent calls return immediately with current status`() = testScope.runTest {
        val delayingManager = object : GrantManager {
            override suspend fun checkStatus(grant: GrantPermission): GrantStatus {
                delay(1000)
                return GrantStatus.NOT_DETERMINED
            }
            override suspend fun request(grant: GrantPermission): GrantStatus = GrantStatus.GRANTED
            override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> = emptyMap()
            override fun openSettings() {}
        }
        val handler = GrantHandler(delayingManager, AppGrant.CAMERA, this)

        // Launch first request
        val deferred1 = async { handler.requestSuspend() }
        runCurrent() // Let the first coroutine acquire the mutex and delay

        // Launch second request while first is holding the mutex
        val deferred2 = async { handler.requestSuspend() }

        // Second one should immediately return the initial status (NOT_DETERMINED)
        // because it fails to acquire the tryLock() and cont.resumes(currentStatus).
        val result2 = deferred2.await()
        assertEquals(GrantStatus.NOT_DETERMINED, result2)

        advanceUntilIdle()
        // First one eventually finishes with GRANTED (since the mock returns GRANTED from request)
        // Actually wait, our delayingManager returns NOT_DETERMINED from checkStatus, then requests.
        val result1 = deferred1.await()
        assertEquals(GrantStatus.GRANTED, result1)
    }
}
