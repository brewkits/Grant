package dev.brewkits.grant.impl

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.grantManager
import dev.brewkits.grant.GrantStatus

/**
 * Production-ready custom grant implementation with platform-specific code.
 *
 * This implementation is INDEPENDENT of third-party libraries like moko-grants,
 * providing full control over grant handling while maintaining clean architecture.
 *
 * **Features**:
 * - Complete async grant requests on both Android and iOS
 * - API version-aware handling (Android 12+ location, Bluetooth grants, etc.)
 * - Multi-grant support for complex scenarios
 * - Lifecycle safety on Android
 * - Main thread safety on iOS
 * - Grant state override for special cases
 *
 * **Architecture**:
 * - commonMain: Interface definition via expect/actual pattern
 * - androidMain: Full Android implementation with Activity Result API
 * - iosMain: Full iOS implementation with framework delegates (CoreLocation, etc.)
 *
 * **Implementation learned from moko-grants but kept independent.**
 */
class MygrantManager(
    private val platformDelegate: PlatformGrantDelegate
) : grantManager {

    override suspend fun checkStatus(grant: AppGrant): GrantStatus {
        return platformDelegate.checkStatus(grant)
    }

    override suspend fun request(grant: AppGrant): GrantStatus {
        return platformDelegate.request(grant)
    }

    override fun openSettings() {
        platformDelegate.openSettings()
    }
}

/**
 * Platform-specific delegate for grant operations.
 *
 * Implementations:
 * - Android: Use ActivityCompat, ContextCompat
 * - iOS: Use AVFoundation, CoreLocation, etc.
 */
expect class PlatformGrantDelegate {
    suspend fun checkStatus(grant: AppGrant): GrantStatus
    suspend fun request(grant: AppGrant): GrantStatus
    fun openSettings()
}
