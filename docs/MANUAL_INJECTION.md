# Manual Injection Guide (No-DI Approach)

While Grant provides optional Koin support, you can easily use it in any project without any Dependency Injection framework.

## 1. Core Concept

Grant uses a factory pattern to create instances of `GrantManager` and `ServiceManager` with the correct platform-specific delegates.

## 2. Platform Setup

### 🤖 Android
On Android, you must provide an application `Context`.

```kotlin
// In your Android application or Activity
val grantManager = GrantFactory.create(context)
val serviceManager = ServiceFactory.createServiceManager(context)
```

### 🍎 iOS
On iOS, no context is required.

```kotlin
// In your iOS code (Swift or Kotlin)
val grantManager = GrantFactory.create()
val serviceManager = ServiceFactory.createServiceManager()
```

## 3. Manual Usage in Shared Code

You can create a singleton or a manual container to hold these instances:

```kotlin
object MyGrantContainer {
    lateinit var grantManager: GrantManager
    lateinit var serviceManager: ServiceManager
    
    val checker by lazy { 
        GrantAndServiceChecker(grantManager, serviceManager) 
    }
}

// Android entry point
MyGrantContainer.grantManager = GrantFactory.create(context)
MyGrantContainer.serviceManager = ServiceFactory.createServiceManager(context)

// iOS entry point
MyGrantContainer.grantManager = GrantFactory.create()
MyGrantContainer.serviceManager = ServiceFactory.createServiceManager()
```

## 4. Why Manual Injection?

- **Zero Bloat:** You don't need to include the Koin library in your final binary.
- **Full Control:** You decide exactly when and how the managers are initialized.
- **Simplicity:** For small apps, a simple singleton is often enough.
