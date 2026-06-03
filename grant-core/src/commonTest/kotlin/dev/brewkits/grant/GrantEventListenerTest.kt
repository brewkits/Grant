package dev.brewkits.grant

import dev.brewkits.grant.fakes.FakeGrantManager
import dev.brewkits.grant.fakes.MultiGrantFakeManager
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that [GrantEventListener] receives the correct events, in the correct
 * order, with the correct [GrantPermission] and [GrantStatus] parameters.
 *
 * Covers both [GrantHandler] and [GrantGroupHandler] across all six callback
 * functions: onRequested, onGranted, onDenied, onRationaleShown,
 * onSettingsGuideShown, onSettingsOpened.
 */
class GrantEventListenerTest {

    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())
    }

    // ── GrantHandler ──────────────────────────────────────────────────────────

    @Test
    fun `onRequested fires with initial status at start of request`() = testScope.runTest {
        val manager = FakeGrantManager().apply { mockStatus = GrantStatus.DENIED }
        val events = mutableListOf<Pair<String, GrantStatus>>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRequested(grant: GrantPermission, status: GrantStatus) {
                    events += "onRequested" to status
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue(events.any { it.first == "onRequested" && it.second == GrantStatus.DENIED })
    }

    @Test
    fun `onGranted fires with GRANTED status`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.GRANTED
        }
        val events = mutableListOf<Pair<String, GrantStatus>>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                    events += "onGranted" to status
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue(events.any { it.first == "onGranted" && it.second == GrantStatus.GRANTED })
    }

    @Test
    fun `onGranted fires with PARTIAL_GRANTED for non-background permission`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.PARTIAL_GRANTED
        }
        val events = mutableListOf<Pair<String, GrantStatus>>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.GALLERY,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                    events += "onGranted" to status
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue(events.any { it.first == "onGranted" && it.second == GrantStatus.PARTIAL_GRANTED })
    }

    @Test
    fun `onDenied fires when first request returns DENIED and no retry is possible`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest
        // First request DENIED while isFirstRequest=true (OS just denied) → soft dead-end.
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.DENIED
        }
        val events = mutableListOf<Pair<String, GrantStatus>>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onDenied(grant: GrantPermission, status: GrantStatus) {
                    events += "onDenied" to status
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue(events.any { it.first == "onDenied" && it.second == GrantStatus.DENIED })
    }

    @Test
    fun `onRationaleShown fires when rationale dialog is presented`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest
        val manager = FakeGrantManager().apply { mockStatus = GrantStatus.DENIED }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRationaleShown(grant: GrantPermission) {
                    events += "onRationaleShown"
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue("onRationaleShown" in events)
    }

    @Test
    fun `onSettingsGuideShown fires when settings guide dialog is presented`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED
            mockRequestResult = GrantStatus.DENIED
        }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onSettingsGuideShown(grant: GrantPermission) {
                    events += "onSettingsGuideShown"
                }
            },
        )
        if (PlatformConfig.isRationaleSupported) {
            handler.request { }
            advanceUntilIdle()
            handler.onRationaleConfirmed()
            advanceUntilIdle()
        } else {
            handler.request { }
            advanceUntilIdle()
        }
        assertTrue("onSettingsGuideShown" in events)
    }

    @Test
    fun `onSettingsOpened fires when user confirms settings guide`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED_ALWAYS
        }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onSettingsOpened(grant: GrantPermission) {
                    events += "onSettingsOpened"
                }
            },
        )
        // First request shows settings guide (DENIED_ALWAYS, not isFirstRequest)
        handler.request { }
        advanceUntilIdle()
        handler.onSettingsConfirmed()
        advanceUntilIdle()
        assertTrue("onSettingsOpened" in events)
    }

    @Test
    fun `events carry the correct GrantPermission reference`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.GRANTED
        }
        val grants = mutableListOf<GrantPermission>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.MICROPHONE,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRequested(grant: GrantPermission, status: GrantStatus) {
                    grants += grant
                }
                override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                    grants += grant
                }
            },
        )
        handler.request { }
        advanceUntilIdle()
        assertTrue(grants.all { it == AppGrant.MICROPHONE })
        assertEquals(2, grants.size)
    }

    @Test
    fun `requestSuspend also fires onRequested and onGranted`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.GRANTED
        }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRequested(grant: GrantPermission, status: GrantStatus) { events += "onRequested" }
                override fun onGranted(grant: GrantPermission, status: GrantStatus) { events += "onGranted" }
            },
        )
        handler.requestSuspend()
        assertTrue("onRequested" in events)
        assertTrue("onGranted" in events)
    }

    @Test
    fun `requestWithCustomUi fires onRequested and onGranted`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.NOT_DETERMINED
            mockRequestResult = GrantStatus.GRANTED
        }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRequested(grant: GrantPermission, status: GrantStatus) { events += "onRequested" }
                override fun onGranted(grant: GrantPermission, status: GrantStatus) { events += "onGranted" }
            },
        )
        handler.requestWithCustomUi(
            onShowRationale = { _, _, _ -> },
            onShowSettings = { _, _, _ -> },
            onGranted = {},
        )
        advanceUntilIdle()
        assertTrue("onRequested" in events)
        assertTrue("onGranted" in events)
    }

    @Test
    fun `refreshStatus fires onGranted when grant obtained via Settings`() = testScope.runTest {
        val manager = FakeGrantManager().apply {
            mockStatus = GrantStatus.DENIED_ALWAYS
        }
        val events = mutableListOf<String>()
        val handler = GrantHandler(
            grantManager = manager,
            grant = AppGrant.CAMERA,
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onGranted(grant: GrantPermission, status: GrantStatus) { events += "onGranted" }
            },
        )
        // Kick off request so a dialog is showing (so refreshStatus can complete it)
        handler.request { }
        advanceUntilIdle()
        // Simulate user granted permission in Settings
        manager.mockStatus = GrantStatus.GRANTED
        handler.refreshStatus()
        advanceUntilIdle()
        assertTrue("onGranted" in events)
    }

    // ── GrantGroupHandler ─────────────────────────────────────────────────────

    @Test
    fun `GrantGroupHandler onRequested fires for each grant`() = testScope.runTest {
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
            setStatus(AppGrant.MICROPHONE, GrantStatus.NOT_DETERMINED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
            setRequestResult(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        }
        val requestedGrants = mutableListOf<GrantPermission>()
        val group = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA, AppGrant.MICROPHONE),
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRequested(grant: GrantPermission, status: GrantStatus) {
                    requestedGrants += grant
                }
            },
        )
        group.request(onAllGranted = {})
        advanceUntilIdle()
        assertTrue(AppGrant.CAMERA in requestedGrants)
        assertTrue(AppGrant.MICROPHONE in requestedGrants)
    }

    @Test
    fun `GrantGroupHandler onGranted fires per-permission when granted`() = testScope.runTest {
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)
        }
        val grantedGrants = mutableListOf<GrantPermission>()
        val group = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA),
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onGranted(grant: GrantPermission, status: GrantStatus) {
                    grantedGrants += grant
                }
            },
        )
        group.request(onAllGranted = {})
        advanceUntilIdle()
        assertTrue(AppGrant.CAMERA in grantedGrants)
    }

    @Test
    fun `GrantGroupHandler onRationaleShown fires for denied grant`() = testScope.runTest {
        if (!PlatformConfig.isRationaleSupported) return@runTest
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED)
        }
        val events = mutableListOf<String>()
        val group = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA),
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onRationaleShown(grant: GrantPermission) { events += "onRationaleShown" }
            },
        )
        group.request(onAllGranted = {})
        advanceUntilIdle()
        assertTrue("onRationaleShown" in events)
    }

    @Test
    fun `GrantGroupHandler onSettingsGuideShown fires after double denial`() = testScope.runTest {
        val manager = MultiGrantFakeManager().apply {
            setStatus(AppGrant.CAMERA, GrantStatus.DENIED)
            setRequestResult(AppGrant.CAMERA, GrantStatus.DENIED)
        }
        val events = mutableListOf<String>()
        val group = GrantGroupHandler(
            grantManager = manager,
            grants = listOf(AppGrant.CAMERA),
            scope = this,
            eventListener = object : GrantEventListener {
                override fun onSettingsGuideShown(grant: GrantPermission) { events += "onSettingsGuideShown" }
            },
        )
        group.request(onAllGranted = {})
        advanceUntilIdle()
        if (PlatformConfig.isRationaleSupported) {
            group.onRationaleConfirmed()
            advanceUntilIdle()
        }
        assertTrue("onSettingsGuideShown" in events)
    }
}
