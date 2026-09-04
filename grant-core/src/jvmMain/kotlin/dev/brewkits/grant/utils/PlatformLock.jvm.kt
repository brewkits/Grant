package dev.brewkits.grant.utils

import java.util.concurrent.locks.ReentrantLock

/**
 * Unlike the browser target, a JVM process is genuinely multi-threaded (Compose Desktop's UI
 * thread is not the only thread touching Grant's state), so this needs a real lock — the same
 * choice `androidMain` already makes.
 */
internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    public actual fun lock() {
        lock.lock()
    }

    public actual fun unlock() {
        lock.unlock()
    }
}
