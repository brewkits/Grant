package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.GrantPermission
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DefaultGrantManagerTest {

    private lateinit var delegate: PlatformGrantDelegate
    private lateinit var manager: DefaultGrantManager

    @BeforeTest
    fun setup() {
        delegate = mockk(relaxed = true)
        manager = DefaultGrantManager(delegate)
    }

    @Test
    fun `checkStatus delegates to platform delegate`() = runTest {
        coEvery { delegate.checkStatus(AppGrant.CAMERA) } returns GrantStatus.GRANTED
        val status = manager.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, status)
        coVerify { delegate.checkStatus(AppGrant.CAMERA) }
    }

    @Test
    fun `request delegates to platform delegate`() = runTest {
        coEvery { delegate.request(AppGrant.CAMERA) } returns GrantStatus.GRANTED
        val status = manager.request(AppGrant.CAMERA)
        assertEquals(GrantStatus.GRANTED, status)
        coVerify { delegate.request(AppGrant.CAMERA) }
    }

    @Test
    fun `request list delegates to platform delegate`() = runTest {
        val grants = listOf(AppGrant.CAMERA)
        coEvery { delegate.request(grants) } returns mapOf(AppGrant.CAMERA to GrantStatus.GRANTED)
        val result = manager.request(grants)
        assertEquals(GrantStatus.GRANTED, result[AppGrant.CAMERA])
    }

    @Test
    fun `openSettings delegates to platform delegate`() {
        manager.openSettings()
        verify { delegate.openSettings() }
    }
}
