package dev.brewkits.grant

/**
 * Observer for permission flow events across [GrantHandler], [GrantGroupHandler],
 * and [GrantAndServiceHandler].
 *
 * Attach a listener to track the user's journey through the permission funnel —
 * useful for analytics, logging, or A/B testing rationale copy.
 *
 * All callbacks are invoked on the coroutine dispatcher that drives the handler
 * (typically the `Main` dispatcher via `viewModelScope`).
 *
 * **Usage with GrantHandler:**
 * ```kotlin
 * val cameraGrant = GrantHandler(
 *     grantManager  = grantManager,
 *     grant         = AppGrant.CAMERA,
 *     scope         = viewModelScope,
 *     eventListener = object : GrantEventListener {
 *         override fun onGranted(grant: GrantPermission, status: GrantStatus) {
 *             analytics.track("camera_granted")
 *         }
 *         override fun onDenied(grant: GrantPermission, status: GrantStatus) {
 *             analytics.track("camera_denied", mapOf("permanent" to (status == GrantStatus.DENIED_ALWAYS)))
 *         }
 *     }
 * )
 * ```
 *
 * **Usage with GrantGroupHandler:**
 * ```kotlin
 * val locationGroup = GrantGroupHandler(
 *     grantManager  = grantManager,
 *     grants        = listOf(AppGrant.LOCATION, AppGrant.LOCATION_ALWAYS),
 *     scope         = viewModelScope,
 *     eventListener = MyAnalyticsGrantListener()
 * )
 * ```
 *
 * Implement only the callbacks you care about — every function has a default
 * no-op body so you are never forced to override all of them.
 */
interface GrantEventListener {

    /**
     * Called when a permission request flow is initiated (after the initial
     * [GrantManager.checkStatus] completes and the state machine starts).
     *
     * @param grant  The permission being requested.
     * @param status The status returned by [GrantManager.checkStatus] at the
     *               start of the flow (e.g. [GrantStatus.NOT_DETERMINED] or
     *               [GrantStatus.DENIED]).
     */
    fun onRequested(grant: GrantPermission, status: GrantStatus) = Unit

    /**
     * Called when the permission is fully satisfied — the user granted access
     * either via the system dialog or by returning from Settings.
     *
     * @param grant  The permission that was granted.
     * @param status [GrantStatus.GRANTED] or [GrantStatus.PARTIAL_GRANTED].
     */
    fun onGranted(grant: GrantPermission, status: GrantStatus) = Unit

    /**
     * Called when the flow ends without the user granting the permission.
     *
     * Covers two terminal states:
     * - [GrantStatus.DENIED] — user tapped "Deny" (soft denial, can request again).
     * - [GrantStatus.DENIED_ALWAYS] — permanently denied; user must go to Settings.
     *
     * @param grant  The permission that was denied.
     * @param status [GrantStatus.DENIED] or [GrantStatus.DENIED_ALWAYS].
     */
    fun onDenied(grant: GrantPermission, status: GrantStatus) = Unit

    /**
     * Called when the rationale dialog is shown to the user.
     *
     * Only fires on platforms where rationale is supported (Android).
     * Useful for measuring rationale effectiveness.
     *
     * @param grant The permission for which the rationale is displayed.
     */
    fun onRationaleShown(grant: GrantPermission) = Unit

    /**
     * Called when the settings guide dialog is shown to the user
     * (i.e. the permission is permanently denied and the user needs to go to Settings).
     *
     * @param grant The permission that requires a settings change.
     */
    fun onSettingsGuideShown(grant: GrantPermission) = Unit

    /**
     * Called when the user confirms the settings guide and the library opens
     * the app's system settings page via [GrantManager.openSettings].
     *
     * @param grant The permission for which settings were opened.
     */
    fun onSettingsOpened(grant: GrantPermission) = Unit
}
