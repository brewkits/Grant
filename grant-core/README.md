# 🎯 Grant Core

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-blue.svg)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20iOS-green.svg)](https://kotlinlang.org/docs/multiplatform.html)

The core library for KMP Grant - a production-ready, UI-agnostic grant management system for Kotlin Multiplatform.

## Features

- ✅ **Clean Architecture** - Interface-based design independent of third-party libraries
- ✅ **UI Framework Agnostic** - Works with Compose, SwiftUI, XML, or any UI framework
- ✅ **Reactive** - Built on Kotlin Coroutines and StateFlow
- ✅ **Composition Pattern** - Eliminates ViewModel boilerplate (3 lines instead of 30+)
- ✅ **Zero Dependencies** - Custom implementation with no third-party requirements
- ✅ **Type-Safe** - Compile-time safety with Kotlin enums and sealed classes

## Installation

Add to your `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.brewkits:grant-core:1.0.0")
        }
    }
}
```

## Quick Example

```kotlin
class CameraViewModel(
    grantManager: GrantManager
) : ViewModel() {

    val cameraGrant = GrantHandler(
        grantManager = grantManager,
        grant = GrantType.CAMERA,
        scope = viewModelScope
    )

    fun onCaptureClick() {
        cameraGrant.request { openCamera() }
    }
}
```

## Documentation

- [Architecture Guide](docs/ARCHITECTURE.md) - Design patterns and architecture decisions
- [Grants Reference](docs/GRANTS.md) - Complete grant types and usage guide
- [Quick Start Guide](docs/QUICK_START.md) - Get started in 5 minutes
- [iOS Setup](docs/QUICK_START_iOS.md) - iOS-specific configuration
- [Transparent Activity Guide](docs/TRANSPARENT_ACTIVITY_GUIDE.md) - Android implementation details

## Supported Grants

| Grant | Android | iOS |
|-------|---------|-----|
| 📷 Camera | ✅ | ✅ |
| 🖼️ Gallery | ✅ | ✅ |
| 📍 Location | ✅ | ✅ |
| 📍 Location Always | ✅ | ✅ |
| 🔔 Notification | ✅ | ✅ |
| 🎤 Microphone | ✅ | ✅ |
| 📞 Contacts | ✅ | ✅ |
| 📅 Calendar | ✅ | ✅ |
| 🔵 Bluetooth | ✅ | ✅ |
| 🏃 Motion | ✅ | ✅ |
| 💾 Storage | ✅ | N/A* |

*iOS uses sandboxed storage

## Core Components

### GrantManager
Main interface for checking and requesting grants.

### GrantHandler
Composition helper that eliminates ViewModel boilerplate.

### GrantGroupHandler
Handles multiple grants as a group.

### GrantStatus
Enum representing grant states: GRANTED, DENIED, DENIED_ALWAYS, NOT_DETERMINED.

### GrantType
Enum of all supported grant types.

## Architecture

```
┌─────────────────────────────────────────┐
│         Feature Layer (App Code)         │
│    ViewModel + UI (Compose/SwiftUI)      │
└────────────────┬────────────────────────┘
                 │ depends on
┌────────────────▼────────────────────────┐
│        Abstraction Layer (Core)          │
│  • GrantManager (interface)              │
│  • GrantType (enum)                      │
│  • GrantStatus (enum)                    │
│  • GrantHandler (composition)            │
└────────────────┬────────────────────────┘
                 │ implemented by
┌────────────────▼────────────────────────┐
│     Implementation Layer (Custom)        │
│  • MyGrantManager                        │
│  • PlatformGrantDelegate                 │
│    - androidMain: Activity Result API    │
│    - iosMain: Framework Delegates        │
└─────────────────────────────────────────┘
```

## Requirements

- **Kotlin**: 2.1.21+
- **Android**: API 26+ (Android 8.0)
- **iOS**: 13.0+
- **Koin**: 4.1.1+ (for dependency injection)

## License

```
Copyright 2026 Brewkits

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing grants and
limitations under the License.
```
