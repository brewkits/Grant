package dev.brewkits.grant.impl

import android.os.Build
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertEquals

/**
 * Android 17's `ACCESS_LOCAL_NETWORK` is gated on the app's **targetSdkVersion**, not on the
 * device's API level: it is documented under "Behavior changes: apps targeting Android 17 or
 * higher" and is absent from the "all apps" list.
 *
 * The original mapping checked only `Build.VERSION.SDK_INT >= 37`. On an Android 17 device, an
 * app targeting API 36 — which keeps working on the local network without the permission, and
 * therefore has no reason to declare it — was mapped to `ACCESS_LOCAL_NETWORK` anyway. The
 * subsequent `checkSelfPermission` for an undeclared permission fails, and the request-history
 * fallback escalates that to `DENIED_ALWAYS`: the user is sent to Settings to find a toggle
 * that is not listed, for a feature that was never broken.
 *
 * "Targets 36, runs on 17" is the common case for roughly a year after each Android release,
 * since Play requires a recent target but not the newest one.
 *
 * Robolectric 4.12.1 cannot boot an SDK 37 environment, so `Build.VERSION.SDK_INT` is set by
 * reflection. That is enough: `toAndroidGrants()` reads the field directly and performs no other
 * API-37-specific work.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class LocalNetworkTargetSdkGatingTest {

    private lateinit var delegate: PlatformGrantDelegate
    private var originalSdkInt: Int = Build.VERSION.SDK_INT

    @Before
    fun setup() {
        originalSdkInt = Build.VERSION.SDK_INT
        delegate = PlatformGrantDelegate(RuntimeEnvironment.getApplication(), InMemoryGrantStore())
    }

    @org.junit.After
    fun tearDown() {
        setDeviceSdk(originalSdkInt)
    }

    private fun setDeviceSdk(level: Int) =
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", level)

    private fun setTargetSdk(level: Int) {
        RuntimeEnvironment.getApplication().applicationInfo.targetSdkVersion = level
    }

    private fun mapping(): List<String> =
        with(delegate) { AppGrant.LOCAL_NETWORK.toAndroidGrants() }

    @Test
    fun `requests the permission only when the app targets API 37 on an API 37 device`() {
        setDeviceSdk(37)
        setTargetSdk(37)
        assertEquals(listOf("android.permission.ACCESS_LOCAL_NETWORK"), mapping())
    }

    @Test
    fun `does not request the permission when the app targets API 36 on an API 37 device`() {
        // The regression this test exists for: enforcement is target-gated, so a legacy-target
        // app still has working local network access and must not be pushed toward Settings.
        setDeviceSdk(37)
        setTargetSdk(36)
        assertEquals(emptyList<String>(), mapping())
    }

    @Test
    fun `does not request the permission on a device below API 37 even when targeting 37`() {
        setDeviceSdk(36)
        setTargetSdk(37)
        assertEquals(emptyList<String>(), mapping())
    }

    @Test
    fun `does not request the permission below API 37 on both device and target`() {
        setDeviceSdk(34)
        setTargetSdk(34)
        assertEquals(emptyList<String>(), mapping())
    }
}
