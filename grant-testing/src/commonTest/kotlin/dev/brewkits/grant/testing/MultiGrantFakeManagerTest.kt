package dev.brewkits.grant.testing

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultiGrantFakeManagerTest {

    @Test
    fun `request updates status so a following checkStatus sees the new state`() = runTest {
        val manager = MultiGrantFakeManager()
        manager.setStatus(AppGrant.CAMERA, GrantStatus.NOT_DETERMINED)
        manager.setRequestResult(AppGrant.CAMERA, GrantStatus.GRANTED)

        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.CAMERA))
        manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
    }

    @Test
    fun `unconfigured checkStatus defaults to NOT_DETERMINED and unconfigured request defaults to GRANTED`() = runTest {
        val manager = MultiGrantFakeManager()

        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.MICROPHONE))
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.MICROPHONE))
    }

    @Test
    fun `configure sets both the pre-request status and the request outcome`() = runTest {
        val manager = MultiGrantFakeManager()
        manager.configure(AppGrant.CAMERA, status = GrantStatus.DENIED, requestResult = GrantStatus.DENIED_ALWAYS)

        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.request(AppGrant.CAMERA))
    }

    @Test
    fun `isRequestCalled and requestCount track every resolved request`() = runTest {
        val manager = MultiGrantFakeManager()

        assertTrue(!manager.isRequestCalled(AppGrant.CAMERA))
        manager.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE))

        assertTrue(manager.isRequestCalled(AppGrant.CAMERA))
        assertTrue(manager.isRequestCalled(AppGrant.MICROPHONE))
        assertEquals(2, manager.requestCount)
    }

    @Test
    fun `openSettings is recorded`() {
        val manager = MultiGrantFakeManager()
        manager.openSettings()

        assertTrue(manager.openSettingsCalled)
    }
}
