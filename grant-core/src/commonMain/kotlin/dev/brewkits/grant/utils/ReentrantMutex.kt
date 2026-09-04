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

    /**
     * Whether the underlying mutex is held right now.
     *
     * Used only to decide whether a mutex is safe to *discard* when pruning a bounded
     * per-identifier map — replacing a held mutex would hand two coroutines separate
     * instances and silently destroy mutual exclusion. It is intentionally not used to
     * decide whether to lock: that would be a check-then-act race.
     */
    val isLocked: Boolean get() = mutex.isLocked

    private val key = object : CoroutineContext.Key<MutexElement> {}

    private inner class MutexElement : AbstractCoroutineContextElement(key)

    suspend fun <T> withLock(block: suspend () -> T): T {
        // Re-entrancy is decided purely by whether this mutex's marker is present in the
        // calling coroutine's context. An earlier version also maintained `ownerContext` and
        // a `count`; neither was ever read to make a decision, and the nested/outer `finally`
        // blocks could drive `count` negative. Dead state inside a synchronisation primitive
        // is the worst place for it, so both are gone rather than "fixed".
        if (coroutineContext[key] != null) {
            return block()
        }

        return kotlinx.coroutines.withContext(coroutineContext + MutexElement()) {
            mutex.withLock { block() }
        }
    }
}
