package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GrantHandlerEventTest {

    private lateinit var mockGrantManager: FakeGrantManager
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        mockGrantManager = FakeGrantManager()
        testScope = TestScope(StandardTestDispatcher())
    }

    private class TestEventListener : GrantEventListener {
        val requested = mutableListOf<Pair<GrantPermission, GrantStatus>>()
        val granted = mutableListOf<Pair<GrantPermission, GrantStatus>>()
        val denied = mutableListOf<Pair<GrantPermission, GrantStatus>>()
        var rationaleShownCount = 0
        var settingsGuideShownCount = 0
        var settingsOpenedCount = 0

        override fun onRequested(grant: GrantPermission, status: GrantStatus) {
            requested.add(grant to status)
        }

        override fun onGranted(grant: GrantPermission, status: GrantStatus) {
            granted.add(grant to status)
        }

        override fun onDenied(grant: GrantPermission, status: GrantStatus) {
            denied.add(grant to status)
        }

        override fun onRationaleShown(grant: GrantPermission) {
            rationaleShownCount++
        }

        override fun onSettingsGuideShown(grant: GrantPermission) {
            settingsGuideShownCount++
        }

        override fun onSettingsOpened(grant: GrantPermission) {
            settingsOpenedCount++
        }
    }

    @Test
    fun `event listener receives onRequested and onGranted when permission is immediately granted`() = testScope.runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        
        val listener = TestEventListener()
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this, eventListener = listener)

        handler.request {}
        advanceUntilIdle()

        assertEquals(1, listener.requested.size)
        assertEquals(AppGrant.CAMERA to GrantStatus.NOT_DETERMINED, listener.requested.first())

        assertEquals(1, listener.granted.size)
        assertEquals(AppGrant.CAMERA to GrantStatus.GRANTED, listener.granted.first())

        assertTrue(listener.denied.isEmpty())
        assertEquals(0, listener.rationaleShownCount)
        assertEquals(0, listener.settingsGuideShownCount)
    }

    @Test
    fun `event listener receives onRationaleShown on soft denial`() = testScope.runTest {
        assumeRationaleSupported {
            mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
            mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED)
            
            val listener = TestEventListener()
            val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this, eventListener = listener)

            handler.request {}
            advanceUntilIdle()

            assertEquals(1, listener.requested.size)
            assertEquals(1, listener.rationaleShownCount)
        }
    }
    
    @Test
    fun `event listener receives onSettingsGuideShown and onSettingsOpened on permanent denial`() = testScope.runTest {
        mockGrantManager.setStatus(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        mockGrantManager.setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)
        
        val listener = TestEventListener()
        val handler = GrantHandler(mockGrantManager, AppGrant.CAMERA, this, eventListener = listener)

        handler.request {}
        advanceUntilIdle()

        assertEquals(1, listener.requested.size)
        assertEquals(1, listener.settingsGuideShownCount)
        assertEquals(0, listener.rationaleShownCount)
        
        handler.onSettingsConfirmed()
        advanceUntilIdle()
        
        assertEquals(1, listener.settingsOpenedCount)
    }
}
