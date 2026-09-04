package dev.brewkits.grant.impl

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import dev.brewkits.grant.PlatformConfig
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidDeniedAlwaysTest {

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate
    private lateinit var activity: Activity

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        PlatformConfig.activity = activity
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q])
    fun `test DENIED_ALWAYS when rationale is false and requested before`() = runBlocking {
        val app = shadowOf(context as Application)
        app.denyPermissions(android.Manifest.permission.CAMERA)
        
        // Mark as requested to distinguish from NOT_DETERMINED
        store.setRequested(AppGrant.CAMERA)
        
        // In Robolectric, denying a permission normally makes rationale false for fresh tests
        val status = delegate.checkStatus(AppGrant.CAMERA)
        assertEquals(GrantStatus.DENIED_ALWAYS, status)
    }

    /**
     * Expectation deliberately changed from DENIED_ALWAYS to DENIED.
     *
     * SCHEDULE_EXACT_ALARM is special app access, not a runtime permission — its
     * protectionLevel is `signature|privileged|appop` (verified on a real Android 17 device:
     * `pm grant` rejects it with "not a changeable permission type"). There is therefore no
     * permanent-denial state: the toggle stays available in Settings forever, and every
     * request() can reopen that exact screen.
     *
     * Reporting DENIED_ALWAYS drove GrantHandler down the settings-guide path, whose
     * openSettings() lands on the app-details page — which does not contain this toggle, so
     * the user hit a dead end. DENIED drives the rationale path instead, and re-requesting
     * reopens the correct screen. This test previously pinned the broken behaviour.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `test SCHEDULE_EXACT_ALARM reports DENIED not DENIED_ALWAYS`() = runBlocking {
        store.setRequested(AppGrant.SCHEDULE_EXACT_ALARM)

        val status = delegate.checkStatus(AppGrant.SCHEDULE_EXACT_ALARM)
        // Robolectric's AlarmManager shadow returns false for canScheduleExactAlarms().
        assertEquals(GrantStatus.DENIED, status)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `test NOTIFICATION GRANTED logic when enabled`() = runBlocking {
        // Notifications are usually GRANTED by default in Robolectric's NotificationManagerCompat shadow
        val status = delegate.checkStatus(AppGrant.NOTIFICATION)
        assertEquals(GrantStatus.GRANTED, status)
    }
}
