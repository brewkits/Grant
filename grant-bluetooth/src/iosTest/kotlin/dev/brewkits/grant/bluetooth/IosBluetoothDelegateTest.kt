package dev.brewkits.grant.bluetooth

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.BluetoothPoweredOffException
import dev.brewkits.grant.delegates.BluetoothTimeoutException
import dev.brewkits.grant.handlers.BluetoothPermissionHandler
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * iOS-native tests for the Bluetooth delegate + handler, exercised through the
 * REAL [BluetoothManagerDelegate] (not a fake).
 *
 * Per the project's thread-safety convention, every suspend path that touches a
 * native CoreBluetooth callback is wrapped in [withTimeout] so a silent deadlock
 * surfaces as a test failure rather than a hung runner.
 *
 * On iOS Simulator there is no Bluetooth hardware, so `SimulatorDetector` makes
 * the delegate return GRANTED — these tests therefore assert "completes with a
 * valid status" rather than a specific device-dependent value.
 */
class IosBluetoothDelegateTest {

    private val validStatuses = setOf(
        GrantStatus.GRANTED,
        GrantStatus.PARTIAL_GRANTED,
        GrantStatus.DENIED,
        GrantStatus.DENIED_ALWAYS,
        GrantStatus.NOT_DETERMINED
    )

    private lateinit var delegate: BluetoothManagerDelegate
    private lateinit var handler: BluetoothPermissionHandler

    @BeforeTest
    fun setup() {
        delegate = BluetoothManagerDelegate()
        handler = BluetoothPermissionHandler(delegate)
    }

    @Test
    fun `delegate checkStatus returns a valid status without crashing`() {
        val status = delegate.checkStatus()
        assertNotNull(status)
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }

    @Test
    fun `delegate requestBluetoothAccess completes within bounds and never hangs`() = runTest {
        // In the XCTest runner there is no main RunLoop, so CBCentralManager's
        // centralManagerDidUpdateState callback never fires. The delegate's internal
        // 10s timeout converts that into a BluetoothTimeoutException rather than an
        // infinite hang — that is exactly the deadlock guard we want to verify.
        // We wrap in a larger outer timeout: if the inner guard regressed, this fails fast.
        val outcome: GrantStatus = withTimeout(15_000L) {
            try {
                delegate.requestBluetoothAccess()
            } catch (e: BluetoothTimeoutException) {
                GrantStatus.DENIED
            } catch (e: BluetoothPoweredOffException) {
                GrantStatus.DENIED
            }
        }
        assertTrue(outcome in validStatuses, "Unexpected status: $outcome")
    }

    @Test
    fun `handler checkStatus returns a valid status`() {
        val status = handler.checkStatus()
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }

    @Test
    fun `handler request does not deadlock and returns a valid status`() = runTest {
        val status = withTimeout(15_000L) { handler.request() }
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }

    @Test
    fun `GrantBluetooth initialize then handler request still completes`() = runTest {
        GrantBluetooth.initialize()
        val status = withTimeout(15_000L) { handler.request() }
        assertTrue(status in validStatuses, "Unexpected status: $status")
    }
}
