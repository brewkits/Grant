package dev.brewkits.grant.utils

import java.util.concurrent.locks.ReentrantLock

internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    public actual fun lock() {
        lock.lock()
    }

    public actual fun unlock() {
        lock.unlock()
    }
}
