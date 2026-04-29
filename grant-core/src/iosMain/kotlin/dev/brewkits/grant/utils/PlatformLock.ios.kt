package dev.brewkits.grant.utils

import platform.Foundation.NSRecursiveLock

internal actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }
}
