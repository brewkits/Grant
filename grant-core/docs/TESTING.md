# Testing Strategy

## Overview

The KMP Grant library emphasizes **compile-time safety** and **type safety** to prevent runtime errors, reducing the need for extensive unit testing while maintaining high code quality.

## Compile-Time Safety Features

### Type Safety
- **Kotlin Enums**: `GrantType` and `GrantStatus` provide compile-time guarantees
- **Sealed Classes**: Pattern matching with `when` expressions is exhaustive
- **Strong Typing**: No stringly-typed APIs or magic strings

### Null Safety
- **Non-nullable by default**: Kotlin's type system eliminates NPEs
- **Explicit nullable types**: `?` operator makes nullability clear
- **Safe calls**: `?.` and `?:` operators prevent null-related crashes

### Coroutine Safety
- **Structured Concurrency**: All operations scoped to ViewModelScope
- **No Global Scope**: Prevents memory leaks
- **Cancellation Support**: Proper cleanup on scope cancellation

## Manual Testing Checklist

### Android Testing
- [x] Camera grant request and approval
- [x] Multiple grants (Camera + Microphone)
- [x] Grant denial with rationale dialog
- [x] Permanent denial with settings navigation
- [x] Configuration changes (rotation) during request
- [x] Background grants (Location Always)
- [x] GrantHandler in ViewModels
- [x] Transparent activity launch
- [x] Multiple simultaneous grant requests
- [x] GrantGroupHandler for bulk requests

### iOS Testing
- [x] Camera grant via GrantHandler
- [x] Location grant (when in use)
- [x] Multiple grants handling
- [x] Grant denial and re-request
- [x] Settings navigation
- [x] Platform delegate callbacks
- [x] MainThread dispatch for UI operations

## Integration Testing

The **demo app** serves as comprehensive integration tests:

### Covered Scenarios
1. **Individual Grants**: Test each grant type independently
2. **Sequential Grants**: Request multiple grants one after another
3. **Parallel Grants**: Request multiple grants simultaneously
4. **UI State Management**: Verify StateFlow updates
5. **Dialog Flows**: Rationale and settings dialogs
6. **Platform Differences**: Android vs iOS behavior

### Demo App Test Cases
```kotlin
// Test Case 1: Simple camera grant
cameraGrant.request { openCamera() }

// Test Case 2: Multiple grants
locationGroup.request { status ->
    when(status) {
        GrantStatus.GRANTED -> startTracking()
        else -> showError()
    }
}

// Test Case 3: Custom messages
cameraGrant.request(
    rationaleMessage = "Need camera for QR scanning",
    settingsMessage = "Enable camera in settings"
) { openScanner() }
```

## Future Test Plans

### Unit Tests (Planned)
- [ ] `GrantStatusTest` - Enum values and states
- [ ] `GrantTypeTest` - All grant types exist
- [ ] `GrantHandlerTest` - State management and flows
- [ ] `GrantGroupHandlerTest` - Bulk grant handling
- [ ] `MockGrantManager` - Test helper for ViewModels

### UI Tests (Planned)
- [ ] Compose Testing for dialog flows
- [ ] Screenshot tests for UI states
- [ ] End-to-end grant flows

### Platform Tests (Planned)
- [ ] Android Instrumented Tests
- [ ] iOS XCTest Suite
- [ ] Platform delegate behavior verification

## Testing Best Practices

### For Library Users

**1. Mock GrantManager in ViewModels**
```kotlin
class MyViewModelTest {
    @Test
    fun testCameraGrant() = runTest {
        val mockManager = MockGrantManager(
            checkStatusResult = GrantStatus.NOT_DETERMINED,
            requestResult = GrantStatus.GRANTED
        )

        val viewModel = MyViewModel(mockManager)
        viewModel.onCameraClick()

        assertEquals(GrantStatus.GRANTED, viewModel.cameraGrant.status.value)
    }
}
```

**2. Test UI State**
```kotlin
@Test
fun testRationaleDialogShown() = runTest {
    val handler = GrantHandler(mockManager, GrantType.CAMERA, testScope)
    handler.request { }

    assertTrue(handler.state.value.showRationale)
}
```

**3. Test Callbacks**
```kotlin
@Test
fun testCallbackExecuted() = runTest {
    var executed = false
    handler.request { executed = true }
    testScheduler.advanceUntilIdle()

    assertTrue(executed)
}
```

## Test Environment Setup

### Android
```gradle
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.mockk:mockk:1.13.8")
}
```

### iOS
```swift
// XCTest setup
import XCTest
@testable import GrantCore

class GrantManagerTests: XCTestCase {
    func testCameraGrantRequest() async {
        // Test implementation
    }
}
```

## Quality Metrics

### Code Coverage Goals
- Core logic: 80%+
- Platform delegates: 70%+
- UI handlers: 60%+

### Static Analysis
- Kotlin Lint: 0 errors
- Detekt: All rules passing
- SwiftLint: iOS code standards

## Continuous Integration

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Android Tests
        run: ./gradlew :grant-core:testDebugUnitTest
      - name: Run iOS Tests
        run: xcodebuild test -scheme GrantCore
```

## Conclusion

The library's architecture prioritizes **type safety** and **compile-time guarantees** over extensive runtime testing, resulting in:
- Fewer potential runtime errors
- Better IDE support and autocomplete
- Clearer API contracts
- Faster development cycles

The demo app provides comprehensive manual testing coverage, while automated tests will be added incrementally based on user feedback and real-world usage patterns.
