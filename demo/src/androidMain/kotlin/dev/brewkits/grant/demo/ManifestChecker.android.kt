package dev.brewkits.grant.demo

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.util.ManifestValidator
import dev.brewkits.grant.util.ValidationResult

actual object ManifestChecker {
    actual fun getMissingPermissions(grant: AppGrant): List<String> {
        val context = DemoApplication.instance ?: return emptyList()

        return when (val result = ManifestValidator.validateGrant(context, grant)) {
            is ValidationResult.Valid -> emptyList()
            is ValidationResult.MissingPermissions -> result.permissions
        }
    }
}
