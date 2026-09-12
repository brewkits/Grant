package dev.brewkits.grant.impl

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for `AppGrant.USE_FULL_SCREEN_INTENT`, the same special-app-access shape as
 * `SCHEDULE_EXACT_ALARM` (see `ExactAlarmRequestTest`): a normal (install-time) permission
 * through API 33, turned into a special-access one on API 34+ with no `requestPermissions()`
 * dialog at all. The platform's real request flow is the dedicated
 * `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` screen, checked via
 * `NotificationManager.canUseFullScreenIntent()`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FullScreenIntentRequestTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
    }

    private fun nextStartedIntentAction(): String? =
        shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .nextStartedActivity
            ?.action

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `request opens the full-screen-intent settings screen instead of silently doing nothing`() = runBlocking {
        delegate.request(AppGrant.USE_FULL_SCREEN_INTENT)

        val action = nextStartedIntentAction()
        assertNotNull(
            action,
            "request(USE_FULL_SCREEN_INTENT) must start an Activity; starting nothing is the " +
                "silent no-op this test exists to prevent",
        )
        assertEquals(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            action,
            "must open the full-screen-notifications screen, not the app-details page — the " +
                "toggle does not exist on app details",
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `request is a no-op below API 34 where the permission is normal, not special access`() = runBlocking {
        delegate.request(AppGrant.USE_FULL_SCREEN_INTENT)

        assertNull(
            nextStartedIntentAction(),
            "nothing should be launched on API 33 — USE_FULL_SCREEN_INTENT is install-time " +
                "there, not gated at runtime",
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `checkStatus reports GRANTED below API 34 without any OS grant`() = runBlocking {
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.USE_FULL_SCREEN_INTENT))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `checkStatus reports NOT_DETERMINED on API 34+ before any request`() = runBlocking {
        // Robolectric's NotificationManager shadow returns false for canUseFullScreenIntent()
        // (no setter exists in 4.16.1 to flip it true — same limitation ExactAlarmRequestTest
        // documents for AlarmManager.canScheduleExactAlarms()).
        assertEquals(GrantStatus.NOT_DETERMINED, delegate.checkStatus(AppGrant.USE_FULL_SCREEN_INTENT))
    }

    /**
     * DENIED, never DENIED_ALWAYS — same reasoning as SCHEDULE_EXACT_ALARM: special app access
     * has no permanent-denial state, so the rationale path (re-request re-opens the same
     * screen) must stay open rather than routing to the settings-guide's app-details page.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `checkStatus reports DENIED, not DENIED_ALWAYS, after a request when the toggle is still off`() = runBlocking {
        delegate.request(AppGrant.USE_FULL_SCREEN_INTENT)

        assertEquals(GrantStatus.DENIED, delegate.checkStatus(AppGrant.USE_FULL_SCREEN_INTENT))
    }
}
