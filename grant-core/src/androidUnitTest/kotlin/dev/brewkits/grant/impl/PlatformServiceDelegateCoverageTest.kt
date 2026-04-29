package dev.brewkits.grant.impl

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import androidx.test.core.app.ApplicationProvider
import dev.brewkits.grant.ServiceStatus
import dev.brewkits.grant.ServiceType
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlatformServiceDelegateCoverageTest {

    private lateinit var context: Context
    private lateinit var delegate: PlatformServiceDelegate

    @BeforeTest
    fun setup() {
        context = spyk(ApplicationProvider.getApplicationContext())
        delegate = PlatformServiceDelegate(context)
        mockkStatic(BluetoothAdapter::class)
        mockkStatic(NfcAdapter::class)
    }

    @AfterTest
    fun teardown() {
        unmockkStatic(BluetoothAdapter::class)
        unmockkStatic(NfcAdapter::class)
    }

    @Test
    fun `test checkLocationService exception returns UNKNOWN`() = runTest {
        every { context.getSystemService(Context.LOCATION_SERVICE) } throws RuntimeException("Test Exception")
        val status = delegate.checkServiceStatus(ServiceType.LOCATION_GPS)
        assertEquals(ServiceStatus.UNKNOWN, status)
    }

    @Test
    fun `test checkBluetoothService exception returns UNKNOWN`() = runTest {
        every { BluetoothAdapter.getDefaultAdapter() } throws RuntimeException("Test Exception")
        val status = delegate.checkServiceStatus(ServiceType.BLUETOOTH)
        assertEquals(ServiceStatus.UNKNOWN, status)
    }

    @Test
    fun `test checkWifiService exception returns UNKNOWN`() = runTest {
        val appCtx = mockk<Context>()
        every { context.applicationContext } returns appCtx
        every { appCtx.getSystemService(Context.WIFI_SERVICE) } throws RuntimeException("Test Exception")
        
        val status = delegate.checkServiceStatus(ServiceType.WIFI)
        assertEquals(ServiceStatus.UNKNOWN, status)
    }

    @Test
    fun `test checkNfcService exception returns UNKNOWN`() = runTest {
        every { NfcAdapter.getDefaultAdapter(context) } throws RuntimeException("Test Exception")
        val status = delegate.checkServiceStatus(ServiceType.NFC)
        assertEquals(ServiceStatus.UNKNOWN, status)
    }

    @Test
    fun `test openServiceSettings exception returns false`() = runTest {
        every { context.startActivity(any()) } throws RuntimeException("Test Exception")
        val result = delegate.openServiceSettings(ServiceType.WIFI)
        assertEquals(false, result)
    }
}
