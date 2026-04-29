package dev.brewkits.grant

import kotlin.test.*

class InMemoryGrantStoreTest {

    private lateinit var store: InMemoryGrantStore

    @BeforeTest
    fun setup() {
        store = InMemoryGrantStore()
    }

    @Test
    fun `test status cache`() {
        assertNull(store.getStatus(AppGrant.CAMERA))
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        assertEquals(GrantStatus.GRANTED, store.getStatus(AppGrant.CAMERA))
    }

    @Test
    fun `test requested tracking`() {
        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
        store.setRequested(AppGrant.CAMERA)
        assertTrue(store.isRequestedBefore(AppGrant.CAMERA))
    }

    @Test
    fun `test raw permission tracking`() {
        assertFalse(store.isRawPermissionRequested("TEST"))
        store.markRawPermissionRequested("TEST")
        assertTrue(store.isRawPermissionRequested("TEST"))
    }

    @Test
    fun `test clear all`() {
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        store.setRequested(AppGrant.CAMERA)
        store.markRawPermissionRequested("TEST")
        
        store.clear()
        
        assertNull(store.getStatus(AppGrant.CAMERA))
        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
        assertFalse(store.isRawPermissionRequested("TEST"))
    }

    @Test
    fun `test clear specific grant`() {
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        store.setStatus(AppGrant.MICROPHONE, GrantStatus.GRANTED)
        
        store.clear(AppGrant.CAMERA)
        
        assertNull(store.getStatus(AppGrant.CAMERA))
        assertNotNull(store.getStatus(AppGrant.MICROPHONE))
    }
}
