package dev.brewkits.grant.utils

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

/**
 * A Mutex that is re-entrant for the same coroutine (identified by its [Job]).
 * 
 * Standard Kotlin [Mutex] is non-reentrant, which can lead to deadlocks if a 
 * function holding a lock calls another function that attempts to acquire the 
 * same lock. This utility solves that for single-coroutine re-entrancy.
 */
internal class ReentrantMutex {
    private val mutex = Mutex()
    private val lock = PlatformLock()
    private var owner: Job? = null
    private var count = 0

    suspend fun <T> withLock(block: suspend () -> T): T {
        val currentJob = coroutineContext[Job]
        
        val isOwner = lock.withLock {
            if (owner == currentJob) {
                count++
                true
            } else {
                false
            }
        }
        
        if (isOwner) {
            try {
                return block()
            } finally {
                lock.withLock {
                    count--
                    if (count == 0) {
                        owner = null
                    }
                }
            }
        }
        
        return mutex.withLock {
            lock.withLock {
                owner = currentJob
                count = 1
            }
            try {
                block()
            } finally {
                lock.withLock {
                    owner = null
                    count = 0
                }
            }
        }
    }
}
