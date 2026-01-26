package dev.brewkits.grant.delegates

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.SimulatorDetector
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreBluetooth.*
import platform.darwin.NSObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString

/**
 * Delegate for handling iOS CoreBluetooth grant requests.
 *
 * iOS Bluetooth grants are checked through CBCentralManager state.
 * Creating a CBCentralManager automatically triggers the grant dialog
 * if authorization hasn't been determined yet.
 */
@OptIn(ExperimentalForeignApi::class)
internal class BluetoothManagerDelegate : NSObject(), CBCentralManagerDelegateProtocol {

    private var centralManager: CBCentralManager? = null
    private var continuation: Continuation<GrantStatus>? = null

    /**
     * Checks current Bluetooth authorization status without requesting.
     * Creates a temporary CBCentralManager to read the status.
     *
     * @return Current grant status
     */
    fun checkStatus(): GrantStatus {
        // iOS Simulator doesn't support Bluetooth hardware
        // Return GRANTED to allow testing without blocking
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "BluetoothDelegate",
                "Running on ${SimulatorDetector.simulatorType} - Bluetooth not supported, returning GRANTED for testing"
            )
            return GrantStatus.GRANTED
        }

        // Create temporary manager to check state
        val tempManager = CBCentralManager(delegate = null, queue = null)

        val authorization = if (tempManager.respondsToSelector(
                NSSelectorFromString("authorization")
            )) {
            // iOS 13.1+
            CBManager.authorization()
        } else {
            // iOS 13.0: infer from state
            if (tempManager.state == CBManagerStatePoweredOn) {
                CBManagerAuthorizationAllowedAlways
            } else {
                CBManagerAuthorizationNotDetermined
            }
        }

        return mapBluetoothStateToStatus(tempManager.state, authorization)
    }

    /**
     * Requests Bluetooth grant by creating a CBCentralManager.
     * The OS will automatically show grant dialog on first access.
     *
     * @return The resulting grant status
     */
    suspend fun requestBluetoothAccess(): GrantStatus {
        // iOS Simulator doesn't support Bluetooth hardware
        // Return GRANTED immediately to allow testing
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "BluetoothDelegate",
                "Running on ${SimulatorDetector.simulatorType} - Returning GRANTED for testing (Bluetooth not supported on simulator)"
            )
            return GrantStatus.GRANTED
        }

        return suspendCancellableCoroutine { cont ->
            continuation = cont

            // Create CBCentralManager - this triggers grant request
            // Queue = null means use main queue
            centralManager = CBCentralManager(
                delegate = this,
                queue = null
            )

            cont.invokeOnCancellation {
                continuation = null
                centralManager = null
            }
        }
    }

    /**
     * CBCentralManagerDelegate callback when Bluetooth state changes.
     * This includes both grant changes and Bluetooth on/off state.
     */
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        val state = central.state
        val authorization = if (central.respondsToSelector(
                NSSelectorFromString("authorization")
            )) {
            // iOS 13.1+
            CBManager.authorization()
        } else {
            // iOS 13.0: authorization not available, infer from state
            if (state == CBManagerStatePoweredOn) {
                CBManagerAuthorizationAllowedAlways
            } else {
                CBManagerAuthorizationNotDetermined
            }
        }

        val status = mapBluetoothStateToStatus(state, authorization)

        val cont = continuation
        if (cont != null) {
            mainContinuation<GrantStatus> { s ->
                if (s == GrantStatus.DENIED_ALWAYS && state == CBManagerStatePoweredOff) {
                    // Special case: Bluetooth is turned off
                    cont.resumeWithException(
                        BluetoothPoweredOffException("Bluetooth is turned off")
                    )
                } else {
                    cont.resume(s)
                }
            }.invoke(status)
            continuation = null
        }
    }

    /**
     * Maps iOS Bluetooth state and authorization to our GrantStatus.
     */
    private fun mapBluetoothStateToStatus(
        state: CBManagerState,
        authorization: CBManagerAuthorization
    ): GrantStatus {
        // First check authorization
        return when (authorization) {
            CBManagerAuthorizationAllowedAlways -> {
                // Authorization granted, but check if Bluetooth is on
                when (state) {
                    CBManagerStatePoweredOn -> GrantStatus.GRANTED
                    CBManagerStatePoweredOff -> GrantStatus.DENIED_ALWAYS // BT off
                    CBManagerStateResetting -> GrantStatus.DENIED // Temporary
                    CBManagerStateUnsupported -> GrantStatus.DENIED_ALWAYS // No BT hardware
                    CBManagerStateUnauthorized -> GrantStatus.DENIED_ALWAYS // Shouldn't happen
                    CBManagerStateUnknown -> GrantStatus.NOT_DETERMINED
                    else -> GrantStatus.NOT_DETERMINED
                }
            }
            CBManagerAuthorizationDenied -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationRestricted -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }
}

/**
 * Exception thrown when Bluetooth grant is granted but Bluetooth is powered off.
 */
internal class BluetoothPoweredOffException(message: String) : Exception(message)
