package dev.brewkits.grant

import dev.brewkits.grant.testing.FakeGrantManager
import dev.brewkits.grant.testing.MultiGrantFakeManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D6: the settings guide must tell the platform WHICH permission it is sending the user to fix
 * (`openSettings(grant)`), since desktop has separate Camera and Microphone privacy pages and the
 * no-argument call can only ever open one of them.
 */
class OpenSettingsForGrantTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    @Test
    fun `GrantHandler settings guide opens the settings of its own grant`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED_ALWAYS
            mockRequestResult = GrantStatus.DENIED_ALWAYS
        }
        val handler = GrantHandler(manager, AppGrant.MICROPHONE, this)

        handler.request { }
        advanceUntilIdle()
        handler.onSettingsConfirmed()
        advanceUntilIdle()

        assertEquals(AppGrant.MICROPHONE, manager.lastOpenSettingsGrant)
        assertTrue(manager.openSettingsCalled, "the no-argument path must still be reached")
    }

    @Test
    fun `GrantGroupHandler settings guide opens the settings of the grant it is showing`() = testScope.runTest {
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)
            setRequestResult(AppGrant.MICROPHONE, GrantStatus.DENIED_ALWAYS)
        }
        val opened = mutableListOf<GrantPermission>()
        val handler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.MICROPHONE),
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onSettingsOpened(grant: GrantPermission) {
                    opened += grant
                }
            },
        )
        advanceUntilIdle()

        handler.request { }
        advanceUntilIdle()
        handler.onSettingsConfirmed()
        advanceUntilIdle()

        assertEquals(AppGrant.MICROPHONE, manager.lastOpenSettingsGrant)
        // Pre-existing defect fixed alongside: resetState() cleared currentGrant before it was
        // read, so onSettingsOpened never fired for the group handler.
        assertEquals(listOf<GrantPermission>(AppGrant.MICROPHONE), opened)
    }
}
