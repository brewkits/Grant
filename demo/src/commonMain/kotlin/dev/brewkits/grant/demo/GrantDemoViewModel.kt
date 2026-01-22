package dev.brewkits.grant.demo

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.grantManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Demo ViewModel showcasing various grant request scenarios.
 *
 * This demo covers:
 * 1. **Sequential Grants** - Request grants one after another
 * 2. **Parallel Grants** - Request multiple grants simultaneously
 * 3. **Runtime vs Dangerous Grants** - Examples of both categories
 *
 * **Grant Categories**:
 * - **Runtime**: Normal grants that don't pose risk (e.g., NOTIFICATION)
 * - **Dangerous**: Grants accessing sensitive data (e.g., CAMERA, LOCATION, CONTACTS)
 */
class GrantDemoViewModel(
    private val grantManager: grantManager,
    private val scope: CoroutineScope
) {
    // ==============================================
    // SCENARIO 1: SEQUENTIAL GRANTS
    // Request camera first, then microphone after
    // ==============================================

    /**
     * DANGEROUS grant - Camera access
     * Used for: Photo/video capture, QR scanning
     */
    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = scope
    )

    /**
     * DANGEROUS grant - Microphone access
     * Used for: Audio recording, voice notes
     */
    val microphoneGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.MICROPHONE,
        scope = scope
    )

    val motionGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.MOTION,
        scope = scope
    )

    private val _sequentialResult = MutableStateFlow("")
    val sequentialResult: StateFlow<String> = _sequentialResult.asStateFlow()

    /**
     * Scenario 1: Sequential Grant Requests
     *
     * Use case: Video recording app that needs BOTH camera and microphone
     * Flow: Camera → (if granted) → Microphone → (if both granted) → Start recording
     *
     * This pattern ensures we only request the second grant after the first is granted.
     */
    fun requestSequentialGrants() {
        _sequentialResult.value = "Requesting camera..."

        // Step 1: Request Camera
        cameraGrant.request(
            rationaleMessage = "Camera is required to capture video for your recordings",
            settingsMessage = "Camera access is disabled. Enable it in Settings > Grants > Camera"
        ) {
            // Camera granted! Now request microphone
            _sequentialResult.value = "✓ Camera granted! Now requesting microphone..."

            // Step 2: Request Microphone (only if camera was granted)
            microphoneGrant.request(
                rationaleMessage = "Microphone is required to record audio with your video",
                settingsMessage = "Microphone access is disabled. Enable it in Settings > Grants > Microphone"
            ) {
                // Both granted! Ready to record
                _sequentialResult.value = "✓✓ Both grants granted! Ready to record video with audio"
                simulateVideoRecording()
            }
        }
    }

    private fun simulateVideoRecording() {
        scope.launch {
            _sequentialResult.update { it + "\n\n🎥 Recording video with audio..." }
        }
    }

    // ==============================================
    // SCENARIO 2: PARALLEL GRANTS
    // Request location + storage at the same time
    // ==============================================

    /**
     * DANGEROUS grant - Location access (when in use)
     * Used for: Maps, location-based services
     */
    val locationGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.LOCATION,
        scope = scope
    )

    /**
     * DANGEROUS grant - Storage access
     * Used for: Reading/writing files, photo galleries
     */
    val storageGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.STORAGE,
        scope = scope
    )

    private val _parallelResult = MutableStateFlow("")
    val parallelResult: StateFlow<String> = _parallelResult.asStateFlow()

    private var locationGranted = false
    private var storageGranted = false

    /**
     * Scenario 2: Parallel Grant Requests
     *
     * Use case: Photo location tagging app that needs BOTH location and storage
     * Flow: Request both → Wait for both → Save geotagged photos
     *
     * This pattern allows requesting multiple independent grants simultaneously.
     * User sees both dialogs in sequence, but we handle them in parallel.
     */
    fun requestParallelGrants() {
        _parallelResult.value = "Requesting location and storage grants..."
        locationGranted = false
        storageGranted = false

        // Request both grants in parallel
        // Note: System shows dialogs one at a time, but we handle results concurrently

        locationGrant.request(
            rationaleMessage = "Location is needed to geotag your photos with the place they were taken",
            settingsMessage = "Location access is disabled. Enable it in Settings > Grants > Location"
        ) {
            locationGranted = true
            _parallelResult.update { it + "\n✓ Location grant granted" }
            checkParallelCompletion()
        }

        storageGrant.request(
            rationaleMessage = "Storage access is needed to save your photos to the gallery",
            settingsMessage = "Storage access is disabled. Enable it in Settings > Grants > Storage"
        ) {
            storageGranted = true
            _parallelResult.update { it + "\n✓ Storage grant granted" }
            checkParallelCompletion()
        }
    }

    private fun checkParallelCompletion() {
        if (locationGranted && storageGranted) {
            _parallelResult.update {
                it + "\n\n✓✓ All grants granted! Saving geotagged photos..."
            }
            simulatePhotoSaving()
        }
    }

    private fun simulatePhotoSaving() {
        scope.launch {
            _parallelResult.update {
                it + "\n\n📸 Photo saved to gallery with location metadata!"
            }
        }
    }

    // ==============================================
    // SCENARIO 3: RUNTIME vs DANGEROUS GRANTS
    // Show the difference in behavior
    // ==============================================

    /**
     * RUNTIME grant - Notifications (POST_NOTIFICATIONS on Android 13+)
     * Note: On older Android or iOS, may be granted automatically
     */
    val notificationGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.NOTIFICATION,
        scope = scope
    )

    /**
     * DANGEROUS grant - Contacts access
     * Always requires explicit user consent
     */
    val contactsGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CONTACTS,
        scope = scope
    )

    /**
     * DANGEROUS grant - Location (always/background)
     * Highest risk level - background access to location
     */
    val locationAlwaysGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.LOCATION_ALWAYS,
        scope = scope
    )

    /**
     * DANGEROUS grant - Bluetooth access
     * Used for: Bluetooth device connections
     */
    val bluetoothGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.BLUETOOTH,
        scope = scope
    )

    /**
     * DANGEROUS grant - Gallery/Photo library access
     * Used for: Selecting photos from gallery
     */
    val galleryGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.GALLERY,
        scope = scope
    )

    private val _grantTypeResult = MutableStateFlow("")
    val grantTypeResult: StateFlow<String> = _grantTypeResult.asStateFlow()

    /**
     * Scenario 3a: Runtime Grant Example
     *
     * Runtime grants are typically less sensitive:
     * - May be granted by default on some platforms
     * - Less invasive to user privacy
     * - Example: Notifications, Bluetooth (for some use cases)
     */
    fun requestRuntimeGrant() {
        _grantTypeResult.value = "Requesting RUNTIME grant (Notifications)..."

        notificationGrant.request(
            rationaleMessage = "Enable notifications to receive important updates and reminders",
            settingsMessage = "Notification access is disabled. Enable it in Settings > Notifications"
        ) {
            _grantTypeResult.value = "✓ RUNTIME grant granted!\n\nNotifications enabled 🔔"
        }
    }

    /**
     * Scenario 3b: Dangerous Grant Example
     *
     * Dangerous grants access sensitive user data:
     * - Always require explicit user consent
     * - Can be revoked at any time
     * - Examples: Camera, Location, Contacts, Microphone
     */
    fun requestDangerousGrant() {
        _grantTypeResult.value = "Requesting DANGEROUS grant (Contacts)..."

        contactsGrant.request(
            rationaleMessage = "We need access to your contacts to help you find and invite friends",
            settingsMessage = "Contacts access is disabled. Enable it in Settings > Grants > Contacts"
        ) {
            _grantTypeResult.value = "✓ DANGEROUS grant granted!\n\nAccessing contacts... 📇"
            simulateContactsAccess()
        }
    }

    /**
     * Scenario 3c: Most Dangerous Grant Example
     *
     * Background location is considered highest risk:
     * - Can track user continuously
     * - Major privacy implications
     * - Often requires additional rationale or two-step flow
     */
    fun requestMostDangerousGrant() {
        _grantTypeResult.value = "Requesting MOST DANGEROUS grant (Background Location)..."

        locationAlwaysGrant.request(
            rationaleMessage = "Background location is needed to track your running routes even when the app is closed.\n\nThis is a sensitive grant that affects your privacy.",
            settingsMessage = "Background location is disabled. Enable it in Settings > Grants > Location > Allow all the time"
        ) {
            _grantTypeResult.value = "✓ MOST DANGEROUS grant granted!\n\n⚠️ Background tracking enabled"
            simulateBackgroundTracking()
        }
    }

    private fun simulateContactsAccess() {
        scope.launch {
            _grantTypeResult.update {
                it + "\n\nFound 42 contacts in your address book"
            }
        }
    }

    private fun simulateBackgroundTracking() {
        scope.launch {
            _grantTypeResult.update {
                it + "\n\n📍 Now tracking your location in the background..."
            }
        }
    }

    // ==============================================
    // UTILITY FUNCTIONS
    // ==============================================

    fun resetResults() {
        _sequentialResult.value = ""
        _parallelResult.value = ""
        _grantTypeResult.value = ""
        locationGranted = false
        storageGranted = false
    }


    /**
     * Refresh all grant statuses
     * Useful to check status after user returns from Settings
     */
    fun refreshAllGrants() {
        listOf(
            cameraGrant,
            microphoneGrant,
            locationGrant,
            locationAlwaysGrant,
            storageGrant,
            galleryGrant,
            contactsGrant,
            notificationGrant,
            bluetoothGrant,
            motionGrant
        ).forEach { handler ->
            handler.refreshStatus()
        }
    }
}
