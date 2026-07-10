package dev.brewkits.grant.demo

import dev.brewkits.grant.AppGrant
import dev.brewkits.grant.GrantHandler
import dev.brewkits.grant.GrantManager
import dev.brewkits.grant.GrantStatus
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
    private val grantManager: GrantManager,
    private val scope: CoroutineScope,
    private val savedStateDelegate: dev.brewkits.grant.SavedStateDelegate = dev.brewkits.grant.NoOpSavedStateDelegate()
) {
    val galleryImagesOnlyGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.GALLERY_IMAGES_ONLY,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val galleryVideoOnlyGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.GALLERY_VIDEO_ONLY,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CAMERA,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val microphoneGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.MICROPHONE,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val motionGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.MOTION,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val calendarGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CALENDAR,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val contactsWriteGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.CONTACTS,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val readContactsGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.READ_CONTACTS,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val bluetoothAdvertiseGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.BLUETOOTH_ADVERTISE,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val readCalendarGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.READ_CALENDAR,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val locationGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.LOCATION,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val storageGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.STORAGE,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val notificationGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.NOTIFICATION,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val locationAlwaysGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.LOCATION_ALWAYS,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val bluetoothGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.BLUETOOTH,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    val galleryGrant = GrantHandler(
        grantManager = grantManager,
        grant = AppGrant.GALLERY,
        scope = scope,
        savedStateDelegate = savedStateDelegate
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun requestWithBusyCheck() {
        _grantTypeResult.value = "Requesting Camera with concurrency protection..."
        scope.launch {
            _isBusy.value = true
            cameraGrant.request(
                rationaleMessage = "We need camera access."
            ) {
                _grantTypeResult.value = "✓ Camera granted!"
                _isBusy.value = false
            }
            
            // Try to trigger again immediately to test "Already in progress" log
            cameraGrant.request(rationaleMessage = "Second request") { }
        }
    }

    /**
     * Scenario 6: Custom UI Integration
     * 
     * Demonstrate requestWithCustomUi for non-Compose or custom dialog flows.
     */
    fun requestWithCustomUiExample(
        onShowRationale: (String, () -> Unit, () -> Unit) -> Unit,
        onShowSettings: (String, () -> Unit, () -> Unit) -> Unit
    ) {
        _scenario5Result.value = "Requesting Camera via Custom UI flow..."
        cameraGrant.requestWithCustomUi(
            rationaleMessage = "Custom Rationale: Camera is needed for this demo.",
            settingsMessage = "Custom Settings: Please enable camera manually.",
            onShowRationale = onShowRationale,
            onShowSettings = onShowSettings
        ) {
            _scenario5Result.value = "✓ Camera granted via Custom UI!"
        }
    }

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

    /**
     * Group Handler for related grants
     * Used for: Photo location tagging app that needs BOTH location and storage
     */
    val locationAndStorageGroup = dev.brewkits.grant.GrantGroupHandler(
        grantManager = grantManager,
        grants = listOf(AppGrant.LOCATION, AppGrant.STORAGE),
        scope = scope
    )

    private val _parallelResult = MutableStateFlow("")
    val parallelResult: StateFlow<String> = _parallelResult.asStateFlow()

    /**
     * Scenario 2: Atomic Group Grant Requests
     *
     * Use case: Photo location tagging app that needs BOTH location and storage
     * Flow: Request both at once → System groups dialogs → Save geotagged photos
     *
     * This pattern allows requesting multiple independent grants simultaneously.
     * The OS handles them efficiently (e.g. one grouped dialog on Android).
     */
    fun requestParallelGrants() {
        _parallelResult.value = "Requesting location and storage grants via Group Handler..."

        locationAndStorageGroup.request(
            rationaleMessages = mapOf(
                AppGrant.LOCATION to "Location is needed to geotag your photos.",
                AppGrant.STORAGE to "Storage access is needed to save your photos."
            ),
            settingsMessages = mapOf(
                AppGrant.LOCATION to "Enable Location in Settings.",
                AppGrant.STORAGE to "Enable Storage in Settings."
            )
        ) {
            // Callback is only invoked when ALL grants in the group are granted!
            _parallelResult.update {
                it + "\n\n✓✓ All grants in group granted! Saving geotagged photos..."
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

    private val _grantTypeResult = MutableStateFlow("")
    val grantTypeResult: StateFlow<String> = _grantTypeResult.asStateFlow()

    private val _v11Result = MutableStateFlow("")
    val v11Result: StateFlow<String> = _v11Result.asStateFlow()

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
     * Scenario 3: denial-flow walkthrough — request Contacts and report into the scenario
     * card. Deny the OS dialog, request again to see the rationale, deny once more to land
     * on the settings guide. (The card was previously mis-wired to [requestGalleryGrant],
     * so this walkthrough never actually ran — fixed in 2.3.0.)
     */
    fun requestContactsDenialDemo() {
        _grantTypeResult.value = "Requesting READ_CONTACTS — deny it to walk the rationale → settings flow..."
        readContactsGrant.request(
            rationaleMessage = "We need contacts access to demonstrate the denial → rationale → settings flow.",
            settingsMessage = "Contacts access is disabled. Enable it in Settings.",
        ) {
            _grantTypeResult.value = "✓ CONTACTS granted!\n\nDenial-flow demo complete."
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
    fun requestGalleryGrant() {
        _grantTypeResult.value = "Requesting GALLERY grant (Supports PARTIAL_GRANTED)..."

        galleryGrant.request(
            rationaleMessage = "We need access to your gallery to let you pick a profile picture.",
            settingsMessage = "Gallery access is disabled. Enable it in Settings."
        ) {
            val status = galleryGrant.status.value
            if (status == dev.brewkits.grant.GrantStatus.PARTIAL_GRANTED) {
                _grantTypeResult.value = "✓ GALLERY PARTIAL_GRANTED!\n\nAccessing limited photos... 🖼️"
            } else {
                _grantTypeResult.value = "✓ GALLERY GRANTED!\n\nAccessing all photos... 🖼️"
            }
        }
    }

    fun requestGalleryImagesOnlyGrant() {
        _grantTypeResult.value = "Requesting GALLERY_IMAGES_ONLY..."
        galleryImagesOnlyGrant.request(
            rationaleMessage = "We need image-only access to let you pick a profile picture.",
            settingsMessage = "Image access is disabled. Enable it in Settings."
        ) {
            _grantTypeResult.value = "✓ GALLERY_IMAGES_ONLY GRANTED/PARTIAL!\n\nAccessing images only... 🖼️"
        }
    }

    fun requestGalleryVideoOnlyGrant() {
        _grantTypeResult.value = "Requesting GALLERY_VIDEO_ONLY..."
        galleryVideoOnlyGrant.request(
            rationaleMessage = "We need video-only access to let you upload a video clip.",
            settingsMessage = "Video access is disabled. Enable it in Settings."
        ) {
            _grantTypeResult.value = "✓ GALLERY_VIDEO_ONLY GRANTED/PARTIAL!\n\nAccessing videos only... 🎥"
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

    /**
     * Scenario 4a: Least-privilege contacts — read only
     * Use case: Contact picker, CRM viewer (does NOT need write access)
     */
    fun requestReadContactsGrant() {
        _v11Result.value = "Requesting READ_CONTACTS (read-only, least privilege)..."
        readContactsGrant.request(
            rationaleMessage = "We need read-only access to your contacts to help you find friends",
            settingsMessage = "Contacts access is disabled. Enable it in Settings"
        ) {
            _v11Result.value = "✓ READ_CONTACTS granted!\n\nContact picker ready (read-only)"
        }
    }

    /**
     * Scenario 4b: Full contacts access — read + write
     * Use case: Sync app, contact manager (needs to create/update contacts)
     */
    fun requestFullContactsGrant() {
        _v11Result.value = "Requesting CONTACTS (read + write)..."
        contactsWriteGrant.request(
            rationaleMessage = "We need read and write access to sync your contacts with the server",
            settingsMessage = "Contacts access is disabled. Enable it in Settings"
        ) {
            _v11Result.value = "✓ CONTACTS (read+write) granted!\n\nContact sync ready"
        }
    }

    /**
     * Scenario 4c: BLE advertising — peripheral mode
     * Use case: Beacon app, proximity detection sender side
     */
    fun requestBluetoothAdvertiseGrant() {
        _v11Result.value = "Requesting BLUETOOTH_ADVERTISE (BLE peripheral mode)..."
        bluetoothAdvertiseGrant.request(
            rationaleMessage = "We need Bluetooth advertising to make your device discoverable as a beacon",
            settingsMessage = "Bluetooth advertising is disabled. Enable Nearby Devices in Settings"
        ) {
            _v11Result.value = "✓ BLUETOOTH_ADVERTISE granted!\n\nDevice can now advertise as BLE beacon"
        }
    }

    /**
     * Scenario 4d: Calendar read-only
     * Use case: Calendar viewer, reminder app
     */
    fun requestReadCalendarGrant() {
        _v11Result.value = "Requesting READ_CALENDAR (read-only, least privilege)..."
        readCalendarGrant.request(
            rationaleMessage = "We need read-only access to your calendar to show your upcoming events",
            settingsMessage = "Calendar access is disabled. Enable it in Settings"
        ) {
            _v11Result.value = "✓ READ_CALENDAR granted!\n\nCalendar viewer ready (read-only)"
        }
    }

    private fun simulateBackgroundTracking() {
        scope.launch {
            _grantTypeResult.update {
                it + "\n\n📍 Now tracking your location in the background..."
            }
        }
    }

    private val _scenario5Result = MutableStateFlow("")
    val scenario5Result: StateFlow<String> = _scenario5Result.asStateFlow()

    /**
     * Scenario 5a: Motion — Dummy Query pattern on iOS.
     * Android: ACTIVITY_RECOGNITION (API 29+) or GMS fallback (< API 29)
     */
    fun requestMotionGrant() {
        _scenario5Result.value = "Requesting MOTION...\n${OsInfo.motionBehaviorNote()}"
        motionGrant.request(
            rationaleMessage = "Motion access is needed to detect your physical activity (walking, running, cycling).",
            settingsMessage = "Motion permission denied. Enable it in Settings > Privacy > Motion & Fitness"
        ) {
            val status = motionGrant.status.value
            _scenario5Result.value = when (status) {
                GrantStatus.GRANTED -> "✓ MOTION GRANTED\n${OsInfo.motionBehaviorNote()}\n\n🏃 Activity tracking active!"
                GrantStatus.DENIED_ALWAYS -> "⛔ MOTION BLOCKED\nUser must enable in Settings > Privacy > Motion & Fitness"
                else -> "✕ MOTION DENIED (status: $status)"
            }
        }
    }

    /**
     * Scenario 5b: Calendar full access (CALENDAR read+write).
     * iOS 17+: returns PARTIAL_GRANTED if user grants write-only access.
     */
    fun requestCalendarFullGrant() {
        _scenario5Result.value = "Requesting CALENDAR (full access)...\n• iOS 17+: System shows Full Access vs Add Only\n• Write-Only → PARTIAL_GRANTED"
        calendarGrant.request(
            rationaleMessage = "Calendar access is needed to create and manage your event reminders.",
            settingsMessage = "Calendar access denied. Enable it in Settings > Privacy > Calendars"
        ) {
            val status = calendarGrant.status.value
            _scenario5Result.value = when (status) {
                GrantStatus.GRANTED -> "✓ CALENDAR GRANTED (Full Access)\n\n📅 Can read and write all events"
                GrantStatus.PARTIAL_GRANTED -> "◑ CALENDAR PARTIAL (Write-Only on iOS 17+)\n\n📅 Can add events, cannot read existing ones"
                GrantStatus.DENIED_ALWAYS -> "⛔ CALENDAR BLOCKED\nEnable in Settings > Privacy > Calendars"
                else -> "✕ CALENDAR DENIED (status: $status)"
            }
        }
    }

    /**
     * Scenario 5c: Gallery with explicit PARTIAL_GRANTED handling.
     * Android 14+: Shows "Select Photos" option → PARTIAL_GRANTED
     * iOS: "Limited Library" → PARTIAL_GRANTED
     */
    fun requestGalleryPartialGrant() {
        _scenario5Result.value = "Requesting GALLERY...\n${OsInfo.galleryBehaviorNote()}"
        galleryGrant.request(
            rationaleMessage = "Photo library access is needed to let you pick and share photos.",
            settingsMessage = "Gallery access denied. Enable it in Settings > Privacy > Photos"
        ) {
            val status = galleryGrant.status.value
            _scenario5Result.value = when (status) {
                GrantStatus.GRANTED -> "✓ GALLERY GRANTED (Full Access)\n${OsInfo.galleryBehaviorNote()}\n\n🖼️ All photos accessible"
                GrantStatus.PARTIAL_GRANTED -> "◑ GALLERY PARTIAL (Limited Access!)\n${OsInfo.galleryBehaviorNote()}\n\n🖼️ Only selected photos accessible"
                GrantStatus.DENIED_ALWAYS -> "⛔ GALLERY BLOCKED\nEnable in Settings > Privacy > Photos"
                else -> "✕ GALLERY DENIED (status: $status)"
            }
        }
    }

    /**
     * Scenario 5d: Notification — shows the API 33+ requirement.
     */
    fun requestNotificationScenario5() {
        _scenario5Result.value = "Requesting NOTIFICATION...\n${OsInfo.notificationBehaviorNote()}"
        notificationGrant.request(
            rationaleMessage = "Enable notifications to receive real-time updates and reminders.",
            settingsMessage = "Notifications disabled. Enable in Settings > Notifications"
        ) {
            _scenario5Result.value = "✓ NOTIFICATION GRANTED\n${OsInfo.notificationBehaviorNote()}\n\n🔔 Push notifications enabled!"
        }
    }

    fun resetResults() {
        _sequentialResult.value = ""
        _parallelResult.value = ""
        _grantTypeResult.value = ""
        _v11Result.value = ""
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
            storageGrant,
            locationAlwaysGrant,
            galleryGrant,
            galleryImagesOnlyGrant,
            galleryVideoOnlyGrant,
            notificationGrant,
            bluetoothGrant,
            bluetoothAdvertiseGrant,
            motionGrant,
            calendarGrant,
            contactsWriteGrant,
            readContactsGrant,
            readCalendarGrant
        ).forEach { handler ->
            handler.refreshStatus()
        }
        locationAndStorageGroup.refreshAllStatuses()
    }
}
