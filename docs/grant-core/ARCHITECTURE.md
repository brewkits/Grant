# 🏗️ Architecture & Design Decisions

This document explains the architectural decisions behind KMP Grant library and provides insights into the design patterns used.

## 🎯 Design Goals

1. **UI Independence**: Business logic must not depend on UI framework
2. **Platform Abstraction**: Hide iOS/Android differences behind clean interfaces
3. **Clean ViewModels**: Minimize boilerplate in feature code
4. **Platform Parity**: Single API that works identically on iOS and Android
5. **Extensibility**: Easy to add new grants without breaking changes
6. **No Third-Party Dependencies**: Full control over implementation

## 📐 Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│              Feature Layer (App Code)                    │
│         ViewModel + UI (Compose/SwiftUI/XML)             │
│                                                           │
│  • CameraViewModel                                       │
│  • LocationViewModel                                     │
│  • ProfileViewModel                                      │
└───────────────────────┬─────────────────────────────────┘
                        │ depends on (interface only)
┌───────────────────────▼─────────────────────────────────┐
│           Abstraction Layer (Core - commonMain)          │
│                                                           │
│  • grantManager (interface) ◄───────────────────────┼─ 🎯 App only sees this
│  • AppGrant (enum)                                  │
│  • GrantStatus (enum)                               │
│  • GrantHandler (composition helper)                │
│  • GrantGroupHandler (multi-grant)             │
└───────────────────────┬─────────────────────────────────┘
                        │ implemented by
┌───────────────────────▼─────────────────────────────────┐
│         Implementation Layer (Custom - commonMain)       │
│                                                           │
│  • MyGrantManager                                   │
│  • PlatformGrantDelegate (expect/actual)            │
└───────────────────────┬─────────────────────────────────┘
                        │ platform-specific
         ┌──────────────┴──────────────┐
         │                             │
┌────────▼────────┐           ┌────────▼────────┐
│   androidMain    │           │    iosMain       │
│                  │           │                  │
│ • Activity Result│           │ • AVFoundation   │
│ • ContextCompat  │           │ • CoreLocation   │
│ • Transparent    │           │ • Contacts       │
│   Activity       │           │ • UserNotif...   │
│ • Settings       │           │ • Settings       │
└──────────────────┘           └──────────────────┘
```

## 🎨 Design Patterns

### 1. Wrapper/Adapter Pattern

**Problem**: Directly using platform APIs in business logic creates:
- **Tight Coupling**: Hard to change implementation
- **Test Complexity**: Need to mock platform-specific types
- **Code Duplication**: Same logic repeated across features

**Solution**: Abstract behind `grantManager` interface

```kotlin
// ❌ BAD: Direct platform dependency
class CameraViewModel(
    private val context: Context  // Android-specific!
) {
    suspend fun requestCamera() {
        if (ContextCompat.checkSelfGrant(context, CAMERA) == GRANTED) {
            // ...
        }
    }
}

// ✅ GOOD: Depend on abstraction
class CameraViewModel(
    private val grantManager: GrantManager  // Platform-agnostic!
) {
    suspend fun requestCamera() {
        val status = grantManager.checkStatus(AppGrant.CAMERA)
        when (status) {
            GrantStatus.GRANTED -> openCamera()
            // ...
        }
    }
}
```

**Benefits**:
- ✅ Testable (mock interface, not platform types)
- ✅ Platform-independent ViewModels
- ✅ Single source of truth for grant logic

### 2. Composition Pattern (GrantHandler)

**Problem**: Without helper, ViewModels become bloated:

```kotlin
// ❌ BAD: 40+ lines of boilerplate per grant
class CameraViewModel(private val manager: GrantManager) : ViewModel() {
    private val _showRationale = MutableStateFlow(false)
    val showRationale = _showRationale.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings = _showSettings.asStateFlow()

    private val _isRequesting = MutableStateFlow(false)
    val isRequesting = _isRequesting.asStateFlow()

    fun onCaptureClick() {
        viewModelScope.launch {
            _isRequesting.value = true
            when (manager.checkStatus(AppGrant.CAMERA)) {
                GrantStatus.GRANTED -> openCamera()
                GrantStatus.NOT_DETERMINED -> requestGrant()
                GrantStatus.DENIED -> _showRationale.value = true
                GrantStatus.DENIED_ALWAYS -> _showSettings.value = true
            }
            _isRequesting.value = false
        }
    }

    fun onRationaleConfirmed() {
        viewModelScope.launch {
            _showRationale.value = false
            _isRequesting.value = true
            val result = manager.request(AppGrant.CAMERA)
            if (result == GrantStatus.GRANTED) {
                openCamera()
            } else if (result == GrantStatus.DENIED_ALWAYS) {
                _showSettings.value = true
            }
            _isRequesting.value = false
        }
    }

    fun onSettingsConfirmed() {
        _showSettings.value = false
        manager.openSettings()
    }

    fun onDismiss() {
        _showRationale.value = false
        _showSettings.value = false
    }

    // ... 40+ lines total
}
```

**Solution**: Compose with `GrantHandler`

```kotlin
// ✅ GOOD: 3 lines!
class CameraViewModel(manager: GrantManager) : ViewModel() {
    val cameraGrant = GrantHandler(
        manager,
        AppGrant.CAMERA,
        viewModelScope
    )

    fun onCaptureClick() {
        cameraGrant.request { openCamera() }
    }
}
```

**Benefits**:
- ✅ **DRY**: Write logic once, reuse everywhere
- ✅ **Consistency**: All features follow same flow
- ✅ **Maintainability**: Change logic in one place
- ✅ **13x Less Code**: 3 lines vs 40+ lines

### 3. Expect/Actual Pattern

**Implementation**: Platform-specific code via Kotlin Multiplatform

```kotlin
// commonMain/kotlin/.../impl/MyGrantManager.kt
expect class PlatformGrantDelegate {
    suspend fun checkStatus(grant: AppGrant): GrantStatus
    suspend fun request(grant: AppGrant): GrantStatus
    fun openSettings()
}

// androidMain/kotlin/.../impl/PlatformGrantDelegate.android.kt
actual class PlatformGrantDelegate(
    private val context: Context
) {
    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        // Android implementation using ContextCompat
        val androidGrants = grant.toAndroidGrants()
        return if (androidGrants.all { hasGrant(it) }) {
            GrantStatus.GRANTED
        } else {
            // Check shouldShowRequestGrantRationale...
        }
    }

    actual suspend fun request(grant: AppGrant): GrantStatus {
        // Android implementation using GrantRequestActivity
        val requestId = GrantRequestActivity.requestGrants(
            context,
            grant.toAndroidGrants()
        )
        // Wait for result...
    }
}

// iosMain/kotlin/.../impl/PlatformGrantDelegate.ios.kt
actual class PlatformGrantDelegate {
    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        // iOS implementation
        return when (grant) {
            AppGrant.CAMERA -> {
                when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                    AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
                    AVAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
                    AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
                    else -> GrantStatus.DENIED
                }
            }
            // ... other grants
        }
    }
}
```

**Benefits**:
- ✅ **Type Safety**: Compiler ensures platform implementations exist
- ✅ **Shared Logic**: Common code in `MyGrantManager`
- ✅ **Platform Flexibility**: Each platform can optimize independently

### 4. State Flow Pattern

**Design Decision**: Use `StateFlow` instead of callbacks

```kotlin
// ❌ Alternative: Callbacks (imperative, hard to test)
interface GrantHandler {
    fun request(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onDeniedAlways: () -> Unit
    )
}

// ✅ Our Approach: StateFlow (declarative, easy to test)
class GrantHandler {
    val state: StateFlow<GrantUiState>

    data class GrantUiState(
        val showRationale: Boolean = false,
        val showSettingsGuide: Boolean = false,
        val isRequesting: Boolean = false
    )
}
```

**Benefits**:
- ✅ Natural fit with Compose (`collectAsState()`)
- ✅ Easy to combine multiple grants
- ✅ Testable (assert state changes)
- ✅ Immutable UI state

### 5. Transparent Activity Pattern (Android)

**Problem**: Activity Result API requires a valid Activity context

**Solution**: Use a transparent, no-UI activity for grant requests

```kotlin
// grant-core/src/androidMain/kotlin/.../impl/GrantRequestActivity.kt
class GrantRequestActivity : ComponentActivity() {
    // Transparent theme, no UI
    // Handles grant request, returns result, finishes

    companion object {
        fun requestGrants(
            context: Context,
            grants: List<String>
        ): String {
            val requestId = UUID.randomUUID().toString()
            val intent = Intent(context, GrantRequestActivity::class.java)
            context.startActivity(intent)
            return requestId
        }
    }
}
```

**Benefits**:
- ✅ No Fragment/Activity requirement in app code
- ✅ Works from anywhere (ViewModel, Repository, etc.)
- ✅ Lifecycle-safe
- ✅ User never sees the activity

### 6. Intelligent Dialog Flow Pattern

**Problem**: Managing grant dialogs requires:
- Tracking grant state (NOT_DETERMINED vs DENIED vs DENIED_ALWAYS)
- Knowing which dialog to show (system vs rationale vs settings)
- Handling platform differences (Android's `shouldShowRequestPermissionRationale`, iOS states)
- Managing dialog visibility and user actions

**Traditional Approach** (manual state management):

```kotlin
// ❌ Every ViewModel needs this logic
class FeatureViewModel {
    private val _showRationale = MutableStateFlow(false)
    private val _showSettings = MutableStateFlow(false)

    fun onButtonClick() {
        viewModelScope.launch {
            val status = grantManager.checkStatus(grant)
            when (status) {
                GrantStatus.NOT_DETERMINED -> {
                    val result = grantManager.request(grant)
                    when (result) {
                        GrantStatus.GRANTED -> executeFeature()
                        GrantStatus.DENIED -> _showRationale.value = true
                        GrantStatus.DENIED_ALWAYS -> _showSettings.value = true
                    }
                }
                GrantStatus.DENIED -> _showRationale.value = true
                GrantStatus.DENIED_ALWAYS -> _showSettings.value = true
                GrantStatus.GRANTED -> executeFeature()
            }
        }
    }

    fun onRationaleConfirmed() {
        viewModelScope.launch {
            _showRationale.value = false
            val result = grantManager.request(grant)
            // ... handle result again
        }
    }

    // More boilerplate...
}
```

**Our Solution**: Intelligent state-based dialog management in `GrantHandler`

```kotlin
// ✅ GrantHandler handles everything automatically
class GrantHandler(
    private val grantManager: GrantManager,
    private val grant: AppGrant,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(GrantUiState())
    val state: StateFlow<GrantUiState> = _state.asStateFlow()

    fun request(
        rationaleMessage: String? = null,
        settingsMessage: String? = null,
        onGranted: () -> Unit
    ) {
        scope.launch {
            val currentStatus = grantManager.checkStatus(grant)
            handleStatus(currentStatus, rationaleMessage, settingsMessage)
        }
    }

    private suspend fun handleStatus(
        status: GrantStatus,
        rationaleMessage: String?,
        settingsMessage: String?
    ) {
        when (status) {
            GrantStatus.GRANTED -> {
                resetState()
                onGrantedCallback?.invoke()
                onGrantedCallback = null
            }
            GrantStatus.NOT_DETERMINED -> {
                val result = grantManager.request(grant)
                handleStatus(result, rationaleMessage, settingsMessage)
            }
            GrantStatus.DENIED -> {
                // ALWAYS show rationale - user needs explanation
                _state.update {
                    it.copy(
                        isVisible = true,
                        showRationale = true,
                        rationaleMessage = rationaleMessage
                    )
                }
            }
            GrantStatus.DENIED_ALWAYS -> {
                // ALWAYS show settings guide - only way to fix
                _state.update {
                    it.copy(
                        isVisible = true,
                        showSettingsGuide = true,
                        settingsMessage = settingsMessage
                    )
                }
            }
        }
    }
}
```

**Key Intelligence**:

1. **Automatic State Detection**
   ```kotlin
   // Android
   val status = if (hasPermission) {
       GrantStatus.GRANTED
   } else if (shouldShowRequestPermissionRationale(permission)) {
       GrantStatus.DENIED  // Can show rationale
   } else {
       // Either never asked or permanently denied
       // Let request() determine via actual request
       GrantStatus.NOT_DETERMINED
   }

   // iOS
   val status = when (authorizationStatus) {
       AVAuthorizationStatusAuthorized -> GrantStatus.GRANTED
       AVAuthorizationStatusDenied -> GrantStatus.DENIED_ALWAYS
       AVAuthorizationStatusNotDetermined -> GrantStatus.NOT_DETERMINED
   }
   ```

2. **Smart Dialog Decision**
   - `NOT_DETERMINED` → Request immediately (shows system dialog)
   - `DENIED` → Always show rationale (explain why needed)
   - `DENIED_ALWAYS` → Always show settings guide (only way to fix)
   - `GRANTED` → Execute callback immediately

3. **Platform-Aware Multi-Permission**
   ```kotlin
   // Android: Location Always = 3 permissions on API 30+
   listOf(
       ACCESS_FINE_LOCATION,
       ACCESS_COARSE_LOCATION,
       ACCESS_BACKGROUND_LOCATION
   )

   // Check if ANY denied permission can show rationale
   val deniedGrants = grantsResult.filter { !it.value }.keys
   val anyCanShowRationale = deniedGrants.any { grant ->
       shouldShowRequestPermissionRationale(grant)
   }

   // Only DENIED_ALWAYS if ALL are permanently denied
   return if (anyCanShowRationale) {
       GrantResult.DENIED
   } else {
       GrantResult.DENIED_PERMANENTLY
   }
   ```

4. **Automatic Cleanup**
   ```kotlin
   fun onDismiss() {
       resetState()
       // Clear callback to prevent memory leak
       onGrantedCallback = null
   }
   ```

**Decision Tree**:

```
request(onGranted: () -> Unit)
    │
    ├─ checkStatus()
    │   │
    │   ├─ GRANTED ────────────────► Execute onGranted()
    │   │
    │   ├─ NOT_DETERMINED ────────► request() → System Dialog
    │   │                              │
    │   │                              ├─ GRANTED ────► Execute onGranted()
    │   │                              ├─ DENIED ─────► Show Rationale
    │   │                              └─ DENIED_ALWAYS ─► Show Settings
    │   │
    │   ├─ DENIED ────────────────► Show Rationale Dialog
    │   │                            "Why we need this permission"
    │   │                            [Grant Permission] [Cancel]
    │   │                              │
    │   │                              ├─ Confirmed ──► request() again
    │   │                              └─ Dismissed ──► Cleanup
    │   │
    │   └─ DENIED_ALWAYS ─────────► Show Settings Dialog
    │                                "Go to Settings to enable"
    │                                [Open Settings] [Later]
    │                                  │
    │                                  ├─ Confirmed ──► Open Settings
    │                                  └─ Dismissed ──► Cleanup
```

**Benefits**:

✅ **Zero Configuration** - Works automatically without manual state tracking

✅ **Platform-Aware** - Handles Android `shouldShowRequestPermissionRationale` and iOS states correctly

✅ **Multi-Permission Smart** - Correctly analyzes complex permission groups (Location Always, Gallery, etc.)

✅ **Always Shows Right Dialog** - Rationale when needed, Settings when required, nothing when already granted

✅ **Memory Safe** - Automatic callback cleanup prevents leaks

✅ **Consistent UX** - Same behavior across all features

✅ **Testable** - State-based design makes testing easy

**Usage Example**:

```kotlin
// ViewModel (3 lines!)
val cameraGrant = GrantHandler(manager, AppGrant.CAMERA, viewModelScope)

fun onTakePhotoClick() {
    cameraGrant.request { openCamera() }
}

// UI (automatic dialog handling)
@Composable
fun CameraScreen(vm: CameraViewModel) {
    GrantDialogHandler(handler = vm.cameraGrant)

    Button(onClick = { vm.onTakePhotoClick() }) {
        Text("Take Photo")
    }
}

@Composable
fun GrantDialogHandler(handler: GrantHandler) {
    val state by handler.state.collectAsState()

    if (state.showRationale) {
        AlertDialog(
            title = { Text("Grant Required") },
            text = { Text(state.rationaleMessage ?: "...") },
            confirmButton = {
                TextButton(onClick = { handler.onRationaleConfirmed() }) {
                    Text("Grant Permission")
                }
            }
        )
    }

    if (state.showSettingsGuide) {
        AlertDialog(
            title = { Text("Grant Denied") },
            text = { Text(state.settingsMessage ?: "...") },
            confirmButton = {
                TextButton(onClick = { handler.onSettingsConfirmed() }) {
                    Text("Open Settings")
                }
            }
        )
    }
}
```

## 🔧 Implementation Details

### Android Platform

**Key Technologies**:
- `Activity Result API` - Modern grant request API
- `ContextCompat` - Grant status checks
- `Transparent Activity` - Handles grants without UI
- `StateFlow` - Async result communication

**Grant Flow**:
1. App calls `grantManager.request(AppGrant.CAMERA)`
2. `PlatformGrantDelegate` starts `GrantRequestActivity`
3. Transparent activity shows system grant dialog
4. User responds (Allow/Deny)
5. Activity posts result to `StateFlow` keyed by request ID
6. `PlatformGrantDelegate` suspends until result arrives
7. Activity finishes (user never saw it)

### iOS Platform

**Key Technologies**:
- `AVFoundation` - Camera/Microphone grants
- `CoreLocation` - Location grants with delegate
- `Contacts` - Contacts grants
- `UserNotifications` - Notification grants
- `CoreMotion` - Motion/Activity grants

**Grant Flow**:
1. App calls `grantManager.request(AppGrant.CAMERA)`
2. `PlatformGrantDelegate` calls iOS framework API
3. iOS shows system grant alert
4. Framework calls completion handler
5. Result converted to `GrantStatus`

**Thread Safety**:
- All iOS UI operations run on main thread
- Use `MainThreadUtils` for thread safety

## 🧪 Testing Strategy

### Unit Testing ViewModels

```kotlin
class CameraViewModelTest {
    private lateinit var mockManager: GrantManager
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setup() {
        mockManager = mockk()
        viewModel = CameraViewModel(mockManager)
    }

    @Test
    fun `when grant granted, camera opens`() = runTest {
        // Given
        coEvery { mockManager.request(AppGrant.CAMERA) } returns
            GrantStatus.GRANTED

        // When
        viewModel.onCaptureClick()

        // Then
        // Assert camera opened
    }

    @Test
    fun `when grant denied, shows rationale`() = runTest {
        // Given
        coEvery { mockManager.checkStatus(any()) } returns
            GrantStatus.DENIED

        // When
        viewModel.cameraGrant.request { }

        // Then
        assertTrue(viewModel.cameraGrant.state.value.showRationale)
    }
}
```

### Testing GrantHandler

```kotlin
class GrantHandlerTest {
    @Test
    fun `when grant granted immediately, runs action`() = runTest {
        val mockManager = mockk<grantManager> {
            coEvery { checkStatus(any()) } returns GrantStatus.GRANTED
        }

        val handler = GrantHandler(
            mockManager,
            AppGrant.CAMERA,
            this
        )

        var actionRan = false
        handler.request { actionRan = true }

        assertTrue(actionRan)
    }
}
```

## 📊 Design Trade-offs

### Trade-off 1: Custom vs Third-Party Library

**Decision**: Fully custom implementation (no moko-grants)

**Pros**:
- ✅ Full control over implementation
- ✅ No dependency on external library
- ✅ Optimized for our use cases
- ✅ Smaller binary size
- ✅ Support for all grants we need (MOTION, etc.)

**Cons**:
- ❌ More code to maintain
- ❌ Need to handle platform updates ourselves

**Mitigation**: Well-tested, clean architecture makes maintenance easier

### Trade-off 2: No UI Components

**Decision**: Core library is UI-agnostic

**Pros**:
- ✅ Works with any UI framework (Compose, SwiftUI, XML)
- ✅ Smaller dependency graph
- ✅ Can use in non-UI code
- ✅ Flexibility in dialog design

**Cons**:
- ❌ Users must write dialog UI
- ❌ No out-of-box Material dialogs

**Mitigation**: Provide clear examples in docs and demo app

### Trade-off 3: Single Grant Per Handler

**Decision**: Each `GrantHandler` manages ONE grant

**Pros**:
- ✅ Simple mental model
- ✅ Easy to compose multiple grants
- ✅ Clear separation of concerns
- ✅ Better for most use cases

**Cons**:
- ❌ Need multiple handlers for multiple grants

**Mitigation**: Provide `GrantGroupHandler` for bundled grants

### Trade-off 4: Koin for DI

**Decision**: Use Koin for dependency injection

**Pros**:
- ✅ Kotlin-first, multiplatform-ready
- ✅ Simple setup
- ✅ No code generation
- ✅ Lightweight

**Cons**:
- ❌ Runtime DI (vs compile-time)
- ❌ Another dependency

**Mitigation**: Core interfaces are DI-agnostic, easy to swap

## 🔌 Extensibility Points

### Adding New Grants

**Step 1**: Add to `AppGrant` enum

```kotlin
// grant-core/src/commonMain/kotlin/.../AppGrant.kt
enum class AppGrant {
    // Existing...
    CAMERA,
    LOCATION,

    // NEW!
    FACE_ID,
    HEALTH_DATA
}
```

**Step 2**: Update `PlatformGrantDelegate` implementations

```kotlin
// androidMain
actual class PlatformGrantDelegate(context: Context) {
    private fun AppGrant.toAndroidGrants(): List<String> {
        return when (this) {
            // Existing...

            // NEW!
            AppGrant.FACE_ID -> listOf(
                "android.permission.USE_BIOMETRIC"
            )
        }
    }
}

// iosMain
actual class PlatformGrantDelegate {
    actual suspend fun checkStatus(grant: AppGrant): GrantStatus {
        return when (grant) {
            // Existing...

            // NEW!
            AppGrant.FACE_ID -> {
                val context = LAContext()
                // Check biometry availability...
            }
        }
    }
}
```

**That's it!** No other code changes needed. App code just uses:

```kotlin
val faceIdGrant = GrantHandler(
    manager,
    AppGrant.FACE_ID,  // Use new grant!
    scope
)
```

### Custom Grant Manager

Want to use a different implementation? Just implement the interface:

```kotlin
class MyCustomGrantManager : GrantManager {
    override suspend fun checkStatus(grant: AppGrant): GrantStatus {
        // Your custom logic
        // Could use different library, API, or approach
    }

    override suspend fun request(grant: AppGrant): GrantStatus {
        // Your custom logic
    }

    override fun openSettings() {
        // Your custom logic
    }
}

// Update DI module
val grantModule = module {
    single<grantManager> { MyCustomGrantManager() }
}
```

All app code continues to work unchanged!

## ❌ Anti-Patterns to Avoid

### 1. Requesting Grant on Init

```kotlin
// ❌ BAD: Request without user action
class ViewModel(manager: GrantManager) {
    init {
        viewModelScope.launch {
            manager.request(AppGrant.CAMERA)  // Violates UX guidelines!
        }
    }
}

// ✅ GOOD: Request on user action
class ViewModel(manager: GrantManager) {
    fun onCaptureClick() {
        cameraGrant.request { openCamera() }
    }
}
```

### 2. Mixing UI Logic in ViewModel

```kotlin
// ❌ BAD: @Composable in ViewModel
class ViewModel {
    @Composable
    fun ShowGrantDialog() {  // Violates separation of concerns!
        AlertDialog(...)
    }
}

// ✅ GOOD: UI in composable, state in ViewModel
@Composable
fun Screen(viewModel: ViewModel) {
    val state by viewModel.grant.state.collectAsState()

    if (state.showRationale) {
        AlertDialog(...)  // UI code in UI layer
    }
}
```

### 3. Directly Using Platform APIs

```kotlin
// ❌ BAD: Platform-specific code in ViewModel
class ViewModel(private val context: Context) {  // Android-specific!
    fun checkCamera() {
        if (ContextCompat.checkSelfGrant(...) == GRANTED) {
            // ...
        }
    }
}

// ✅ GOOD: Use abstraction
class ViewModel(private val manager: GrantManager) {  // Platform-agnostic!
    suspend fun checkCamera() {
        val status = manager.checkStatus(AppGrant.CAMERA)
        // ...
    }
}
```

## 📚 Related Patterns

- **Repository Pattern**: `grantManager` is like a repository for grant state
- **Facade Pattern**: `GrantHandler` is a facade over `grantManager`
- **Strategy Pattern**: Different `PlatformGrantDelegate` implementations per platform
- **Observer Pattern**: `StateFlow` for reactive state updates

## 🔗 References

- [Android App Architecture Guide](https://developer.android.com/topic/architecture)
- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Kotlin Multiplatform Documentation](https://kotlinlang.org/docs/multiplatform.html)
- [Android Runtime Grants Best Practices](https://developer.android.com/training/grants/requesting)
- [iOS Authorization Best Practices](https://developer.apple.com/documentation/uikit/protecting_the_user_s_privacy)

---

**Next Steps**:
- Read [GRANTS.md](GRANTS.md) for usage guide
- See [QUICK_START.md](QUICK_START.md) for tutorial
- Check [DEMO_GUIDE.md](DEMO_GUIDE.md) for sample app
