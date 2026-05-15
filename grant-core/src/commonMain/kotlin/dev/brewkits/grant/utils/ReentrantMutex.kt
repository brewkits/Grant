package dev.brewkits.grant.utils

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * A Mutex that is re-entrant for the same coroutine context.
 * 
 * Standard Kotlin [Mutex] is non-reentrant. This implementation uses a custom 
 * CoroutineContext element to track ownership across nested calls within the 
 * same coroutine hierarchy.
 */
internal class ReentrantMutex {
    private val mutex = Mutex()
    private val lock = PlatformLock()
    private var ownerContext: CoroutineContext? = null
    private var count = 0

    private val key = object : CoroutineContext.Key<MutexElement> {}

    private inner class MutexElement : AbstractCoroutineContextElement(key)

    suspend fun <T> withLock(block: suspend () -> T): T {
        val currentContext = coroutineContext
        
        val isOwner = lock.withLock {
            // Check if this mutex is already in the coroutine context
            if (currentContext[key] != null) {
                count++
                true
            } else {
                false
            }
        }
        
        if (isOwner) {
            return try {
                block()
            } finally {
                lock.withLock { count-- }
            }
        }
        
        // Use a new context that carries the mutex ownership
        return kotlinx.coroutines.withContext(currentContext + MutexElement()) {
            mutex.withLock {
                lock.withLock {
                    ownerContext = coroutineContext
                    count = 1
                }
                try {
                    block()
                } finally {
                    lock.withLock {
                        ownerContext = null
                        count = 0
                    }
                }
            }
        }
    }
}
