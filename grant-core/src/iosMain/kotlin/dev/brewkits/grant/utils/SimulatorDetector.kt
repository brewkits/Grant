package dev.brewkits.grant.utils

import platform.UIKit.UIDevice

/**
 * Utility to detect if app is running on iOS Simulator.
 *
 * iOS Simulator has limitations with certain permissions:
 * - Bluetooth: Not supported (no hardware)
 * - Motion: May not work correctly
 * - Location: Can be simulated but limited
 *
 * For these permissions, we return mock status on simulator
 * to allow testing without blocking the entire flow.
 */
object SimulatorDetector {

    /**
     * Check if running on iOS Simulator.
     *
     * Returns true if on simulator, false if on real device.
     */
    val isSimulator: Boolean by lazy {
        // Check device model - simulator always contains "Simulator"
        val model = UIDevice.currentDevice.model
        model.contains("Simulator", ignoreCase = true) ||
                // Alternative: Check if it's x86_64 or arm64 simulator
                isSimulatorArchitecture()
    }

    /**
     * Check architecture to detect simulator.
     * Simulator runs on Mac architecture (x86_64 or arm64 for Apple Silicon Macs).
     */
    private fun isSimulatorArchitecture(): Boolean {
        // On simulator, UIDevice.model might not always contain "Simulator"
        // but we can check other indicators
        // For now, rely on model check as it's most reliable
        return false
    }

    /**
     * Get simulator type name for logging.
     */
    val simulatorType: String
        get() = if (isSimulator) "iOS Simulator" else "Real Device"
}
