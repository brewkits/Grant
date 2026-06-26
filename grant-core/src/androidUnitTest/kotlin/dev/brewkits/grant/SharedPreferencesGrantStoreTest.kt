package dev.brewkits.grant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [SharedPreferencesGrantStore].
 *
 * The defining behavior — and the reason this store exists — is that the request-history
 * flags survive being read back by a *different* store instance, which is what happens
 * after process death and restart. A fresh instance pointed at the same app context must
 * still report a previously requested permission as requested. The in-memory status cache,
 * by contrast, must NOT survive across instances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SharedPreferencesGrantStoreTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Each test starts from a clean slate regardless of run order.
        SharedPreferencesGrantStore(context).clear()
    }

    @Test
    fun `setRequested persists across a new store instance (simulates process restart)`() {
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

        // A brand-new instance models the same app after its process was killed and restarted.
        val afterRestart = SharedPreferencesGrantStore(context)

        assertTrue(afterRestart.isRequestedBefore(AppGrant.CAMERA))
    }

    @Test
    fun `unrequested permission is reported as not requested`() {
        val store = SharedPreferencesGrantStore(context)

        assertFalse(store.isRequestedBefore(AppGrant.CAMERA))
    }

    @Test
    fun `raw permission request history persists across a new store instance`() {
        val identifier = "android.permission.CUSTOM"
        SharedPreferencesGrantStore(context).markRawPermissionRequested(identifier)

        val afterRestart = SharedPreferencesGrantStore(context)

        assertTrue(afterRestart.isRawPermissionRequested(identifier))
        assertFalse(afterRestart.isRawPermissionRequested("android.permission.OTHER"))
    }

    @Test
    fun `status cache is session-only and does not persist across instances`() {
        val store = SharedPreferencesGrantStore(context)
        store.setStatus(AppGrant.CAMERA, GrantStatus.DENIED)

        // Live instance still sees it within the session.
        assertEquals(GrantStatus.DENIED, store.getStatus(AppGrant.CAMERA))

        // The OS is the source of truth for the live status, so it must not be persisted.
        val afterRestart = SharedPreferencesGrantStore(context)
        assertNull(afterRestart.getStatus(AppGrant.CAMERA))
    }

    @Test
    fun `clear removes all persisted request history`() {
        val store = SharedPreferencesGrantStore(context)
        store.setRequested(AppGrant.CAMERA)
        store.markRawPermissionRequested("android.permission.CUSTOM")

        store.clear()

        val afterRestart = SharedPreferencesGrantStore(context)
        assertFalse(afterRestart.isRequestedBefore(AppGrant.CAMERA))
        assertFalse(afterRestart.isRawPermissionRequested("android.permission.CUSTOM"))
    }

    @Test
    fun `per-permission clear only removes the targeted permission`() {
        val store = SharedPreferencesGrantStore(context)
        store.setRequested(AppGrant.CAMERA)
        store.setRequested(AppGrant.MICROPHONE)

        store.clear(AppGrant.CAMERA)

        val afterRestart = SharedPreferencesGrantStore(context)
        assertFalse(afterRestart.isRequestedBefore(AppGrant.CAMERA))
        assertTrue(afterRestart.isRequestedBefore(AppGrant.MICROPHONE))
    }
}
