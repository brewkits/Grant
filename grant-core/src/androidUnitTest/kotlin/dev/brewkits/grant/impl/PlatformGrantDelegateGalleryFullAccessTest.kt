package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.InMemoryGrantStore
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the 2.3.0 gallery full-access misclassification (found via the Lam
 * gallery P0, 2026-07-09).
 *
 * On API 34+, `GALLERY.toAndroidGrants()` includes `READ_MEDIA_VISUAL_USER_SELECTED` so the
 * system dialog offers "Select photos" — but full access must be judged on the REQUIRED
 * permissions only (IMAGES/VIDEO). The OS can grant IMAGES + VIDEO while leaving
 * USER_SELECTED denied (ADB `pm grant`, MDM policy, permission auto-reset edge states);
 * counting USER_SELECTED made `checkStatus` report that fully-granted state as denied, and
 * the store-requested fallback escalated it to DENIED_ALWAYS.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PlatformGrantDelegateGalleryFullAccessTest {

    private companion object {
        const val USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    }

    private lateinit var context: Context
    private lateinit var store: InMemoryGrantStore
    private lateinit var delegate: PlatformGrantDelegate

    @BeforeTest
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = InMemoryGrantStore()
        delegate = PlatformGrantDelegate(context, store)
    }

    private fun grant(vararg permissions: String) {
        Shadows.shadowOf(context as android.app.Application).grantPermissions(*permissions)
    }

    @Test
    fun `IMAGES and VIDEO granted without USER_SELECTED is FULL access`() = runTest {
        grant(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        // The escalation precondition of the original bug: the grant had been requested
        // before, so the misclassified "not all granted" fell through to DENIED_ALWAYS.
        store.setRequested(AppGrant.GALLERY)

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.GALLERY))
    }

    @Test
    fun `all three media permissions granted is FULL access`() = runTest {
        grant(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            USER_SELECTED,
        )

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.GALLERY))
    }

    @Test
    fun `USER_SELECTED alone is PARTIAL access`() = runTest {
        grant(USER_SELECTED)

        assertEquals(GrantStatus.PARTIAL_GRANTED, delegate.checkStatus(AppGrant.GALLERY))
    }

    @Test
    fun `nothing granted stays NOT_DETERMINED before any request`() = runTest {
        assertEquals(GrantStatus.NOT_DETERMINED, delegate.checkStatus(AppGrant.GALLERY))
    }

    @Test
    fun `LOCAL_NETWORK reports GRANTED below API 37 without any OS grant`() = runTest {
        // 2.3.0: Android 17's ACCESS_LOCAL_NETWORK maps to an empty permission list below
        // API 37 — checkStatus must treat that as a no-op GRANTED, never NOT_DETERMINED.
        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.LOCAL_NETWORK))
    }

    // ── LOCATION partial access (2.3.0 — same defect class as the gallery USER_SELECTED bug) ──
    //
    // AppGrant.LOCATION maps to [FINE, COARSE] and checkStatus demanded all{granted}.
    // A user who picks "Approximate" in the OS dialog grants ONLY COARSE — the app HAS
    // usable (coarse) location, but the all-granted check failed and the request-history
    // fallback escalated to DENIED_ALWAYS. Android 17 makes Precise/Approximate visually
    // distinct in the dialog, so approximate-only grants become more common.

    @Test
    fun `LOCATION with COARSE only is PARTIAL access`() = runTest {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        store.setRequested(AppGrant.LOCATION)

        assertEquals(GrantStatus.PARTIAL_GRANTED, delegate.checkStatus(AppGrant.LOCATION))
    }

    @Test
    fun `LOCATION with FINE and COARSE is FULL access`() = runTest {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.LOCATION))
    }

    @Test
    fun `LOCATION with nothing granted stays NOT_DETERMINED before any request`() = runTest {
        assertEquals(GrantStatus.NOT_DETERMINED, delegate.checkStatus(AppGrant.LOCATION))
    }

    @Test
    fun `GALLERY_IMAGES_ONLY with IMAGES granted is FULL access`() = runTest {
        grant(Manifest.permission.READ_MEDIA_IMAGES)
        store.setRequested(AppGrant.GALLERY_IMAGES_ONLY)

        assertEquals(GrantStatus.GRANTED, delegate.checkStatus(AppGrant.GALLERY_IMAGES_ONLY))
    }
}
