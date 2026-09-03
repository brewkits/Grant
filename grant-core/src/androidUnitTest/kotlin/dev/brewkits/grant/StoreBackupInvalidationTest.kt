package dev.brewkits.grant

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SharedPreferencesGrantStore] must not inherit request history from another installation.
 *
 * The history answers "have we asked for this permission before?", which is what separates
 * a never-requested permission from a permanently denied one after process death (Issue
 * #55). Restoring it onto a fresh install makes Grant believe it has already asked for
 * permissions the OS considers untouched — the user is shown a settings guide instead of
 * the system dialog.
 *
 * This used to be prevented by `android:fullBackupContent` / `android:dataExtractionRules`
 * on the library manifest. Those were removed in 2.4.1: a library cannot set `<application>`
 * attributes without colliding with apps that declare their own backup rules, and an app
 * resolving that collision with `tools:replace` silently dropped the exclusion anyway.
 *
 * The store now defends itself instead, by stamping the history with the install identity
 * and clearing it when that identity does not match — independent of any app's manifest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class StoreBackupInvalidationTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Test
    fun `history survives a reopen within the same installation`() {
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

        // A new instance models a process restart — the history must NOT be cleared here,
        // which is the entire reason the persistent store exists.
        val reopened = SharedPreferencesGrantStore(context)
        assertTrue(
            reopened.isRequestedBefore(AppGrant.CAMERA),
            "Request history must survive process death within one installation",
        )
    }

    @Test
    fun `history is dropped when the install identity differs`() {
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)

        // Simulate a cloud-backup restore or device transfer: the prefs arrive carrying the
        // identity of the installation they were written on.
        prefs().edit().putLong(KEY_INSTALL_ID, 1L).commit()

        val restored = SharedPreferencesGrantStore(context)
        assertFalse(
            restored.isRequestedBefore(AppGrant.CAMERA),
            "History restored from another installation must be discarded",
        )
    }

    @Test
    fun `history is dropped when it predates the identity stamp`() {
        // Data written by a build older than this mechanism has history but no stamp, and
        // may equally have arrived by restore. Treat it as untrusted.
        SharedPreferencesGrantStore(context).setRequested(AppGrant.CAMERA)
        prefs().edit().remove(KEY_INSTALL_ID).commit()

        val legacy = SharedPreferencesGrantStore(context)
        assertFalse(
            legacy.isRequestedBefore(AppGrant.CAMERA),
            "Unstamped history cannot be proven local and must be discarded",
        )
    }

    @Test
    fun `raw permission history is invalidated alongside AppGrant history`() {
        SharedPreferencesGrantStore(context).markRawPermissionRequested("custom.permission.X")
        prefs().edit().putLong(KEY_INSTALL_ID, 1L).commit()

        val restored = SharedPreferencesGrantStore(context)
        assertFalse(
            restored.isRawPermissionRequested("custom.permission.X"),
            "RawPermission history must be invalidated on restore too",
        )
    }

    private companion object {
        const val PREFS = "grant_request_history"
        const val KEY_INSTALL_ID = "install_identity"
    }
}
