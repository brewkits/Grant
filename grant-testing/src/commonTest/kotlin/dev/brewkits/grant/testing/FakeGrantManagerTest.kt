package dev.brewkits.grant.testing

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantLauncher
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakeGrantManagerTest {

    @Test
    fun `defaults apply when no override is configured`() = runTest {
        val manager = FakeGrantManager()

        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.GRANTED, manager.request(AppGrant.CAMERA))
    }

    @Test
    fun `constructor defaults can be overridden`() = runTest {
        val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED, mockRequestResult = GrantStatus.DENIED_ALWAYS)

        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED_ALWAYS, manager.request(AppGrant.CAMERA))
    }

    @Test
    fun `configure overrides the default for one permission only`() = runTest {
        val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED)
        manager.configure(AppGrant.CAMERA, status = GrantStatus.GRANTED)

        assertEquals(GrantStatus.GRANTED, manager.checkStatus(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED, manager.checkStatus(AppGrant.MICROPHONE))
    }

    @Test
    fun `setResult is an alias for setRequestResult`() = runTest {
        val manager = FakeGrantManager()
        manager.setResult(AppGrant.CAMERA, GrantStatus.DENIED_ALWAYS)

        assertEquals(GrantStatus.DENIED_ALWAYS, manager.request(AppGrant.CAMERA))
    }

    @Test
    fun `request tracks every call in order including duplicates`() = runTest {
        val manager = FakeGrantManager()
        manager.request(AppGrant.CAMERA)
        manager.request(AppGrant.MICROPHONE)
        manager.request(AppGrant.CAMERA)

        assertTrue(manager.requestCalled)
        assertEquals(listOf<GrantPermission>(AppGrant.CAMERA, AppGrant.MICROPHONE, AppGrant.CAMERA), manager.requestedGrants)
    }

    @Test
    fun `batch request resolves every permission and records all of them`() = runTest {
        val manager = FakeGrantManager()
        manager.configure(AppGrant.CAMERA, status = GrantStatus.GRANTED)
        manager.configure(AppGrant.MICROPHONE, status = GrantStatus.DENIED)

        val results = manager.request(listOf(AppGrant.CAMERA, AppGrant.MICROPHONE))

        assertEquals(GrantStatus.GRANTED, results[AppGrant.CAMERA])
        assertEquals(GrantStatus.DENIED, results[AppGrant.MICROPHONE])
        assertEquals(listOf<GrantPermission>(AppGrant.CAMERA, AppGrant.MICROPHONE), manager.requestedGrants)
    }

    @Test
    fun `openSettings and setLauncher are recorded`() {
        val manager = FakeGrantManager()
        manager.openSettings()
        val launcher = object : GrantLauncher {
            override fun launch(permissions: List<String>, onResult: (Map<String, Boolean>) -> Unit) {
                onResult(emptyMap())
            }
        }
        manager.setLauncher(launcher)

        assertTrue(manager.openSettingsCalled)
        assertEquals(launcher, manager.capturedLauncher)
    }

    @Test
    fun `shouldThrow makes checkStatus and request throw instead of returning`() = runTest {
        val manager = FakeGrantManager()
        manager.shouldThrow = IllegalStateException("simulated platform failure")

        assertFailsWith<IllegalStateException> { manager.checkStatus(AppGrant.CAMERA) }
        assertFailsWith<IllegalStateException> { manager.request(AppGrant.CAMERA) }
    }

    @Test
    fun `reset clears overrides and call records and restores constructor defaults`() = runTest {
        val manager = FakeGrantManager(mockStatus = GrantStatus.DENIED)
        manager.configure(AppGrant.CAMERA, status = GrantStatus.GRANTED)
        manager.request(AppGrant.CAMERA)
        manager.openSettings()

        manager.reset()

        assertEquals(GrantStatus.NOT_DETERMINED, manager.checkStatus(AppGrant.CAMERA))
        assertTrue(manager.requestedGrants.isEmpty())
        assertTrue(!manager.openSettingsCalled)
        assertTrue(!manager.requestCalled)
    }

    @Test
    fun `checkStatusCalls records every check in order`() = runTest {
        val manager = FakeGrantManager()
        manager.checkStatus(AppGrant.CAMERA)
        manager.checkStatus(AppGrant.MICROPHONE)

        assertEquals(listOf<GrantPermission>(AppGrant.CAMERA, AppGrant.MICROPHONE), manager.checkStatusCalls)
    }
}
