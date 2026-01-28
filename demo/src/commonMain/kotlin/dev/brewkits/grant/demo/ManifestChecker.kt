package dev.brewkits.grant.demo

import dev.brewkits.grant.AppGrant

/**
 * Platform-specific manifest/Info.plist validation
 */
expect object ManifestChecker {
    /**
     * Check if all required permissions for a grant are declared in manifest
     * @return List of missing permission strings, or empty if all declared
     */
    fun getMissingPermissions(grant: AppGrant): List<String>
}
