package dev.brewkits.grant

/**
 * A DSL builder for orchestrating sequential multi-permission flows.
 * 
 * While [GrantGroupHandler] requests permissions concurrently in a batch, 
 * [GrantFlow] allows you to define complex, step-by-step logic where the outcome
 * of one permission dictates the next step.
 * 
 * ### Usage Example
 * ```kotlin
 * val permissionFlow = grantFlow {
 *     val cameraStatus = cameraHandler.requestSuspend(
 *         rationaleMessage = "Camera is needed first."
 *     )
 *     
 *     if (cameraStatus == GrantStatus.GRANTED) {
 *         // Only ask for Location if Camera was granted
 *         locationHandler.requestSuspend(
 *             rationaleMessage = "Now we need location to tag your photos."
 *         )
 *     }
 * }
 * 
 * // Later, execute the flow
 * scope.launch {
 *     permissionFlow.execute()
 * }
 * ```
 */
class GrantFlow internal constructor(
    private val block: suspend GrantFlowScope.() -> Unit
) {
    /**
     * Executes the defined grant flow.
     */
    suspend fun execute() {
        val scope = GrantFlowScopeImpl()
        scope.block()
    }
}

/**
 * Scope for the [grantFlow] builder, providing utility functions
 * for sequential permission handling.
 */
interface GrantFlowScope

internal class GrantFlowScopeImpl : GrantFlowScope

/**
 * Creates a new [GrantFlow] for orchestrating sequential permission requests.
 * 
 * @param block The sequential logic to execute. Use [GrantHandler.requestSuspend] inside.
 * @return A [GrantFlow] instance ready to be executed.
 */
fun grantFlow(block: suspend GrantFlowScope.() -> Unit): GrantFlow {
    return GrantFlow(block)
}
