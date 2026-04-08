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
 * iOS typically responds within 1–2 seconds. 10 seconds allows for slow
 * devices, user reading the dialog, and system animations.
 */
private const val BLUETOOTH_REQUEST_TIMEOUT_MS = 10_000L

/**
 * Delegate for handling iOS CoreBluetooth grant requests.
 *
 * **FIX H4 — Ephemeral CBCentralManager on checkStatus:**
 * Previously, `checkStatus()` created a new `CBCentralManager(delegate:queue:)`
 * on every call. This is problematic because:
 * 1. Initialises the BT stack unnecessarily on every status check.
 * 2. On iOS 13.0, creating a CBCentralManager (even with delegate=null) may
 *    trigger an unexpected permission prompt.
 * 3. The ephemeral instance is not released safely due to Kotlin/Native ARC timing.
 *
 * Fix: `checkStatus()` now uses `CBManager.authorization()` directly (iOS 13.1+)
 * for a lightweight, side-effect-free status read. No CBCentralManager needed.
 */
@OptIn(ExperimentalForeignApi::class)
internal class BluetoothManagerDelegate : NSObject(), CBCentralManagerDelegateProtocol {

    private var centralManager: CBCentralManager? = null
    private var continuation: Continuation<GrantStatus>? = null

    /**
     * FIX H4: Check authorization status WITHOUT creating a CBCentralManager.
     *
     * `CBManager.authorization()` (iOS 13.1+) reads the app's BT authorization
     * state directly from the system without triggering stack initialization
     * or any side effects.
     *
     * On iOS Simulator, returns GRANTED for testing purposes.
     */
    fun checkStatus(): GrantStatus {
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "BluetoothDelegate",
                "Running on ${SimulatorDetector.simulatorType} — Bluetooth not available on simulator, returning GRANTED for testing."
            )
            return GrantStatus.GRANTED
        }

        // FIX H4: Use CBManager.authorization() without any CBCentralManager instantiation.
        // Available on iOS 13.1+. Covers 99%+ of active devices as of 2025.
        val authorization = CBManager.authorization()
        return when (authorization) {
            CBManagerAuthorizationAllowedAlways  -> GrantStatus.GRANTED
            CBManagerAuthorizationDenied         -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationRestricted     -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationNotDetermined  -> GrantStatus.NOT_DETERMINED
            else                                 -> GrantStatus.NOT_DETERMINED
        }
    }

    /**
     * Requests Bluetooth access by creating a CBCentralManager.
     * The OS automatically shows the permission dialog on first access.
     *
     * Uses [BLUETOOTH_REQUEST_TIMEOUT_MS] (10 s) timeout to prevent hangs.
     *
     * @throws BluetoothTimeoutException        if dialog never responds
     * @throws BluetoothInitializationException if CBCentralManager creation fails
     * @throws BluetoothPoweredOffException     if Bluetooth is powered off
     */
    suspend fun requestBluetoothAccess(): GrantStatus {
        if (SimulatorDetector.isSimulator) {
            GrantLogger.i(
                "BluetoothDelegate",
                "Running on ${SimulatorDetector.simulatorType} — returning GRANTED for testing."
            )
            return GrantStatus.GRANTED
        }

        return try {
            withTimeout(BLUETOOTH_REQUEST_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    continuation = cont

                    try {
                        // Creating CBCentralManager triggers the BT permission dialog
                        centralManager = CBCentralManager(
                            delegate = this@BluetoothManagerDelegate,
                            queue = null
                        )
                        GrantLogger.i("BluetoothDelegate", "CBCentralManager created, waiting for state update.")
                    } catch (e: Exception) {
                        GrantLogger.e("BluetoothDelegate", "Failed to create CBCentralManager", e)
                        cont.resumeWithException(
                            BluetoothInitializationException("Failed to initialize Bluetooth manager: ${e.message}")
                        )
                    }

                    cont.invokeOnCancellation {
                        GrantLogger.i("BluetoothDelegate", "Bluetooth request cancelled.")
                        continuation = null
                        centralManager = null
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            GrantLogger.w(
                "BluetoothDelegate",
                "Bluetooth request timed out after ${BLUETOOTH_REQUEST_TIMEOUT_MS}ms. User can retry."
            )
            continuation = null
            centralManager = null
            throw BluetoothTimeoutException(
                "Bluetooth permission request timed out after ${BLUETOOTH_REQUEST_TIMEOUT_MS}ms."
            )
        }
    }

    /**
     * CBCentralManagerDelegate callback when Bluetooth state changes.
     */
    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        val state         = central.state
        val authorization = CBManager.authorization()  // same lightweight read as checkStatus()
        val status = mapAuthorizationToStatus(state, authorization)

        val cont = continuation
        if (cont != null) {
            continuation = null
            mainContinuation<GrantStatus> { s ->
                if (s == GrantStatus.DENIED_ALWAYS && state == CBManagerStatePoweredOff) {
                    cont.resumeWithException(BluetoothPoweredOffException("Bluetooth is turned off"))
                } else {
                    cont.resume(s)
                }
            }.invoke(status)
        }
    }

    private fun mapAuthorizationToStatus(
        state: CBManagerState,
        authorization: CBManagerAuthorization
    ): GrantStatus {
        return when (authorization) {
            CBManagerAuthorizationAllowedAlways -> when (state) {
                CBManagerStatePoweredOn    -> GrantStatus.GRANTED
                CBManagerStatePoweredOff   -> GrantStatus.DENIED_ALWAYS  // BT hardware off
                CBManagerStateResetting    -> GrantStatus.DENIED          // Transient — treat as soft denied
                CBManagerStateUnsupported  -> GrantStatus.DENIED_ALWAYS   // No BT hardware
                CBManagerStateUnauthorized -> GrantStatus.DENIED_ALWAYS   // Should not happen here
                CBManagerStateUnknown      -> GrantStatus.NOT_DETERMINED
                else                       -> GrantStatus.NOT_DETERMINED
            }
            CBManagerAuthorizationDenied     -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationRestricted -> GrantStatus.DENIED_ALWAYS
            CBManagerAuthorizationNotDetermined -> GrantStatus.NOT_DETERMINED
            else -> GrantStatus.NOT_DETERMINED
        }
    }
}

/** Thrown when Bluetooth is authorized but the radio is powered off. Recoverable. */
internal class BluetoothPoweredOffException(message: String) : Exception(message)

/** Thrown when the permission request times out waiting for user interaction. Recoverable. */
internal class BluetoothTimeoutException(message: String) : Exception(message)

/** Thrown when CBCentralManager initialization fails unexpectedly. */
internal class BluetoothInitializationException(message: String) : Exception(message)
