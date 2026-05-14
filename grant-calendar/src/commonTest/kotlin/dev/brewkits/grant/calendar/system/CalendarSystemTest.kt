package dev.brewkits.grant.calendar.system

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantGroupHandler
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.calendar.fakes.FakeGrantManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * System tests for Calendar permissions in realistic usage scenarios.
 *
 * - Calendar event creation (CALENDAR full access)
 * - Calendar viewer (READ_CALENDAR read-only)
 * - iOS 17+ partial access upgrade flow
 * - Calendar + Reminders batch permission
 * - Read recovery after DENIED
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarSystemTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var manager: FakeGrantManager

    @BeforeTest
    fun setup() {
        manager = FakeGrantManager()
    }

    @Test
    fun `calendar event creation - CALENDAR full access granted on first request`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        var eventCreationEnabled = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)

        handler.request { eventCreationEnabled = true }
        advanceUntilIdle()

        assertTrue(eventCreationEnabled)
    }

    @Test
    fun `calendar viewer - READ_CALENDAR sufficient and does not over-request CALENDAR`() = testScope.runTest {
        manager.configure(AppGrant.READ_CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        var viewerEnabled = false
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.READ_CALENDAR, scope = testScope)

        handler.request { viewerEnabled = true }
        advanceUntilIdle()

        assertTrue(viewerEnabled)
        assertEquals(listOf<Any>(AppGrant.READ_CALENDAR), manager.requestedGrants,
            "Calendar viewer must use READ_CALENDAR, not CALENDAR")
    }

    @Test
    fun `iOS 17 write-only - user granted Add Events Only and app acknowledges PARTIAL_GRANTED`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)

        handler.request(settingsMessage = "Enable Full Access to read existing events") {}
        advanceUntilIdle()

        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value,
            "Status must reflect write-only access granted on iOS 17")
    }

    @Test
    fun `full access upgrade after write-only - user goes to Settings and grants full`() = testScope.runTest {
        // Start with PARTIAL_GRANTED (write-only on iOS 17+)
        manager.configure(AppGrant.CALENDAR, GrantStatus.PARTIAL_GRANTED, GrantStatus.PARTIAL_GRANTED)
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CALENDAR,
            scope = testScope
        )

        handler.request(settingsMessage = "Enable Full Calendar Access in Settings") {}
        advanceUntilIdle()
        assertEquals(GrantStatus.PARTIAL_GRANTED, handler.status.value)

        // Simulate: user went to Settings and upgraded to Full Access, then returned to app
        manager.configure(AppGrant.CALENDAR, GrantStatus.GRANTED, GrantStatus.GRANTED)
        handler.onSettingsConfirmed()
        advanceUntilIdle()
        // App must call refreshStatus() when user returns from Settings
        handler.refreshStatus()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, handler.status.value,
            "After Settings upgrade + refreshStatus, status must be GRANTED")
    }

    @Test
    fun `calendar + notification batch - both permissions requested together`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)
        manager.configure(AppGrant.NOTIFICATION, GrantStatus.NOT_DETERMINED, GrantStatus.GRANTED)

        val groupHandler = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CALENDAR, AppGrant.NOTIFICATION),
            scope = testScope
        )

        groupHandler.request {}
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.CALENDAR])
        assertEquals(GrantStatus.GRANTED, groupHandler.statuses.value[AppGrant.NOTIFICATION])
    }

    @Test
    fun `requestSuspend CALENDAR returns status directly`() = testScope.runTest {
        manager.mockStatus = GrantStatus.NOT_DETERMINED
        manager.mockRequestResult = GrantStatus.GRANTED
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)

        val result = handler.requestSuspend()
        advanceUntilIdle()

        assertEquals(GrantStatus.GRANTED, result)
    }

    @Test
    fun `calendar blocked - DENIED_ALWAYS shows settings guide`() = testScope.runTest {
        manager.configure(AppGrant.CALENDAR, GrantStatus.DENIED_ALWAYS, GrantStatus.DENIED_ALWAYS)
        val handler = GrantHandler(grantManager = manager, grant = AppGrant.CALENDAR, scope = testScope)

        handler.request(settingsMessage = "Enable Calendar in Settings > Privacy") {}
        advanceUntilIdle()

        val state = handler.state.value
        assertTrue(state.showSettingsGuide)
    }
}
