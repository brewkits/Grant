package dev.brewkits.grant.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReentrantMutexTest {

    @Test
    fun `ReentrantMutex - allows nested locking in the same coroutine`() = runTest {
        val mutex = ReentrantMutex()
        var callCount = 0

        mutex.withLock {
            callCount++
            mutex.withLock {
                callCount++
                mutex.withLock {
                    callCount++
                }
            }
        }

        assertEquals(3, callCount, "Nested locking should work")
    }

    @Test
    fun `ReentrantMutex - blocks different coroutines`() = runTest {
        val mutex = ReentrantMutex()
        var firstLocked = false
        var secondLocked = false

        val job1 = async {
            mutex.withLock {
                firstLocked = true
                kotlinx.coroutines.delay(100)
            }
        }

        val job2 = async {
            kotlinx.coroutines.delay(20)
            mutex.withLock {
                secondLocked = true
            }
        }

        job1.await()
        job2.await()

        assertEquals(true, firstLocked)
        assertEquals(true, secondLocked)
    }
}
