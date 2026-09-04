package dev.brewkits.grant.utils

/**
 * No-op lock. A browser tab runs JavaScript single-threaded on the event loop — there is no
 * preemptive concurrency for this to guard against, unlike Android's `ReentrantLock` or iOS's
 * `NSRecursiveLock`. This is a fact about the JS runtime model, not a shortcut: two `suspend`
 * functions can still interleave at suspension points, but never execute two lines of Kotlin
 * at the literal same instant the way OS threads can.
 */
internal actual class PlatformLock actual constructor() {
    public actual fun lock() {}
    public actual fun unlock() {}
}
