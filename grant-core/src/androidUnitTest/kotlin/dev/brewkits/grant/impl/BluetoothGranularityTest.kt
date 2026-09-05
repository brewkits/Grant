package dev.brewkits.grant.impl

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.InMemoryGrantStore
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Android permission mapping for the scan/connect split added in 2.4.0.
 *
 * **Why the split exists.** `AppGrant.BLUETOOTH` requests `BLUETOOTH_SCAN` *and*
 * `BLUETOOTH_CONNECT` together. Android 12 separated them because they differ in sensitivity:
 * a plain `BLUETOOTH_SCAN` is treated as capable of deriving physical location (scan results
 * reveal nearby devices), while `BLUETOOTH_CONNECT` is not. So a connect-only app — a POS
 * terminal, a car key, a scale — that used `BLUETOOTH` was taking on a location implication
 * for a capability it never exercised.
 *
 * These tests assert what each value maps to, per API level, because that mapping is the whole
 * point of the split and a regression in it would be invisible at the call site.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BluetoothGranularityTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformGrantDelegate

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        delegate = PlatformGrantDelegate(context, InMemoryGrantStore())
    }

    private fun mapping(grant: AppGrant): List<String> = with(delegate) { grant.toAndroidGrants() }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `scan-only maps to BLUETOOTH_SCAN alone, never CONNECT`() {
        val permissions = mapping(AppGrant.BLUETOOTH_SCAN)

        assertEquals(listOf(Manifest.permission.BLUETOOTH_SCAN), permissions)
        assertFalse(
            Manifest.permission.BLUETOOTH_CONNECT in permissions,
            "a scan-only app must not be made to request connect access",
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `connect-only maps to BLUETOOTH_CONNECT alone, never SCAN`() {
        val permissions = mapping(AppGrant.BLUETOOTH_CONNECT)

        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), permissions)
        assertFalse(
            Manifest.permission.BLUETOOTH_SCAN in permissions,
            "this is the point of the split: BLUETOOTH_SCAN carries a location implication " +
                "that a connect-only app must not inherit",
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun `the combined BLUETOOTH value still requests both, unchanged`() {
        val permissions = mapping(AppGrant.BLUETOOTH)

        assertTrue(Manifest.permission.BLUETOOTH_SCAN in permissions)
        assertTrue(Manifest.permission.BLUETOOTH_CONNECT in permissions)
    }

    /**
     * Below API 31 connecting to an already-paired device needed no runtime permission at all
     * (`BLUETOOTH`/`BLUETOOTH_ADMIN` are install-time), so an empty mapping is correct and
     * `checkStatus` reports GRANTED without prompting.
     *
     * Requesting `ACCESS_FINE_LOCATION` here — which `AppGrant.BLUETOOTH` still must, because
     * it also covers scanning — would ask a connect-only app for location it never needs.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `connect-only requires no runtime permission below API 31`() {
        val permissions = mapping(AppGrant.BLUETOOTH_CONNECT)

        assertTrue(permissions.isEmpty(), "expected no runtime permission, got $permissions")
        assertFalse(
            Manifest.permission.ACCESS_FINE_LOCATION in permissions,
            "a connect-only app must never be asked for location",
        )
    }

    /**
     * Scanning is the half that genuinely required `ACCESS_FINE_LOCATION` before API 31 — the
     * platform could infer position from nearby-device results, which is exactly why API 31
     * introduced `BLUETOOTH_SCAN` with its `neverForLocation` opt-out.
     */
    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `scan still requires location below API 31, where that was genuinely the requirement`() {
        val permissions = mapping(AppGrant.BLUETOOTH_SCAN)

        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    }
}
