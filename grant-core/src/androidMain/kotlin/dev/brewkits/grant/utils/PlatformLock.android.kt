package dev.brewkits.grant.utils

import java.util.concurrent.locks.ReentrantLock

internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun lock() {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }
}
