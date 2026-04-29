package dev.brewkits.grant.handlers

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.delegates.BluetoothManagerDelegate
import dev.brewkits.grant.delegates.BluetoothPoweredOffException
import dev.brewkits.grant.delegates.BluetoothTimeoutException
import dev.brewkits.grant.utils.GrantLogger

private const val TAG = "BluetoothPermissionHandler"

/**
 * Handles Bluetooth permissions via CoreBluetooth framework.
 *
 * **Why CoreBluetooth is isolated here:**
 * Linking CoreBluetooth causes Apple to require NSBluetoothAlwaysUsageDescription
 * in Info.plist, even if the app never requests Bluetooth access. Isolating this
 * import prevents that.
 *
 * Delegates all native CoreBluetooth calls to [BluetoothManagerDelegate], which
 * already lives in the `delegates/` package and carries the CoreBluetooth imports.
 *
 * @param delegate  Shared [BluetoothManagerDelegate] that manages CBCentralManager lifecycle.
 */
internal class BluetoothPermissionHandler(
    private val delegate: BluetoothManagerDelegate
) : IosPermissionHandler {

    override fun checkStatus(): GrantStatus = delegate.checkStatus()

    override suspend fun request(): GrantStatus =
        try {
            delegate.requestBluetoothAccess()
        } catch (e: BluetoothTimeoutException) {
            GrantLogger.w(TAG, "Bluetooth request timed out: ${e.message}")
            GrantStatus.DENIED
        } catch (e: BluetoothPoweredOffException) {
            GrantLogger.w(TAG, "Bluetooth is powered off: ${e.message}")
            GrantStatus.DENIED
        } catch (e: Exception) {
            GrantLogger.e(TAG, "Bluetooth request failed: ${e.message}", e)
            GrantStatus.DENIED
        }
}
