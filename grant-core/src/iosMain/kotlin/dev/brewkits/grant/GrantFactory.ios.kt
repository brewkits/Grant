package dev.brewkits.grant

import dev.brewkits.grant.impl.PlatformGrantDelegate

/**
 * iOS implementation of platform delegate factory.
 *
 * No parameters needed for iOS.
 */
actual fun createPlatformDelegate(context: Any?): PlatformGrantDelegate {
    return PlatformGrantDelegate()
}
