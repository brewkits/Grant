package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus
import kotlinx.coroutines.delay

/**
 * Simple mock implementation for demo purposes.
 *
 * This simulates realistic grant request behavior:
 * - First request: User denies → DENIED (shows rationale)
 * - Second request: User denies again → DENIED_ALWAYS (must go to settings)
 * - Third request: User grants → GRANTED
 *
 * This allows testing all three dialog flows:
 * 1. Rationale dialog (after first denial)
 * 2. Settings dialog (after second denial)
 * 3. Success callback (after grant)
 */
class SimpleGrantManager : GrantManager {

    private val grantedGrants = mutableSetOf<AppGrant>()
    private val requestCount = mutableMapOf<AppGrant, Int>()

    /**
     * Simulation mode for testing different scenarios
     */
    enum class SimulationMode {
        /**
         * Realistic flow: deny → deny → grant
         * This is the default mode for testing all dialogs
         */
        REALISTIC,

        /**
         * Always grant immediately (for quick testing)
         */
        AUTO_GRANT,

        /**
         * Always deny softly (test rationale dialog repeatedly)
         */
        ALWAYS_DENY,

        /**
         * Always deny permanently (test settings dialog repeatedly)
         */
        ALWAYS_DENY_PERMANENTLY
    }

    var simulationMode = SimulationMode.REALISTIC

    override suspend fun checkStatus(grant: AppGrant): GrantStatus {
        return when {
            grant in grantedGrants -> GrantStatus.GRANTED
            (requestCount[grant] ?: 0) >= 2 -> {
                // After 2 denials, it becomes permanent
                if (simulationMode == SimulationMode.ALWAYS_DENY_PERMANENTLY) {
                    GrantStatus.DENIED_ALWAYS
                } else {
                    GrantStatus.DENIED
                }
            }
            (requestCount[grant] ?: 0) >= 1 -> GrantStatus.DENIED
            else -> GrantStatus.NOT_DETERMINED
        }
    }

    override suspend fun request(grant: AppGrant): GrantStatus {
        // Simulate system dialog delay
        delay(500)

        val count = requestCount[grant] ?: 0
        requestCount[grant] = count + 1

        return when (simulationMode) {
            SimulationMode.AUTO_GRANT -> {
                // Always grant immediately
                grantedGrants.add(grant)
                GrantStatus.GRANTED
            }

            SimulationMode.ALWAYS_DENY -> {
                // Always deny softly (to test rationale)
                GrantStatus.DENIED
            }

            SimulationMode.ALWAYS_DENY_PERMANENTLY -> {
                // Always deny permanently (to test settings)
                GrantStatus.DENIED_ALWAYS
            }

            SimulationMode.REALISTIC -> {
                // Realistic flow:
                // 1st request: Deny → DENIED
                // 2nd request: Deny → DENIED_ALWAYS
                // 3rd+ request: Grant → GRANTED
                when (count) {
                    0 -> {
                        // First time: User denies
                        GrantStatus.DENIED
                    }
                    1 -> {
                        // Second time: User denies permanently
                        GrantStatus.DENIED_ALWAYS
                    }
                    else -> {
                        // Third time: User goes to settings and grants
                        grantedGrants.add(grant)
                        GrantStatus.GRANTED
                    }
                }
            }
        }
    }

    override fun openSettings() {
        // Mock implementation - does nothing
        // In real app, this would open system settings
    }

    /**
     * Reset simulation state (useful for demo)
     */
    fun reset() {
        grantedGrants.clear()
        requestCount.clear()
    }
}
