package dev.brewkits.grant.delegates

import dev.brewkits.grant.GrantStatus
import dev.brewkits.grant.utils.GrantLogger
import dev.brewkits.grant.utils.SimulatorDetector
import dev.brewkits.grant.utils.mainContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.CoreBluetooth.*
import platform.darwin.NSObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSelectorFromString

/**
 * Bluetooth request timeout in milliseconds.
 *
 * **Why 10 seconds?**
 * - iOS typically responds within 1-2 seconds for permission dialogs
 * - 10 seconds allows for:
 *   - Slow devices or system lag
 *   - User reading the permission prompt
 *   - System animations/transitions
 * - Prevents indefinite hangs if system fails to respond
 *
 * **What happens on timeout?**
 * - Throws BluetoothTimeoutException (recoverable)
 * - User can retry the request
 * - Logs warning for debugging
 */
private const val BLUETOOTH_REQUEST_TIMEOUT_MS = 10_000L

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
     * **Timeout Behavior:**
     * - Uses [BLUETOOTH_REQUEST_TIMEOUT_MS] (10 seconds) timeout
     * - iOS typically responds within 1-2 seconds
     * - Timeout allows for slow systems while preventing indefinite hangs
     * - On timeout, throws BluetoothTimeoutException (recoverable - user can retry)
     *
     * @return The resulting grant status
     * @throws BluetoothTimeoutException if request times out after 10 seconds
     * @throws BluetoothInitializationException if CBCentralManager creation fails
     * @throws BluetoothPoweredOffException if Bluetooth is powered off
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

        return try {
            withTimeout(BLUETOOTH_REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    continuation = cont

                    try {
                        // Create CBCentralManager - this triggers grant request
                        // Queue = null means use main queue
                        centralManager = CBCentralManager(
                            delegate = this@BluetoothManagerDelegate,
                            queue = null
                        )

                        GrantLogger.i("BluetoothDelegate", "CBCentralManager created, waiting for state update")
                    } catch (e: Exception) {
                        GrantLogger.e("BluetoothDelegate", "Failed to create CBCentralManager", e)
                        cont.resumeWithException(
                            BluetoothInitializationException("Failed to initialize Bluetooth manager: ${e.message}")
                        )
                    }

                    cont.invokeOnCancellation {
                        GrantLogger.i("BluetoothDelegate", "Bluetooth request cancelled")
                        continuation = null
                        centralManager = null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            GrantLogger.w(
                "BluetoothDelegate",
                "Bluetooth request timed out after ${BLUETOOTH_REQUEST_TIMEOUT_MS}ms. " +
                "This is recoverable - user can retry the request."
            )
            continuation = null
            centralManager = null
            throw BluetoothTimeoutException(
                "Bluetooth permission request timed out after ${BLUETOOTH_REQUEST_TIMEOUT_MS}ms. " +
                "This may indicate system lag or user delay. Please retry."
            )
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
 * This is a recoverable error - user needs to enable Bluetooth in Settings.
 */
internal class BluetoothPoweredOffException(message: String) : Exception(message)

/**
 * Exception thrown when Bluetooth permission request times out.
 * This is a temporary error - user can retry.
 */
internal class BluetoothTimeoutException(message: String) : Exception(message)

/**
 * Exception thrown when CBCentralManager initialization fails.
 * This is a temporary error - may be due to system state.
 */
internal class BluetoothInitializationException(message: String) : Exception(message)
