package dev.brewkits.grant.utils

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread

/**
 * Wraps a callback to ensure it runs on the main thread.
 */
internal inline fun <T> mainContinuation(
    noinline block: (T) -> Unit
): (T) -> Unit = { arg ->
    if (NSThread.isMainThread()) {
        block.invoke(arg)
    } else {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            block.invoke(arg)
        }
    }
}

/**
 * Wraps a callback with two parameters to ensure it runs on the main thread.
 */
internal inline fun <T1, T2> mainContinuation2(
    noinline block: (T1, T2) -> Unit
): (T1, T2) -> Unit = { arg1, arg2 ->
    if (NSThread.isMainThread()) {
        block.invoke(arg1, arg2)
    } else {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            block.invoke(arg1, arg2)
        }
    }
}

/**
 * Ensures the block is executed on the Main Thread.
 * Important for iOS grant requests.
 */
internal suspend fun <T> runOnMain(block: suspend () -> T): T {
    return withContext(Dispatchers.Main.immediate) {
        block()
    }
}
