package dev.brewkits.grant.testing

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeGrantStoreTest {

    @Test
    fun `getStatus is null until setStatus is called`() {
        val store = FakeGrantStore()

        assertNull(store.getStatus(AppGrant.CAMERA))
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        assertEquals(GrantStatus.GRANTED, store.getStatus(AppGrant.CAMERA))
    }

    @Test
    fun `isRequestedBefore reflects setRequested`() {
        val store = FakeGrantStore()

        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
        store.setRequested(AppGrant.CAMERA)
        assertTrue(store.isRequestedBefore(AppGrant.CAMERA))
    }

    @Test
    fun `clear for one grant removes only that grant's state`() {
        val store = FakeGrantStore()
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        store.setRequested(AppGrant.CAMERA)
        store.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        store.clear(AppGrant.CAMERA)

        assertNull(store.getStatus(AppGrant.CAMERA))
        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
        assertEquals(GrantStatus.DENIED, store.getStatus(AppGrant.MICROPHONE))
    }

    @Test
    fun `clear with no argument removes every grant's state`() {
        val store = FakeGrantStore()
        store.setStatus(AppGrant.CAMERA, GrantStatus.GRANTED)
        store.setStatus(AppGrant.MICROPHONE, GrantStatus.DENIED)

        store.clear()

        assertNull(store.getStatus(AppGrant.CAMERA))
        assertNull(store.getStatus(AppGrant.MICROPHONE))
    }

    @Test
    fun `raw permission requested-tracking is independent of AppGrant tracking`() {
        val store = FakeGrantStore()

        assertFalse(store.isRawPermissionRequested("CUSTOM_SENSOR"))
        store.markRawPermissionRequested("CUSTOM_SENSOR")
        assertTrue(store.isRawPermissionRequested("CUSTOM_SENSOR"))
        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
    }
}
