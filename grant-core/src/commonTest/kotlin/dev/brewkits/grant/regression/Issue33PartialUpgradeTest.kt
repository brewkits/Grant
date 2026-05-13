package dev.brewkits.grant.regression

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.fakes.FakeGrantManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Issue33PartialUpgradeTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `LOCATION_ALWAYS PARTIAL_GRANTED should try to request before showing settings guide`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.PARTIAL_GRANTED
            mockRequestResult = GrantStatus.GRANTED // Simulate user granting it in the OS dialog/settings
        }
        val handler = GrantHandler(manager, AppGrant.LOCATION_ALWAYS, this)

        handler.request { }
        advanceUntilIdle()

        // If it works as I expect (OS dialog first), then requestCalled should be true.
        // Current behavior (bug): requestCalled is false, and it immediately shows settings guide.
        assertTrue(manager.requestCalled, "grantManager.request() should be called even if PARTIAL_GRANTED to allow background upgrade")
        
        val state = handler.state.value
        assertFalse(state.showSettingsGuide, "Settings guide should NOT show up if request was successful")
        assertEquals(GrantStatus.GRANTED, handler.status.value)
    }
}
