package dev.brewkits.grant.utils

internal expect class PlatformLock() {
    fun lock()
    fun unlock()
}

internal inline fun <T> PlatformLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
