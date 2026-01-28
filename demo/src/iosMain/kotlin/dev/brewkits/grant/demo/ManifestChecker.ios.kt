package dev.brewkits.grant.demo

import dev.brewkits.grant.AppGrant

actual object ManifestChecker {
    actual fun getMissingPermissions(grant: AppGrant): List<String> {
        // iOS validates Info.plist at compile time and runtime
        // The grant library will show appropriate errors if keys are missing
        return emptyList()
    }
}
