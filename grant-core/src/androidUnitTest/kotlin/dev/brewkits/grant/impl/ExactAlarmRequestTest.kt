package dev.brewkits.grant.impl

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
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
 * Regression tests for `request(AppGrant.SCHEDULE_EXACT_ALARM)`.
 *
 * **The defect these pin down:** SCHEDULE_EXACT_ALARM is *special app access*, not a runtime
 * permission — its protectionLevel is `signature|privileged|appop`. Proven on a real Android 17
 * device against this project's own demo package:
 *
 * ```
 * $ adb shell pm grant dev.brewkits.grant.demo android.permission.SCHEDULE_EXACT_ALARM
 * SecurityException: ... is not a changeable permission type
 * $ adb shell pm grant dev.brewkits.grant.demo android.permission.CAMERA
 * (succeeds)
 * ```
 *
 * `requestPermissions()` therefore shows no dialog and can never grant it. Before the fix,
 * `request()` fell through to exactly that call: nothing happened, the store recorded the
 * permission as asked, and the next `checkStatus()` escalated it to DENIED_ALWAYS — sending the
 * user to a Settings page that does not contain the toggle. A silent no-op ending in a dead
 * end, which is the failure class this library exists to prevent (Issue #55).
 *
 * The platform's real request flow for special app access is the dedicated Settings screen, so
 * that is what `request()` must open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExactAlarmRequestTest {

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
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `request opens the exact-alarm settings screen instead of silently doing nothing`() = runBlocking {
        delegate.request(AppGrant.SCHEDULE_EXACT_ALARM)

        val action = nextStartedIntentAction()
        assertNotNull(
            action,
            "request(SCHEDULE_EXACT_ALARM) must start an Activity; starting nothing is the " +
                "silent no-op this test exists to prevent",
        )
        assertEquals(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            action,
            "must open the Alarms & reminders screen, not the app-details page — the toggle " +
                "does not exist on app details",
        )
    }

    /**
     * Below API 31 the permission does not exist and `canScheduleExactAlarms()` has no
     * counterpart, so the status override reports GRANTED and `request()` must return before
     * doing anything at all — no Settings screen, no prompt.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `request is a no-op below API 31 where the permission does not exist`() = runBlocking {
        delegate.request(AppGrant.SCHEDULE_EXACT_ALARM)

        assertNull(
            nextStartedIntentAction(),
            "nothing should be launched on API 30 — exact-alarm access is not gated there",
        )
    }
}
