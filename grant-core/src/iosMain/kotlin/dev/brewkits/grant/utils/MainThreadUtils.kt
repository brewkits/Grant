package dev.brewkits.grant.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import platform.Foundation.NSRunLoop
import platform.Foundation.NSThread
import kotlin.coroutines.CoroutineContext
import platform.darwin.dispatch_async_f
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.staticCFunction
import kotlinx.coroutines.withContext

/**
 * Coroutine dispatcher that ensures code runs on iOS main thread.
 * iOS UI operations and many framework APIs must run on the main thread.
 *
 * Learned from moko-grants implementation.
 */
internal object MainRunDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        NSRunLoop.mainRunLoop.performBlock {
            block.run()
        }
    }
}

/**
 * Wraps a callback to ensure it runs on the main thread.
 *
 * iOS frameworks use callbacks that may be called on background threads.
 * This helper ensures the continuation is always resumed on the main thread,
 * preventing crashes when the continuation interacts with UI or main-thread-only APIs.
 *
 * @param block The callback to wrap
 * @return A new callback that will execute on the main thread
 *
 * Example:
 * ```kotlin
 * suspendCoroutine { continuation ->
 *     AVCaptureDevice.requestAccessForMediaType(
 *         AVMediaTypeVideo,
 *         mainContinuation { granted ->
 *             continuation.resume(granted)
 *         }
 *     )
 * }
 * ```
 */
internal inline fun <T> mainContinuation(
    noinline block: (T) -> Unit
): (T) -> Unit = { arg ->
    if (NSThread.isMainThread()) {
        block.invoke(arg)
    } else {
        NSRunLoop.mainRunLoop.performBlock {
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
        NSRunLoop.mainRunLoop.performBlock {
            block.invoke(arg1, arg2)
        }
    }
}

/**
 * Schedules a block to be executed on the run loop.
 * This is a common pattern in iOS development to ensure thread safety.
 */
@OptIn(ExperimentalForeignApi::class)
private fun NSRunLoop.performBlock(block: () -> Unit) {
    dispatch_async_f(
        queue = dispatch_get_main_queue(),
        context = StableRef.create(block).asCPointer(),
        work = staticCFunction { blockPtr ->
            val toRun = blockPtr!!.asStableRef<() -> Unit>().get()
            toRun()
        }
    )
}

/**
 * Ensures the block is executed on the Main Thread.
 * Important for iOS grant requests (AVCaptureDevice, PHPhotoLibrary, etc.)
 */
internal suspend fun <T> runOnMain(block: suspend () -> T): T {
    return if (NSThread.isMainThread()) {
        block()
    } else {
        withContext(MainRunDispatcher) {
            block()
        }
    }
}
