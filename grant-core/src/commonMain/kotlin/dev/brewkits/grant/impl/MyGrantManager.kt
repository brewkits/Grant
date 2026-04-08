package dev.brewkits.grant.impl

import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantPermission
import dev.brewkits.grant.GrantStatus

/**
 * Default production implementation of [GrantManager].
 *
 * Delegates all platform-specific permission operations to [PlatformGrantDelegate],
 * which provides native iOS (CoreLocation, AVFoundation, etc.) and Android
 * (Activity Result API, ContextCompat.checkSelfPermission, etc.) implementations.
 *
 * **Architecture (expect/actual pattern)**:
 * - `commonMain`: This class + [PlatformGrantDelegate] expect declaration
 * - `androidMain`: [PlatformGrantDelegate] actual — full Android implementation
 * - `iosMain`: [PlatformGrantDelegate] actual — full iOS implementation
 *
 * **FIX L1**: Renamed from `MyGrantManager` (placeholder name) to
 * `DefaultGrantManager` to reflect its role as the production-ready default.
 * `MyGrantManager` is kept as a typealias for backwards compatibility.
 */
class DefaultGrantManager(
    private val platformDelegate: PlatformGrantDelegate
) : GrantManager {

    override suspend fun checkStatus(grant: GrantPermission): GrantStatus =
        platformDelegate.checkStatus(grant)

    override suspend fun request(grant: GrantPermission): GrantStatus =
        platformDelegate.request(grant)

    override suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus> =
        platformDelegate.request(grants)

    override fun openSettings() =
        platformDelegate.openSettings()
}

/**
 * Backwards-compatibility alias. Prefer [DefaultGrantManager] for new code.
 *
 * FIX L1: `MyGrantManager` was a placeholder name that leaked implementation
 * details. This alias preserves binary compatibility for existing integrations.
 */
@Deprecated(
    message = "Use DefaultGrantManager. MyGrantManager is a placeholder name and will be removed in v2.0.",
    replaceWith = ReplaceWith("DefaultGrantManager", "dev.brewkits.grant.impl.DefaultGrantManager"),
    level = DeprecationLevel.WARNING
)
typealias MyGrantManager = DefaultGrantManager

/**
 * Platform-specific delegate for grant operations.
 *
 * **Permission Types:**
 * - [AppGrant]: Built-in permissions (CAMERA, LOCATION, etc.)
 * - [RawPermission]: Custom platform-specific permissions
 *
 * **Implementations:**
 * - Android (`androidMain`): Uses ActivityCompat, ContextCompat, Activity Result API
 * - iOS (`iosMain`): Uses AVFoundation, CoreLocation, CoreBluetooth, EventKit, etc.
 */
expect class PlatformGrantDelegate {
    suspend fun checkStatus(grant: GrantPermission): GrantStatus
    suspend fun request(grant: GrantPermission): GrantStatus
    suspend fun request(grants: List<GrantPermission>): Map<GrantPermission, GrantStatus>
    fun openSettings()
}
