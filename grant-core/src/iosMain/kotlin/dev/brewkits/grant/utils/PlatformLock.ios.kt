package dev.brewkits.grant.utils

import platform.Foundation.NSRecursiveLock

internal actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()

    public actual fun lock() {
        lock.lock()
    }

    public actual fun unlock() {
        lock.unlock()
    }
}
